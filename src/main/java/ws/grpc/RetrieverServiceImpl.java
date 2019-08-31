package ws.grpc;
/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.NotUsed;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

import ws.grpc.api.*;
import ws.tango.HttpRequestActor;


public class RetrieverServiceImpl implements RetrieverService {
    private final Materializer mat;
    private final ActorSystem core;

    public RetrieverServiceImpl(Materializer mat, ActorSystem core) {
        this.mat = mat;
        this.core = core;
    }

    @Override
    public CompletionStage<LocalList> fetch(RemoteList in) {
        ActorRef ref = core.actorOf(Props.create(HttpRequestActor.class));
        ref.tell(in, ActorRef.noSender());
        LocalList reply = LocalList.newBuilder().setStatus("OK").build();
        return CompletableFuture.completedFuture(reply);
    }
}
//#full-service-impl
