/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package consulo.execution.debug.impl.internal.breakpoint.ui.group;

import consulo.execution.debug.breakpoint.XBreakpoint;
import consulo.execution.debug.breakpoint.ui.XBreakpointGroupingRule;
import consulo.execution.debug.breakpoint.ui.XBreakpointsGroupingPriorities;
import jakarta.annotation.Nonnull;

import java.util.Collection;

/**
 * @author zajac
 * @since 2012-05-23
 */
public class XBreakpointGroupingByTypeRule<B> extends XBreakpointGroupingRule<B, XBreakpointTypeGroup> {

  public XBreakpointGroupingByTypeRule() {
    super("XBreakpointGroupingByTypeRule", "Type");
  }

  @Override
  public boolean isAlwaysEnabled() {
    return true;
  }

  @Override
  public int getPriority() {
    return XBreakpointsGroupingPriorities.BY_TYPE;
  }

  @Override
  public XBreakpointTypeGroup getGroup(@Nonnull B b, @Nonnull Collection<XBreakpointTypeGroup> groups) {
    if (b instanceof XBreakpoint) {
      final XBreakpoint breakpoint = (XBreakpoint)b;
      for (XBreakpointTypeGroup group : groups) {
        if (group.getBreakpointType() == breakpoint.getType()) {
          return group;
        }
      }
      return new XBreakpointTypeGroup(breakpoint.getType());
    }
    return null;
  }
}
