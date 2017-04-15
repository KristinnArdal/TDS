package main;

import ibis.util.ThreadPool;
import algo.sta.network.Network4;
import algo.sta.node.NodeRunner4;

public class TDSStaticTree implements Runnable { 
	
	private boolean done;
	private NodeRunner4[] nodeRunners;
	private Network4 network;
	private int nnodes;
	private long maxWait;
	
	public TDSStaticTree(int nnodes, long maxWait) {
		this.nnodes = nnodes;
		this.done = false;
		this.nodeRunners = new NodeRunner4[nnodes];
		this.network = new Network4(nnodes);
		this.maxWait = maxWait;
	}
	
	public synchronized void setDone(){
		done = true;
		notifyAll();
	}
	
	private synchronized void waitTillDone(){
		while(!done){
			try{
				ThreadPool.createNew(() -> {
					try{
						Thread.sleep(maxWait);
					}catch(Exception e){
						//ignore
					}
					TDS.writeString(-1, "[   STA ]\tNO TERMINATION DETECTED IN " + maxWait + " ms" );
    				this.setDone();
				}, "TimeoutCount_1" );
				wait();
			}catch(InterruptedException e){
				
			}
		}
	}
	
	public void run(){

		for(int i = 0; i < nnodes; i++)
			nodeRunners[i] = new NodeRunner4(i, nnodes, network, i == 0);
		
		network.waitForAllNodes();
		waitTillDone();
		network.killNodes();
		TDS.instance().setDone(1);
	}
	
	public synchronized void announce(){
		done = true;
		notifyAll();
	}
	
}
