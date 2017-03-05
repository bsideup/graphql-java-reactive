package com.github.bsideup.graphql.reactive.support;

import com.github.bsideup.graphql.reactive.Change;
import io.reactivex.schedulers.Timed;
import io.reactivex.subscribers.TestSubscriber;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.assertj.core.api.ListAssert;
import org.assertj.core.groups.Tuple;

import java.util.function.Consumer;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class ChangesTestSubscriber extends TestSubscriber<Timed<Change>> {

    public static String getPathString(Change change) {
        StringBuilder pathBuilder = new StringBuilder();

        for (Object key : change.getPath()) {
            if (key instanceof Integer) {
                pathBuilder.append("[").append(key).append("]");
            } else {
                if (pathBuilder.length() > 0) {
                    pathBuilder.append(".");
                }

                pathBuilder.append(key);
            }
        }

        return pathBuilder.toString();
    }

    public ChangesTestSubscriber assertChanges(Consumer<ListAssert<Tuple>> consumer) {
        consumer.accept(
                assertThat(values())
                        .extracting(
                                it -> DurationFormatUtils.formatDuration(it.time(MILLISECONDS), "ss:SSS", true),
                                it -> getPathString(it.value()),
                                it -> it.value().getData()
                        ));
        return this;
    }
}
