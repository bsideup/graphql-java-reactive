package com.github.bsideup.graphql.reactive.support;

import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import io.reactivex.schedulers.TestScheduler;

import java.util.function.Consumer;

import static graphql.Scalars.GraphQLLong;
import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;
import static graphql.schema.GraphQLSchema.newSchema;

public class AbstractTest {

    protected TestScheduler scheduler = new TestScheduler();

    protected ChangesTestSubscriber subscriber = new ChangesTestSubscriber();

    protected GraphQLFieldDefinition.Builder newLongField(String name) {
        return newField(name, GraphQLLong);
    }

    protected GraphQLFieldDefinition.Builder newStringField(String name) {
        return newField(name, GraphQLString);
    }

    protected GraphQLFieldDefinition.Builder newField(String name, GraphQLOutputType type) {
        return newFieldDefinition().name(name).type(type);
    }

    protected GraphQLSchema newQuerySchema(Consumer<GraphQLObjectType.Builder> queryConsumer) {
        GraphQLObjectType.Builder queryType = newObject().name("QueryType");

        queryConsumer.accept(queryType);

        return newSchema().query(queryType).build();
    }
}
