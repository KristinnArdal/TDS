package algo.ofss.node;

import util.Color;
import util.LamportClock;

public class NodeState1 {
	private boolean passive;
	private final int nodeNumber;
	private int color;
	private int nnodes;
	private int count;
	private LamportClock lc;
	
	public NodeState1(boolean passive, int nodeNumber, int nnodes){
		this.passive = passive;
		this.nodeNumber = nodeNumber;
		this.nnodes = nnodes;
		this.color = Color.WHITE;
		this.lc = new LamportClock();
	}
	
	public synchronized NodeState1 copy(){
		NodeState1 s = new NodeState1(passive, nodeNumber, nnodes);
		s.count = this.count;
		s.color = this.color;
		s.lc = new LamportClock(lc);
		return s;
	}
	
	public synchronized boolean isPassive(){
		return this.passive;
	}
	
	public int getNodeNumber(){
		return this.nodeNumber;
	}
	
	public synchronized void setColor(int color){
		this.color = color;
	}
	
	public synchronized int getColor(){
		return this.color;
	}
	
	public synchronized void incCount(){
		this.count++;
	}
	
	public synchronized void decCount(){
		this.count--;
	}
	
	public synchronized int getCount(){
		return this.count;
	}
	
	public synchronized void waitUntilPassive(){
		while(!passive){
			try{
				wait();
			}catch (InterruptedException e){
				//ignore
			}
		}
	}

	public synchronized void setPassive(boolean b) {
		this.passive = b;
	}

	public synchronized void updateClock(LamportClock other) {
		lc.update(other);
	}

	public synchronized void incClock() {
		lc.inc();
	}

	public synchronized LamportClock getLc() {
		return lc;
	}

}
