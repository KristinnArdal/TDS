package algo.lw.message;

import java.io.Serializable;
import java.util.TreeSet;

import util.LamportClock;

public class Message implements Serializable {

	static final long serialVersionUID = 7L;

	int sender;
	Type type;
	int value;
	LamportClock lc;
	TreeSet<Integer> S;

	public enum Type {
		BASIC,
		ACK,
		RFLUSH
	}

	/**
	 * @param sender
	 * @param type
	 * @param value
	 * @param lc
	 */
	public Message(int sender, Type type, int value, LamportClock lc, TreeSet<Integer> S) {
		this.sender = sender;
		this.type = type;
		this.value = value;
		this.lc = new LamportClock(lc);
		this.S = new TreeSet<Integer>(S);
	}

	/**
	 * @return the sender
	 */
	public int getSender() {
		return sender;
	}

	/**
	 * @param sender the sender to set
	 */
	public void setSender(int sender) {
		this.sender = sender;
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

	/**
	 * @return the s
	 */
	public TreeSet<Integer> getS() {
		return S;
	}

}
