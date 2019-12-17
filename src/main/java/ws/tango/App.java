package ws.tango;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.Cluster;
import akka.cluster.ddata.ORMultiMap;
import akka.cluster.ddata.PNCounterMap;
import akka.grpc.javadsl.ServiceHandler;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.UseHttp2;
import akka.japi.Function;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.util.concurrent.CompletionStage;

import ws.grpc.echo.EchoServiceImpl;
import ws.grpc.api.echo.EchoServiceHandlerFactory;
import ws.grpc.RetrieverServiceImpl;
import ws.grpc.api.RetrieverServiceHandlerFactory;


/**
 *
 */
public class App
{
    public static void main( String[] args ) {
        Config conf = ConfigFactory.parseString("akka.http.server.preview.enable-http2=on," +
                "akka.actor.provider=cluster")
                .withFallback(ConfigFactory.defaultApplication())
                .resolve();
        ActorSystem core = ActorSystem.create("CORE", conf);
        // running as a single-node pseudo-cluster for now
        Cluster cluster = Cluster.get(core);
        cluster.join(cluster.selfAddress());
        Materializer mat = ActorMaterializer.create(core);
        ORMultiMap<String, String> workspace = ORMultiMap.create();  // TODO(rch): typed cluster singleton?
        ActorRef objective = core.actorOf(Objective.props(workspace), "objective");

        //#concatOrNotFound
        Function<HttpRequest, CompletionStage<HttpResponse>> retrieverService =
                RetrieverServiceHandlerFactory.create(new RetrieverServiceImpl(mat, core, objective), mat, core);
        Function<HttpRequest, CompletionStage<HttpResponse>> echoService =
                EchoServiceHandlerFactory.create(new EchoServiceImpl(), mat, core);
        @SuppressWarnings("unchecked")
        Function<HttpRequest, CompletionStage<HttpResponse>> serviceHandlers =
                ServiceHandler.concatOrNotFound(retrieverService, echoService);

        Http.get(core).bindAndHandleAsync(
                serviceHandlers,
                ConnectHttp.toHost("127.0.0.1", 8080, UseHttp2.always()),
                mat)
                //#concatOrNotFound
                .thenAccept(binding -> {
                    System.out.println("gRPC server bound to: " + binding.localAddress());
                });
    }
}
