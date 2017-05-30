package algo.lw.network;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

import ibis.util.ThreadPool;

import main.TDS;

import util.Options;

public class NodeCrasher {
	private Network7 network;
	private int nnodes;
	private int numCrashedNodes;
	private int[] crashedNodes;
	private boolean stop;

	private Random random;

	/**
	 * @param network
	 * @param nnodes
	 */
	public NodeCrasher(Network7 network, int nnodes) {
		this.stop = false;
		this.network = network;
		this.nnodes = nnodes;
		numCrashedNodes = Options.instance().get(Options.CRASHED_NODES);
		this.crashedNodes = new int[numCrashedNodes];
		for (int i = 0; i < numCrashedNodes; i++) {
			crashedNodes[i] = -1;
		}
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

		TDS.writeString(0, " [  LW ]\tWill crash nodes: " + Arrays.toString(crashedNodes));

		for (int crashedNode : crashedNodes) {
			int delay = random.nextInt(2000);
			try { Thread.sleep(delay); } catch (InterruptedException e) {}
			network.crash(crashedNode);
			notifyNodesRandomly(crashedNode, crashedNodes);
		}
	}

	private void notifyNodesRandomly(int crashedNode, int[] ignoreCrashed) {
		ArrayList<Integer> toSend = new ArrayList<Integer>();
		for (int i = 0; i < nnodes; i++) {
			toSend.add(i);
		}
		Collections.shuffle(toSend);
		for (int i : toSend) {
			ThreadPool.createNew(() -> {
				int delay = random.nextInt(5000);
				try { Thread.sleep(delay); } catch (InterruptedException e) {}
				network.sendCrashMessage(i, crashedNode);
			}, "CrashNotifier");
		}
	}

	private boolean contains(int[] array, int value) {
		for (int val : array)
			if (val == value)
				return true;
		return false;
	}
}
