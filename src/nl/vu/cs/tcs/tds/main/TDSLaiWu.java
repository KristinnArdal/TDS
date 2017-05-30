package main;

import algo.lw.network.Network7;
import algo.lw.node.NodeRunner7;

import ibis.util.ThreadPool;

public class TDSLaiWu implements Runnable {
	private boolean done;
	private NodeRunner7[] nodeRunners;
	private Network7 network;
	private int nnodes;
	private long maxWait;

	/**
	 * @param nnodes
	 * @param maxWait
	 */
	public TDSLaiWu(int nnodes, long maxWait, int max_messages) {
		this.nnodes = nnodes;
		this.done = false;
		this.nodeRunners = new NodeRunner7[nnodes];
		this.network = new Network7(nnodes, max_messages);
		this.maxWait = maxWait;
	}

	public synchronized void setDone() {
		done = true;
		notifyAll();
	}

	private synchronized void waitTillDone() {
		while (!done) {
			try {
				ThreadPool.createNew(() -> {
					try {
						Thread.sleep(maxWait);
					} catch(Exception e) {
						// ignore
					}
					TDS.writeString(-1, "[   LW }\tNO TERMINATION DETECTED IN " + maxWait + " ms");
					this.setDone();
				}, "TimeoutCount_7");
				wait();
			} catch (InterruptedException e) {
			}
		}
	}

	@Override
	public void run() {
		for (int i = 0; i < nnodes; i++) {
			nodeRunners[i] = new NodeRunner7(i, nnodes, network);
		}

		network.waitForAllNodes();
		waitTillDone();
		network.killNodes();
		TDS.instance().setDone(7);
	}

	public synchronized void announce() {
		done = true;
		notifyAll();
	}

}
