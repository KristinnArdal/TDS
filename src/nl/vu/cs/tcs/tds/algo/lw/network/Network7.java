package algo.lw.network;

import java.util.HashSet;
import java.util.Random;

import algo.lw.message.Message;
import algo.lw.node.NodeRunner7;

import ibis.util.ThreadPool;

import main.TDS;


public class Network7 {
	private final int nnodes;
	private final int max_messages;

	private final NodeRunner7[] nodeRunners;
	private final NodeCrasher nc;
	private int nodeCount = 0;
	private long lastPassive;

	private Random random = new Random();

	private volatile int nrBMessages = 0;
	private volatile int nrCMessages = 0;

	private int[][] message_count;

	private HashSet<Integer> crashedNodes = new HashSet<Integer>();

	/* CONSTANTS */
	private final int MAX_MESSAGE_DELAY = 50;

	/**
	 * @param nnodes
	 * @param max_messages
	 */
	public Network7(int nnodes, int max_messages) {
		this.nnodes = nnodes;
		this.max_messages = max_messages;
		message_count = new int[nnodes][nnodes];
		nodeRunners = new NodeRunner7[nnodes];
		nc = new NodeCrasher(this, nnodes);
	}

	public synchronized void registerNode(NodeRunner7 nodeRunner) {
		this.nodeCount++;
		nodeRunners[nodeRunner.getId()] = nodeRunner;

		if (nodeCount == nnodes) {
			notifyAll();
		}
	}

	public void registerPassive() {
		ThreadPool.createNew(new Runnable() {
			@Override
			public void run() {
				synchronized (Network7.class) {
					lastPassive = System.currentTimeMillis();
				}
			}
		}, "PassiveRegister");
	}


	public boolean allowedToSend() {
		return max_messages == -1 || nrBMessages < max_messages;
	}

	public void returnFlush(final int destination, final Message message) {
			final int delay = random.nextInt(MAX_MESSAGE_DELAY);
			ThreadPool.createNew(new Runnable() {
				@Override
				public void run() {
					do {
						try {
							Thread.sleep(50);
						} catch (InterruptedException e) {
							//ignore
						}
					} while (!isZeroMessageCount(destination, message.getSender()));

					try {
						Thread.sleep(delay);
					} catch (InterruptedException e) {
						// ignore
					}
					final int sender = message.getSender();
					message.setSender(destination);
					nodeRunners[sender].receiveMessage(message);
				}
			}, "Sender");
			return;
		}

	public void sendMessage(final int destination, final Message message) {
		if (message.getType() == Message.Type.RFLUSH) {
			// wait until all messages have been send from destination to the sender of the flush message
			//TDS.writeString(7, "[NETWORK]: sender - " + message.getSender() + ", destination - " + destination);
			do {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					//ignore
				}
			} while (!isZeroMessageCount(destination, message.getSender()));

			final int delay = random.nextInt(MAX_MESSAGE_DELAY);
			ThreadPool.createNew(new Runnable() {
				@Override
				public void run() {
					try {
						Thread.sleep(delay);
					} catch (InterruptedException e) {
						// ignore
					}
					final int sender = message.getSender();
					message.setSender(destination);
					nodeRunners[sender].receiveMessage(message);
				}
			}, "Sender");
			return;
		}

		if (message.getType() == Message.Type.BASIC) {
			nrBMessages += 1;
		}
		else if (message.getType() == Message.Type.ACK) {
			nrCMessages += 1;
		}

		synchronized(this) {
			if (crashedNodes.contains(destination)) {
				return;
			}
			else {
				incMessageCount(message.getSender(), destination);
			}
		}

		final int delay = random.nextInt(MAX_MESSAGE_DELAY);
		ThreadPool.createNew(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(delay);
				} catch (InterruptedException e) {
					// ignore
				}
				nodeRunners[destination].receiveMessage(message);
				decMessageCount(message.getSender(), destination);
			}
		}, "Sender");
	}

	public synchronized void waitForAllNodes() {
		while (nodeCount < nnodes) {
			try {
				wait();
			} catch (InterruptedException e) {
				//ignore
			}
		}
		this.startAllNodes();
		nc.start();
	}

	public synchronized void startAllNodes() {
		for (NodeRunner7 r : nodeRunners) {
			r.start();
		}
	}

	public void killNodes() {
		for (NodeRunner7 r : nodeRunners) {
			r.stopRunning();
		}
	}

	public int selectTarget(int nodeID) {
		int dest;
		if (nnodes == 1) {
			return nodeID;
		}
		do {
			dest = random.nextInt(nnodes);
		} while (dest == nodeID);
		return dest;
	}

	public void announce() {
		// stub, write statistics and tell TDS that it has to terminate
		TDS.writeString(7, " Network:\tTermination declared " + (System.currentTimeMillis() - lastPassive) + " milliseconds after last node became passive");
		TDS.writeString(7, " Network:\tNumber of basic messages: " + this.nrBMessages);
		TDS.writeString(7, " Network:\tNumber of control messages: " + this.nrCMessages);
		ThreadPool.createNew(new Runnable() {
			@Override
			public void run() {
				TDS.instance().announce(7);
			}
		}, "TDS_7");
	}

	private synchronized void incMessageCount(int source, int destination) {
		message_count[source][destination]++;
	}

	private synchronized void decMessageCount(int source, int destination) {
		message_count[source][destination]--;
	}

	private boolean isZeroMessageCount(int source, int destination) {
		return message_count[source][destination] == 0;
	}

	public synchronized void crash(final int nodeID) {
		if(nodeRunners[nodeID] != null) {
			crashedNodes.add(nodeID);
			ThreadPool.createNew(new Runnable() {
				@Override
				public void run() {
					nodeRunners[nodeID].crash();
				}
			}, "crasher");
		}
	}

	public synchronized void sendCrashMessage(int receiver, int crashedNode) {
		if (crashedNodes.contains(receiver)) { return; }
		ThreadPool.createNew(new Runnable() {
			@Override
			public void run() {
				nodeRunners[receiver].receiveCrash(crashedNode);
			}
		}, "crashNotifier");
		return;
	}
}
