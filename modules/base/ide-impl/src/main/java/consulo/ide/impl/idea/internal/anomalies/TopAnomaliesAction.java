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
package consulo.ide.impl.idea.internal.anomalies;

import consulo.ui.ex.action.ActionGroup;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import consulo.ui.annotation.RequiredUIAccess;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author Vassiliy.Kudryashov
 */
public class TopAnomaliesAction extends ActionGroup {
  private static final Comparator<Pair<?, Integer>> COMPARATOR = new Comparator<Pair<?, Integer>>() {
    @Override
    public int compare(Pair<?, Integer> o1, Pair<?, Integer> o2) {
      int i = o2.getSecond() - o1.getSecond();
      if (i != 0) {
        return i;
      }
      int h1 = o1.hashCode();
      int h2 = o2.hashCode();
      if (h1 > h2) {
        return 1;
      }
      if (h1 < h2) {
        return -1;
      }
      return 0;
    }
  };
  private static final int LIMIT = 10;

  private static final ResettableAction TOP_PARENTS = new ResettableAction("Parents") {
    TreeSet<Pair<JComponent, Integer>> top = new TreeSet<Pair<JComponent, Integer>>(COMPARATOR);
    TreeSet<Pair<JComponent, Integer>> old = new TreeSet<Pair<JComponent, Integer>>(COMPARATOR);

    @RequiredUIAccess
    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setText("Top " + LIMIT + " Component Parents");
    }

    @Override
    void reset() {
      top.clear();
      old.clear();
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(AnActionEvent e) {
      old = new TreeSet<Pair<JComponent, Integer>>(top);
      top.clear();
      Window[] windows = Window.getWindows();
      for (Window window : windows) {
        if (window.isVisible() && (window instanceof JFrame)) {
          JFrame f = (JFrame)window;
          checkParents((JComponent)f.getContentPane(), top, LIMIT);
        }
      }

      System.out.println("Top " + LIMIT + " component parents");
      for (Pair<JComponent, Integer> pair : top) {
        System.out.println(
                pair.first.getClass().getName() + " (" + pair.second + " children)" + getChange(old, pair.first, pair.second));
      }
    }

    private void checkParents(JComponent component, Set<Pair<JComponent, Integer>> top, int limit) {
      top.add(Pair.create(component, component.getComponentCount()));

      trimToLimit(top, limit);

      for (int i = 0; i < component.getComponentCount(); i++) {
        Component child = component.getComponent(i);
        if (child instanceof JComponent) {
          checkParents((JComponent)child, top, limit);
        }
      }
    }
  };

  private static final ResettableAction TOP_UI_PROPERTIES = new ResettableAction("ClientProperties") {
    TreeSet<Pair<JComponent, Integer>> top = new TreeSet<Pair<JComponent, Integer>>(COMPARATOR);
    TreeSet<Pair<JComponent, Integer>> old = new TreeSet<Pair<JComponent, Integer>>(COMPARATOR);

    @RequiredUIAccess
    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setText("Top " + LIMIT + " ClientProperties");
    }

    @Override
    void reset() {
      top.clear();
      old.clear();
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(AnActionEvent e) {
      old = new TreeSet<Pair<JComponent, Integer>>(top);
      top.clear();
      Window[] windows = Window.getWindows();
      for (Window window : windows) {
        if (window.isVisible() && (window instanceof JFrame)) {
          JFrame f = (JFrame)window;
          checkClientProperties((JComponent)f.getContentPane(), top, LIMIT);
        }
      }
      System.out.println("Top " + LIMIT + " ClientProperties");
      for (Pair<JComponent, Integer> pair : top) {
        System.out.println(pair.first.getClass().getName() + " (" + pair.second + " properties)" + getChange(old, pair.first, pair.second));
      }
    }

    private void checkClientProperties(JComponent component, Set<Pair<JComponent, Integer>> top, int limit) {
      try {
        Field clientProperties = JComponent.class.getDeclaredField("clientProperties");
        clientProperties.setAccessible(true);
        Object o = clientProperties.get(component);
        if (o != null) {
          Method size = o.getClass().getMethod("size");
          size.setAccessible(true);
          Object sizeResult = size.invoke(o);
          if (sizeResult instanceof Integer) {
            top.add(Pair.create(component, (Integer)sizeResult));
            trimToLimit(top, limit);
          }
        }
      }
      catch (NoSuchMethodException e) {
      }
      catch (InvocationTargetException e) {
      }
      catch (IllegalAccessException e) {
      }
      catch (NoSuchFieldException e) {
      }
      for (int i = 0; i < component.getComponentCount(); i++) {
        Component child = component.getComponent(i);
        if (child instanceof JComponent) {
          checkClientProperties((JComponent)child, top, limit);
        }
      }
    }
  };


  private static final ResettableAction RESET_THEM_ALL = new ResettableAction("Reset Statistics") {
    @Override
    void reset() {
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(AnActionEvent e) {
      for (ResettableAction action : CHILDREN) {
        action.reset();
      }
    }
  };

  private static ResettableAction[] CHILDREN = {TOP_PARENTS, TOP_UI_PROPERTIES, RESET_THEM_ALL};

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setText("Top " + LIMIT);
  }

  @Nonnull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    return CHILDREN;
  }

  private static <K, V> void trimToLimit(Set<Pair<K, V>> top, int limit) {
    int k = 0;
    for (Iterator<Pair<K, V>> iterator = top.iterator(); iterator.hasNext(); ) {
      k++;
      iterator.next();
      if (k >= limit) {
        iterator.remove();
      }
    }
  }

  private static <K, V extends Integer> String getChange(Set<Pair<K, V>> old, K key, int newResult) {
    for (Pair<K, V> oldPair : old) {
      if (oldPair.first == key) {
        int oldResult = oldPair.second.intValue();
        if (oldResult != newResult) {
          return (oldResult > newResult ? " -" : " +") + Math.abs(newResult - oldResult);
        }
        break;
      }
    }
    return "";
  }

  private static abstract class ResettableAction extends AnAction {
    protected ResettableAction(@Nullable String text) {
      super(text);
    }

    abstract void reset();
  }
}
