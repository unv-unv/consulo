// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.application.util.query;

import consulo.application.Application;
import consulo.application.util.ReadActionProcessor;
import consulo.application.util.function.CommonProcessors;
import consulo.application.util.function.Processors;
import consulo.util.collection.UnmodifiableIterator;
import consulo.util.concurrent.AsyncFuture;
import consulo.util.concurrent.AsyncUtil;
import consulo.util.lang.ObjectUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.function.Predicate;

/**
 * @author peter
 */
public abstract class AbstractQuery<Result> implements Query<Result> {
    private final ThreadLocal<Boolean> myIsProcessing = new ThreadLocal<>();

    // some clients rely on the (accidental) order of found result
    // to discourage them, randomize the results sometimes to induce errors caused by the order reliance
    private static final boolean RANDOMIZE = Application.get().isUnitTestMode() || Application.get().isInternal();
    private static final Comparator<Object> CRAZY_ORDER =
        (o1, o2) -> -Integer.compare(System.identityHashCode(o1), System.identityHashCode(o2));

    @Override
    @Nonnull
    public Collection<Result> findAll() {
        assertNotProcessing();
        List<Result> result = new ArrayList<>();
        Predicate<Result> processor = Processors.cancelableCollectProcessor(result);
        forEach(processor);
        if (RANDOMIZE && result.size() > 1) {
            result.sort(CRAZY_ORDER);
        }
        return result;
    }

    @Nonnull
    @Override
    public Iterator<Result> iterator() {
        assertNotProcessing();
        return new UnmodifiableIterator<>(findAll().iterator());
    }

    @Override
    @Nullable
    public Result findFirst() {
        assertNotProcessing();
        CommonProcessors.FindFirstProcessor<Result> processor = new CommonProcessors.FindFirstProcessor<>();
        forEach(processor);
        return processor.getFoundValue();
    }

    private void assertNotProcessing() {
        assert myIsProcessing.get() == null : "Operation is not allowed while query is being processed";
    }

    @Nonnull
    @Override
    public Result[] toArray(@Nonnull Result[] a) {
        assertNotProcessing();

        Collection<Result> all = findAll();
        return all.toArray(a);
    }

    @Nonnull
    @Override
    public Query<Result> allowParallelProcessing() {
        return new AbstractQuery<>() {
            @Override
            protected boolean processResults(@Nonnull Predicate<? super Result> consumer) {
                return AbstractQuery.this.doProcessResults(consumer);
            }
        };
    }

    @Nonnull
    private Predicate<Result> threadSafeProcessor(@Nonnull Predicate<? super Result> consumer) {
        Object lock = ObjectUtil.sentinel("AbstractQuery lock");
        return e -> {
            synchronized (lock) {
                return consumer.test(e);
            }
        };
    }

    @Override
    public boolean forEach(@Nonnull Predicate<? super Result> consumer) {
        return doProcessResults(threadSafeProcessor(consumer));
    }

    private boolean doProcessResults(@Nonnull Predicate<? super Result> consumer) {
        assertNotProcessing();

        myIsProcessing.set(true);
        try {
            return processResults(consumer);
        }
        finally {
            myIsProcessing.remove();
        }
    }

    @Nonnull
    @Override
    public AsyncFuture<Boolean> forEachAsync(@Nonnull Predicate<? super Result> consumer) {
        return AsyncUtil.wrapBoolean(forEach(consumer));
    }

    /**
     * Assumes consumer being capable of processing results in parallel
     */
    protected abstract boolean processResults(@Nonnull Predicate<? super Result> consumer);

    /**
     * Should be called only from {@link #processResults} implementations to delegate to another query
     */
    protected static <T> boolean delegateProcessResults(@Nonnull Query<T> query, @Nonnull Predicate<? super T> consumer) {
        if (query instanceof AbstractQuery) {
            return ((AbstractQuery<T>)query).doProcessResults(consumer);
        }
        return query.forEach(consumer);
    }

    @Nonnull
    public static <T> Query<T> wrapInReadAction(@Nonnull Query<? extends T> query) {
        return new AbstractQuery<>() {
            @Override
            protected boolean processResults(@Nonnull Predicate<? super T> consumer) {
                return AbstractQuery.delegateProcessResults(query, ReadActionProcessor.wrapInReadAction(consumer));
            }
        };
    }
}
