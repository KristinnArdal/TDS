package algo.ftsta.network;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

import ibis.util.ThreadPool;

import util.Options;

import main.TDS;

public class NodeCrasher {

	private Network5 network;
	private int numCrashedNodes;
	private int[] crashedNodes;
	private int nnodes;
	private boolean stop;

	private Random random;

	public NodeCrasher(Network5 network, int nnodes) {
		this.stop = false;
		this.network = network;
		this.nnodes = nnodes;
		numCrashedNodes = Options.instance().get(Options.CRASHED_NODES);
		this.crashedNodes = new int[numCrashedNodes];
		this.random = new Random();
	}

	public void start() {
		ThreadPool.createNew(() -> {
			go();
		}, "Crasher");
	}

	public void go() {
		for (int i = 0; i < numCrashedNodes; i++) {
			int newCrash = random.nextInt(nnodes);
			while(contains(crashedNodes, newCrash))
				newCrash = random.nextInt(nnodes);
			crashedNodes[i] = newCrash;
		}

		TDS.writeString(0, " [FTSTA]\tWill crash nodes: " + Arrays.toString(crashedNodes));

		for (int crashedNode : crashedNodes) {
			int delay = random.nextInt(2000);
			try { Thread.sleep(delay); } catch (InterruptedException e) {}
			network.crash(crashedNode);
			try { notifyNodesRandomly(crashedNode, crashedNodes); } catch (NodeCrasherStopException e) { return; }
		}
	}

	private void notifyNodesRandomly(int crashedNode, int[] ignoreCrashed) throws NodeCrasherStopException{
		ArrayList<Integer> toSend = new ArrayList<Integer>();
		for (int i = 0; i < nnodes; i++) {
			toSend.add(i);
		}
		Collections.shuffle(toSend);
		for (int i: toSend) {
			ThreadPool.createNew(() -> {
				int delay = random.nextInt(5000);
				try { Thread.sleep(delay); } catch (InterruptedException e) {}
				network.sendCrashedMessage(i, crashedNode);
			}, "CrashNotifier");
		}
	}

	private boolean contains(int[] array, int value) {
		for (int val : array)
			if (val == value)
				return true;
		return false;
	}

	private boolean shouldStop() throws NodeCrasherStopException{
		if (stop)
			throw new NodeCrasherStopException();
		return stop;
	}

	public synchronized void stop() {
		stop = true;
	}
}
