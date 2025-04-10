// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.util.collection;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UnmodifiableHashMapTest {
    @Test
    public void testEmpty() {
        UnmodifiableHashMap<Object, Object> empty = UnmodifiableHashMap.empty();
        assertThat(empty)
            .doesNotContainKey("foo")
            .hasSize(0)
            .isEmpty();

        assertThat(empty)
            .extractingByKey("foo").isNull();

        Map<String, String> map = Map.of("k", "v");
        assertThat(empty.withAll(map))
            .isEqualTo(map);

        assertThat(UnmodifiableHashMap.empty(HashingStrategy.identity()))
            .isEmpty();
    }

    @Test
    public void testNull() {
        UnmodifiableHashMap<Object, Object> empty = UnmodifiableHashMap.empty();

        assertThat(empty.containsKey(null))
            .isFalse();

        assertThatThrownBy(() -> empty.withAll(Collections.singletonMap(null, null)))
            .isInstanceOf(IllegalArgumentException.class);

        Map<Integer, String> nullMap = new HashMap<>();
        nullMap.put(0, "0");
        nullMap.put(null, null);

        for (int size : new int[]{1, 2, 3, 4, 10, 11, 12}) {
            UnmodifiableHashMap<Integer, String> map = create(size);
            assertThatThrownBy(() -> map.withAll(nullMap))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testPut() {
        assertThatThrownBy(() -> UnmodifiableHashMap.empty().put("foo", "bar"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testRemove() {
        assertThatThrownBy(() -> UnmodifiableHashMap.empty().remove("foo"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testPutAll() {
        //noinspection RedundantCollectionOperation
        assertThatThrownBy(() -> UnmodifiableHashMap.empty().putAll(Collections.singletonMap("foo", "bar")))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testClear() {
        assertThatThrownBy(() -> UnmodifiableHashMap.empty().clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void testWith() {
        UnmodifiableHashMap<Integer, String> map = UnmodifiableHashMap.empty();
        for (int i = 0; i < 50; i++) {
            String value = String.valueOf(i);
            map = map.with(i, value);
            assertThat(map)
                .hasSize(i + 1)
                .containsKey(i)
                .containsValue(value)
                .doesNotContainValue(String.valueOf(i + 1))
                .extractingByKey(i).isEqualTo(value);
            assertThat(map)
                .extractingByKey(i + 1).isNull();

            UnmodifiableHashMap<Integer, String> map1 = map.with(i, value);
            assertThat(map)
                .isSameAs(map1);

            UnmodifiableHashMap<Integer, String> map2 = map.with(i, String.valueOf(i + 1));
            assertThat(map)
                .isNotSameAs(map2)
                .hasSameSizeAs(map)
                .containsOnlyKeys(map.keySet());
            assertThat(map2)
                .containsValue(String.valueOf(i + 1))
                .doesNotContainValue(value);
        }
    }


    @Test
    public void testWithAll() {
        for (int size : new int[]{0, 1, 2, 3, 4, 10, 11, 12}) {
            UnmodifiableHashMap<Integer, String> map = create(size);
            assertThat(map.get(null)).isNull();

            UnmodifiableHashMap<Integer, String> map2 = map.withAll(Map.of());
            assertThat(map2)
                .isSameAs(map);

            int k1 = size + 1;
            String v1 = String.valueOf(k1);
            map2 = map.withAll(Collections.singletonMap(k1, v1));
            assertThat(map2)
                .hasSize(size + 1)
                .containsAllEntriesOf(map2)
                .extractingByKey(k1).isEqualTo(v1);

            int k2 = size + 2;
            String v2 = String.valueOf(k2);
            map2 = map.withAll(Map.of(k1, v1, k2, v2));
            assertThat(map2)
                .hasSize(size + 2)
                .containsAllEntriesOf(map2)
                .extractingByKey(k1).isEqualTo(v1);
            assertThat(map2)
                .extractingByKey(k2).isEqualTo(v2);
        }
    }

    @Test
    public void testWithAllDuplicated() {
        for (int size : new int[]{1, 2, 3, 4, 10, 11, 12}) {
            UnmodifiableHashMap<Integer, String> map = create(size);

            int k0 = 0;
            String v0 = map.get(0);

            UnmodifiableHashMap<Integer, String> map2 = map.withAll(Map.of(k0, v0));
            assertThat(map2)
                .isSameAs(map);

            int k1 = size + 1;
            String v1 = String.valueOf(k1);
            map2 = map.withAll(Map.of(k0, v0, k1, v1));
            assertThat(map2)
                .hasSize(size + 1)
                .containsAllEntriesOf(map2)
                .extractingByKey(k0).isEqualTo(v0);
            assertThat(map2)
                .extractingByKey(k1).isEqualTo(v1);
        }
    }

    @Test
    public void testWithout() {
        int size = 51;
        UnmodifiableHashMap<Integer, String> map = create(size);
        for (int i = 0; i < size; i++) {
            map = map.without(i);
            assertThat(map)
                .hasSize(size - 1 - i)
                .doesNotContainKey(i);
            assertThat(i == size - 1 || map.containsKey(i + 1)).isTrue();
        }
        map = create(size);
        for (int i = size; i >= 0; i--) {
            map = map.without(i);
            assertThat(map)
                .hasSize(i)
                .doesNotContainKey(i);
            assertThat(i == 0 || map.containsKey(i - 1)).isTrue();
        }
    }

    @Test
    public void testAddCollisions() {
        UnmodifiableHashMap<Long, String> map = UnmodifiableHashMap.empty();
        for (int i = 0; i < 50; i++) {
            long key = ((long)i << 32) | i ^ 135;
            map = map.with(key, String.valueOf(key));
            assertThat(map)
                .hasSize(i + 1)
                .containsKey(key)
                .containsValue(String.valueOf(key))
                .doesNotContainValue(String.valueOf(key + 1))
                .extractingByKey(key).isEqualTo(String.valueOf(key));
            assertThat(map)
                .extractingByKey(key + 1).isNull();
        }
    }

    @Test
    public void testGet() {
        for (int size : new int[]{0, 1, 2, 3, 4, 10, 11, 12}) {
            UnmodifiableHashMap<Integer, String> map = create(size);
            assertThat(map.get(null)).isNull();
        }
    }

    @Test
    public void testIterate() {
        for (int size : new int[]{0, 1, 2, 3, 4, 10, 11, 12}) {
            UnmodifiableHashMap<Integer, String> map = create(size);

            assertThat(map.keySet())
                .isEqualTo(IntStream.range(0, size).boxed().collect(Collectors.toSet()));

            assertThat(new HashSet<>(map.values()))
                .isEqualTo(IntStream.range(0, size).mapToObj(String::valueOf).collect(Collectors.toSet()));

            assertThat(new HashSet<>(map.entrySet())).isEqualTo(
                IntStream.range(0, size)
                    .mapToObj(i -> new AbstractMap.SimpleImmutableEntry<>(i, String.valueOf(i)))
                    .collect(Collectors.toSet())
            );
        }
    }

    @Test
    public void testIterateEmpty() {
        UnmodifiableHashMap<Object, Object> empty = UnmodifiableHashMap.empty();
        assertThatThrownBy(() -> empty.keySet().iterator().next())
            .isInstanceOf(NoSuchElementException.class);

        assertThatThrownBy(() -> empty.values().iterator().next())
            .isInstanceOf(NoSuchElementException.class);

        assertThatThrownBy(() -> empty.entrySet().iterator().next())
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    public void testValues() {
        UnmodifiableHashMap<Integer, String> map = create(10);
        assertThat(map.values().contains("9"))
            .isTrue();
        assertThat(map.values().contains("11"))
            .isFalse();
    }

    @Test
    public void testForEach() {
        for (int size : new int[]{0, 1, 2, 3, 4, 10, 11, 12}) {
            UnmodifiableHashMap<Integer, String> map = create(size);
            Set<Integer> keys1 = new HashSet<>();
            Set<String> values1 = new HashSet<>();
            map.keySet().forEach(keys1::add);
            map.values().forEach(values1::add);

            Set<Integer> keys2 = new HashSet<>();
            Set<String> values2 = new HashSet<>();
            map.forEach((k, v) -> {
                keys2.add(k);
                values2.add(v);
            });

            assertThat(keys1)
                .isEqualTo(IntStream.range(0, size).boxed().collect(Collectors.toSet()))
                .containsExactlyElementsOf(keys2);

            assertThat(values1)
                .isEqualTo(IntStream.range(0, size).mapToObj(String::valueOf).collect(Collectors.toSet()))
                .containsExactlyElementsOf(values2);
        }
    }

    @Test
    public void testToString() {
        for (int size : new int[]{0, 1, 2, 3, 4, 10, 11, 12}) {
            UnmodifiableHashMap<Integer, String> map = create(size);
            String actual = map.toString();
            assertThat(actual)
                .startsWith("{")
                .endsWith("}");
            String content = actual.substring(1, actual.length() - 1);
            if (size == 0) {
                assertThat(content).isEmpty();
                continue;
            }

            Set<String> parts = Set.of(content.split(", ", -1));
            assertThat(parts)
                .hasSize(size)
                .isEqualTo(IntStream.range(0, size).mapToObj(i -> i + "=" + i).collect(Collectors.toSet()));
        }
    }

    @Test
    @SuppressWarnings("EqualsBetweenInconvertibleTypes")
    public void testEquals() {
        for (int size : new int[]{0, 1, 2, 3, 4, 10, 11, 12}) {
            UnmodifiableHashMap<Integer, String> map = create(size);
            UnmodifiableHashMap<Integer, String> map1 = map.with(size - 1, String.valueOf(size));
            HashMap<Integer, String> hashMap = new HashMap<>(map);

            assertThat(map)
                .isEqualTo(map)
                .isNotEqualTo(map1)
                .isEqualTo(hashMap)
                .isNotEqualTo(new Object());

            assertThat(hashMap)
                .isEqualTo(map)
                .isNotEqualTo(map1);

            assertThat(map1)
                .isNotEqualTo(map)
                .isNotEqualTo(hashMap);
        }
    }

    @Test
    public void testHashCode() {
        for (int size : new int[]{0, 1, 2, 3, 4, 10, 11, 12}) {
            UnmodifiableHashMap<Integer, String> map = create(size);
            HashMap<Integer, String> hashMap = new HashMap<>(map);
            assertThat(hashMap.hashCode()).isEqualTo(map.hashCode());
        }
    }

    @Test
    public void fromMap() {
        UnmodifiableHashMap<Integer, String> map = create(10);
        assertThat(map)
            .isSameAs(UnmodifiableHashMap.fromMap(map));

        assertThat(UnmodifiableHashMap.fromMap(Map.of()))
            .isSameAs(UnmodifiableHashMap.empty());
    }

    private static UnmodifiableHashMap<Integer, String> create(int size) {
        UnmodifiableHashMap<Integer, String> map = UnmodifiableHashMap.fromMap(
            IntStream.range(0, size < 4 ? size : size / 4 * 4)
                .mapToObj(i -> new AbstractMap.SimpleImmutableEntry<>(i, String.valueOf(i)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
        );
        while (map.size() < size) {
            map = map.with(map.size(), String.valueOf(map.size()));
        }
        return map;
    }
}