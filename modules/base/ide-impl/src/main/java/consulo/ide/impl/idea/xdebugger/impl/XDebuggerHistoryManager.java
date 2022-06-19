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
package consulo.ide.impl.idea.xdebugger.impl;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.Service;
import consulo.annotation.component.ServiceImpl;
import consulo.execution.debug.breakpoint.XExpression;
import consulo.ide.ServiceManager;
import consulo.ide.impl.idea.xdebugger.impl.breakpoints.XExpressionImpl;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * @author nik
 */
@Singleton
@Service(ComponentScope.PROJECT)
@ServiceImpl
public class XDebuggerHistoryManager {
  public static final int MAX_RECENT_EXPRESSIONS = 10;
  private final Map<String, LinkedList<XExpression>> myRecentExpressions = new HashMap<String, LinkedList<XExpression>>();

  public static XDebuggerHistoryManager getInstance(@Nonnull Project project) {
    return ServiceManager.getService(project, XDebuggerHistoryManager.class);
  }

  public boolean addRecentExpression(@Nonnull @NonNls String id, @Nullable XExpression expression) {
    if (expression == null || StringUtil.isEmptyOrSpaces(expression.getExpression())) {
      return false;
    }

    LinkedList<XExpression> list = myRecentExpressions.get(id);
    if (list == null) {
      list = new LinkedList<XExpression>();
      myRecentExpressions.put(id, list);
    }
    if (list.size() == MAX_RECENT_EXPRESSIONS) {
      list.removeLast();
    }

    XExpression trimmedExpression = new XExpressionImpl(expression.getExpression().trim(), expression.getLanguage(), expression.getCustomInfo());
    list.remove(trimmedExpression);
    list.addFirst(trimmedExpression);
    return true;
  }

  public List<XExpression> getRecentExpressions(@NonNls String id) {
    LinkedList<XExpression> list = myRecentExpressions.get(id);
    return list != null ? list : Collections.<XExpression>emptyList();
  }
}
