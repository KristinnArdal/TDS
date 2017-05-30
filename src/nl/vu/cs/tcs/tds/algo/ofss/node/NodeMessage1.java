package algo.ofss.node;

import util.LamportClock;

public class NodeMessage1 {
	
	public int sender;
	private LamportClock lc;
	
	public NodeMessage1(int sender, LamportClock lc){
		this.sender = sender;
		this.lc = new LamportClock(lc);
	}
	
	public int getSenderId(){
		return this.sender;
	}

	/**
	 * @return the lc
	 */
	public LamportClock getLc() {
		return lc;
	}

}
