package ws.grpc.echo;
/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import ws.grpc.api.echo.EchoMessage;
import ws.grpc.api.echo.EchoService;


public class EchoServiceImpl implements EchoService {

    @Override
    public CompletionStage<EchoMessage> echo(EchoMessage in) {
        return CompletableFuture.completedFuture(in);
    }
}
