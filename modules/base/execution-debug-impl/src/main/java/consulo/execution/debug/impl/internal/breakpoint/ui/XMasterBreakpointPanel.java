/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package consulo.execution.debug.impl.internal.breakpoint.ui;

import consulo.codeEditor.util.popup.DetailView;
import consulo.execution.debug.XBreakpointManager;
import consulo.execution.debug.breakpoint.XBreakpoint;
import consulo.execution.debug.impl.internal.action.handler.XBreakpointPanelProvider;
import consulo.execution.debug.impl.internal.breakpoint.XBreakpointBase;
import consulo.execution.debug.impl.internal.breakpoint.XBreakpointManagerImpl;
import consulo.execution.debug.impl.internal.breakpoint.XDependentBreakpointManagerImpl;
import consulo.project.Project;
import consulo.ui.ex.awt.UIUtil;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class XMasterBreakpointPanel extends XBreakpointPropertiesSubPanel {
    private JPanel myMasterBreakpointComboBoxPanel;
    private JPanel myAfterBreakpointHitPanel;
    private JRadioButton myLeaveEnabledRadioButton;
    @SuppressWarnings("UnusedDeclaration")
    private JPanel myContentPane;
    private JPanel myMainPanel;

    private BreakpointChooser myMasterBreakpointChooser;
    private XDependentBreakpointManagerImpl myDependentBreakpointManager;

    private List<BreakpointItem> getBreakpointItemsExceptMy() {
        List<BreakpointItem> items = new ArrayList<>();
        XBreakpointPanelProvider.getInstance().provideBreakpointItems(myProject, items);
        for (BreakpointItem item : items) {
            if (item.getBreakpoint() == myBreakpoint) {
                items.remove(item);
                break;
            }
        }
        items.add(new BreakpointNoneItem());
        return items;
    }

    @Override
    public void init(Project project, XBreakpointManager breakpointManager, @Nonnull XBreakpointBase breakpoint) {
        super.init(project, breakpointManager, breakpoint);
        myDependentBreakpointManager = ((XBreakpointManagerImpl) breakpointManager).getDependentBreakpointManager();
        myMasterBreakpointChooser = new BreakpointChooser(project, new BreakpointChooser.Delegate() {
            @Override
            public void breakpointChosen(Project project, BreakpointItem breakpointItem) {
                updateAfterBreakpointHitPanel();
            }
        }, null, getBreakpointItemsExceptMy());

        myMasterBreakpointComboBoxPanel.add(myMasterBreakpointChooser.getComponent(), BorderLayout.CENTER);
    }

    @Override
    public boolean lightVariant(boolean showAllOptions) {
        XBreakpoint<?> masterBreakpoint = myDependentBreakpointManager.getMasterBreakpoint(myBreakpoint);
        if (!showAllOptions && masterBreakpoint == null) {
            myMainPanel.setVisible(false);
            return true;
        }
        return false;
    }

    private void updateAfterBreakpointHitPanel() {
        boolean enable = myMasterBreakpointChooser.getSelectedBreakpoint() != null;
        UIUtil.setEnabled(myAfterBreakpointHitPanel, enable, true);
    }

    @Override
    void loadProperties() {
        XBreakpoint<?> masterBreakpoint = myDependentBreakpointManager.getMasterBreakpoint(myBreakpoint);
        if (masterBreakpoint != null) {
            myMasterBreakpointChooser.setSelectesBreakpoint(masterBreakpoint);
            myLeaveEnabledRadioButton.setSelected(myDependentBreakpointManager.isLeaveEnabled(myBreakpoint));
        }
        updateAfterBreakpointHitPanel();
    }

    @Override
    void saveProperties() {
        XBreakpoint<?> masterBreakpoint = (XBreakpoint<?>) myMasterBreakpointChooser.getSelectedBreakpoint();
        if (masterBreakpoint == null) {
            myDependentBreakpointManager.clearMasterBreakpoint(myBreakpoint);
        }
        else {
            myDependentBreakpointManager.setMasterBreakpoint(myBreakpoint, masterBreakpoint, myLeaveEnabledRadioButton.isSelected());
        }
    }

    public void setDetailView(DetailView detailView) {
        if (myMasterBreakpointChooser != null) {
            myMasterBreakpointChooser.setDetailView(detailView);
        }
    }

    public void hide() {
        myContentPane.getParent().remove(myContentPane);
    }
}
