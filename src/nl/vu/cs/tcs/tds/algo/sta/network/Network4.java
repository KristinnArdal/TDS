package algo.sta.network;

import java.util.Random;

import ibis.util.ThreadPool;
import main.TDS;
import performance.PerformanceLogger;
import algo.sta.node.NodeRunner4;
import algo.sta.message.Message;

public class Network4 {

	private final int nnodes;
	private final int max_messages;
	private final NodeRunner4[] nodeRunners;
	private int nodeCount = 0;
	private int DELAY_MAX = 50;
	private Random random = new Random();
	protected long lastIdle;

	// performance counters
	private volatile int nrBMessages = 0; // basic messages
	private volatile int nrCMessages = 0; // control messages

	public Network4(int nnodes, int max_messages) {
		this.nnodes = nnodes;
		this.max_messages = max_messages;
		nodeRunners = new NodeRunner4[nnodes];
	}

	public synchronized void registerNode(NodeRunner4 nodeRunner) {
		this.nodeCount++;
		nodeRunners[nodeRunner.getId()] = nodeRunner;

		if (nodeCount == nnodes) {
			notifyAll();
		}
	}

	public void registerIdle(int NodeID) {
		ThreadPool.createNew(new Runnable() {
			@Override
			public void run() {
				synchronized (Network4.class) {
					lastIdle = System.currentTimeMillis();
				}
			}
		}, "IdleRegister");
	}
		

	public void sendMessage(final int destination, final Message message) {
		// check the type of the message and count it
		switch (message.getType()) {
			case Message.E_PROPOSE:
			case Message.E_ACCEPT:
			case Message.E_REJECT:
				break;
			case Message.M_S:
				nrBMessages += 1;
				nrCMessages += 1;
				break;
			case Message.M_ACK:
			case Message.R_S:
			case Message.R_ACK:
			case Message.STOP:
				nrCMessages += 1;
				break;
			default:
				break;
		}
		final int delay = random.nextInt(DELAY_MAX);
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
		for (NodeRunner4 r : nodeRunners) {
			r.start();
		}
	}


	public void killNodes() {
		for (NodeRunner4 r : nodeRunners) {
			r.stopRunning();
		}
	}

	public int selectTarget(int mynode) {
		int dest;
		if (nnodes == 1) {
			return mynode;
		}
		do {
			dest = random.nextInt(nnodes);
		} while (dest == mynode);
		return dest;
	}

	public boolean allowedToSend() {
		return max_messages == -1 || nrBMessages < max_messages;
	}

	public void announce() {
		TDS.writeString(4, " Network:\tTermination declared " + (System.currentTimeMillis() - this.lastIdle) + " milliseconds after last node became passive");
		TDS.writeString(4, " Network:\tNumber of basic messages: " + this.nrBMessages);
		TDS.writeString(4, " Network:\tNumber of control messages: " + this.nrCMessages);
		ThreadPool.createNew(new Runnable() {
			@Override
			public void run() {
				TDS.instance().announce(4);
			}
		}, "TDS_1");
	}

}
