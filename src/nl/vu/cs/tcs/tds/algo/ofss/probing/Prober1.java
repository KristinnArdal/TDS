package algo.ofss.probing;

import main.TDS;
import performance.PerformanceLogger;
import algo.ofss.network.Network1;
import algo.ofss.node.NodeRunner1;
import util.Color;

public class Prober1 {
	
	private final int totalNodes;
	private final int mynode;
	private final Network1 network;
	private final NodeRunner1 nodeRunner;
	private boolean waitNodeRunner;
	
	public Prober1(int mynode, int totalNodes, Network1 network, NodeRunner1 nodeRunner){
		this.nodeRunner = nodeRunner;
		this.totalNodes = totalNodes;
		this.mynode = mynode;
		this.network = network;
		if(mynode == 0){
			ProbeMessage1 probeMessage = new ProbeMessage1(mynode, totalNodes, this.nodeRunner.getState().getLc());
			PerformanceLogger.instance().incTokens(1);
			sendProbeMessage(0, probeMessage);
		}
	}
	
    private void writeString(String s) {
        TDS.writeString(1," Node " + mynode + ": \t" + s);
    }
	
	public synchronized void receiveMessage(ProbeMessage1 probeMessage){
		nodeRunner.updateClock(probeMessage.getLc());
		this.waitUntilPassive();
		long start = System.nanoTime();
		if(nodeRunner.getId() == totalNodes - 1){
			if(probeMessage.consistentSnapshot(nodeRunner)){
				writeString("TERMINATION DETECTED");
				writeString("Lamports clock at end: " + nodeRunner.getState().getLc());
				writeString("Termination detected "
						+ (System.currentTimeMillis() - network.getLastPassive())
						+ " milliseconds after last node became passive.");
				long end = System.nanoTime();
				PerformanceLogger.instance().addProcTime(1, end - start);
				network.printStatistics();
				TDS.instance().announce(1);
			}
			else
				retransmit(probeMessage);
		}else{
			propagate(probeMessage);
		}
		long end = System.nanoTime();
		PerformanceLogger.instance().addProcTime(1, end - start);
	}
	
	private void retransmit(ProbeMessage1 probeMessage){
		probeMessage.zeroCount();
		probeMessage.setColor(Color.WHITE);
		nodeRunner.setColor(Color.WHITE);

		probeMessage.setLc(nodeRunner.getState().getLc());
		
		PerformanceLogger.instance().incTokens(1);
		PerformanceLogger.instance().addTokenBits(1, probeMessage.copy());
		sendProbeMessage(0, probeMessage);
	}
	
	private void propagate(ProbeMessage1 probeMessage){
		probeMessage.addToCount(nodeRunner.getState().getCount());
		if(nodeRunner.getState().getColor() == Color.BLACK)
			probeMessage.setColor(Color.BLACK);
		nodeRunner.setColor(Color.WHITE);

		probeMessage.setLc(nodeRunner.getState().getLc());
		
		PerformanceLogger.instance().incTokens(1);
		PerformanceLogger.instance().addTokenBits(1, probeMessage.copy());
		sendProbeMessage(mynode == (totalNodes - 1)? 0: mynode + 1, probeMessage);
	}

	private void sendProbeMessage(int destination, ProbeMessage1 probeMessage) {
		network.sendProbeMessage(destination, probeMessage);
		nodeRunner.incClock();
	}
	
	

	public synchronized void nodeRunnerStopped() {
		this.waitNodeRunner = false;
		notifyAll();
	}
	
    private void waitUntilPassive(){
    	this.waitNodeRunner = !this.nodeRunner.isPassive();
    	while(waitNodeRunner){
    		writeString("PROBE waiting node for passive");
    		try {
				wait();
			} catch (InterruptedException e) {
				//ignore
			}
    	}
    }

}
