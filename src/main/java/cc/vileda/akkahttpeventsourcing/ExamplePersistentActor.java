package cc.vileda.akkahttpeventsourcing;

import akka.persistence.SnapshotOffer;
import akka.persistence.UntypedPersistentActor;

public class ExamplePersistentActor extends UntypedPersistentActor {
	@Override
	public String persistenceId() { return "sample-id-1"; }

	private ExampleState state = new ExampleState();

	public int getNumEvents() {
		return state.size();
	}

	@Override
	public void onReceiveRecover(Object msg) {
		if (msg instanceof Evt) {
			state.update((Evt) msg);
		} else if (msg instanceof SnapshotOffer) {
			state = (ExampleState)((SnapshotOffer)msg).snapshot();
		} else {
			unhandled(msg);
		}
	}

	@Override
	public void onReceiveCommand(Object msg) {
		if (msg instanceof Cmd) {
			final String data = ((Cmd)msg).getData();
			final Evt evt1 = new Evt(data + "-" + getNumEvents());
			persist(evt1, evt -> {
				state.update(evt);
				if (evt.equals(evt1)) {
					getContext().system().eventStream().publish(evt);
					getSender().tell(String.valueOf(evt.hashCode()), getSelf());
				}
			});
		} else if (msg.equals("snap")) {
			saveSnapshot(state.copy());
		} else if (msg.equals("print")) {
			System.out.println(state);
		} else {
			unhandled(msg);
		}
	}
}
