// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.application.internal.perfomance;

import consulo.application.internal.perfomance.Activity;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class StartUpMeasurer {
  final static AtomicReference<LoadingState> currentState = new AtomicReference<>(LoadingState.BOOTSTRAP);

  public static final long MEASURE_THRESHOLD = TimeUnit.MILLISECONDS.toNanos(10);

  // `what + noun` is used as scheme for name to make analyzing easier (to visually group - `components loading/initialization/etc`,
  // not to put common part of name to end of).
  // It is not serves only display purposes - it is IDs. Visualizer and another tools to analyze data uses phase IDs,
  // so, any changes must be discussed across all involved and reflected in changelog (see `format-changelog.md`).
  public static final class Phases {
    public static final String APP_STARTER = "appStarter";

    // this phase name is not fully clear - it is time from `ApplicationLoader.initApplication` to `ApplicationLoader.run`
    public static final String INIT_APP = "app initialization";

    public static final String PLACE_ON_EVENT_QUEUE = "place on event queue";

    // actually, now it is also registers services, not only components,but it doesn't worth to rename
    public static final String REGISTER_COMPONENTS_SUFFIX = "component registration";
    public static final String CREATE_COMPONENTS_SUFFIX = "component creation";

    public static final String PROJECT_PRE_STARTUP = "project pre-startup";

    public static final String PROJECT_DUMB_POST_STARTUP = "project dumb post-startup";
  }

  @SuppressWarnings("StaticNonFinalField")
  public static boolean measuringPluginStartupCosts = true;

  public static void stopPluginCostMeasurement() {
    measuringPluginStartupCosts = false;
  }

  private static long startTime = System.nanoTime();

  private static final ConcurrentLinkedQueue<ActivityImpl> items = new ConcurrentLinkedQueue<>();

  private static boolean isEnabled = true;

  public static boolean isEnabled() {
    return isEnabled;
  }

  // @ApiStatus.Internal
  public static final Map<String, Map<String, Long>> pluginCostMap = new HashMap<>();

  public static long getCurrentTime() {
    return System.nanoTime();
  }

  /**
   * Since start in ms.
   */
  @SuppressWarnings("unused")
  public static long sinceStart() {
    return TimeUnit.NANOSECONDS.toMillis(getCurrentTime() - startTime);
  }

  /**
   * The instant events correspond to something that happens but has no duration associated with it.
   * See https://docs.google.com/document/d/1CvAClvFfyA5R-PhYUmn5OOQtYMH4h6I0nSsKchNAySU/preview#heading=h.lenwiilchoxp
   * <p>
   * Scope is not supported — reported as global.
   */
  public static void addInstantEvent(@Nonnull String name) {
    if (!isEnabled) {
      return;
    }

    ActivityImpl activity = new ActivityImpl(name, null);
    activity.setEnd(-1);
    addActivity(activity);
  }

  @Nonnull
  public static Activity startActivity(@Nonnull String name) {
    return startActivity(name, ActivityCategory.APP_INIT);
  }

  @Nonnull
  public static Activity startActivity(@Nonnull String name, @Nonnull ActivityCategory category) {
    return startActivity(name, category, null);
  }

  @Nonnull
  public static Activity startActivity(@Nonnull String name, @Nonnull ActivityCategory category, @Nullable String pluginId) {
    ActivityImpl activity = new ActivityImpl(name, getCurrentTime(), /* parent = */ null, /* level = */  pluginId);
    activity.setCategory(category);
    return activity;
  }

  @Nonnull
  public static Activity startMainActivity(@Nonnull String name) {
    return new ActivityImpl(name, null);
  }

  /**
   * Default threshold is applied.
   */
  public static long addCompletedActivity(long start, @Nonnull Class<?> clazz, @Nonnull ActivityCategory category, @Nullable String pluginId) {
    return addCompletedActivity(start, clazz, category, pluginId, -1);
  }

  public static long addCompletedActivity(long start, @Nonnull Class<?> clazz, @Nonnull ActivityCategory category, @Nullable String pluginId, long threshold) {
    if (!isEnabled) {
      return -1;
    }

    long end = getCurrentTime();
    long duration = end - start;
    if (duration <= threshold) {
      return duration;
    }

    addCompletedActivity(start, end, clazz.getName(), category, pluginId);
    return duration;
  }

  /**
   * Default threshold is applied.
   */
  public static long addCompletedActivity(long start, @Nonnull String name, @Nonnull ActivityCategory category, String pluginId) {
    long end = getCurrentTime();
    long duration = end - start;
    if (duration <= MEASURE_THRESHOLD) {
      return duration;
    }

    addCompletedActivity(start, end, name, category, pluginId);
    return duration;
  }

  public static void addCompletedActivity(long start, long end, @Nonnull String name, @Nonnull ActivityCategory category, String pluginId) {
    if (!isEnabled) {
      return;
    }

    ActivityImpl item = new ActivityImpl(name, start, /* parent = */ null, pluginId);
    item.setCategory(category);
    item.setEnd(end);
    addActivity(item);
  }

  public static void setCurrentState(@Nonnull LoadingState state) {
    LoadingState old = currentState.getAndSet(state);
    if (old.ordinal() > state.ordinal()) {
      LoadingState.getLogger().error("New state " + state + " cannot precede old " + old);
    }
    stateSet(state);
  }

  public static void compareAndSetCurrentState(@Nonnull LoadingState expectedState, @Nonnull LoadingState newState) {
    if (currentState.compareAndSet(expectedState, newState)) {
      stateSet(newState);
    }
  }

  private static void stateSet(@Nonnull LoadingState state) {
    addInstantEvent(state.displayName);
  }

  //@ApiStatus.Internal
  public static void processAndClear(boolean isContinueToCollect, @Nonnull Consumer<? super ActivityImpl> consumer) {
    isEnabled = isContinueToCollect;

    while (true) {
      ActivityImpl item = items.poll();
      if (item == null) {
        break;
      }

      consumer.accept(item);
    }
  }

  // @ApiStatus.Internal
  public static long getStartTime() {
    return startTime;
  }

  static void addActivity(@Nonnull ActivityImpl activity) {
    items.add(activity);
  }

  // @ApiStatus.Internal
  public static void addTimings(@Nonnull LinkedHashMap<String, Long> timings, @Nonnull String groupName) {
    if (!items.isEmpty()) {
      throw new IllegalStateException("addTimings must be not called if some events were already added using API");
    }

    if (timings.isEmpty()) {
      return;
    }

    List<Map.Entry<String, Long>> entries = new ArrayList<>(timings.entrySet());

    ActivityImpl parent = new ActivityImpl(groupName, entries.get(0).getValue(), null, null);
    parent.setEnd(getCurrentTime());

    for (int i = 0; i < entries.size(); i++) {
      long start = entries.get(i).getValue();
      if (start < startTime) {
        startTime = start;
      }

      ActivityImpl activity = new ActivityImpl(entries.get(i).getKey(), start, parent, null);
      activity.setEnd(i == entries.size() - 1 ? parent.getEnd() : entries.get(i + 1).getValue());
      items.add(activity);
    }
    items.add(parent);
  }

  //@ApiStatus.Internal
  public static void addPluginCost(@Nonnull String pluginId, @Nonnull String phase, long time) {
    if (!isMeasuringPluginStartupCosts()) {
      return;
    }

    synchronized (pluginCostMap) {
      doAddPluginCost(pluginId, phase, time, pluginCostMap);
    }
  }

  public static boolean isMeasuringPluginStartupCosts() {
    return measuringPluginStartupCosts;
  }

  //@ApiStatus.Internal
  public static void doAddPluginCost(@Nonnull String pluginId, @Nonnull String phase, long time, @Nonnull Map<String, Map<String, Long>> pluginCostMap) {
    Map<String, Long> costPerPhaseMap = pluginCostMap.get(pluginId);
    if (costPerPhaseMap == null) {
      costPerPhaseMap = new HashMap<>();
      pluginCostMap.put(pluginId, costPerPhaseMap);
    }
    Long oldCost = costPerPhaseMap.getOrDefault(phase, 0L);

    costPerPhaseMap.put(phase, oldCost + time);
  }
}