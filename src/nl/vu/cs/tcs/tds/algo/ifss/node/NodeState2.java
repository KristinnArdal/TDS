package algo.ifss.node;

import util.LamportClock;

public class NodeState2 {

	private boolean passive;
	private final int nodeId;
	private int black, count, seq, nnodes;
	private LamportClock lc;

	public NodeState2(boolean passive, int mynode, int nnodes) {
		this.passive = passive;
		this.nodeId = mynode;
		this.nnodes = nnodes;
		init();
	}

	private void init(){
		this.count = 0;
		this.black = nodeId;
		this.seq = 0;
		this.lc = new LamportClock();
	}

	public synchronized NodeState2 copy() {
		NodeState2 copy = new NodeState2(this.passive, this.nodeId, this.nnodes);
		copy.count = this.count;
		copy.black = this.black;
		copy.seq = this.seq;
		copy.lc = new LamportClock(this.lc);
		return copy;
	}

	public synchronized boolean isPassive() { return this.passive; }
	public synchronized void setPassive(boolean passive) { this.passive = passive; notifyAll(); }
	public int getNodeId() { return this.nodeId; }
	public synchronized void incCount() { this.count++; }
	public synchronized void decCount() { this.count--; this.passive = false; }
	public synchronized void setCount(int c) {this.count = c;}
	public synchronized void incSeq() { this.seq++; }
	public synchronized int getSeq() { return this.seq; }

	/**
	 * @return the lc
	 */
	public LamportClock getLc() {
		return lc;
	}

	public synchronized int getCount() { return this.count; }
	public synchronized int getBlack() { return this.black; }
	public synchronized void setBlack(int node) { this.black = node; }

	public synchronized void waitUntilPassive() {
		while(!passive)
			try { wait(); } catch (InterruptedException e) {}
	}



	public synchronized void setAllWhite(){} // ????

	public synchronized void incClock() {
		this.lc.inc();
	}

	public synchronized void updateClock(LamportClock other) {
		this.lc.update(other);
	}

}
