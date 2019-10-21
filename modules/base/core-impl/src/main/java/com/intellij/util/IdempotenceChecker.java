// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.ResolveResult;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Contract;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

public class IdempotenceChecker {
  private static final Logger LOG = Logger.getInstance(IdempotenceChecker.class);
  private static final Set<Class> ourReportedValueClasses = Collections.synchronizedSet(new THashSet<>());
  private static final ThreadLocal<Integer> ourRandomCheckNesting = ThreadLocal.withInitial(() -> 0);
  private static final RegistryValue ourRateCheckProperty = Registry.get("platform.random.idempotence.check.rate");

  /**
   * Perform some basic checks whether the two given objects are equivalent and interchangeable,
   * as described in e.g {@link com.intellij.psi.util.CachedValue} contract. This method should be used
   * in places caching results of various computations, which are expected to be idempotent:
   * they can be performed several times, or on multiple threads, and the results should be interchangeable.<p></p>
   * <p>
   * What to do if you get an error from here:
   * <ul>
   * <li>
   * Start by looking carefully at the computation (which usually can be found by navigating the stack trace)
   * and find out why it could be non-idempotent. See common culprits below.</li>
   * <li>
   * If the computation is complex and depends on other caches, you could try to perform
   * {@code IdempotenceChecker.checkEquivalence()} for their results as well, localizing the error.</li>
   * <li>
   * If it's a test, you could try reproducing and debugging it. To increase the probability of failure,
   * you can temporarily add {@code Registry.get("platform.random.idempotence.check.rate").setValue(1, getTestRootDisposable())}
   * to perform the idempotence check on every cache access. Note that this can make your test much slower.
   * </li>
   * </ul>
   * <p>
   * Common culprits:
   * <ul>
   * <li>Caching and returning a mutable object (e.g. array or List), which clients then mutate from different threads;
   * to fix, either clone the return value or use unmodifiable wrappers</li>
   * <li>Depending on a {@link ThreadLocal} or method parameters with different values.</li>
   * <li>For failures from {@link #applyForRandomCheck}: outdated cached value (not all dependencies are specified, or their modification counters aren't properly incremented)</li>
   * </ul>
   *
   * @param existing      the value computed on the first invocation
   * @param fresh         the value computed a bit later, expected to be equivalent
   * @param providerClass a class of the function performing the computation, used to prevent reporting the same error multiple times
   */
  public static <T> void checkEquivalence(@Nullable T existing, @Nullable T fresh, @Nonnull Class providerClass) {
    String s = checkValueEquivalence(existing, fresh);
    if (s != null && ourReportedValueClasses.add(providerClass)) {
      LOG.error(s);
    }
  }

  private static String objAndClass(Object o) {
    if (o == null) return "null";

    String s = o.toString();
    return s.contains(o.getClass().getSimpleName()) || o instanceof String || o instanceof Number || o instanceof Class ? s : s + " (class " + o.getClass().getName() + ")";
  }

  private static String checkValueEquivalence(@Nullable Object existing, @Nullable Object fresh) {
    if (existing == fresh) return null;

    String eqMsg = checkClassEquivalence(existing, fresh);
    if (eqMsg != null) return eqMsg;

    Object[] eArray = asArray(existing);
    if (eArray != null) {
      return checkArrayEquivalence(eArray, Objects.requireNonNull(asArray(fresh)), existing);
    }

    if (existing instanceof CachedValueBase.Data) {
      return checkCachedValueData((CachedValueBase.Data)existing, (CachedValueBase.Data)fresh);
    }
    if (existing instanceof List || isOrderedSet(existing)) {
      return checkCollectionElements((Collection)existing, (Collection)fresh);
    }
    if (isOrderedMap(existing)) {
      return checkCollectionElements(((Map)existing).entrySet(), ((Map)fresh).entrySet());
    }
    if (existing instanceof Set) {
      return whichIsField("size", existing, fresh, checkCollectionSizes(((Set)existing).size(), ((Set)fresh).size()));
    }
    if (existing instanceof Map) {
      if (existing instanceof ConcurrentMap) {
        return null; // likely to be filled lazily
      }
      return whichIsField("size", existing, fresh, checkCollectionSizes(((Map)existing).size(), ((Map)fresh).size()));
    }
    if (existing instanceof PsiNamedElement) {
      return checkPsiEquivalence((PsiElement)existing, (PsiElement)fresh);
    }
    if (existing instanceof ResolveResult) {
      PsiElement existingPsi = ((ResolveResult)existing).getElement();
      PsiElement freshPsi = ((ResolveResult)fresh).getElement();
      if (existingPsi != freshPsi) {
        String s = checkClassEquivalence(existingPsi, freshPsi);
        if (s == null) s = checkPsiEquivalence(existingPsi, freshPsi);
        return whichIsField("element", existing, fresh, s);
      }
      return null;
    }
    if (isExpectedToHaveSaneEquals(existing) && !existing.equals(fresh)) {
      return reportProblem(existing, fresh);
    }
    return null;
  }

  private static boolean isOrderedMap(Object o) {
    return o instanceof LinkedHashMap || o instanceof SortedMap;
  }

  private static boolean isOrderedSet(Object o) {
    return o instanceof LinkedHashSet || o instanceof SortedSet;
  }

  private static String whichIsField(@Nonnull String field, @Nonnull Object existing, @Nonnull Object fresh, @Nullable String msg) {
    return msg == null ? null : appendDetail(msg, "which is " + field + " of " + existing + " and " + fresh);
  }

  @Nullable
  private static Object[] asArray(Object o) {
    if (o instanceof Object[]) return (Object[])o;
    if (o instanceof Map.Entry) return new Object[]{((Map.Entry)o).getKey(), ((Map.Entry)o).getValue()};
    if (o instanceof Pair) return new Object[]{((Pair)o).first, ((Pair)o).second};
    if (o instanceof Trinity) return new Object[]{((Trinity)o).first, ((Trinity)o).second, ((Trinity)o).third};
    return null;
  }

  private static String checkCachedValueData(@Nonnull CachedValueBase.Data existing, @Nonnull CachedValueBase.Data fresh) {
    Object[] deps1 = existing.getDependencies();
    Object[] deps2 = fresh.getDependencies();
    Object eValue = existing.get();
    Object fValue = fresh.get();
    if (deps1.length != deps2.length) {
      String msg = reportProblem(deps1.length, deps2.length);
      msg = appendDetail(msg, "which is length of CachedValue dependencies: " + Arrays.toString(deps1) + " and " + Arrays.toString(deps2));
      msg = appendDetail(msg, "where values are  " + objAndClass(eValue) + " and " + objAndClass(fValue));
      return msg;
    }

    return checkValueEquivalence(eValue, fValue);
  }

  private static boolean isExpectedToHaveSaneEquals(@Nonnull Object existing) {
    return existing instanceof Comparable;
  }

  @Contract("null,_->!null;_,null->!null")
  private static String checkClassEquivalence(@Nullable Object existing, @Nullable Object fresh) {
    if (existing == null || fresh == null) {
      return reportProblem(existing, fresh);
    }
    Class<?> c1 = existing.getClass();
    Class<?> c2 = fresh.getClass();
    if (c1 != c2 && !objectsOfDifferentClassesCanStillBeEquivalent(existing, fresh)) {
      return whichIsField("class", existing, fresh, reportProblem(c1, c2));
    }
    return null;
  }

  private static boolean objectsOfDifferentClassesCanStillBeEquivalent(@Nonnull Object existing, @Nonnull Object fresh) {
    if (existing instanceof Map && fresh instanceof Map && isOrderedMap(existing) == isOrderedMap(fresh)) return true;
    if (existing instanceof Set && fresh instanceof Set && isOrderedSet(existing) == isOrderedSet(fresh)) return true;
    if (existing instanceof List && fresh instanceof List) return true;
    return ContainerUtil.intersects(allSupersWithEquals.get(existing.getClass()), allSupersWithEquals.get(fresh.getClass()));
  }

  private static final Map<Class, Set<Class>> allSupersWithEquals = ConcurrentFactoryMap
          .createMap(clazz -> JBIterable.generate(clazz, Class::getSuperclass).filter(c -> c != Object.class && ReflectionUtil.getDeclaredMethod(c, "equals", Object.class) != null).toSet());

  private static String checkPsiEquivalence(@Nonnull PsiElement existing, @Nonnull PsiElement fresh) {
    if (!existing.equals(fresh) && !existing.isEquivalentTo(fresh) && !fresh.isEquivalentTo(existing) && (seemsToBeResolveTarget(existing) || seemsToBeResolveTarget(fresh))) {
      return reportProblem(existing, fresh);
    }
    return null;
  }

  private static boolean seemsToBeResolveTarget(@Nonnull PsiElement psi) {
    if (psi.isPhysical()) return true;
    PsiElement nav = psi.getNavigationElement();
    return nav != null && nav.isPhysical();
  }

  private static String checkCollectionElements(@Nonnull Collection existing, @Nonnull Collection fresh) {
    if (fresh.isEmpty()) {
      return null; // for cases when an empty collection is cached and then filled lazily on request
    }
    return checkArrayEquivalence(existing.toArray(), fresh.toArray(), existing);
  }

  private static String checkCollectionSizes(int size1, int size2) {
    if (size2 == 0) {
      return null; // for cases when an empty collection is cached and then filled lazily on request
    }
    if (size1 != size2) {
      return reportProblem(size1, size2);
    }
    return null;
  }

  private static String checkArrayEquivalence(Object[] a1, Object[] a2, Object original1) {
    int len1 = a1.length;
    int len2 = a2.length;
    if (len1 != len2) {
      return appendDetail(reportProblem(len1, len2), "which is length of " + Arrays.toString(a1) + " and " + Arrays.toString(a2));
    }
    for (int i = 0; i < len1; i++) {
      String msg = checkValueEquivalence(a1[i], a2[i]);
      if (msg != null) {
        return whichIsField(original1 instanceof Map.Entry ? (i == 0 ? "key" : "value") : i + "th element", Arrays.toString(a1), Arrays.toString(a2), msg);
      }
    }
    return null;
  }

  private static String reportProblem(Object o1, Object o2) {
    return appendDetail("Non-idempotent computation: it returns different results when invoked multiple times or on different threads:", objAndClass(o1) + " != " + objAndClass(o2));
  }

  private static String appendDetail(String message, String detail) {
    return message + "\n  " + StringUtil.trimLog(detail, 10_000);
  }

  /**
   * @return whether random checks are enabled and it makes sense to call a potentially expensive {@link #applyForRandomCheck} at all.
   */
  public static boolean areRandomChecksEnabled() {
    return ApplicationManager.getApplication().isUnitTestMode() && !ApplicationInfoImpl.isInPerformanceTest();
  }

  /**
   * Call this when accessing an already cached value, so that once in a while
   * (depending on "platform.random.idempotence.check.rate" registry value)
   * the computation is re-run and checked for consistency with that cached value.
   */
  public static <T> void applyForRandomCheck(T data, Object provider, Computable<? extends T> recomputeValue) {
    if (areRandomChecksEnabled() && shouldPerformRandomCheck()) {
      RecursionGuard.StackStamp stamp = RecursionManager.markStack();
      Integer prevNesting = ourRandomCheckNesting.get();
      ourRandomCheckNesting.set(prevNesting + 1);
      try {
        T fresh = recomputeValue.compute();
        if (stamp.mayCacheNow()) {
          checkEquivalence(data, fresh, provider.getClass());
        }
      }
      finally {
        ourRandomCheckNesting.set(prevNesting);
      }
    }
  }

  private static boolean shouldPerformRandomCheck() {
    int rate = ourRateCheckProperty.asInteger();
    return rate > 0 && ThreadLocalRandom.current().nextInt(rate) == 0;
  }

  @TestOnly
  public static boolean isCurrentThreadInsideRandomCheck() {
    return ourRandomCheckNesting.get() > 0;
  }

}
