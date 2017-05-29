package com.github.bsideup.graphql.reactive;

import com.github.bsideup.graphql.reactive.support.AbstractTest;
import com.github.bsideup.graphql.reactive.support.ChangesTestSubscriber;
import com.google.common.collect.ImmutableMap;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import io.reactivex.Flowable;
import org.junit.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Arrays;
import java.util.function.Consumer;

import static graphql.schema.GraphQLObjectType.newObject;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class ReactiveExecutionStrategyTest extends AbstractTest {

    protected ReactiveExecutionStrategy strategy = new ReactiveExecutionStrategy();

    @Test
    public void testPlainField() throws Exception {
        GraphQLSchema schema = newQuerySchema(it -> it
                .field(newLongField("a").dataFetcher(env -> Flowable.interval(1, SECONDS, scheduler).take(2)))
        );

        ExecutionResult executionResult = new GraphQL(schema, strategy).execute("{ a }");

        assertThat(executionResult)
                .isNotNull()
                .satisfies(it -> assertThat(it.<Publisher<Change>>getData()).isNotNull().isInstanceOf(Publisher.class));

        Flowable.fromPublisher((Publisher<Change>) executionResult.getData()).timestamp(scheduler).subscribe(subscriber);

        scheduler.advanceTimeBy(2, SECONDS);

        subscriber
                .assertChanges(it -> it.containsExactly(
                        tuple("01:000", "", ImmutableMap.of("a", 0L)),
                        tuple("02:000", "a", 1L)
                ))
                .assertComplete();
    }

    @Test
    public void testStartWith() throws Exception {
        GraphQLSchema schema = newQuerySchema(it -> it
                .field(newLongField("a").dataFetcher(env -> Flowable.interval(1, SECONDS, scheduler).take(2).startWith(-42L)))
        );

        ExecutionResult executionResult = new GraphQL(schema, strategy).execute("{ a }");

        Flowable.fromPublisher((Publisher<Change>) executionResult.getData()).timestamp(scheduler).subscribe(subscriber);

        scheduler.advanceTimeBy(2, SECONDS);

        subscriber
                .assertChanges(it -> it.containsExactly(
                        tuple("00:000", "", ImmutableMap.of("a", -42L)),
                        tuple("01:000", "a", 0L),
                        tuple("02:000", "a", 1L)
                ))
                .assertComplete();
    }

    @Test
    public void testMultipleFields() throws Exception {
        GraphQLSchema schema = newQuerySchema(it -> it
                .field(newLongField("a").dataFetcher(env -> Flowable.interval(300, MILLISECONDS, scheduler).take(8)))
                .field(newLongField("b").dataFetcher(env -> Flowable.timer(2, SECONDS, scheduler)))
                .field(newLongField("c").dataFetcher(env -> Flowable.just(42L)))
        );

        ExecutionResult executionResult = new GraphQL(schema, strategy).execute("{ a, b, c }");

        Flowable.fromPublisher((Publisher<Change>) executionResult.getData()).timestamp(scheduler).subscribe(subscriber);

        // c resolved, wait for a and b
        subscriber.assertEmpty();

        scheduler.advanceTimeBy(1, SECONDS);

        // a & c resolved, wait for b
        subscriber.assertEmpty();

        scheduler.advanceTimeBy(2, SECONDS);

        subscriber
                .assertChanges(it -> it.containsExactly(
                        tuple("02:000", "", ImmutableMap.of("a", 5L, "b", 0L, "c", 42L)),
                        tuple("02:100", "a", 6L),
                        tuple("02:400", "a", 7L)
                ))
                .assertComplete();
    }

    @Test
    public void testNested() throws Exception {
        GraphQLObjectType innerType = newObject()
                .name("Inner")
                .field(newStringField("b").dataFetcher(env -> Flowable.interval(300, MILLISECONDS, scheduler).take(4).map(i -> i + " " + env.getSource())))
                .build();

        GraphQLSchema schema = newQuerySchema(it -> it
                .field(newField("a", innerType).dataFetcher(env -> Flowable.interval(1, SECONDS, scheduler).take(3)))
        );

        ExecutionResult executionResult = new GraphQL(schema, strategy).execute("{ a { b } }");

        Flowable.fromPublisher((Publisher<Change>) executionResult.getData()).timestamp(scheduler).subscribe(subscriber);

        scheduler.advanceTimeBy(5, SECONDS);

        subscriber
                .assertChanges(it -> it.containsExactly(
                        tuple("01:300", "", ImmutableMap.of("a", ImmutableMap.of("b", "0 0"))),
                        tuple("01:600", "a.b", "1 0"),
                        tuple("01:900", "a.b", "2 0"),
                        tuple("02:300", "a", ImmutableMap.of("b", "0 1")),
                        tuple("02:600", "a.b", "1 1"),
                        tuple("02:900", "a.b", "2 1"),
                        tuple("03:300", "a", ImmutableMap.of("b", "0 2")),
                        tuple("03:600", "a.b", "1 2"),
                        tuple("03:900", "a.b", "2 2"),
                        tuple("04:200", "a.b", "3 2")
                ))
                .assertComplete();
    }

    @Test
    public void testArray() throws Exception {
        GraphQLObjectType innerType = newObject()
                .name("Inner")
                .field(newStringField("b").dataFetcher(env -> Flowable.interval(300, MILLISECONDS, scheduler).take(4).map(i -> i + " " + env.getSource())))
                .build();

        GraphQLSchema schema = newQuerySchema(it -> it
                .field(newField("a", new GraphQLList(innerType)).dataFetcher(env -> Flowable.interval(500, MILLISECONDS, scheduler).take(3).toList().toFlowable()))
        );

        ExecutionResult executionResult = new GraphQL(schema, strategy).execute("{ a { b } }");

        Flowable.fromPublisher((Publisher<Change>) executionResult.getData()).timestamp(scheduler).subscribe(subscriber);

        scheduler.advanceTimeBy(3, SECONDS);

        subscriber
                .assertChanges(it -> it.containsExactly(
                        tuple("01:800", "", ImmutableMap.of("a", Arrays.asList(ImmutableMap.of("b", "0 0"), ImmutableMap.of("b", "0 1"), ImmutableMap.of("b", "0 2")))),

                        tuple("02:100", "a[0].b", "1 0"),
                        tuple("02:100", "a[1].b", "1 1"),
                        tuple("02:100", "a[2].b", "1 2"),

                        tuple("02:400", "a[0].b", "2 0"),
                        tuple("02:400", "a[1].b", "2 1"),
                        tuple("02:400", "a[2].b", "2 2"),

                        tuple("02:700", "a[0].b", "3 0"),
                        tuple("02:700", "a[1].b", "3 1"),
                        tuple("02:700", "a[2].b", "3 2")
                ))
                .assertComplete();
    }

    @Test
    public void testAnyPublisher() throws Exception {
        Duration tick = Duration.ofSeconds(1);

        GraphQLSchema schema = newQuerySchema(it -> it
                // use Flux from Project Reactor
                .field(newLongField("a").dataFetcher(env -> Flux.interval(tick).take(2)))
        );

        ExecutionResult executionResult = new GraphQL(schema, strategy).execute("{ a }");

        // Use Reactor's StepVerifier
        StepVerifier.withVirtualTime(() -> (Publisher<Change>) executionResult.getData())
                .expectSubscription()
                .expectNoEvent(tick)
                .assertNext(matchChange("", ImmutableMap.of("a", 0L)))
                .expectNoEvent(tick)
                .assertNext(matchChange("a", 1L))
                .verifyComplete();
    }

    protected Consumer<Change> matchChange(String path, Object value) {
        return change -> assertThat(change)
                .describedAs("change")
                .isNotNull()
                .satisfies(it -> assertThat(ChangesTestSubscriber.getPathString(it)).describedAs("path").isEqualTo(path))
                .satisfies(it -> assertThat(it.getData()).describedAs("data").isEqualTo(value));
    }

    @Test
    public void testStaticValues() throws Exception {
        GraphQLSchema schema = newQuerySchema(it -> it
                .field(newStringField("a").dataFetcher(env -> "staticA"))
                .field(newStringField("b").dataFetcher(env -> "staticB"))
        );

        ExecutionResult executionResult = new GraphQL(schema, strategy).execute("{ a, b }");

        Flowable.fromPublisher((Publisher<Change>) executionResult.getData()).timestamp(scheduler).subscribe(subscriber);

        subscriber
                .assertChanges(it -> it.containsExactly(
                        tuple("00:000", "", ImmutableMap.of("a", "staticA", "b", "staticB"))
                ))
                .assertComplete();
    }
}
