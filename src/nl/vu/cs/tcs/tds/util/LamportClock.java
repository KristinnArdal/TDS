package util;

import java.io.Serializable;

public class LamportClock implements Serializable{
	static final long serialVersionUID = 99;
	private int value;

	public LamportClock() {
		this.value = 0;
	}

	/**
	 * @param value
	 */
	public LamportClock(int value) {
		this.value = value;
	}

	public LamportClock(LamportClock other) {
		this.value = other.value;
	}

	public void update(LamportClock other) {
		this.value = Math.max(this.value, other.getValue()) + 1;
	}

	public void inc() {
		this.value++;
	}

	/**
	 * @return the value
	 */
	public int getValue() {
		return value;
	}

	public String toString() {
		return "" + value;
	}
}
