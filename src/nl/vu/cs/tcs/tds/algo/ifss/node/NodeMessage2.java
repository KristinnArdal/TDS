package algo.ifss.node;

import java.io.Serializable;

public class NodeMessage2 implements Serializable {
		private static final long serialVersionUID = 2L;
    public int sender;
    public int seq;
    
    public NodeMessage2(int sender, int seq){
        this.sender = sender;
        this.seq = seq;
    }
    
    public int getSeq(){
        return this.seq;
    }
    
    public int getSenderId(){
        return this.sender;
    }

}
