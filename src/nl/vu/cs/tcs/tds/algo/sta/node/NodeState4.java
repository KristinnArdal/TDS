package algo.sta.node;

import java.util.TreeSet;

public class NodeState4 {
	private boolean passive;
	private final int mynode;
	private int parent;
	private TreeSet<Integer> children;
	private int nnodes;

	public NodeState4(boolean passive, int mynode, int nnodes) {
		this.passive = passive;
		this.mynode = mynode;
		this.nnodes = nnodes;
		this.parent = -1;
		this.children = new TreeSet<Integer>();
	}

	public boolean isPassive() {
		return this.passive;
	}

	public int getNodeNumber() {
		return this.mynode;
	}

	public boolean isRoot() {
		return this.parent == this.mynode;
	}

	public int getParent() {
		return this.parent;
	}
	
	public boolean hasParent() {
		return (this.parent != -1);
	}

	public void setParent(int parent) {
		this.parent = parent;
	}
	
	// return a copy of the children (should a copy be returned?)
	public TreeSet<Integer> getChildren() {
		return new TreeSet<Integer>(this.children);
	}

	public void addChild(int child) {
		this.children.add(child);
	}

	public void waitUntilPassive() {
		while (!passive) {
			try {
				wait();
			} catch (InterruptedException e) {
				//ignore
			}
		}
	}

	public void setPassive(boolean passive) {
		this.passive = passive;
	}
}
