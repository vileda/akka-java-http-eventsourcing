package cc.vileda.akkahttpeventsourcing;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.http.javadsl.server.Handler1;
import akka.http.javadsl.server.HttpApp;
import akka.http.javadsl.server.RequestVal;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.server.values.Parameters;
import akka.http.scaladsl.Http;
import akka.persistence.SnapshotOffer;
import akka.persistence.UntypedPersistentActor;
import akka.util.Timeout;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static akka.pattern.Patterns.ask;

class Cmd implements Serializable {
	private static final long serialVersionUID = 1L;
	private final String data;

	public Cmd(String data) {
		this.data = data;
	}

	public String getData() {
		return data;
	}
}

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

class ExampleState implements Serializable {
	private static final long serialVersionUID = 1L;
	private final ArrayList<String> events;

	public ExampleState() {
		this(new ArrayList<>());
	}

	public ExampleState(ArrayList<String> events) {
		this.events = events;
	}

	public ExampleState copy() {
		return new ExampleState(new ArrayList<>(events));
	}

	public void update(Evt evt) {
		events.add(evt.getData());
	}

	public int size() {
		return events.size();
	}

	@Override
	public String toString() {
		return events.toString();
	}
}

class ExamplePersistentActor extends UntypedPersistentActor {
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

public class Server extends HttpApp {
	private final ActorRef persister;

	public Server(ActorRef persister) {

		this.persister = persister;
	}

	public static void main(String[] args) throws IOException {
		final ActorSystem system = ActorSystem.create();

		final ActorRef persister = system.actorOf(Props.create(ExamplePersistentActor.class), "persister");

		CompletionStage<Http.ServerBinding> bindingFuture =
				new Server(persister).bindRoute("localhost", 8080, system);

		bindingFuture.exceptionally(failure -> {
			System.err.println("Something very bad happened! " + failure.getMessage());
			system.terminate();
			return null;
		});

		System.out.println("<ENTER> to exit!");
		System.in.read();
		system.shutdown();
	}

	private RequestVal<String> name = Parameters.stringValue("name").withDefault("Mister X");

	@Override
	public Route createRoute() {
		Route helloRoute =
				handleWith1(name,
						(Handler1<String>) (ctx, name1) -> {
							switch (name1) {
								case "print":
								case "snap":
									persister.tell(name1, ActorRef.noSender());
							}

							Timeout timeout = new Timeout(Duration.create(5, TimeUnit.SECONDS));
							final Future<Object> future = ask(persister, new Cmd(name1), timeout);
							try {
								String result = (String) Await.result(future, timeout.duration());
								return ctx.complete("Hello " + name1 + "!" + result);
							} catch (Exception e) {
								e.printStackTrace();
								return ctx.complete("FAIL " + name1 + "!");
							}
						});

		return
				route(
						get(
								// matches the empty path
								pathSingleSlash().route(
										getFromResource("web/index.html")
								),
								path("ping").route(
										complete("PONG!")
								),
								path("hello").route(
										helloRoute
								)
						)
				);
	}
}