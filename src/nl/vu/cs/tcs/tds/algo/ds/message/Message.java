package algo.ds.message;

import java.io.Serializable;

public class Message implements Serializable {

	static final long serialVersionUID = 6L;

	int sender;
	Type type;

	/**
	 * @param sender of the message
	 * @param type of message
	 */
	public Message(int sender, Type type) {
		this.sender = sender;
		this.type = type;
	}

	public enum Type {
		BASIC,
		ACK
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

}
