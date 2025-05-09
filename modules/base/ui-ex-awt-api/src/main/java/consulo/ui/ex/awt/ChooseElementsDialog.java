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
package consulo.ui.ex.awt;

import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.ex.awt.event.DoubleClickListener;
import consulo.annotation.DeprecationInfo;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.image.Image;
import consulo.util.concurrent.AsyncResult;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class ChooseElementsDialog<T> extends DialogWrapper {
    protected ElementsChooser<T> myChooser;
    @Nonnull
    private LocalizeValue myDescription;

    public ChooseElementsDialog(
        Project project,
        Collection<? extends T> items,
        @Nonnull LocalizeValue title,
        @Nonnull LocalizeValue description
    ) {
        this(project, items, title, description, false);
    }

    public ChooseElementsDialog(
        Project project,
        Collection<? extends T> items,
        @Nonnull LocalizeValue title,
        @Nonnull LocalizeValue description,
        boolean sort
    ) {
        super(project, true);
        myDescription = description;
        initializeDialog(items, title, sort);
    }

    public ChooseElementsDialog(Component parent, Collection<T> items, @Nonnull LocalizeValue title) {
        this(parent, items, title, LocalizeValue.empty(), false);
    }

    public ChooseElementsDialog(
        Component parent,
        Collection<T> items,
        @Nonnull LocalizeValue title,
        @Nonnull LocalizeValue description,
        boolean sort
    ) {
        super(parent, true);
        myDescription = description;
        initializeDialog(items, title, sort);
    }

    @Deprecated
    @DeprecationInfo("Use variant with LocalizeValue")
    public ChooseElementsDialog(Project project, Collection<? extends T> items, String title, String description) {
        this(project, items, LocalizeValue.ofNullable(title), LocalizeValue.ofNullable(description), false);
    }

    @Deprecated
    @DeprecationInfo("Use variant with LocalizeValue")
    public ChooseElementsDialog(Project project, Collection<? extends T> items, String title, String description, boolean sort) {
        this(project, items, LocalizeValue.ofNullable(title), LocalizeValue.ofNullable(description), sort);
    }

    @Deprecated
    @DeprecationInfo("Use variant with LocalizeValue")
    public ChooseElementsDialog(Component parent, Collection<T> items, String title) {
        this(parent, items, LocalizeValue.ofNullable(title), LocalizeValue.empty(), false);
    }

    @Deprecated
    @DeprecationInfo("Use variant with LocalizeValue")
    public ChooseElementsDialog(Component parent, Collection<T> items, String title, @Nullable String description, boolean sort) {
        this(parent, items, LocalizeValue.ofNullable(title), LocalizeValue.ofNullable(description), sort);
    }

    private void initializeDialog(Collection<? extends T> items, @Nonnull LocalizeValue title, boolean sort) {
        setTitle(title);
        myChooser = new ElementsChooser<>(false) {
            @Override
            protected String getItemText(@Nonnull T item) {
                return ChooseElementsDialog.this.getItemText(item);
            }
        };
        myChooser.setColorUnmarkedElements(false);

        List<? extends T> elements = new ArrayList<T>(items);
        if (sort) {
            Collections.sort(elements, (o1, o2) -> getItemText(o1).compareToIgnoreCase(getItemText(o2)));
        }
        setElements(elements, elements.size() > 0 ? elements.subList(0, 1) : Collections.<T>emptyList());
        myChooser.getComponent()
            .registerKeyboardAction(e -> doOKAction(), KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);

        new DoubleClickListener() {
            @Override
            protected boolean onDoubleClick(MouseEvent e) {
                doOKAction();
                return true;
            }
        }.installOn(myChooser.getComponent());

        init();
    }

    @Nonnull
    @RequiredUIAccess
    public AsyncResult<List<T>> showAsync2() {
        AsyncResult<List<T>> result = AsyncResult.undefined();
        AsyncResult<Void> showAsync = showAsync();
        showAsync.doWhenDone(() -> result.setDone(getChosenElements()));
        showAsync.doWhenRejected((Runnable)result::setRejected);
        return result;
    }

    @Deprecated
    @DeprecationInfo("See #showAsync2()")
    @RequiredUIAccess
    public List<T> showAndGetResult() {
        show();
        return getChosenElements();
    }

    protected abstract String getItemText(T item);

    @Nullable
    protected abstract Image getItemIcon(T item);

    @Nonnull
    public List<T> getChosenElements() {
        return isOK() ? myChooser.getSelectedElements() : Collections.<T>emptyList();
    }

    public void selectElements(@Nonnull List<T> elements) {
        myChooser.selectElements(elements);
    }

    @Override
    @RequiredUIAccess
    public JComponent getPreferredFocusedComponent() {
        return myChooser.getComponent();
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(ScrollPaneFactory.createScrollPane(myChooser.getComponent()), BorderLayout.CENTER);
        if (myDescription != LocalizeValue.empty()) {
            panel.add(new JLabel(myDescription.get()), BorderLayout.NORTH);
        }
        return panel;
    }

    protected void setElements(Collection<? extends T> elements, Collection<? extends T> elementsToSelect) {
        myChooser.clear();
        for (T item : elements) {
            myChooser.addElement(item, false, createElementProperties(item));
        }
        myChooser.selectElements(elementsToSelect);
    }

    private ElementsChooser.ElementProperties createElementProperties(T item) {
        return new ElementsChooser.ElementProperties() {
            @Override
            public Image getIcon() {
                return getItemIcon(item);
            }

            @Override
            public Color getColor() {
                return null;
            }
        };
    }
}
