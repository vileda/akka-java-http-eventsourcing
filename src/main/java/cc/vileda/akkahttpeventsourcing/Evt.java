package cc.vileda.akkahttpeventsourcing;

import java.io.Serializable;

class Evt implements Serializable {
	private static final long serialVersionUID = 1L;
	private final String data;

	public Evt(String data) {
		this.data = data;
	}

	public String getData() {
		return data;
	}
}
