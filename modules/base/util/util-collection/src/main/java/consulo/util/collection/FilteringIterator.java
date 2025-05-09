/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.util.collection;

import consulo.util.lang.function.Predicates;
import jakarta.annotation.Nonnull;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

/**
 * {@link #remove} throws {@link IllegalStateException} if called after {@link #hasNext}
 *
 * @author dsl
 * @author dyoma
 */
public class FilteringIterator<Dom, E extends Dom> implements PeekableIterator<E> {
    private final Iterator<Dom> myDelegate;
    private final Predicate<? super Dom> myCondition;
    private boolean myNextObtained;
    private boolean myCurrentIsValid;
    private Dom myCurrent;
    private Boolean myCurrentPassedFilter;
    @Deprecated
    public static final Predicate NOT_NULL = Predicates.notNull();

    public FilteringIterator(@Nonnull Iterator<Dom> delegate, @Nonnull Predicate<? super Dom> condition) {
        myDelegate = delegate;
        myCondition = condition;
    }

    private void obtainNext() {
        if (myNextObtained) {
            return;
        }
        boolean hasNext = myDelegate.hasNext();
        setCurrent(hasNext ? myDelegate.next() : null);

        myCurrentIsValid = hasNext;
        myNextObtained = true;
    }

    @Override
    public boolean hasNext() {
        obtainNext();
        if (!myCurrentIsValid) {
            return false;
        }
        boolean value = isCurrentPassesFilter();
        while (!value && myDelegate.hasNext()) {
            Dom next = myDelegate.next();
            setCurrent(next);
            value = isCurrentPassesFilter();
        }
        return value;
    }

    private void setCurrent(Dom next) {
        myCurrent = next;
        myCurrentPassedFilter = null;
    }

    private boolean isCurrentPassesFilter() {
        if (myCurrentPassedFilter != null) {
            return myCurrentPassedFilter;
        }
        boolean passed = myCondition.test(myCurrent);
        myCurrentPassedFilter = passed;
        return passed;
    }

    @Override
    @SuppressWarnings("unchecked")
    public E next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        E result = (E)myCurrent;
        myNextObtained = false;
        return result;
    }

    /**
     * Works after call {@link #next} until call {@link #hasNext}
     *
     * @throws IllegalStateException if {@link #hasNext} called
     */
    @Override
    public void remove() {
        if (myNextObtained) {
            throw new IllegalStateException();
        }
        myDelegate.remove();
    }

    @Override
    @SuppressWarnings("unchecked")
    public E peek() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return (E)myCurrent;
    }

    public static <T> Iterator<T> skipNulls(Iterator<T> iterator) {
        return create(iterator, Predicates.notNull());
    }

    @SuppressWarnings("unchecked")
    public static <Dom, T extends Dom> Iterator<T> create(Iterator<Dom> iterator, Predicate<? super Dom> condition) {
        return condition == Predicates.alwaysTrue() ? (Iterator<T>)iterator : new FilteringIterator<>(iterator, condition);
    }

    public static <T> Predicate<T> alwaysTrueCondition(Class<T> aClass) {
        return Predicates.alwaysTrue();
    }

    public static <T> InstanceOf<T> instanceOf(Class<? extends T> aClass) {
        return new InstanceOf<>(aClass);
    }

    @SuppressWarnings("unchecked")
    public static <T> Iterator<T> createInstanceOf(Iterator<?> iterator, Class<T> aClass) {
        return create((Iterator<T>)iterator, instanceOf(aClass));
    }

    public static class InstanceOf<T> implements Predicate<Object> {
        private final Class<? extends T> myInstancesClass;

        public InstanceOf(Class<? extends T> instancesClass) {
            myInstancesClass = instancesClass;
        }

        @Override
        public boolean test(Object object) {
            return myInstancesClass.isInstance(object);
        }
    }
}
