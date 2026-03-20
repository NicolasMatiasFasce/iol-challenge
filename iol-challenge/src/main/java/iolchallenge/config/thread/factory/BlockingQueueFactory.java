package iolchallenge.config.thread.factory;

import iolchallenge.config.thread.model.*;
import org.eclipse.jetty.util.BlockingArrayQueue;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.function.Function;

import static java.util.Optional.ofNullable;

@Component
public class BlockingQueueFactory {

    public BlockingQueue<Runnable> create(BlockingQueueProperties properties) {
        var array = ofNullable(properties.array()).map(arrayFactory);
        var jettyArray = ofNullable(properties.jettyArray()).map(jettyArrayFactory);
        var synchronous = ofNullable(properties.synchronous()).map(synchronousFactory);
        var linked = ofNullable(properties.linked()).map(linkedFactory);

        return array
                .or(() -> jettyArray)
                .or(() -> synchronous)
                .or(() -> linked)
                .orElseThrow(() -> new UnsupportedOperationException("Failed to bind ?.thread-pool.queue properties. Update your application's configuration"));
    }

    private static final Function<ArrayBlockingQueueProperties, BlockingQueue<Runnable>> arrayFactory = properties ->
            ofNullable(properties.fair())
            .map(f -> new ArrayBlockingQueue<Runnable>(properties.capacity(), f))
            .orElseGet(() -> new ArrayBlockingQueue<>(properties.capacity()));

    private static final Function<BlockingArrayQueueProperties, BlockingQueue<Runnable>> jettyArrayFactory = properties -> {
        if (properties.capacity() != null && properties.growBy() != null && properties.maxCapacity() != null) {
            return new BlockingArrayQueue<>(properties.capacity(), properties.growBy(), properties.maxCapacity());
        }

        if (properties.capacity() != null && properties.growBy() != null) {
            return new BlockingArrayQueue<>(properties.capacity(), properties.growBy());
        }

        if (properties.capacity() != null) {
            return new BlockingArrayQueue<>(properties.capacity());
        }

        if (properties.maxCapacity() != null) {
            return new BlockingArrayQueue<>(properties.maxCapacity());
        }

        return new BlockingArrayQueue<>();
    };

    private static final Function<SynchronousQueueProperties, BlockingQueue<Runnable>> synchronousFactory = properties ->
        ofNullable(properties.fair())
                .map((Function<Boolean, SynchronousQueue<Runnable>>) SynchronousQueue::new)
                .orElseGet(SynchronousQueue::new);

    private static final Function<LinkedBlockingQueueProperties, BlockingQueue<Runnable>> linkedFactory = properties ->
            ofNullable(properties.capacity())
                    .map((Function<Integer, LinkedBlockingQueue<Runnable>>) LinkedBlockingQueue::new)
                    .orElseGet(LinkedBlockingQueue::new);
}
