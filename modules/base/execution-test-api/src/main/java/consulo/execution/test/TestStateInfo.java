/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package consulo.execution.test;

import consulo.execution.localize.ExecutionLocalize;
import consulo.localize.LocalizeValue;

/**
 * @author Roman Chernyatchik
 *
 * Describes properties of tests/suites states
 */
public interface TestStateInfo {
  /**
   * @return If test/test suite is running
   */
  boolean isInProgress();

  /**
   * Some magic definition from AbstractTestProxy class.
   * If state is defect something wrong is with it and should be shown
   * properly in UI.
   */
  boolean isDefect();

  /**
   * @return True if test/suite has been already launched
   */
  boolean wasLaunched();

  /**
   * Describes final states, e.g such states will not be
   * changed after finished event.
   * @return True if is final
   */
  boolean isFinal();

  /**
   * @return Was terminated by user
   */
  boolean wasTerminated();

  /**
   * It's some magic parameter that describes state type.
   */
  Magnitude getMagnitude();

  //WARN: It is Hack, see PoolOfTestStates, API is necessary
  enum Magnitude {
    SKIPPED_INDEX(0, 1, ExecutionLocalize.smTestRunnerMagnitudeSkippedFailedTitle()),
    COMPLETE_INDEX(1, 3, ExecutionLocalize.smTestRunnerMagnitudeCompletedFailedTitle()),
    NOT_RUN_INDEX(2, 0, ExecutionLocalize.smTestRunnerMagnitudeNotRunFailedTitle()),
    RUNNING_INDEX(3, 7, ExecutionLocalize.smTestRunnerMagnitudeRunningFailedTitle()),
    TERMINATED_INDEX(4, 6, ExecutionLocalize.smTestRunnerMagnitudeTerminatedFailedTitle()),
    IGNORED_INDEX(5, 2, ExecutionLocalize.smTestRunnerMagnitudeIgnoredFailedTitle()),
    FAILED_INDEX(6, 4, ExecutionLocalize.smTestRunnerMagnitudeAssertionFailedTitle()),
    ERROR_INDEX(8, 5, ExecutionLocalize.smTestRunnerMagnitudeTesterrorTitle()),
    PASSED_INDEX(COMPLETE_INDEX.getValue(), COMPLETE_INDEX.getSortWeight(), ExecutionLocalize.smTestRunnerMagnitudePassedTitle());

    private final int myValue;
    private final int mySortWeight;
    private final LocalizeValue myTitle;

    /**
     * @param value Some magic parameter from legal
     * @param sortWeight Weight for sort comparator
     * @param title Title
     */
    Magnitude(final int value, final int sortWeight, final LocalizeValue title) {
      myValue = value;
      myTitle = title;
      mySortWeight = sortWeight;
    }

    public int getValue() {
      return myValue;
    }

    public String getTitle() {
      return myTitle.get();
    }

    public int getSortWeight() {
      return mySortWeight;
    }
  }
}
