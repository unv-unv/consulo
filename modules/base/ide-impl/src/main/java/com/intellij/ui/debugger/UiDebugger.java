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

package com.intellij.ui.debugger;

import consulo.ui.ex.action.ActionManager;
import consulo.component.extension.Extensions;
import consulo.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import consulo.ui.ex.JBColor;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.UiDecorator;
import com.intellij.ui.tabs.impl.JBEditorTabs;
import consulo.ui.ex.awt.JBUI;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;

import javax.annotation.Nonnull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;

public class UiDebugger extends JPanel implements Disposable {

  private final DialogWrapper myDialog;
  private final JBTabs myTabs;
  private final UiDebuggerExtension[] myExtensions;

  public UiDebugger() {
    consulo.disposer.Disposer.register(consulo.disposer.Disposer.get("ui"), this);

    myTabs = new JBEditorTabs(null, ActionManager.getInstance(), null, this);
    myTabs.getPresentation().setPaintBorder(JBUI.scale(1), 0, 0, 0)
            .setActiveTabFillIn(JBColor.GRAY).setUiDecorator(new UiDecorator() {
      @Override
      @Nonnull
      public UiDecoration getDecoration() {
        return new UiDecoration(null, JBUI.insets(4, 4, 4, 4));
      }
    });

    myExtensions = Extensions.getExtensions(UiDebuggerExtension.EP_NAME);
    addToUi(myExtensions);

    myDialog = new DialogWrapper((Project)null, true) {
      {
        init();
      }

      @Override
      protected JComponent createCenterPanel() {
        Disposer.register(getDisposable(), UiDebugger.this);
        return myTabs.getComponent();
      }

      @Override
      public JComponent getPreferredFocusedComponent() {
        return myTabs.getComponent();
      }

      @Override
      protected String getDimensionServiceKey() {
        return "UiDebugger";
      }

      @Override
      protected JComponent createSouthPanel() {
        final JPanel result = new JPanel(new BorderLayout());
        result.add(super.createSouthPanel(), BorderLayout.EAST);
        final JSlider slider = new JSlider(0, 100);
        slider.setValue(100);
        slider.addChangeListener(new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent e) {
            final int value = slider.getValue();
            float alpha = value / 100f;

            final Window wnd = SwingUtilities.getWindowAncestor(slider);
            if (wnd != null) {
              final WindowManagerEx mgr = WindowManagerEx.getInstanceEx();
              if (value == 100) {
                mgr.setAlphaModeEnabled(wnd, false);
              }
              else {
                mgr.setAlphaModeEnabled(wnd, true);
                mgr.setAlphaModeRatio(wnd, 1f - alpha);
              }
            }
          }
        });
        result.add(slider, BorderLayout.WEST);
        return result;
      }

      @Nonnull
      @Override
      protected Action[] createActions() {
        return new Action[]{new AbstractAction("Close") {
          @Override
          public void actionPerformed(ActionEvent e) {
            doOKAction();
          }
        }};
      }
    };
    myDialog.setModal(false);
    myDialog.setTitle("UI Debugger");
    myDialog.setResizable(true);

    myDialog.show();
  }

  @Override
  public void show() {
    myDialog.getPeer().getWindow().toFront();
  }

  private void addToUi(UiDebuggerExtension[] extensions) {
    for (UiDebuggerExtension each : extensions) {
      myTabs.addTab(new TabInfo(each.getComponent()).setText(each.getName()));
    }
  }

  @Override
  public void dispose() {
    for (UiDebuggerExtension each : myExtensions) {
      each.disposeUiResources();
    }
  }
}
