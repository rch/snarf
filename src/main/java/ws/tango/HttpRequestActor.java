package ws.tango;

import akka.actor.AbstractActor;
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
import java.util.Iterator;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static akka.pattern.PatternsCS.pipe;

// TODO(rch): migrate to 2.6.x
//import static akka.util.ByteString.emptyByteString;

import ws.grpc.api.Image;
import ws.grpc.api.ImageList;

import javax.imageio.ImageIO;


public class HttpRequestActor extends AbstractActor {
    final Http http = Http.get(context().system());
    final ExecutionContextExecutor dispatcher = context().dispatcher();
    final Materializer materializer = ActorMaterializer.create(context());

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(ImageList.class, msg -> handleBatch(msg))
                .match(Image.class, msg -> pipe(fetch(msg), dispatcher).to(self()))
                .match(HttpResponse.class, msg -> handleHttpResponse(msg))
                .build();
    }

    private void handleBatch(ImageList msg) {
        java.util.List<Image> lst = msg.getItemsList();
        for (Iterator<Image> it = lst.iterator(); it.hasNext();) {
            self().tell(it.next(), self());
        }
    }

    private CompletionStage<HttpResponse> fetch(Image msg) {
        System.out.println("fetching: " + msg.getUrl());
        return http.singleRequest(HttpRequest.create(msg.getUrl()));
    }

    private void handleHttpResponse(HttpResponse msg) {
        System.out.println("HttpResponse: " + msg);
        final CompletionStage<HttpEntity.Strict> strictEntity = msg.entity()
                .toStrict(FiniteDuration.create(3, TimeUnit.SECONDS).toMillis(), materializer);
        strictEntity.thenCompose(strict -> strict.getDataBytes()
                .runFold(ByteString.empty(), (acc, b) -> acc.concat(b), materializer)
                .thenApply(this::handleBytes));
    }

    private BufferedImage handleBytes(ByteString in) {
        File outputFile = new File("/tmp/output.png");
        BufferedImage img = null;
        int[] pixel;
        try {
            img = ImageIO.read(in.iterator().asInputStream());
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    pixel = img.getRaster().getPixel(x, y, new int[3]);
                    System.out.println(HexableColor.toHex(pixel[0], pixel[1], pixel[2]));
                }
            }
            ImageIO.write(img, "png", outputFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return img;
    }
}
