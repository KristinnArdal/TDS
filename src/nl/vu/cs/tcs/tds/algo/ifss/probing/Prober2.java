package algo.ifss.probing;

import java.io.Serializable;

import ibis.util.ThreadPool;
import main.TDS;
import performance.PerformanceLogger;
import algo.ifss.network.Network2;
import algo.ifss.node.NodeRunner2;
import algo.ifss.node.NodeState2;

public class Prober2{
	private final int totalNodes;
	private final int mynode;
	private final Network2 network;

	private final NodeRunner2 nodeRunner;
	private boolean holdsToken = false;
	private boolean waitNodeRunner = false;

	public Prober2(int mynode, int totalNodes, Network2 network, NodeRunner2 nodeRunner) {
		this.nodeRunner = nodeRunner;
		this.totalNodes = totalNodes;
		this.mynode = mynode;
		this.network = network;

		if(mynode == 0) {
			ProbeMessage2 token = new ProbeMessage2(mynode, totalNodes, nodeRunner.getState().getLc());
			PerformanceLogger.instance().addTokenBits(2, token.copy());
			network.sendFirstProbeMessage(0, token);
			nodeRunner.incClock();
		}
	}

	public synchronized void receiveFirstMessage(ProbeMessage2 token) {
		nodeRunner.updateClock(token.getLc());

		writeString("Starting Probing!!!");
		this.waitUntilPassive();
		long start = System.nanoTime();
		token.incCount(nodeRunner.getState().getCount());
		nodeRunner.setBlack(nodeRunner.furthest(nodeRunner.getBlack(), token.getBlack()));

		//Don't check for termination here. Its impossible

		//propagate token!
		token.setSender(0);
		token.setLc(nodeRunner.getState().getLc());
		sendProbeMessage((mynode + 1) % totalNodes, token);
		nodeRunner.setCount(0);
		nodeRunner.setBlack(mynode);
		nodeRunner.incSeq();
		long end = System.nanoTime();
		PerformanceLogger.instance().addProcTime(2, end - start);

	}


	public synchronized void receiveMessage(ProbeMessage2 token) {
		nodeRunner.updateClock(token.getLc());

		this.waitUntilPassive();
		long start = System.nanoTime();
		writeString("Handling Token");
		token.incCount(nodeRunner.getState().getCount());

		//writeString("new count_t= " + token.getCount());

		nodeRunner.setBlack(nodeRunner.furthest(nodeRunner.getBlack(), token.getBlack()));

		//writeString("new black_t= " + token.getBlack());


		if((token.getCount() == 0) && (nodeRunner.getBlack() == nodeRunner.getId())){
			//if(token.getCount() == 0 && state.getBlack() == mynode){
			writeString("TERMINATION DETECTED");
			writeString("Lamports clock at end: " + nodeRunner.getState().getLc());
			writeString("Termination detected "
					+ (System.currentTimeMillis() - network.getLastPassive())
					+ " milliseconds after last node became passive.");

			long end = System.nanoTime();
			PerformanceLogger.instance().addProcTime(2, end - start);
			network.printStatistics();
			TDS.instance().announce(2);
		}else{
			writeString("INCONSISTENT SNAPSHOT");
			propagate(token);
		}

		long end = System.nanoTime();
		PerformanceLogger.instance().addProcTime(5, end - start);
		}

		private void propagate(ProbeMessage2 token) {
			token.setBlack(nodeRunner.furthest(nodeRunner.getState().getBlack(), (mynode + 1) % totalNodes));
			token.setSender(mynode);
			token.setLc(nodeRunner.getState().getLc());
			sendProbeMessage((mynode + 1) % totalNodes, token);
			nodeRunner.setCount(0);
			nodeRunner.setBlack(mynode);
			nodeRunner.incSeq();

			PerformanceLogger.instance().incTokens(2);
			PerformanceLogger.instance().addTokenBits(2, token.copy());

		}



		private void waitUntilPassive() {
			//this.waitNodeRunner = !this.nodeRunner.isPassive();
			while(!this.nodeRunner.isPassive()) {
				//synchronized(nodeRunner){
				writeString("PROBE waiting node for passive");
				try { wait(); } catch(InterruptedException e) {} //maybe remove break
				//}
			}
		}

		private void sendProbeMessage(int destination, ProbeMessage2 probeMessage) {
			network.sendProbeMessage(destination, probeMessage);
			nodeRunner.incClock();
		}

		public synchronized void nodeRunnerStopped() {
			this.waitNodeRunner = false;
			notifyAll();
		}

		private void writeString(String s) {
			TDS.writeString(2, " Node " + mynode + ": \t" + s);
		}
	}
