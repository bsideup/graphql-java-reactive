package com.github.bsideup.graphql.reactive;

import com.github.bsideup.graphql.reactive.support.AbstractTest;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import io.reactivex.Flowable;
import io.reactivex.Single;
import org.junit.Test;
import org.reactivestreams.Publisher;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.tuple;

public class AdaptersTest extends AbstractTest {

    @Test
    public void testCompletableFuture() throws Exception {
        ReactiveExecutionStrategy strategy = new ReactiveExecutionStrategy();

        GraphQLSchema schema = newQuerySchema(it -> it
                .field(newStringField("a").dataFetcher(env -> CompletableFuture.completedFuture("static")))
        );

        ExecutionResult executionResult = new GraphQL(schema, strategy).execute("{ a }");

        Flowable.fromPublisher((Publisher<Change>) executionResult.getData()).timestamp(scheduler).subscribe(subscriber);

        subscriber
                .assertChanges(it -> it.containsExactly(
                        tuple("00:000", "", ImmutableMap.of("a", "static"))
                ))
                .assertComplete();
    }

    @Test
    public void testCustomOverride() throws Exception {
        ReactiveExecutionStrategy strategy = new ReactiveExecutionStrategy() {
            @Override
            protected Object adapt(Object result) {
                if (result instanceof ListenableFuture) {
                    return Single.create(emitter -> {
                        ListenableFuture<Object> listenableFuture = (ListenableFuture<Object>) result;
                        Futures.addCallback(
                                listenableFuture,
                                new FutureCallback<Object>() {
                                    @Override
                                    public void onSuccess(Object result) {
                                        emitter.onSuccess(result);
                                    }

                                    @Override
                                    public void onFailure(Throwable e) {
                                        emitter.onError(e);
                                    }
                                }
                        );
                    }).toFlowable();
                }
                return super.adapt(result);
            }
        };
        GraphQLSchema schema = newQuerySchema(it -> it
                .field(newStringField("a").dataFetcher(env -> Futures.immediateFuture("static")))
        );

        ExecutionResult executionResult = new GraphQL(schema, strategy).execute("{ a }");

        Flowable.fromPublisher((Publisher<Change>) executionResult.getData()).timestamp(scheduler).subscribe(subscriber);

        subscriber
                .assertChanges(it -> it.containsExactly(
                        tuple("00:000", "", ImmutableMap.of("a", "static"))
                ))
                .assertComplete();
    }
}
