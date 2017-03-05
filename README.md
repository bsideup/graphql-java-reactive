# graphql-java-reactive

[Reactive Streams](http://www.reactive-streams.org)-based execution strategy for http://github.com/graphql-java/graphql-java.

# Table of Contents
 
- [Overview](#overview)
- [Hello World](#hello-world)
- [Migration](#migration)
- [License](#license)
 

 # Overview
 While GraphQL allows you to define very rich queries with multiple levels of depth, it is not possible to track the data changes efficiently. Once you received the data, you have to poll your GraphQL API.  Moreover, even if a single field changed the whole object will be sent.

Subscription model helps to avoid it, and instead of sending the data it sends the changes.

# Hello World

This is "hello world" in graphql-java-reactive:

```java
GraphQLSchema schema = newSchema()
        .query(newObject()
                .name("helloWorldQuery")
                .field(newFieldDefinition()
                        .type(GraphQLString)
                        .name("hello")
                        .dataFetcher(env -> Flowable
                                .interval(1, TimeUnit.SECONDS)
                                .take(3)
                                .map(i -> "world " + i))
                )
        )
        .build();

GraphQL graphQL = new GraphQL(schema, new ReactiveExecutionStrategy());

Publisher<Change> changes = (Publisher<Change>) graphQL.execute("{ hello }").getData();

Flowable.fromPublisher(changes).timeInterval().blockingSubscribe(System.out::println);
// Prints:
// Timed[time=1017, unit=MILLISECONDS, value=Change{path=[], data={hello=world 0}}]
// Timed[time=1000, unit=MILLISECONDS, value=Change{path=[hello], data=world 1}]
// Timed[time=1000, unit=MILLISECONDS, value=Change{path=[hello], data=world 2}]
```

As you can see, initial object was dispatched as a change with an empty path.  
Since `hello` is defined as an asynchronous publisher with 1 second interval, we received it after 1 second.

Every other change is more specific and changes only `hello` property.

**P.S.** This example uses RxJava 2, but you can use any Reactive Streams compatible implementation like Project Reactor, RxJava 1, etc...

# Migration
Notable change from the standard execution strategy is that the result is not a value, but `Publisher` of changes. However, first change always contains fully resolved object.  
It means that you can use `.take(1).map(it -> it.getData())` to emulate old behavior, but with Reactive types.

DataFetcher might return non-Reactive type. By default only `CompletableFuture` will be treated as asynchronous type (you [can override `adapt` method](https://github.com/bsideup/graphql-java-reactive/blob/126d590/core/src/test/java/com/github/bsideup/graphql/reactive/AdaptersTest.java#L42) from ReactiveExecutionStrategy to add your own types), every other type will be considered as a static value.

# License

graphql-java-reactive is licensed under the MIT License. See [LICENSE](LICENSE.md) for details.

Copyright (c) 2017, Sergei Egorov and [Contributors](https://github.com/bsideup/graphql-java-reactive/graphs/contributors)
