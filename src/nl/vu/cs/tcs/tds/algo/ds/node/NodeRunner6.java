package algo.ds.node;

import java.util.Random;

import algo.ds.message.Message;
import algo.ds.message.Message.Type;
import algo.ds.network.Network6;

import main.TDS;

import util.LamportClock;
import util.Options;

public class NodeRunner6 implements Runnable {

	private final int nodeID;
	private final int nnodes;
	private final Network6 network;
	private Random random = new Random();

	private boolean inTree = false;
	private int parent = -1;
	private int child_count = 0;
	private int[] in;

	private boolean active;

	private boolean mustStop = false;
	private boolean started = false;

	private LamportClock lc;

	private final int MAX_ACTIVITY_DELAY = 1000;

	/**
	 * @param nodeID
	 * @param nnodes
	 * @param network
	 */
	public NodeRunner6(int nodeID, int nnodes, Network6 network) {
		this.nodeID = nodeID;
		this.nnodes = nnodes;
		this.network = network;
		this.lc = new LamportClock();

		this.active = this.inTree = this.isRoot();
		this.in = new int[nnodes];
		network.registerNode(this);
		Thread t = new Thread(this);
		t.start();
	}

	/**
	 * @return the nodeID
	 */
	public int getId() { return nodeID; }

	private boolean isRoot() { return nodeID == 0; }

	private void writeString(String s) {
		TDS.writeString(6, "Node " + nodeID + ": \t" + s);
	}

	public synchronized void sendMessage(int target, Type type, int value) {
		switch (type) {
			case BASIC:
				writeString("Sending BASIC message to " + target);
				child_count++;
				break;
			case ACK:
				writeString("Sending ACK message to " + target);
				break;
		}

		network.sendMessage(target, new Message(this.nodeID, type, value, this.lc));
		lc.inc();
	}

	public synchronized void receiveMessage(Message message) {
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
				child_count -= value;
				if (!active) {
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
			writeString("becoming passive");
			network.registerPassive();
			synchronized(this) {
				active = false;
			}
			respond_minor();
			respond_major();
		}
	}

	private synchronized void respond_minor() {
		for (int i = 0; i < nnodes; i++) {
			if (i == parent || in[i] == 0) { continue; }
			else {
				sendMessage(i, Type.ACK, in[i]);
				in[i] = 0;
			}
		}
	}

	private synchronized void respond_major() {
		if (child_count == 0) {
			if (isRoot()) {
				inTree = false;
				writeString("TERMINATION DECLARED!");
				writeString("Lamports clock at end: " + this.lc);
				network.killNodes();
				network.announce();
			}
			else {
				sendMessage(parent, Type.ACK, in[parent]);
				in[parent] = 0;
				parent = -1;
				inTree = false;
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
			for (int j = 0; j < nMessages && network.allowedToSend(); j++) {
				int target = network.selectTarget(nodeID);
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

}
