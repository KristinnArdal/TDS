package algo.ftsta.node;

import java.util.Random;

import ibis.util.ThreadPool;
import java.util.Vector;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import main.TDS;

import util.Options;
import util.LamportClock;

import algo.ftsta.network.Network5;
import algo.ftsta.message.Message;

public class NodeRunner5 implements Runnable {

	// general variables
	private final int nodeID;
	private final int nnodes;
	private final Network5 network;
	private Random random = new Random();

	// state of the node
	private boolean idle = true;
	private boolean free = false;
	private boolean inactive = false;

	// tree structure
	private int parent = -1;
	private HashSet<Integer> children;

	private boolean started = false;
	private boolean mustStop = false;

	// message variables
	private HashMap<Integer, Integer> child_inactive;
	private int[] num_unack_msgs;
	private boolean owned = false;
	private Vector<Integer> ownedBy;
	private Vector<Integer> rAckNext;
	private Vector<Integer> ownedNodes;
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

	// lamport clock
	private LamportClock lc;

	public NodeRunner5(int nodeID, int nnodes, Network5 network, boolean initiallyActive) {
		this.nodeID = nodeID;
		this.nnodes = nnodes;
		this.subCount = 1;
		network.registerNode(this);
		this.network = network;
		this.ownedBy = new Vector<Integer>();
		this.rAckNext = new Vector<Integer>();
		this.ownedNodes = new Vector<Integer>();
		this.nodes = new Vector<Integer>();

		this.child_inactive = new HashMap<Integer, Integer>();
		this.num_unack_msgs = new int[nnodes];

		this.children = new HashSet<Integer>();
		this.childCountsGotten = new HashSet<Integer>();
		this.CRASHED = new HashSet<Integer>();
		this.lc = new LamportClock();

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
	public synchronized void setInactive() { this.inactive = true; }
	public synchronized void setActive() { this.inactive = false; }
	public synchronized boolean isInactive() { return this.inactive; }

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
		network.sendMessage(node, new Message(this.nodeID, type, this.currSN, value, lc));
		if (type != Message.E_ACCEPT && type != Message.E_PROPOSE && type != Message.E_REJECT) {
			lc.inc();
		}
		if (type == Message.M_S) {
			num_unack_msgs[node]++;
		}
		//else if (type == Message.R_S) {
		//	waitingForAck = true;
		//}
	}

	private int calculateRoot() {
		for (int i = 0; i < nnodes; i++) {
			if (!CRASHED.contains(i))
				return i;
		}
		return -1; // should never happen
	}

	private boolean canDeclare() {
		return subCount == (nnodes - CRASHED.size());
	}

	/*
	 * main logic of the node
	 */

	public synchronized void stopRunning() {
		if (!this.isInactive()) {
			writeString("stopped while active");
		}
		this.mustStop = true;
		notifyAll();
	}

	public synchronized void crash() {
		writeString("I CRASHED");
		this.setIdle();
		this.setInactive();
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

		if (CRASHED.contains(sender)) { // we don't accept incoming messages from nodes that we know have crashed
			return;
		}

		if (type != Message.E_ACCEPT && type != Message.E_PROPOSE && type != Message.E_REJECT) {
			this.lc.update(message.getLc());
		}

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
				subCount += value;
				writeString(sender + " became child");
				child_inactive.put(sender, 0);
				this.children.add(sender);
				this.numberOfAcks++;
				if (this.numberOfAcks == nnodes) {
					this.sendMessageWithValue(this.getParent(), Message.E_ACCEPT, subCount);
				}
				break;
			case Message.E_REJECT:
				//writeString("reject message from " + sender);
				this.numberOfAcks++;
				if (this.numberOfAcks == nnodes) {
					this.sendMessageWithValue(this.getParent(), Message.E_ACCEPT, subCount);
				}
				break;
			case Message.M_S:
				writeString(sender + " ownes this node");
				this.setBusy();
				free = false;
				notifyAll();
				if (this.isInactive()) {
					this.sendMessage(this.getParent(), Message.R_S);
					this.setActive();
					this.owned = true;
					ownedBy.add(sender);
				}
				else {
					this.sendMessage(sender, Message.M_ACK);
				}
				break;
			case Message.M_ACK:
				writeString(sender + " ownership released");
				num_unack_msgs[sender]--;
				ownedNodes.removeElement(sender);
				break;
			case Message.R_S:
				writeString("RESUME message from " + sender);
				child_inactive.put(sender, child_inactive.getOrDefault(sender, 0) - 1);
				if (this.isInactive()) {
					this.setActive();
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
					for (int node : ownedBy) {
						this.sendMessage(node, Message.M_ACK);
					}
					this.owned = false;
					ownedBy.clear();
				}
				for (int nextNode : rAckNext) {
					this.sendMessage(nextNode, Message.R_ACK);
				}
				rAckNext.clear();
				break;
			case Message.STOP:
				writeString("STOP message from " + sender);
				child_inactive.put(sender, child_inactive.getOrDefault(sender, 0) + 1);
				break;
			case Message.SNAP:
				writeString("SNAP from " + sender + " with value " + value + " received");
				if (SN == currSN) {
					subCount += value;
					childCountsGotten.add(sender);
				}
				break;
			case Message.CONN_S: // always accept for now
				children.add(sender);
				child_inactive.put(sender, child_inactive.getOrDefault(sender, 0));
				sendMessage(sender, Message.CONN_ACK);
				writeString(sender + " added as child");
				break;
			case Message.CONN_ACK:
				// parent assumes that node is active
				//if (isInactive()) {
				//	sendMessage(parent, Message.STOP);
				//}
				// same action as when receiving R_ACK
				if (waitingForAck) { waitingForAck = false; }
				if (owned) {
					for (int node : ownedBy) {
						this.sendMessage(node, Message.M_ACK);
					}
					this.owned = false;
					ownedBy.clear();
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
					child_inactive.remove(sender);
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
		writeString(idle + " " + free + " " + inactive + " " + child_inactive + " " + Arrays.toString(num_unack_msgs) + " " + countSent + " " + waitingForAck + " " + subCount + " " + owned + " " + ownedBy);
		boolean no_unack_msgs = true;
		for (int n : num_unack_msgs) {
			if (n != 0) {
				no_unack_msgs = false;
				break;
			}
		}

		if (idle && no_unack_msgs) {
			this.free = true;
		}

		boolean allChildrenInactive = true;
		for (Integer child : children) {
			if (child_inactive.get(child) != 1) {
				allChildrenInactive = false;
				break;
			}
		}

		// check if the node is allowed to stop
		//if (!this.isInactive() && runningChildren.isEmpty() && this.isIdle() && ownedNodes.isEmpty() && !owned) {
		if (allChildrenInactive && free && !inactive) {
			writeString("Thread stopped");
			this.setInactive();
			if (!this.isRoot()) {
				this.sendMessage(this.parent, Message.STOP);
			}
		}

		// check if termination can be declared (only if root)
		if (isInactive() && this.isRoot() && this.canDeclare()) {
			writeString("TERMINATION DECLARED!");
			writeString("Lamport clock at end: " + lc);
			network.killNodes();
			network.announce();
		}

		// check if subCount should be sent to parent node, should only be done if
		// this node has received counts from all of its children and is not
		// waiting for an acknowledgement
		if (!countSent && CRASHED.size() == currSN && !waitingForAck && !this.isRoot() && childCountsGotten.size() == children.size()) {
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
			this.setActive();
		}
		this.Wait();
		if (this.nodeID == 0) {
			writeString("Main algorithm started");
		}

		//writeString(String.format("%d is parent", this.parent));
		// start main loop of algorithm
		
		update();

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
				if (mustStop) { return; }
				this.setIdle();
				writeString("becoming passive");
				update();
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

			for (int j = 0; j < nMessages && network.allowedToSend() && !mustStop; j++) {
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
		updateCurrSN(CRASHED.size());

		writeString(crashedNode + " crashed");
		// remove crashed node from all lists that might contain it
		if (children.contains(crashedNode)) {
			children.remove(crashedNode);
			if (rAckNext.contains(crashedNode)) { rAckNext.removeElement(crashedNode); }
			child_inactive.remove(crashedNode);
		}

		// node can appear multiple times in ownedNodes
		num_unack_msgs[crashedNode] = 0;
		while (ownedNodes.contains(crashedNode)) {
			ownedNodes.removeElement(crashedNode);
		}
		if (owned && ownedBy.contains(crashedNode)) { // node was owned by crashed node
			ownedBy.removeElement(crashedNode);
			if (ownedBy.isEmpty()) {
				this.waitingForAck = true; // this node needs to wait for the R_ACK message before reporting its count to the root.
				owned = false;
			}
		}


		if (this.isRoot() && this.parent != this.nodeID) {
			if (this.parent != crashedNode) //Only send a DCONN if the node has not crashed
				sendMessage(this.parent, Message.DCONN);
			this.parent = this.nodeID;
			this.waitingForAck = false;
			if (owned) {
				for (int node : ownedBy) {
					this.sendMessage(node, Message.M_ACK);
				}
				this.owned = false;
				ownedBy.clear();
			}
			for (int nextNode : rAckNext) {
				this.sendMessage(nextNode, Message.R_ACK);
			}
			rAckNext.clear();
		}
		else if (this.parent == crashedNode) {
			this.parent = calculateRoot();
			writeString("New parent: " + this.parent);
			countSent = false;
			sendMessage(this.parent, Message.CONN_S);
			waitingForAck = true;
			this.inactive = false;
		}
		update();
	}
}
