package algo.fts.node;

import util.LamportClock;

public class NodeMessage3 {
    
    private int sender, seq;
		private LamportClock lc;

    public NodeMessage3(int sender, int seq, LamportClock lc) {
        this.sender = sender;
        this.seq = seq;
				this.lc = new LamportClock(lc);
    }

    public int getSenderId() {
        return this.sender;
    }
    
    public int getSeq() {
        return this.seq;
	}

	/**
	 * @return the lc
	 */
	public LamportClock getLc() {
		return lc;
	}

}
