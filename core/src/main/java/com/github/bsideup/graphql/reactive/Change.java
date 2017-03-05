package com.github.bsideup.graphql.reactive;

import graphql.execution.ExecutionContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.StreamSupport;

public class Change {

    private final ExecutionContext context;
    private final Object data;

    public Change(ExecutionContext context, Object data) {
        this.context = context;
        this.data = data;
    }

    public ExecutionContext getParentContext() {
        return context instanceof ReactiveContext ? ((ReactiveContext) context).getParent() : null;
    }

    public ExecutionContext getContext() {
        return context;
    }

    public Iterable<Object> getReversePath() {
        return new Iterable<Object>() {

            @Override
            public Iterator<Object> iterator() {
                return new Iterator<Object>() {

                    ExecutionContext parent = context;

                    @Override
                    public boolean hasNext() {
                        return parent instanceof ReactiveContext && (((ReactiveContext) parent).getKey() != null);
                    }

                    @Override
                    public Object next() {
                        ReactiveContext reactiveContext = (ReactiveContext) this.parent;
                        Object result = reactiveContext.getKey();
                        this.parent = reactiveContext.getParent();
                        return result;
                    }
                };
            }
        };
    }

    public List<Object> getPath() {
        return StreamSupport.stream(getReversePath().spliterator(), false)
                .collect(
                        ArrayList::new,
                        (list, e) -> list.add(0, e),
                        (left, right) -> left.addAll(0, right)
                );
    }

    public Object getData() {
        return data;
    }

    @Override
    public String toString() {
        return "Change{" +
                "path=" + getPath() +
                ", data=" + data +
                '}';
    }
}
