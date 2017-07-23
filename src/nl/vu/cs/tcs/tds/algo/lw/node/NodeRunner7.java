package algo.lw.node;

import java.util.Arrays;
import java.util.Random;
import java.util.TreeSet;

import algo.lw.message.Message;
import algo.lw.message.Message.Type;
import algo.lw.network.Network7;

import main.TDS;

import util.LamportClock;
import util.Options;

public class NodeRunner7 implements Runnable {

	private final int nodeID;
	private final int nnodes;
	private final Network7 network;
	private Random random = new Random();

	private boolean root;
	private boolean inTree = false;
	private int parent = -1;
	private int[] out;
	private int child_count;
	private int[] in;
	

	private boolean active;

	private boolean mustStop = false;
	private boolean started = false;

	private LamportClock lc;

	// Sets used for fault tolerance
	private TreeSet<Integer> DF;
	private TreeSet<Integer> KF;

	private int[] numFaulty;

	/* CONSTANTS */

	private final int MAX_ACTIVITY_DELAY = 1000;

	/**
	 * @param nodeID
	 * @param nnodes
	 * @param network
	 */
	public NodeRunner7(int nodeID, int nnodes, Network7 network) {
		this.nodeID = nodeID;
		this.nnodes = nnodes;
		this.network = network;
		this.lc = new LamportClock();

		this.DF = new TreeSet<Integer>();
		this.KF = new TreeSet<Integer>();

		this.numFaulty = new int[nnodes];

		this.active = this.inTree = this.root = this.isRoot();
		this.out = new int[nnodes];
		this.child_count = 0;
		this.in = new int[nnodes];

		network.registerNode(this);
		Thread t = new Thread(this);
		t.start();
	}

	/**
	 * @return the nodeID
	 */
	public int getId() { return nodeID; }

	private boolean isRoot() { return nodeID == getRoot(); }

	private synchronized void setPassive() {
		network.registerPassive();
		this.active = false;
	}

	private void writeString(String s) {
		TDS.writeString(7, "Node " + nodeID + ": \t" + s);
	}

	public synchronized void sendMessage(int target, Type type, int value) {
		switch (type) {
			case BASIC:
				writeString("Sending BASIC message to " + target);
				out[target]++;
				child_count++;
				break;
			case ACK:
				writeString("Sending ACK message to " + target);
				break;
			case RFLUSH:
				writeString("Sending RFLUSH message to " + target);
				break;
		}

		network.sendMessage(target, new Message(this.nodeID, type, value, this.lc, this.DF)); // DF is only needed for ACK messages
		lc.inc();
	}

	public synchronized void receiveMessage(Message message) {
		if (mustStop) { return; }
		Type type = message.getType();
		int sender = message.getSender();
		int value = message.getValue();
		this.lc.update(message.getLc());
		writeString("Received a message from " + sender);
		switch (type) {
			case BASIC:
				in[sender] += 1;
				if (!inTree) {
					parent = sender;
					inTree = true;
				}
				active = true;
				notifyAll();
				break;
			case ACK:
				out[sender] -= value;
				child_count -= value;
				KF.addAll(message.getS());
				numFaulty[sender] = Math.max(message.getS().size(), numFaulty[sender]);
				if (!active) {
					respond_major();
				}
				break;
			case RFLUSH:
				in[sender] = 0;
				child_count -= out[sender];
				out[sender] = 0;
				if (!active) {
					respond_minor();
					respond_major();
				}
				break;
		}
		notifyAll();
	}

	@Override
	public void run() {
		writeString("Thread started");
		Wait();
		
		while (!mustStop) {
			synchronized(this) {
				while(!active) {
					try {
						wait();
					} catch (InterruptedException e) {
						//ignore
					}
					if (mustStop) {
						return;
					}
				}
			}
			writeString("becoming active");
			activity();
			if (mustStop) { return; }

			writeString("becoming passive");
			setPassive();
			if (mustStop) { return; }

			respond_minor();
			if (mustStop) { return; }

			respond_major();
		}
	}

	private synchronized void respond_minor() {
		for (int i = 0; i < nnodes; i++) {
			if (i == parent || DF.contains(i) || in[i] == 0) { continue; }
			else {
				sendMessage(i, Type.ACK, in[i]);
				in[i] = 0;
			}
		}
	}

	private synchronized void respond_major() {
		// writeString("child_count : " + child_count);
		// writeString("out : " + Arrays.toString(out));
		if (child_count == 0) {
			// for (int i = 0; i < nnodes; i++) {
			// 	if (i != parent && !DF.contains(i) && in[i] != 0) {
			// 		writeString("in[" + i + "]: " + in[i]);
			// 		return;
			// 	}
			// }
			// if (root) {
			// 	writeString("DF: " + DF);
			// 	writeString("KF: " + KF);
			// 	writeString("numFaulty: " + Arrays.toString(numFaulty));
			// }
			
			if (!root & inTree) {
				sendMessage(parent, Type.ACK, in[parent]);
				in[parent] = 0;
				parent = -1;
				inTree = false;
			}
			else if (DF.containsAll(KF)) {
				for (int i = 0; i < nnodes; i++) {
					if (DF.contains(i) || i == nodeID) { continue;}
					if (numFaulty[i] != DF.size()) { return; }
				}
				inTree = false;
				writeString("TERMINATION DECLARED!");
				writeString("Lamports clock at end: " + this.lc);
				network.killNodes();
				network.announce();
			}
		}
	}

	private synchronized void Wait() {
		while (!started) {
			try {
				wait();
			} catch (InterruptedException e) {
				//ignore
			}
		}
		writeString("Node started");
	}

	public synchronized void start() {
		this.started = true;
		notifyAll();
	}

	public void activity() {
		writeString("starting activity");
		int level = Options.instance().get(Options.ACTIVITY_LEVEL);
		int nActivities = 1 + random.nextInt(level);
		for (int i = 0; i < nActivities; i++) {
			int timeToSleep = random.nextInt(MAX_ACTIVITY_DELAY);
			try {
				Thread.sleep(timeToSleep);
			} catch (InterruptedException e) {
				// ignore
			}

			int nMessages = random.nextInt(level) + (this.isRoot() ? 1:0);
			for (int j = 0; j < nMessages && network.allowedToSend() && !mustStop; j++) {
				int target = network.selectTarget(nodeID);
				while(DF.contains(target)) {
					target = network.selectTarget(nodeID);
				}
				sendMessage(target, Type.BASIC, 0);
			}
		}
	}

	public synchronized void stopRunning() {
		if (this.active) {
			writeString("stopped while active");
		}
		else if (this.inTree) {
			writeString("stopped while still in tree");
		}
		this.mustStop = true;
		notifyAll();
	}

	public synchronized void crash() {
		writeString("I CRASHED");
		// this.setPassive();
		this.active = false;
		this.mustStop = true;
		this.inTree = false;
	}

	private synchronized int getRoot() {
		for (int i = 0; i < nnodes; i++) {
			if (!DF.contains(i)) {
				return i;
			}
		}
		return 0; // should not happen
	}

	public synchronized void receiveCrash(int crashedNode) {
		DF.add(crashedNode);
		out[crashedNode]++;
		child_count++;
		sendMessage(crashedNode, Message.Type.RFLUSH, 0);
		int newRoot = getRoot();
		inTree = true;
		if (newRoot == nodeID) {
			root = true;
			parent = -1;
		}
		else {
			parent = newRoot;
		}
		if (!active) {
			respond_minor();
			respond_major();
		}
	}

}
