package algo.ds.message;

import java.io.Serializable;

import util.LamportClock;

public class Message implements Serializable {

	static final long serialVersionUID = 6L;

	int sender;
	Type type;
	int value;
	LamportClock lc;

	public enum Type {
		BASIC,
		ACK
	}

	/**
	 * @param sender
	 * @param type
	 * @param value
	 * @param lc
	 */
	public Message(int sender, Type type, int value, LamportClock lc) {
		this.sender = sender;
		this.type = type;
		this.value = value;
		this.lc = new LamportClock(lc);
	}

	/**
	 * @return the sender
	 */
	public int getSender() {
		return sender;
	}

	/**
	 * @return the type
	 */
	public Type getType() {
		return type;
	}

	/**
	 * @return the value
	 */
	public int getValue() {
		return value;
	}

	/**
	 * @return the lc
	 */
	public LamportClock getLc() {
		return lc;
	}

}
