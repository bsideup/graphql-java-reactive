package com.example;

import com.github.bsideup.graphql.reactive.Change;
import com.github.bsideup.graphql.reactive.ReactiveExecutionStrategy;
import com.google.common.collect.ImmutableMap;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import io.reactivex.Completable;
import org.reactivestreams.Publisher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static graphql.Scalars.GraphQLFloat;
import static graphql.Scalars.GraphQLLong;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;
import static graphql.schema.GraphQLSchema.newSchema;

@RestController
public class GraphQLController {

    static {
        // Simulate memory usage

        Completable
                .fromRunnable(() -> {
                    byte[] bytes = new byte[5000 * 1000];
                })
                .repeatWhen(it -> it.delay(500, TimeUnit.MILLISECONDS))
                .subscribe();
    }

    GraphQLObjectType serverStats = newObject()
            .name("ServerStats")
            .field(newFieldDefinition()
                    .name("cpu")
                    .type(GraphQLFloat)
                    .dataFetcher(env -> Flux
                            .intervalMillis(1000)
                            .map(__ -> ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage())
                            // Ignore
                            .distinctUntilChanged(Double::intValue)
                    )
            )
            .field(newFieldDefinition()
                    .name("memory")
                    .staticValue(Runtime.getRuntime())
                    .type(newObject()
                            .name("MemoryStats")
                            .field(newFieldDefinition()
                                    .name("max")
                                    .type(GraphQLLong)
                                    .dataFetcher(env -> ((Runtime) env.getSource()).maxMemory())
                            )
                            .field(newFieldDefinition()
                                    .name("allocated")
                                    .type(GraphQLLong)
                                    .dataFetcher(env -> Flux
                                            .intervalMillis(1000)
                                            .map(__ -> ((Runtime) env.getSource()).totalMemory())
                                            // Ignore changes less than 1Kb
                                            .distinctUntilChanged(it -> it.intValue() / 1000000)
                                    )
                            )
                            .field(newFieldDefinition()
                                    .name("free")
                                    .type(GraphQLLong)
                                    .dataFetcher(env -> Flux
                                            .intervalMillis(1000)
                                            .map(__ -> ((Runtime) env.getSource()).freeMemory())
                                            // Ignore changes less than 1Mb
                                            .distinctUntilChanged(it -> it.intValue() / 1000000)
                                    )
                            )
                    )
            )
            .build();

    GraphQLSchema schema = newSchema()
            .query(newObject()
                    .name("QueryType")
                    .field(newFieldDefinition().name("serverStats").type(serverStats).staticValue(new Object()))
            )
            .build();

    GraphQL graphQL = new GraphQL(schema, new ReactiveExecutionStrategy(), new ReactiveExecutionStrategy());

    @GetMapping(value = "/graphql", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Map<String, Object>> query(@RequestParam("query") String query) throws Exception {
        ExecutionResult result = graphQL.execute(query);

        List<GraphQLError> errors = result.getErrors();
        if (!errors.isEmpty()) {
            // TODO
            return Flux.error(new Exception(errors.get(0).toString()));
        }

        return Flux.from((Publisher<Change>) result.getData())
                .map(change -> ImmutableMap.of("path", change.getPath(), "data", change.getData()));
    }

    @GetMapping(path = "/", produces = MediaType.TEXT_HTML_VALUE)
    public Resource index() {
        return new ClassPathResource("static/index.html");
    }
}
