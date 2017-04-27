package algo.ftsta.node;

import main.TDS;

import java.util.HashSet;

import algo.ftsta.network.Network5;

public class FailureDetector {

	private int nodeID, nnodes;
	private Network5 network;
	private NodeRunner5 nodeRunner;

	/**
	 * @param nodeID
	 * @param nnodes
	 * @param network
	 * @param nodeRunner
	 */
	public FailureDetector(int nodeID, int nnodes, Network5 network, NodeRunner5 nodeRunner) {
		this.nodeID = nodeID;
		this.nnodes = nnodes;
		this.network = network;
		this.nodeRunner = nodeRunner;
	}

	public synchronized void receiveCrash(int crashedNode) {
		HashSet<Integer> crashedReportUnion = new HashSet<Integer>();
		crashedReportUnion.addAll(nodeRunner.getCRASHED());
		// long start = System.nanoTime();

		if (!crashedReportUnion.contains(crashedNode)) {
			writeString(crashedNode + " crashed");
			nodeRunner.getCRASHED().add(crashedNode);
		}
	}

	private void writeString(String string) {
		TDS.writeString(5, " Node " + nodeID + ":\t" + string);
	}

}
