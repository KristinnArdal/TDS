package algo.sta.node;

import java.util.Random;

import ibis.util.ThreadPool;
import util.Options;
import java.util.Vector;
import java.util.Collections;
import java.util.HashMap;

import main.TDS;

import util.LamportClock;

import algo.sta.network.Network4;
import algo.sta.message.Message;

public class NodeRunner4 implements Runnable {

	// general variables
	private final int nodeID;
	private final int nnodes;
	private final Network4 network;
	private Random random = new Random();

	// state of the node
	private boolean idle = true;
	private boolean free = false;
	private boolean inactive = false;

	// tree structure
	private int parent = -1;

	private boolean started = false;
	private boolean mustStop = false;

	// message variables
	private HashMap<Integer, Integer> child_inactive;
	private int num_unack_msgs = 0;
	private boolean owned = false;
	private int ownedBy = -1;
	private Vector<Integer> rAckNext;
	private Vector<Integer> ownedNodes;
	private Vector<Integer> nodes;

	// counter needed for echo algorithm
	private int numberOfAcks = 0;

	private LamportClock lc;

	public NodeRunner4(int nodeID, int nnodes, Network4 network, boolean initiallyActive) {
		this.nodeID = nodeID;
		this.nnodes = nnodes;
		network.registerNode(this);
		this.network = network;
		this.rAckNext = new Vector<Integer>();
		this.ownedNodes = new Vector<Integer>();
		this.nodes = new Vector<Integer>();

		this.child_inactive = new HashMap<Integer, Integer>();

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

	public int getId() {
		return this.nodeID;
	}

	public synchronized boolean isRoot() {
		return this.parent == this.nodeID;
	}

	public synchronized int getParent() {
		return this.parent;
	}

	public synchronized void setParent(int parent) {
		this.parent = parent;
	}

	public synchronized boolean hasParent() {
		return (this.parent != -1);
	}

	// Set and get methods for idle variable
	public synchronized void setIdle() {
		network.registerIdle(this.nodeID);
		this.idle = true;
	}

	public synchronized void setBusy() {
		this.idle = false;
		// initiate work
	}
	
	public synchronized boolean isIdle() {
		return this.idle;
	}

	// Set and get methods for inactive variable
	public synchronized void setInactive() {
		this.inactive = true;
	}

	public synchronized void setActive() {
		this.inactive = false;
	}

	public synchronized boolean isInactive() {
		return this.inactive;
	}

	/*
	 * Helper/wrapper function for common patterns
	 */

	private void writeString(String s) {
		TDS.writeString(4, " Node " + nodeID + ": \t" + s);
	}

	// Not used at the moment
	public synchronized void sendMessage(int node, int type) {
		//writeString("send a message to " + node);
		network.sendMessage(node, new Message(this.nodeID, type, this.lc));
		if (type != Message.E_ACCEPT && type != Message.E_PROPOSE && type != Message.E_REJECT) {
			this.lc.inc();
		}
		if (type == Message.M_S) {
			num_unack_msgs++;
		}
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

	private void sendEchoAll() {
		for (Integer i : nodes) {
			if (i == parent || i == this.nodeID) {
				this.numberOfAcks++;
				continue;
			}
			else {
				try{
					Thread.sleep(10);
				} catch (InterruptedException e) {
				}
				this.sendMessage(i, Message.E_PROPOSE);
			}
		}
	}

	public synchronized void receiveMessage(Message message) {
		//writeString("received message from " + message.getSender());
 		int type = message.getType(); 
		int sender = message.getSender(); 
		if (type != Message.E_ACCEPT && type != Message.E_PROPOSE && type != Message.E_REJECT) {
			this.lc.update(message.getLc());
		}
		
		// If sender is root and type is E_ACCEPT then the echo algorithm is finished
		if (sender == 0 && type == Message.E_ACCEPT) {
			// start doing work
			writeString("Echo algorithm finished");
			ThreadPool.createNew(new Runnable() {
				@Override
				public void run() {
					network.startAllNodes();
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
					ThreadPool.createNew(new Runnable() {
						@Override
						public void run() {
							sendEchoAll();
						}
					}, "echoSend");
				}
				break; 
			case Message.E_ACCEPT: 
				writeString(sender + " became child");
				child_inactive.put(sender, 0);
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
				free = false;
				notifyAll();
				if (this.isInactive()) {
					this.sendMessage(this.getParent(), Message.R_S);
					this.setActive();
					this.owned = true;
					ownedBy = sender;
				}
				else {
					this.sendMessage(sender, Message.M_ACK);
				}
				break; 
			case Message.M_ACK: 
				writeString(sender + " ownership released");
				num_unack_msgs--;
				ownedNodes.removeElement(sender);
				update();
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
				child_inactive.put(sender, child_inactive.get(sender) + 1);
				update();
				break; 
			default: // Should not happen 
				break; 
		}
	}

	private synchronized void update() {
		if (idle && num_unack_msgs == 0) {
			this.free = true;
		}

		boolean allChildrenInactive = true;
		for (Integer key : child_inactive.values()) {
			if (key != 1) {
				allChildrenInactive = false;
				break;
			}
		}

		if (allChildrenInactive && free && !inactive) {
			writeString("Thread stopped");
			this.setInactive();
			if (this.isRoot()) {
				writeString("TERMINATION DECLARED!");
				writeString("Lamport clock at end: " + lc);
				network.killNodes();
				network.announce();
			}
			else {
				this.sendMessage(this.parent, Message.STOP);
			}
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
				int target = network.selectTarget(nodeID);
				synchronized(this) {
					this.sendMessage(target, Message.M_S);
					// This needs to be synchronized since we can be working with ownedNodes elsewhere
					ownedNodes.add(target);
				}
			}
		}
	}
}
