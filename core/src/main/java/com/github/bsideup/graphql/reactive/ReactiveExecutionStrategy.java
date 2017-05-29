package com.github.bsideup.graphql.reactive;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.execution.*;
import graphql.language.Field;
import graphql.schema.*;
import io.reactivex.Flowable;
import io.reactivex.Single;
import org.reactivestreams.Publisher;

import java.util.AbstractMap.SimpleEntry;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static graphql.execution.ExecutionParameters.newParameters;
import static java.util.stream.Collectors.toList;

public class ReactiveExecutionStrategy extends ExecutionStrategy {

    @Override
    public ExecutionResult execute(ExecutionContext context, ExecutionParameters parameters) throws NonNullableFieldWasNullException {
        Map<String, List<Field>> fields = parameters.fields();
        Object source = parameters.source();
        return complexChangesFlow(
                context,
                source,
                __ -> fields.entrySet().stream(),
                (entry, sourceValue) -> {
                    ReactiveContext subContext = new ReactiveContext(context, entry.getKey());

                    ExecutionParameters newParameters = newParameters()
                            .typeInfo(parameters.typeInfo())
                            .fields(parameters.fields())
                            .arguments(parameters.arguments())
                            .source(source == null ? null : sourceValue)
                            .build();
                    return resolveField(subContext, newParameters, entry.getValue());
                },
                results -> {
                    Map<Object, Object> result = new HashMap<>();
                    for (Object entry : results) {
                        result.put(((SimpleEntry) entry).getKey(), ((SimpleEntry) entry).getValue());
                    }
                    return result;
                }
        );
    }

    protected Object adapt(Object result) {
        if (result instanceof CompletionStage) {
            CompletionStage<Object> stage = (CompletionStage<Object>) result;

            return Single.create(emitter -> stage.whenComplete((it, e) -> {
                if (e != null) {
                    emitter.onError(e);
                } else {
                    emitter.onSuccess(it);
                }
            })).toFlowable();
        }

        return result;
    }

    @Override
    protected ExecutionResult completeValue(ExecutionContext context, ExecutionParameters parameters, List<Field> fields) {
        Object result = adapt(parameters.source());
        GraphQLType fieldType = parameters.typeInfo().type();

        if ((fieldType instanceof GraphQLScalarType || fieldType instanceof GraphQLEnumType) && result instanceof Publisher) {
            Flowable<Change> changesFlow = Flowable.fromPublisher((Publisher<?>) result)
                    .map(it -> {
                        ExecutionParameters newParameters = newParameters()
                                .source(it)
                                .fields(parameters.fields())
                                .typeInfo(parameters.typeInfo())
                                .build();

                        return new Change(context, completeValue(context, newParameters, fields).getData());
                    });

            return new ExecutionResultImpl(changesFlow, null);
        }

        if (fieldType instanceof GraphQLList && result != null) {
            GraphQLType wrappedType = ((GraphQLList) fieldType).getWrappedType();

            return complexChangesFlow(
                    context,
                    result,
                    source -> {
                        Stream<Object> stream;
                        if (source instanceof Iterable) {
                            stream = StreamSupport.stream(((Iterable<Object>) source).spliterator(), false);
                        } else {
                            stream = Arrays.stream((Object[]) source);
                        }

                        AtomicInteger i = new AtomicInteger();
                        return stream.map(it -> new SimpleEntry<>(i.getAndIncrement(), adapt(it)));
                    },
                    (entry, __) -> {
                        ExecutionParameters newParameters = ExecutionParameters.newParameters()
                                .source(entry.getValue())
                                .fields(parameters.fields())
                                .arguments(parameters.arguments())
                                .typeInfo(parameters.typeInfo().asType(wrappedType))
                                .build();
                        return completeValue(new ReactiveContext(context, entry.getKey()), newParameters, fields);
                    },
                    results -> Stream.of(results)
                            .map(SimpleEntry.class::cast)
                            .sorted(Comparator.comparingInt(it -> (Integer) it.getKey()))
                            .map(SimpleEntry::getValue)
                            .collect(toList())
            );
        }

        return super.completeValue(context, parameters, fields);
    }

    protected <K, V> ExecutionResultImpl complexChangesFlow(
            ExecutionContext context,
            Object source,
            Function<Object, Stream<Entry<K, V>>> entries,
            BiFunction<Entry<K, V>, Object, ExecutionResult> executor,
            io.reactivex.functions.Function<Object[], Object> resultCombiner
    ) {
        Publisher<?> sourceFlow;
        if (source instanceof Publisher) {
            sourceFlow = (Publisher<?>) source;
        } else {
            sourceFlow = Flowable.just(source != null ? source : new Object());
        }

        Flowable<Change> changesFlow = Flowable.fromPublisher(sourceFlow).switchMap(sourceValue -> {
            List<Publisher<Change>> changes = new ArrayList<>();

            AtomicBoolean initialized = new AtomicBoolean(false);

            Stream<Flowable<Entry<K, Object>>> subFlows = entries.apply(sourceValue)
                    .map(entry -> {
                        ExecutionResult executionResult = executor.apply(entry, sourceValue);

                        Object data = executionResult != null ? executionResult.getData() : null;

                        if (data instanceof Publisher) {
                            Flowable<Change> changeFlow = Flowable.fromPublisher((Publisher<Change>) data)
                                    // Share it to avoid double subscription
                                    .share();

                            changes.add(changeFlow);

                            return changeFlow
                                    // Ignore all changes except from current context
                                    .filter(change -> context == change.getParentContext())
                                    .map(it -> new SimpleEntry<>(entry.getKey(), it.getData()));
                        } else {
                            return Flowable.just(new SimpleEntry<>(entry.getKey(), data));
                        }
                    });

            return Flowable
                    // Combine latest notification from all inner publishers
                    .combineLatest((Iterable<Flowable<Entry<K, Object>>>) subFlows::iterator, resultCombiner)
                    // Take one, then start producing changes
                    .take(1)
                    .doOnNext(__ -> initialized.set(true))
                    .map(it -> new Change(context, it))
                    .mergeWith(Flowable
                            .merge(changes)
                            // Filter out all notifications until combineLatest produces a result
                            .filter(__ -> initialized.get())
                            // Skip first notification because it's a cause of combineLatest's completition
                            .skip(1)
                    );
        });

        return new ExecutionResultImpl(changesFlow, context.getErrors());
    }
}
