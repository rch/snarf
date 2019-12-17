package ws.tango;

import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.FileIO;
import akka.stream.Materializer;
import akka.util.ByteString;
import scala.concurrent.duration.FiniteDuration;
import scala.concurrent.ExecutionContextExecutor;
import com.google.protobuf.ProtocolStringList;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static akka.pattern.PatternsCS.pipe;

// TODO(rch): migrate to 2.6.x
//import static akka.util.ByteString.emptyByteString;

import ws.grpc.api.ImageUrl;
import ws.grpc.api.ImageList;

import javax.imageio.ImageIO;

// http://localhost:8080/static/FApqk3D.jpg

public class HttpRequestActor extends AbstractActor {

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    private final Http http = Http.get(context().system());
    private final ExecutionContextExecutor dispatcher = context().dispatcher();
    private final Materializer materializer = ActorMaterializer.create(context());

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(ImageUrl.class, msg -> pipe(fetch(msg), dispatcher).to(self()))
                .match(ImageList.class, this::handleBatch)
                .match(HttpResponse.class, this::handleHttpResponse)
                .build();
    }

    private void handleBatch(ImageList msg) {
        java.util.List<ImageUrl> lst = msg.getItemsList();
        for (ImageUrl image : lst) {
            self().tell(image, self());
        }
    }

    private CompletionStage<HttpResponse> fetch(ImageUrl msg) {
        return http.singleRequest(HttpRequest.create(msg.getText()));
    }

    private void handleHttpResponse(HttpResponse msg) {
        final CompletionStage<HttpEntity.Strict> strictEntity = msg.entity()
                .toStrict(FiniteDuration.create(3, TimeUnit.SECONDS).toMillis(), materializer);
        strictEntity.thenCompose(strict -> strict.getDataBytes()
                .runFold(ByteString.empty(), (acc, b) -> acc.concat(b), materializer)
                .thenApply(this::handleBytes));
    }

    private Map<String, Integer> handleBytes(ByteString in) {
        Map<String, Integer> freq = new HashMap<String, Integer>();
        try {
            int pixel;
            String color;
            BufferedImage img = ImageIO.read(in.iterator().asInputStream());
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    pixel = img.getRGB(x, y);
                    color = HexableColor.toHex(
                            (pixel >> 16) & 0xff,
                            (pixel >> 8) & 0xff,
                            (pixel) & 0xff);
                    freq.put(color, 1 + freq.getOrDefault(color, 0));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return freq;
    }

    private static List<Map.Entry<String, Integer>> topN(Map<String, Integer> freq, Integer limit) {
        return freq.entrySet()
                .stream()
                .sorted((i1, i2) -> Integer.compare(i2.getValue(), i1.getValue()))
                .limit(limit)
                .collect(Collectors.toList());
    }
}
