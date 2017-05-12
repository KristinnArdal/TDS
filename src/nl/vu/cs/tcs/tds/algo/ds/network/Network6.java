package algo.ds.network;

import java.util.Random;

import algo.ds.node.NodeRunner6;

import ibis.util.ThreadPool;

import main.TDS;

import algo.ds.message.Message;

public class Network6 {
	private final int nnodes;
	private final int max_messages;

	private final NodeRunner6[] nodeRunners;
	private int nodeCount = 0;
	private long lastPassive;

	private Random random = new Random();

	private volatile int nrBMessages = 0;
	private volatile int nrCMessages = 0;

	/* CONSTANTS */
	private final int MAX_MESSAGE_DELAY = 50;

	/**
	 * @param nnodes
	 * @param max_messages
	 */
	public Network6(int nnodes, int max_messages) {
		this.nnodes = nnodes;
		this.max_messages = max_messages;
		nodeRunners = new NodeRunner6[nnodes];
	}

	public synchronized void registerNode(NodeRunner6 nodeRunner) {
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
				synchronized (Network6.class) {
					lastPassive = System.currentTimeMillis();
				}
			}
		}, "PassiveRegister");
	}


	public boolean allowedToSend() {
		return max_messages == -1 || nrBMessages < max_messages;
	}

	public void sendMessage(final int destination, final Message message) {
		if (message.getType() == Message.Type.BASIC) {
			nrBMessages += 1;
		}
		else if (message.getType() == Message.Type.ACK) {
			nrCMessages += 1;
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
	}

	public synchronized void startAllNodes() {
		for (NodeRunner6 r : nodeRunners) {
			r.start();
		}
	}

	public void killNodes() {
		for (NodeRunner6 r : nodeRunners) {
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
		TDS.writeString(6, " Network:\tTermination declared " + (System.currentTimeMillis() - lastPassive) + " milliseconds after last node became passive");
		TDS.writeString(6, " Network:\tNumber of basic messages: " + this.nrBMessages);
		TDS.writeString(6, " Network:\tNumber of control messages: " + this.nrCMessages);
		ThreadPool.createNew(new Runnable() {
			@Override
			public void run() {
				TDS.instance().announce(6);
			}
		}, "TDS_6");
	}
}
