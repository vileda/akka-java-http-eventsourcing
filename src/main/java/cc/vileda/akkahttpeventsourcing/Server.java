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
import akka.util.Timeout;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.io.IOException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static akka.pattern.Patterns.ask;

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