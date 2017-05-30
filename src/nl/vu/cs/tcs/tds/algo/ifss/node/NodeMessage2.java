package algo.ifss.node;

import java.io.Serializable;

import util.LamportClock;

public class NodeMessage2 implements Serializable {
		private static final long serialVersionUID = 2L;
		private LamportClock lc;
    public int sender;
    public int seq;
    
    public NodeMessage2(int sender, int seq, LamportClock lc){
        this.sender = sender;
        this.seq = seq;
				this.lc = new LamportClock(lc);
    }
    
	/**
	 * @return the lc
	 */
	public LamportClock getLc() {
		return lc;
	}

	public int getSeq(){
        return this.seq;
    }
    
    public int getSenderId(){
        return this.sender;
    }

}
