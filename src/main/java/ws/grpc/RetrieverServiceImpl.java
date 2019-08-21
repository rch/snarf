package ws.grpc;
/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import akka.NotUsed;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

import ws.grpc.api.*;


public class RetrieverServiceImpl implements RetrieverService {
    private final Materializer mat;

    public RetrieverServiceImpl(Materializer mat) {
        this.mat = mat;
    }

    @Override
    public CompletionStage<LocalList> fetch(RemoteList in) {
        LocalList reply = LocalList.newBuilder().setStatus("OK").build();
        return CompletableFuture.completedFuture(reply);
    }
}
//#full-service-impl
