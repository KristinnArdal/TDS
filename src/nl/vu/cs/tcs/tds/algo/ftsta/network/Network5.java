package algo.ftsta.network;

import java.util.HashSet;
import java.util.Random;

import ibis.util.ThreadPool;

import main.TDS;

import algo.ftsta.node.NodeRunner5;
//import algo.ftsta.node.FailureDetector;
import algo.ftsta.message.Message;

public class Network5 {

	private final int nnodes;
	private final int max_messages;
	private final NodeRunner5[] nodeRunners;
	private final NodeCrasher nc;
	//private final FailureDetector[] fds;

	private int nodeCount = 0;
	private int DELAY_MAX = 50;
	private Random random = new Random();

	private long lastIdle;

	private HashSet<Integer> crashedNodes = new HashSet<Integer>();


	// performance counters
	private volatile int nrBMessages = 0; // basic messages
	private volatile int nrCMessages = 0; // control messages

	public Network5(int nnodes, int max_messages) {
		this.nnodes = nnodes;
		this.max_messages = max_messages;
		nodeRunners = new NodeRunner5[nnodes];
		this.nc = new NodeCrasher(this, nnodes);
		//this.fds = new FailureDetector[nnodes];
	}

	public synchronized void registerNode(NodeRunner5 nodeRunner) {
		this.nodeCount++;
		nodeRunners[nodeRunner.getId()] = nodeRunner;
		//fds[nodeRunner.getId()] = new FailureDetector(nodeRunner.getId(), nnodes, this, nodeRunner);

		if (nodeCount == nnodes) {
			notifyAll();
		}
	}

	public void registerIdle(int NodeID) {
		ThreadPool.createNew(new Runnable() {
			@Override
			public void run() {
				synchronized (Network5.class) {
					lastIdle = System.currentTimeMillis();
				}
			}
		}, "IdleRegister");
	}
		

	public void sendMessage(final int destination, final Message message) {
		// If the node has crashed it does not need to be sent in the simulation
		if (crashedNodes.contains(destination)) {
			return;
		}

		// check the type of the message and count it
		switch (message.getType()) {
			case Message.E_PROPOSE:
			case Message.E_ACCEPT:
			case Message.E_REJECT:
				break;
			case Message.M_S:
				nrBMessages += 1;
				nrCMessages += 1;
				break;
			case Message.M_ACK:
			case Message.R_S:
			case Message.R_ACK:
			case Message.STOP:
				nrCMessages += 1;
				break;
			case Message.SNAP:
			case Message.CONN_S:
			case Message.CONN_ACK:
			case Message.CONN_REJ:
			case Message.DCONN:
				nrCMessages += 1;
				break;
			default:
				break;
		}

		final int delay = random.nextInt(DELAY_MAX);
		ThreadPool.createNew(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(delay);
				} catch (InterruptedException e) {
					// ignore
				}
				nodeRunners[destination].receiveMessage(message);
			}
		}, "Sender");
	}

	public synchronized void waitForAllNodes() {
		while (nodeCount < nnodes) {
			try {
				wait();
			} catch (InterruptedException e) {
				//ignore
			}
		}
		this.startAllNodes();
	}
	
	public synchronized void echoFinished() {
		nc.start();
		startAllNodes();
	}

	public synchronized void startAllNodes() {
		for (NodeRunner5 r : nodeRunners) {
			r.start();
		}
	}


	public void killNodes() {
		for (NodeRunner5 r : nodeRunners) {
			r.stopRunning();
		}
	}

	public int selectTarget(int mynode) {
		int dest;
		if (nnodes == 1) {
			return mynode;
		}
		do {
			dest = random.nextInt(nnodes);
		} while (dest == mynode);
		return dest;
	}

	public boolean allowedToSend() {
		return max_messages == -1 || nrBMessages < max_messages;
	}

	public void announce() {
		TDS.writeString(5, " Network:\tTermination declared " + (System.currentTimeMillis() - this.lastIdle) + " milliseconds after last node became passive");
		TDS.writeString(5, " Network:\tNumber of basic messages: " + this.nrBMessages);
		TDS.writeString(5, " Network:\tNumber of control messages: " + this.nrCMessages);
		ThreadPool.createNew(new Runnable() {
			@Override
			public void run() {
				TDS.instance().announce(5);
			}
		}, "TDS_1");
	}

	public synchronized void crash(int nodeID) {
		if(nodeRunners[nodeID] != null ) {
			nodeRunners[nodeID].crash();
			crashedNodes.add(nodeID);
		}
	}

	public synchronized void sendCrashedMessage(int receiver, int crashedNode) {
		if (crashedNodes.contains(receiver)) { return; }
		ThreadPool.createNew(new Runnable() {
			@Override
			public void run() {
				nodeRunners[receiver].receiveCrash(crashedNode);
			}
		}, "crashNotifier");
		return;
	}

}
