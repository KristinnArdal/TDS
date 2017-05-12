package algo.ftsta.node;

import java.util.Random;

import ibis.util.ThreadPool;
import util.Options;
import java.util.Vector;
import java.util.Collections;
import java.util.HashSet;

import main.TDS;

import algo.ftsta.network.Network5;
import algo.ftsta.message.Message;

public class NodeRunner5 implements Runnable {

	// general variables
	private final int nodeID;
	private final int nnodes;
	private final Network5 network;
	private Random random = new Random();
	private int sequenceNumber;

	// state of the node
	private boolean idle = true;
	private boolean stopped = true;

	// tree structure
	private int parent = -1;
	private HashSet<Integer> children;

	private boolean started = false;
	private boolean mustStop = false;

	// message variables
	private boolean owned = false;
	private int ownedBy = -1;
	private Vector<Integer> rAckNext;
	private Vector<Integer> ownedNodes;
	private Vector<Integer> runningChildren;
	private Vector<Integer> nodes;

	// crash detection
	private volatile HashSet<Integer> CRASHED;

	// node check variables
	private boolean countSent = true;
	private int subCount; // count of the number of nodes in its subtree
 	// child node is in the set if current node has received the subcounts for it for current SN
	private HashSet<Integer> childCountsGotten;
	// current sequence number the node is counting for
	private int currSN = 0;
	private boolean waitingForAck = false;

	// counter needed for echo algorithm
	private int numberOfAcks = 0;

	public NodeRunner5(int nodeID, int nnodes, Network5 network, boolean initiallyActive) {
		this.nodeID = nodeID;
		this.nnodes = nnodes;
		this.subCount = nnodes;
		this.sequenceNumber = 0;
		network.registerNode(this);
		this.network = network;
		this.rAckNext = new Vector<Integer>();
		this.ownedNodes = new Vector<Integer>();
		this.runningChildren = new Vector<Integer>();
		this.nodes = new Vector<Integer>();
		this.children = new HashSet<Integer>();
		this.childCountsGotten = new HashSet<Integer>();
		this.CRASHED = new HashSet<Integer>();

		for (int i = 0; i < nnodes; i++) {
			nodes.add(i);
		}
		Collections.shuffle(nodes);

		Thread t = new Thread(this);
		t.start();
	}

	/*
	 * Get and set functions for class variables
	 */

	public int getId() { return this.nodeID; }
	public synchronized boolean isRoot() { return this.calculateRoot() == this.nodeID; }
	public synchronized int getParent() { return this.parent; }
	public synchronized void setParent(int parent) { this.parent = parent; }
	public HashSet<Integer> getCRASHED() { return CRASHED; }
	public synchronized boolean hasParent() { return (this.parent != -1); }

	// Set and get methods for idle variable
	public synchronized void setIdle() { network.registerIdle(this.nodeID); this.idle = true; }
	public synchronized void setBusy() { this.idle = false; }
	public synchronized boolean isIdle() { return this.idle; }

	// Set and get methods for stopped variable
	public synchronized void setStopped() { this.stopped = true; }
	public synchronized void setRunning() { this.stopped = false; }
	public synchronized boolean isStopped() { return this.stopped; }

	/*
	 * Helper/wrapper function for common patterns
	 */

	private void writeString(String s) {
		TDS.writeString(5, " Node " + nodeID + ": \t" + s);
	}

	public synchronized void sendMessage(int node, int type) {
		this.sendMessageWithValue(node, type, 0);
	}

	public synchronized void sendMessageWithValue(int node, int type, int value) {
		network.sendMessage(node, new Message(this.nodeID, type, this.currSN, value));
	}

	private int calculateRoot() {
		for (int i = 0; i < nnodes; i++) {
			if (!CRASHED.contains(i))
				return i;
		}
		return -1; // should never happen
	}

	private boolean canDeclare() {
		// currSN == sequenceNumber where currSN is the hightest SN that we have received in a message
		return this.isStopped() && subCount == (nnodes - CRASHED.size());
	}

	/*
	 * main logic of the node
	 */

	public synchronized void stopRunning() {
		if (!this.isStopped()) {
			writeString("stopped while active");
		}
		this.mustStop = true;
		notifyAll();
	}

	public synchronized void crash() {
		writeString("I CRASHED");
		this.setIdle();
		this.setStopped();
		this.mustStop = true;
	}

	private synchronized void updateCurrSN(int SN) {
		if (SN > currSN) {
			currSN = SN;
			subCount = 1;
			countSent = false;
			childCountsGotten.clear();
		}
	}

	public synchronized void receiveMessage(Message message) {
		//writeString("received message from " + message.getSender());
 		int type = message.getType();
		int sender = message.getSender();
		int SN = message.getSequenceNumber();
		int value = message.getValue();

		// update sequence number if message has higher
		updateCurrSN(SN);

		// If sender is root and type is E_ACCEPT then the echo algorithm is finished
		if (sender == 0 && type == Message.E_ACCEPT) {
			// start doing work
			writeString("Echo algorithm finished");
			ThreadPool.createNew(new Runnable() {
				@Override
				public void run() {
					network.echoFinished();
				}
			}, "network");
			return;
		}

		switch (type) {
			case Message.E_PROPOSE:
				if (this.hasParent()) {
					// send E_REJECT to the sender
					this.sendMessage(sender, Message.E_REJECT);
				}
				else {
					// send E_PROPOSE to all other nodes with a small dealy
					this.setParent(sender);
					writeString(sender + " set as parent");
					for (Integer i : nodes) {
						if (i == sender || i == this.nodeID) {
							this.numberOfAcks++;
							continue;
						}
						else {
							this.sendMessage(i, Message.E_PROPOSE);
						}
					}
				}
				break;
			case Message.E_ACCEPT:
				writeString(sender + " became child");
				this.children.add(sender);
				this.numberOfAcks++;
				if (this.numberOfAcks == nnodes) {
					this.sendMessage(this.getParent(), Message.E_ACCEPT);
				}
				break;
			case Message.E_REJECT:
				//writeString("reject message from " + sender);
				this.numberOfAcks++;
				if (this.numberOfAcks == nnodes) {
					this.sendMessage(this.getParent(), Message.E_ACCEPT);
				}
				break;
			case Message.M_S:
				writeString(sender + " ownes this node");
				this.setBusy();
				notifyAll();
				if (this.isStopped()) {
					this.setRunning();
					this.sendMessage(this.getParent(), Message.R_S);
					this.owned = true;
					ownedBy = sender;
				}
				else {
					this.sendMessage(sender, Message.M_ACK);
				}
				break;
			case Message.M_ACK:
				if (ownedNodes.contains(sender)) {
					writeString(sender + " ownership released");
					ownedNodes.removeElement(sender);
				}
				//update();
				break;
			case Message.R_S:
				writeString("RESUME message from " + sender);
				runningChildren.add(sender);
				if (this.isStopped()) {
					this.setRunning();
					this.sendMessage(this.getParent(), Message.R_S);
					rAckNext.add(sender);
				}
				else {
					this.sendMessage(sender, Message.R_ACK);
				}
				break;
			case Message.R_ACK:
				if (waitingForAck) { waitingForAck = false; }
				if (owned) {
					this.sendMessage(ownedBy, Message.M_ACK);
					this.owned = false;
					ownedBy = -1;
				}
				for (int nextNode : rAckNext) {
					this.sendMessage(nextNode, Message.R_ACK);
				}
				rAckNext.clear();
				break;
			case Message.STOP:
				writeString("STOP message from " + sender);
				runningChildren.removeElement(sender);
				//update();
				break;
			case Message.SNAP:
				if (SN == currSN) {
					subCount += value;
					childCountsGotten.add(sender);
				}
				break;
			case Message.CONN_S: // always accept for now
				children.add(sender);
				runningChildren.add(sender);
				sendMessage(sender, Message.CONN_ACK);
				writeString(sender + " added as child");
				break;
			case Message.CONN_ACK:
				// parent assumes that node is active
				if (isStopped()) {
					sendMessage(parent, Message.STOP);
				}
				// same action as when receiving R_ACK
				if (waitingForAck) { waitingForAck = false; }
				if (owned) {
					this.sendMessage(ownedBy, Message.M_ACK);
					this.owned = false;
					ownedBy = -1;
				}
				for (int nextNode : rAckNext) {
					this.sendMessage(nextNode, Message.R_ACK);
				}
				rAckNext.clear();
				break;
			case Message.CONN_REJ:
				break;
			case Message.DCONN:
				if (children.contains(sender)) {
					children.remove(sender);
					if (rAckNext.contains(sender)) { rAckNext.removeElement(sender); }
					if (runningChildren.contains(sender)) { runningChildren.removeElement(sender); }
				}
				break;
			default: // Should not happen
				break;
		}
		if (type != Message.E_PROPOSE && type != Message.E_ACCEPT && type != Message.E_REJECT) {
			update();
		}
	}

	private synchronized void update() {
		// check if the node is allowed to stop
		if (!this.isStopped() && runningChildren.isEmpty() && this.isIdle() && ownedNodes.isEmpty() && !owned) {
			this.setStopped();
			writeString("Thread stopped");
			if (!this.isRoot()) {
				this.sendMessage(this.parent, Message.STOP);
			}
//			if (owned) {
//				this.sendMessage(ownedBy, Message.M_ACK);
//				this.owned = false;
//				this.ownedBy = -1;
//			}
		}

		// check if termination can be declared (only if root)
		if (this.isRoot() && this.canDeclare()) {
			writeString("TERMINATION DECLARED!");
			network.killNodes();
			network.announce();
		}

		// check if subCount should be sent to parent node, should only be done if
		// this node has received counts from all of its children and is not
		// waiting for an acknowledgement
		if (!countSent && !waitingForAck && !this.isRoot() && childCountsGotten.size() == children.size()) {
			sendMessageWithValue(parent, Message.SNAP, subCount);
			countSent = true;
		}
	}

	@Override
	public void run() {
		writeString("Thread started");
		this.Wait();
		started = false;
		if (this.nodeID == 0) {
			writeString("Echo algorithm started");
			// Start echo algorithm by sending a propose message to self
			this.sendMessage(this.nodeID, Message.E_PROPOSE);
			this.setBusy();
			this.setRunning();
		}
		this.Wait();
		if (this.nodeID == 0) {
			writeString("Main algorithm started");
		}

		//writeString(String.format("%d is parent", this.parent));
		// start main loop of algorithm

		while (!mustStop) {
			synchronized(this) {
				while (this.isIdle()) {
					try {
						wait();
					} catch (InterruptedException e) {
						//nothing
					}
					if (mustStop) {
						return;
					}
				}
			}
			writeString("becoming active");
			activity();
			synchronized(this) {
				this.setIdle();
				writeString("becoming passive");
			}
			update();
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
			int timeToSleep = random.nextInt(1000);
			try {
				Thread.sleep(timeToSleep);
			} catch (InterruptedException e) {
				//ignore
			}
			int nMessages = random.nextInt(level) + (this.nodeID == 0? 1:0);

			for (int j = 0; j < nMessages && network.allowedToSend(); j++) {
				// Get node to send message to
				int target = network.selectTarget(nodeID);

				//Node does not contact other nodes that it knows are crashed
				while (CRASHED.contains(target))
					target = network.selectTarget(nodeID);

				synchronized(this) {
					this.sendMessage(target, Message.M_S);
					// This needs to be synchronized since we can be working with ownedNodes elsewhere
					ownedNodes.add(target);
				}
			}
		}
	}

	public synchronized void receiveCrash(int crashedNode) {
		if (CRASHED.contains(crashedNode)) { // If we have already logged the crash
			return;
		}
		CRASHED.add(crashedNode);
		sequenceNumber++;
		updateCurrSN(sequenceNumber);

		writeString(crashedNode + " crashed");
		// remove crashed node from all lists that might contain it
		if (children.contains(crashedNode)) {
			children.remove(crashedNode);
			if (rAckNext.contains(crashedNode)) { rAckNext.removeElement(crashedNode); }
			if (runningChildren.contains(crashedNode)) { runningChildren.removeElement(crashedNode); }

		}
		// node can appread multiple times in crashedNode
		while (ownedNodes.contains(crashedNode)) {
			ownedNodes.removeElement(crashedNode);
		}
		if (owned && ownedBy == crashedNode) { // node was owned by another node
			this.waitingForAck = true; // this node needs to wait for the R_ACK message before reporting its count to the root.
			owned = false;
			ownedBy = -1;
		}


		if (this.isRoot() && this.parent != this.nodeID) {
			sendMessage(this.parent, Message.DCONN);
			this.parent = this.nodeID;
		}
		else if (this.parent == crashedNode) {
			this.parent = calculateRoot();
			writeString("New parent: " + this.parent);
			countSent = false;
			sendMessage(this.parent, Message.CONN_S);
			waitingForAck = true;
		}
		update();
	}
}
