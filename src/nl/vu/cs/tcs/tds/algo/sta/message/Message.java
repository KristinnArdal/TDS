package algo.sta.message;

import java.io.Serializable;

import algo.sta.node.NodeRunner4;
import algo.sta.node.NodeState4;

import util.LamportClock;

import util.Color;

public class Message implements Serializable {

	/*
	 * Declaration of the different messgage types
	 */
	
	// Echo algorithm messages
	public static final int E_PROPOSE = 0;
	public static final int E_ACCEPT = 1;
	public static final int E_REJECT = 2;

	// Basic message M_{i,j} and acknowledgement
	public static final int M_S = 10;
	public static final int M_ACK = 11;

	// Resume message and acknowledgement
	// needed when resume is triggered by basic message
	public static final int R_S = 20;
	public static final int R_ACK = 21;

	// Stop message
	public static final int STOP = 30;

	private static final long serialVersionUID = 4L;
	private int sender;
	private int type;
	private LamportClock lc;

	public Message(int sender, int type, LamportClock lc) {
		this.sender = sender;
		this.type = type;
		this.lc = new LamportClock(lc);
	}

	public int getType() {
		return this.type;
	}

	/**
	 * @return the lc
	 */
	public LamportClock getLc() {
		return lc;
	}

	public int getSender() {
		return this.sender;
	}
}
