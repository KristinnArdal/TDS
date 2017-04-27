package main;

import ibis.util.ThreadPool;
import algo.ftsta.network.Network5;
import algo.ftsta.node.NodeRunner5;

public class TDSFTStaticTree implements Runnable { 
	
	private boolean done;
	private NodeRunner5[] nodeRunners;
	private Network5 network;
	private int nnodes;
	private long maxWait;
	
	public TDSFTStaticTree(int nnodes, long maxWait) {
		this.nnodes = nnodes;
		this.done = false;
		this.nodeRunners = new NodeRunner5[nnodes];
		this.network = new Network5(nnodes);
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
					TDS.writeString(-1, "[ FTSTA ]\tNO TERMINATION DETECTED IN " + maxWait + " ms" );
    				this.setDone();
				}, "TimeoutCount_1" );
				wait();
			}catch(InterruptedException e){
				
			}
		}
	}
	
	public void run(){

		for(int i = 0; i < nnodes; i++)
			nodeRunners[i] = new NodeRunner5(i, nnodes, network, i == 0);
		
		network.waitForAllNodes();
		waitTillDone();
		network.killNodes();
		TDS.instance().setDone(5);
	}
	
	public synchronized void announce(){
		done = true;
		notifyAll();
	}
	
}
