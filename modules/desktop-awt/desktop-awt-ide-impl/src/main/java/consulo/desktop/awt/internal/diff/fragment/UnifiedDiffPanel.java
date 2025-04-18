/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package consulo.desktop.awt.internal.diff.fragment;

import consulo.dataContext.DataProvider;
import consulo.desktop.awt.internal.diff.util.DiffPanelBase;
import consulo.diff.DiffContext;
import consulo.diff.comparison.DiffTooBigException;
import consulo.project.Project;
import consulo.ui.ex.awt.AsyncProcessIcon;
import consulo.ui.ex.awt.JBUI;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;

import static consulo.desktop.awt.internal.diff.util.AWTDiffUtil.createMessagePanel;

public class UnifiedDiffPanel extends DiffPanelBase {
    private static final String GOOD_CONTENT = "GoodContent";
    private static final String LOADING_CONTENT = "LoadingContent";
    private static final String TOO_BIG_CONTENT = "TooBigContent";
    private static final String OPERATION_CANCELED_CONTENT = "OperationCanceledContent";
    private static final String ERROR_CONTENT = "ErrorContent";

    @Nonnull
    private final AsyncProcessIcon.Big myBusyIcon;

    public UnifiedDiffPanel(
        @Nullable Project project,
        @Nonnull UnifiedContentPanel content,
        @Nonnull DataProvider provider,
        @Nonnull DiffContext context
    ) {
        super(project, provider, context);
        myBusyIcon = new AsyncProcessIcon.Big("UnifiedDiff");
        JPanel centerPanel = JBUI.Panels.simplePanel(content).addToTop(myNotificationsPanel);
        myContentPanel.add(centerPanel, GOOD_CONTENT);
        myContentPanel.add(myBusyIcon, LOADING_CONTENT);
        myContentPanel.add(createMessagePanel("Can not calculate diff. " + DiffTooBigException.MESSAGE), TOO_BIG_CONTENT);
        myContentPanel.add(createMessagePanel("Can not calculate diff. Operation canceled."), OPERATION_CANCELED_CONTENT);
        myContentPanel.add(createMessagePanel("Error"), ERROR_CONTENT);

        setCurrentCard(LOADING_CONTENT, false);
    }

    //
    // Card layout
    //

    public void setLoadingContent() {
        setCurrentCard(LOADING_CONTENT);
    }

    public void setGoodContent() {
        setCurrentCard(GOOD_CONTENT);
    }

    public void setTooBigContent() {
        setCurrentCard(TOO_BIG_CONTENT);
    }

    public void setOperationCanceledContent() {
        setCurrentCard(OPERATION_CANCELED_CONTENT);
    }

    public void setErrorContent() {
        setCurrentCard(ERROR_CONTENT);
    }

    @Override
    protected void setCurrentCard(@Nonnull String card) {
        if (card == LOADING_CONTENT) {
            myBusyIcon.resume();
        }
        else {
            myBusyIcon.suspend();
        }

        super.setCurrentCard(card);
    }

    //
    // Misc
    //

    public boolean isGoodContent() {
        return myCurrentCard == GOOD_CONTENT;
    }
}
