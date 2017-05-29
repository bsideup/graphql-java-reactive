package com.github.bsideup.graphql.reactive;

import graphql.execution.ExecutionContext;

class ReactiveContext extends ExecutionContext {

    final Object key;

    final ExecutionContext parent;

    public ReactiveContext(ExecutionContext context, Object key) {
        super(
                context.getInstrumentation(),
                context.getExecutionId(),
                context.getGraphQLSchema(),
                context.getQueryStrategy(),
                context.getMutationStrategy(),
                context.getSubscriptionStrategy(),
                context.getFragmentsByName(),
                context.getOperationDefinition(),
                context.getVariables(),
                context.getRoot()
        );
        this.key = key;
        this.parent = context;
    }

    public Object getKey() {
        return key;
    }

    public ExecutionContext getParent() {
        return parent;
    }
}
