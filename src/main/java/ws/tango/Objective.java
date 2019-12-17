package ws.tango;

import static java.util.concurrent.TimeUnit.SECONDS;

import akka.actor.Props;
import akka.cluster.ddata.*;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import scala.Option;
import scala.collection.immutable.Set;
import scala.concurrent.duration.Duration;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.cluster.Cluster;
import akka.cluster.ddata.Replicator.Changed;
import akka.cluster.ddata.Replicator.Subscribe;
import akka.cluster.ddata.Replicator.Update;
import akka.cluster.ddata.Replicator.UpdateResponse;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import ws.grpc.api.ContainsUrlRequest;


public class Objective extends AbstractActor {

    private static final int SEED = 25964951;

    private static final String TICK = "tick";

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    private final ActorRef replicator =
            DistributedData.get(getContext().getSystem()).replicator();
    private final Cluster node = Cluster.get(getContext().getSystem());

    private final Cancellable tickTask = getContext().getSystem().scheduler().schedule(
            Duration.create(5, SECONDS), Duration.create(5, SECONDS), getSelf(), TICK,
            getContext().dispatcher(), getSelf());

    private final Key<ORSet<String>> dataKey = ORSetKey.create("key");

    private final HashFunction hf = Hashing.murmur3_32(SEED);

    private ORMultiMap<String, String> workspace;

    static Props props(ORMultiMap<String, String> workspace) {
        return Props.create(Objective.class, workspace);
    }

    private Objective(ORMultiMap<String, String> workspace) {
        this.workspace = workspace;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(String.class, a -> receiveUrl(a))
                .match(ContainsUrlRequest.class, msg -> recieveContainsUrlRequest(msg))
                .build();
    }

    private String hashKey(String url) {
        return this.hf.hashString(url.toLowerCase(), StandardCharsets.UTF_8).toString().substring(0, 3);
    }

    private void receiveUrl(String url) {
        //  The number of top level entries should not exceed 100000
        //  We will be able to improve this if needed, but the design is still not intended for billions of entries.
        this.workspace.addBinding(this.node, this.hashKey(url), url);
    }

    private boolean recieveContainsUrlRequest(ContainsUrlRequest msg) {
        String url = msg.getText();
        String key = this.hashKey(url);
        Option<Set<String>> s = this.workspace.get(key);
        if (s.isEmpty()) {
            return false;
        } else {
            return s.contains(url);
        }
    }


    private void receiveTick() {
        String s = String.valueOf((char) ThreadLocalRandom.current().nextInt(97, 123));
        if (ThreadLocalRandom.current().nextBoolean()) {
            // add
            log.info("Adding: {}", s);
            Update<ORSet<String>> update = new Update<>(
                    dataKey,
                    ORSet.create(),
                    Replicator.writeLocal(),
                    curr ->  curr.add(node, s));
            replicator.tell(update, getSelf());
        } else {
            // remove
            log.info("Removing: {}", s);
            Update<ORSet<String>> update = new Update<>(
                    dataKey,
                    ORSet.create(),
                    Replicator.writeLocal(),
                    curr ->  curr.remove(node, s));
            replicator.tell(update, getSelf());
        }
    }


    private void receiveChanged(Changed<ORSet<String>> c) {
        ORSet<String> data = c.dataValue();
        log.info("Current elements: {}", data.getElements());
    }

    private void receiveUpdateResoponse() {
        // ignore
    }


    @Override
    public void preStart() {
        Subscribe<ORSet<String>> subscribe = new Subscribe<>(dataKey, getSelf());
        replicator.tell(subscribe, ActorRef.noSender());
    }

    @Override
    public void postStop(){
        tickTask.cancel();
    }

}

