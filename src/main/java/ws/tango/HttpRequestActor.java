package ws.tango;

import akka.actor.AbstractActor;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import scala.concurrent.ExecutionContextExecutor;
import com.google.protobuf.ProtocolStringList;

import java.util.Iterator;
import java.util.concurrent.CompletionStage;
import static akka.pattern.PatternsCS.pipe;
import ws.grpc.api.RemoteList;


public class HttpRequestActor extends AbstractActor {
    final Http http = Http.get(context().system());
    final ExecutionContextExecutor dispatcher = context().dispatcher();
    final Materializer materializer = ActorMaterializer.create(context());

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(RemoteList.class, msg -> handleBatch(msg))
                .match(String.class, url -> pipe(fetch(url), dispatcher).to(self()))
                .build();
    }

    private void handleBatch(RemoteList msg) {
        ProtocolStringList lst = msg.getItemsList();
        for (Iterator<String> it = lst.iterator(); it.hasNext();) {
            CompletionStage<HttpResponse> stage = fetch(it.next());
        }
    }

    CompletionStage<HttpResponse> fetch(String url) {
        System.out.println("fetching: " + url);
        return http.singleRequest(HttpRequest.create(url));
    }
}
