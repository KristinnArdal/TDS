package algo.sta.network;

import java.util.Random;

import ibis.util.ThreadPool;
import main.TDS;
import performance.PerformanceLogger;
import algo.sta.node.NodeRunner4;
import algo.sta.message.Message;

public class Network4 {

	private final int nnodes;
	private final NodeRunner4[] nodeRunners;
	private int nodeCount = 0;
	private int DELAY_MAX = 50;
	private Random random = new Random();
	protected long lastPassive;

	public Network4(int nnodes) {
		this.nnodes = nnodes;
		nodeRunners = new NodeRunner4[nnodes];
	}

	public synchronized void registerNode(NodeRunner4 nodeRunner) {
		this.nodeCount++;
		nodeRunners[nodeRunner.getId()] = nodeRunner;

		if (nodeCount == nnodes) {
			notifyAll();
		}
	}

	public void sendMessage(final int destination, final Message message) {
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

	public void announce() {
		ThreadPool.createNew(new Runnable() {
			@Override
			public void run() {
				TDS.instance().announce(4);
			}
		}, "TDS_1");
	}

}
