package algo.ftsta.message;

import java.io.Serializable;

public class Message implements Serializable {

	/*
	 * Declaration of the different message types
	 * (should maybe be made into enum type)
	 */
	
	/* Messages used in the echo algorithm */
	public static final int E_PROPOSE = 0;
	public static final int E_ACCEPT = 1;
	public static final int E_REJECT = 2;

	/* Messages used in the original static tree algorithm */

	// Basic message M_{i,j} and acknowledgement
	public static final int M_S = 10;
	public static final int M_ACK = 11;

	// Resume message and acknowledgement
	// needed when resume is triggered by basic message
	public static final int R_S = 20;
	public static final int R_ACK = 21;

	// Stop message
	public static final int STOP = 30;

	/* Messages needed for fault tolerance */

	// Snapshot message
	public static final int SNAP = 40;
	
	// connect messages used for reattaching to tree structure
	public static final int CONN_S = 50;
	public static final int CONN_ACK = 51;
	public static final int CONN_REJ = 52;

	// disconnect message
	public static final int DCONN = 60;


	private static final long serialVersionUID = 4L;
	private int sender;
	private int type;
	private int sequenceNumber;
	private int value;

	public Message(int sender, int type, int sequenceNumber) {
		this.sender = sender;
		this.type = type;
		this.sequenceNumber = sequenceNumber;
		this.value = 0;
	}


	/**
	 * @param sender 					the id of the sender node
	 * @param type 						the type of the message sent
	 * @param sequenceNumber 	the sequence number associated with the message
	 * @param value 					a value sent with the message
	 */
	public Message(int sender, int type, int sequenceNumber, int value) {
		this.sender = sender;
		this.type = type;
		this.sequenceNumber = sequenceNumber;
		this.value = value;
	}

	/**
	 * @return the id of the sender node
	 */
	public int getSender() {
		return sender;
	}

	/**
	 * @return the type of the message
	 */
	public int getType() {
		return type;
	}

	/**
	 * @return the sequenceNumber
	 */
	public int getSequenceNumber() {
		return sequenceNumber;
	}

	/**
	 * @return the value
	 */
	public int getValue() {
		return value;
	}

}
