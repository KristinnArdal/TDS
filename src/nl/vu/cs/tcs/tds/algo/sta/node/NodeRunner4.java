package algo.sta.node;

import java.util.Random;

import ibis.util.ThreadPool;
import util.Options;
import java.util.Vector;
import main.TDS;
import performance.PerformanceLogger;
import algo.sta.network.Network4;
import algo.sta.message.Message;
import util.Color;

public class NodeRunner4 implements Runnable {

	// general variables
	private final int nodeID;
	private final int nnodes;
	private final Network4 network;
	private NodeState4 state; //TODO: remove state
	private Random random = new Random();

	// state of the node
	private boolean idle = true;
	private boolean stopped = true;

	// tree structure
	private int parent = -1;

	private boolean started = false;
	private boolean mustStop = false;

	// message variables
	private boolean owned = false;
	private int ownedBy = -1;
	private Vector<Integer> rAckNext;
	private Vector<Integer> ownedNodes;
	private Vector<Integer> runningChildren;

	// counter needed for echo algorithm
	private int numberOfAcks = 0;

	public NodeRunner4(int nodeID, int nnodes, Network4 network, boolean initiallyActive) {
		this.nodeID = nodeID;
		this.nnodes = nnodes;
		this.state = new NodeState4(!initiallyActive, nodeID, nnodes);
		network.registerNode(this);
		this.network = network;
		this.rAckNext = new Vector<Integer>();
		this.ownedNodes = new Vector<Integer>();
		this.runningChildren = new Vector<Integer>();

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
		this.idle = true;
	}

	public synchronized void setBusy() {
		this.idle = false;
		// initiate work
	}
	
	public synchronized boolean isIdle() {
		return this.idle;
	}

	// Set and get methods for stopped variable
	public synchronized void setStopped() {
		this.stopped = true;
	}

	public synchronized void setRunning() {
		this.stopped = false;
	}

	public synchronized boolean isStopped() {
		return this.stopped;
	}

	/*
	 * Helper/wrapper function for common patterns
	 */

	private void writeString(String s) {
		TDS.writeString(4, " Node " + nodeID + ": \t" + s);
	}

	// Not used at the moment
	public synchronized void sendMessage(int node, int type) {
		writeString("send a message to " + node);
		network.sendMessage(node, new Message(this.nodeID, type));
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

	public synchronized void receiveMessage(Message message) {
		//writeString("received message from " + message.getSender());
 		int type = message.getType(); 
		int sender = message.getSender(); 
		
		// If sender is root and type is E_ACCEPT then the echo algorithm is finished
		if (sender == 0 && type == Message.E_ACCEPT) {
			// start doing work
			writeString("Echo algorithm finished");
			final int delay = random.nextInt(50);
			ThreadPool.createNew(new Runnable() {
				@Override
				public void run() {
					try {
						Thread.sleep(delay);
					} catch (InterruptedException e) {
						// ignore
					}
					network.startAllNodes();
				}
			}, "network");
			return;
		}


		switch (type) { 
			case Message.E_PROPOSE: 
				if (this.hasParent()) {
					// send E_REJECT to the sender 
					Message reject = new Message(this.nodeID, Message.E_REJECT); 
					network.sendMessage(sender, reject); 
				}
				else {
					// send E_PROPOSE to all other nodes 
					this.setParent(sender);
					writeString(sender + " set as parent");
					Message propose = new Message(this.nodeID, Message.E_PROPOSE);
					for (int i = 0; i < nnodes; i++) {
						if (i == sender || i == this.nodeID) {
							this.numberOfAcks++;
							continue;
						}
						else {
							network.sendMessage(i, propose);
						}
					}
				}
				break; 
			case Message.E_ACCEPT: 
				//this.state.addChild(sender);
				writeString(sender + " became child");
				this.numberOfAcks++;
				if (this.numberOfAcks == nnodes) {
					Message accept = new Message(this.nodeID, Message.E_ACCEPT);
					network.sendMessage(this.getParent(), accept);
				}
				break; 
			case Message.E_REJECT: 
				writeString("reject message from " + sender);
				this.numberOfAcks++;
				if (this.numberOfAcks == nnodes) {
					Message accept = new Message(this.nodeID, Message.E_ACCEPT);
					network.sendMessage(this.getParent(), accept);
				}
				break; 
			case Message.M_S: 
				writeString(sender + " ownes this node");
				this.setBusy();
				notifyAll();
				if (this.isStopped()) {
					this.setRunning();

					Message resume = new Message(this.nodeID, Message.R_S);
					network.sendMessage(this.getParent(), resume);
					this.owned = true;
					ownedBy = sender;
				}
				else {
					Message ack = new Message(this.nodeID, Message.M_ACK);
					network.sendMessage(sender, ack);
				}
				break; 
			case Message.M_ACK: 
				writeString(sender + " ownership released");
				ownedNodes.removeElement(sender);
				update();
				break; 
			case Message.R_S: 
				writeString("RESUME message from " + sender);
				runningChildren.add(sender);
				if (this.isStopped()) {
					this.setRunning();
					Message resume = new Message(this.nodeID, Message.R_S);
					network.sendMessage(this.getParent(), resume);
					rAckNext.add(sender);
				}
				else {
					Message rAck = new Message(this.nodeID, Message.R_ACK);
					network.sendMessage(sender, rAck);
				}
				break; 
			case Message.R_ACK: 
				if (owned) {
					Message mAck = new Message(this.nodeID, Message.M_ACK);
					network.sendMessage(ownedBy, mAck);
					this.owned = false;
					ownedBy = -1;
				}
				Message rAck = new Message(this.nodeID, Message.R_ACK);
				for (int nextNode : rAckNext) {
					network.sendMessage(nextNode, rAck);
				}
				rAckNext.clear();
				break; 
			case Message.STOP: 
				writeString("STOP message from " + sender);
				runningChildren.removeElement(sender);
				update();
				break; 
			default: // Should not happen 
				break; 
		}
	}

	private synchronized void update() {
		if (!this.isStopped() && runningChildren.isEmpty() && this.isIdle() && ownedNodes.isEmpty()) {
			this.setStopped();
			writeString("Thread stopped");
			if (this.isRoot()) {
				writeString("Root stopped");
				network.killNodes();
				network.announce();
			}
			else {
				Message stop = new Message(this.nodeID, Message.STOP);
				network.sendMessage(this.parent, stop);
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
			Message propose = new Message(this.nodeID, Message.E_PROPOSE);
			network.sendMessage(this.nodeID, propose);
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
			writeString("becoming passive");
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

			for (int j = 0; j < nMessages; j++) {
				int target = network.selectTarget(nodeID);
				//Message m = new Message(this.nodeID, Message.M_S);
				synchronized(this) {
					sendMessage(target, Message.M_S);
					// This needs to be synchronized since we can be working with ownedNodes elsewhere
					ownedNodes.add(target);
				}
			}
		}
		this.setIdle();
	}
}
