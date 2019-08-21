package ws.tango;

import akka.actor.ActorSystem;
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
        Config conf = ConfigFactory.parseString("akka.http.server.preview.enable-http2 = on")
                .withFallback(ConfigFactory.defaultApplication())
                .resolve();
        ActorSystem akws = ActorSystem.create("akws", conf);
        Materializer mat = ActorMaterializer.create(akws);

        //#concatOrNotFound
        Function<HttpRequest, CompletionStage<HttpResponse>> retrieverService =
                RetrieverServiceHandlerFactory.create(new RetrieverServiceImpl(mat), mat, akws);
        Function<HttpRequest, CompletionStage<HttpResponse>> echoService =
                EchoServiceHandlerFactory.create(new EchoServiceImpl(), mat, akws);
        @SuppressWarnings("unchecked")
        Function<HttpRequest, CompletionStage<HttpResponse>> serviceHandlers =
                ServiceHandler.concatOrNotFound(retrieverService, echoService);

        Http.get(akws).bindAndHandleAsync(
                serviceHandlers,
                ConnectHttp.toHost("127.0.0.1", 8080, UseHttp2.always()),
                mat)
                //#concatOrNotFound
                .thenAccept(binding -> {
                    System.out.println("gRPC server bound to: " + binding.localAddress());
                });
    }
}
