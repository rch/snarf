package ws.grpc;
/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.stream.Materializer;

import ws.grpc.api.*;
import ws.tango.HttpRequestActor;


public class RetrieverServiceImpl implements RetrieverService {
    private final Materializer mat;
    private final ActorSystem core;
    private final ActorRef objective;

    public RetrieverServiceImpl(Materializer mat, ActorSystem core, ActorRef objective) {
        this.mat = mat;
        this.core = core;
        this.objective = objective;
    }

    @Override
    public CompletionStage<LocalList> fetch(ImageList inbound) {
        ActorRef ref = core.actorOf(Props.create(HttpRequestActor.class));
        ref.tell(inbound, ActorRef.noSender());
        LocalList.Builder reply = LocalList.newBuilder().setStatus("OK");
        for (ImageUrl img : inbound.getItemsList()) {
            System.out.println(img.getText());
            reply.addItems(img.getText());
            this.objective.tell(img.getText(), ref);
        }
        return CompletableFuture.completedFuture(reply.build());
    }
}
//#full-service-impl
