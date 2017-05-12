package main;

import algo.ds.network.Network6;
import algo.ds.node.NodeRunner6;
import ibis.util.ThreadPool;

public class TDSDijkstraScholten implements Runnable {
	private boolean done;
	private NodeRunner6[] nodeRunners;
	private Network6 network;
	private int nnodes;
	private long maxWait;

	/**
	 * @param nnodes
	 * @param maxWait
	 * @param max_messages
	 */
	public TDSDijkstraScholten(int nnodes, long maxWait, int max_messages) {
		this.nnodes = nnodes;
		this.maxWait = maxWait;
		this.nodeRunners = new NodeRunner6[nnodes];
		this.network = new Network6(nnodes, max_messages);
	}

	public synchronized void setDone(){
		done = true;
		notifyAll();
	}

	private synchronized void waitTillDone() {
		while (!done) {
			try{
				ThreadPool.createNew(() -> {
					try {
						Thread.sleep(maxWait);
					} catch (Exception e) {
						//ignore
					}
					TDS.writeString(-1, " [DS]\tNO TERMINATION DETECTED IN " + maxWait + " ms" );
					this.setDone();
				}, "TimeoutCount_6");
				wait();
			} catch (InterruptedException e) {
				// ignore
			}
		}
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		for (int i = 0; i < nnodes; i++) {
			nodeRunners[i] = new NodeRunner6(i, nnodes, network);
		}

		network.waitForAllNodes();
		waitTillDone();
		network.killNodes();
		TDS.instance().setDone(6);

	}

	public synchronized void announce() {
		done = true;
		notifyAll();
	}
}
