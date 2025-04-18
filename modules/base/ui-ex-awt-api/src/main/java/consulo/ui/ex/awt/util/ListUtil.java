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
package consulo.ui.ex.awt.util;

import consulo.ui.ex.awt.CollectionListModel;
import consulo.ui.ex.awt.SortedListModel;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.speedSearch.FilteringListModel;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public class ListUtil {
    public static final String SELECTED_BY_MOUSE_EVENT = "byMouseEvent";

    public static <T> MouseMotionListener installAutoSelectOnMouseMove(@Nonnull JList<T> list) {
        final MouseMotionAdapter listener = new MouseMotionAdapter() {
            boolean myIsEngaged = false;

            @Override
            public void mouseMoved(MouseEvent e) {
                Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if (myIsEngaged && !UIUtil.isSelectionButtonDown(e) && !(focusOwner instanceof JRootPane)) {
                    Point point = e.getPoint();
                    int index = list.locationToIndex(point);
                    list.putClientProperty(SELECTED_BY_MOUSE_EVENT, Boolean.TRUE);
                    list.setSelectedIndex(index);
                    list.putClientProperty(SELECTED_BY_MOUSE_EVENT, Boolean.FALSE);
                }
                else {
                    myIsEngaged = true;
                }
            }
        };
        list.addMouseMotionListener(listener);
        return listener;
    }

    public abstract static class Updatable {
        private final JButton myButton;
        private boolean myEnabled = true;

        public Updatable(JButton button) {
            myButton = button;
        }

        public void enable(boolean enable) {
            myEnabled = enable;
            update();
        }

        protected void setButtonEnabled(boolean enabled) {
            myButton.setEnabled(enabled && myEnabled);
        }

        protected abstract void update();
    }

    @Nonnull
    public static <T> List<T> removeSelectedItems(@Nonnull JList<T> list) {
        return removeSelectedItems(list, null);
    }

    @Nonnull
    public static <T> List<T> removeIndices(@Nonnull JList<T> list, int[] indices) {
        return removeIndices(list, indices, null);
    }

    @Nonnull
    public static <T> List<T> removeSelectedItems(@Nonnull JList<T> list, @Nullable Predicate<? super T> condition) {
        int[] indices = list.getSelectedIndices();
        return removeIndices(list, indices, condition);
    }

    public static <T> void removeItem(@Nonnull ListModel<T> model, int index) {
        getExtension(model).remove(model, index);
    }

    public static <T> void removeAllItems(@Nonnull ListModel<T> model) {
        getExtension(model).removeAll(model);
    }

    public static <T> void addAllItems(@Nonnull ListModel<T> model, @Nonnull List<? extends T> items) {
        getExtension(model).addAll(model, items);
    }

    private static <T> List<T> removeIndices(@Nonnull JList<T> list, @Nonnull int[] indices, @Nullable Predicate<? super T> condition) {
        if (indices.length == 0) {
            return new ArrayList<>(0);
        }
        ListModel<T> model = list.getModel();
        ListModelExtension<T, ListModel<T>> extension = getExtension(model);
        int firstSelectedIndex = indices[0];
        ArrayList<T> removedItems = new ArrayList<>();
        int deletedCount = 0;
        for (int idx1 : indices) {
            int index = idx1 - deletedCount;
            if (index < 0 || index >= model.getSize()) {
                continue;
            }
            T obj = extension.get(model, index);
            if (condition == null || condition.test(obj)) {
                removedItems.add(obj);
                extension.remove(model, index);
                deletedCount++;
            }
        }
        if (model.getSize() == 0) {
            list.clearSelection();
        }
        else if (list.getSelectedValue() == null) {
            // if nothing remains selected, set selected row
            if (firstSelectedIndex >= model.getSize()) {
                list.setSelectedIndex(model.getSize() - 1);
            }
            else {
                list.setSelectedIndex(firstSelectedIndex);
            }
        }
        return removedItems;
    }

    public static <T> boolean canRemoveSelectedItems(@Nonnull JList<T> list) {
        return canRemoveSelectedItems(list, null);
    }

    public static <T> boolean canRemoveSelectedItems(@Nonnull JList<T> list, @Nullable Predicate<? super T> condition) {
        int[] indices = list.getSelectedIndices();
        if (indices.length == 0) {
            return false;
        }
        ListModel<T> model = list.getModel();
        ListModelExtension<T, ListModel<T>> extension = getExtension(model);
        for (int index : indices) {
            if (index < 0 || index >= model.getSize()) {
                continue;
            }
            T obj = extension.get(model, index);
            if (condition == null || condition.test(obj)) {
                return true;
            }
        }

        return false;
    }

    public static <T> int moveSelectedItemsUp(@Nonnull JList<T> list) {
        ListModel<T> model = list.getModel();
        ListModelExtension<T, ListModel<T>> extension = getExtension(model);
        int[] indices = list.getSelectedIndices();
        if (!canMoveSelectedItemsUp(list)) {
            return 0;
        }
        for (int index : indices) {
            T temp = extension.get(model, index);
            extension.set(model, index, extension.get(model, index - 1));
            extension.set(model, index - 1, temp);
            list.removeSelectionInterval(index, index);
            list.addSelectionInterval(index - 1, index - 1);
        }
        Rectangle cellBounds = list.getCellBounds(indices[0] - 1, indices[indices.length - 1] - 1);
        if (cellBounds != null) {
            list.scrollRectToVisible(cellBounds);
        }
        return indices.length;
    }

    public static <T> boolean canMoveSelectedItemsUp(@Nonnull JList<T> list) {
        int[] indices = list.getSelectedIndices();
        return indices.length > 0 && indices[0] > 0;
    }

    public static <T> int moveSelectedItemsDown(@Nonnull JList<T> list) {
        ListModel<T> model = list.getModel();
        ListModelExtension<T, ListModel<T>> extension = getExtension(model);
        int[] indices = list.getSelectedIndices();
        if (!canMoveSelectedItemsDown(list)) {
            return 0;
        }
        for (int i = indices.length - 1; i >= 0; i--) {
            int index = indices[i];
            T temp = extension.get(model, index);
            extension.set(model, index, extension.get(model, index + 1));
            extension.set(model, index + 1, temp);
            list.removeSelectionInterval(index, index);
            list.addSelectionInterval(index + 1, index + 1);
        }
        Rectangle cellBounds = list.getCellBounds(indices[0] + 1, indices[indices.length - 1] + 1);
        if (cellBounds != null) {
            list.scrollRectToVisible(cellBounds);
        }
        return indices.length;
    }

    public static <T> boolean isPointOnSelection(@Nonnull JList<T> list, int x, int y) {
        int row = list.locationToIndex(new Point(x, y));
        return row >= 0 && list.isSelectedIndex(row);
    }

    @Nullable
    public static <E> Component getDeepestRendererChildComponentAt(@Nonnull JList<E> list, @Nonnull Point point) {
        int idx = list.locationToIndex(point);
        if (idx < 0) {
            return null;
        }

        Rectangle cellBounds = list.getCellBounds(idx, idx);
        if (!cellBounds.contains(point)) {
            return null;
        }

        E value = list.getModel().getElementAt(idx);
        if (value == null) {
            return null;
        }

        Component rendererComponent = list.getCellRenderer().getListCellRendererComponent(list, value, idx, true, true);
        rendererComponent.setBounds(cellBounds.x, cellBounds.y, cellBounds.width, cellBounds.height);
        UIUtil.layoutRecursively(rendererComponent);

        int rendererRelativeX = point.x - cellBounds.x;
        int rendererRelativeY = point.y - cellBounds.y;
        return UIUtil.getDeepestComponentAt(rendererComponent, rendererRelativeX, rendererRelativeY);
    }

    public static <T> boolean canMoveSelectedItemsDown(@Nonnull JList<T> list) {
        ListModel model = list.getModel();
        int[] indices = list.getSelectedIndices();
        return indices.length > 0 && indices[indices.length - 1] < model.getSize() - 1;
    }

    public static <T> Updatable addMoveUpListener(@Nonnull JButton button, @Nonnull JList<T> list) {
        button.addActionListener(e -> {
            moveSelectedItemsUp(list);
            list.requestFocusInWindow();
        });
        return disableWhenNoSelection(button, list);
    }


    public static <T> Updatable addMoveDownListener(@Nonnull JButton button, @Nonnull JList<T> list) {
        button.addActionListener(e -> {
            moveSelectedItemsDown(list);
            list.requestFocusInWindow();
        });
        return disableWhenNoSelection(button, list);
    }

    public static <T> Updatable addRemoveListener(@Nonnull JButton button, @Nonnull JList<T> list) {
        return addRemoveListener(button, list, null);
    }

    public static <T> Updatable addRemoveListener(
        @Nonnull JButton button,
        @Nonnull JList<T> list,
        @Nullable RemoveNotification<T> notification
    ) {
        button.addActionListener(e -> {
            List<T> items = removeSelectedItems(list);
            if (notification != null) {
                notification.itemsRemoved(items);
            }
            list.requestFocusInWindow();
        });
        class MyListSelectionListener extends Updatable implements ListSelectionListener {
            MyListSelectionListener(JButton button) {
                super(button);
            }

            @Override
            public void valueChanged(ListSelectionEvent e) {
                setButtonEnabled(canRemoveSelectedItems(list));
            }

            @Override
            protected void update() {
                valueChanged(null);
            }
        }
        MyListSelectionListener listener = new MyListSelectionListener(button);
        list.getSelectionModel().addListSelectionListener(listener);
        listener.update();
        return listener;
    }

    public static <T> Updatable disableWhenNoSelection(@Nonnull JButton button, @Nonnull JList<T> list) {
        class MyListSelectionListener extends Updatable implements ListSelectionListener {
            MyListSelectionListener(JButton button) {
                super(button);
            }

            @Override
            public void valueChanged(ListSelectionEvent e) {
                setButtonEnabled((list.getSelectedIndex() != -1));
            }

            @Override
            public void update() {
                valueChanged(null);
            }
        }
        MyListSelectionListener listener = new MyListSelectionListener(button);
        list.getSelectionModel().addListSelectionListener(listener);
        listener.update();
        return listener;
    }

    public interface RemoveNotification<ItemType> {
        void itemsRemoved(List<ItemType> items);
    }

    /**
     * @noinspection unchecked
     */
    @Nonnull
    private static <T, ModelType extends ListModel<T>> ListModelExtension<T, ModelType> getExtension(@Nonnull ModelType model) {
        if (model instanceof DefaultListModel) {
            return DEFAULT_MODEL;
        }
        if (model instanceof SortedListModel) {
            return SORTED_MODEL;
        }
        if (model instanceof FilteringListModel) {
            return FILTERED_MODEL;
        }
        if (model instanceof CollectionListModel) {
            return COLLECTION_MODEL;
        }
        throw new AssertionError("Unknown model class: " + model.getClass().getName());
    }

    //@formatter:off
  private interface ListModelExtension<T, ModelType extends ListModel<T>> {
    T get(ModelType model, int index);
    void set(ModelType model, int index, T item);
    void remove(ModelType model, int index);
    void removeAll(ModelType model);
    void addAll(ModelType model, List<? extends T> item);
  }

  private static final ListModelExtension DEFAULT_MODEL = new ListModelExtension<Object, DefaultListModel<Object>>() {
    @Override public Object get(DefaultListModel<Object> model, int index) { return model.get(index);}
    @Override public void set(DefaultListModel<Object> model, int index, Object item) { model.set(index, item);}
    @Override public void remove(DefaultListModel<Object> model, int index) { model.remove(index);}
    @Override public void removeAll(DefaultListModel<Object> model) { model.removeAllElements();}
    @Override public void addAll(DefaultListModel<Object> model, List<?> item) { model.addElement(item);}
  };

  private static final ListModelExtension COLLECTION_MODEL = new ListModelExtension<Object, CollectionListModel<Object>>() {
    @Override public Object get(CollectionListModel<Object> model, int index) { return model.getElementAt(index);}
    @Override public void set(CollectionListModel<Object> model, int index, Object item) { model.setElementAt(item, index);}
    @Override public void remove(CollectionListModel<Object> model, int index) { model.remove(index);}
    @Override public void removeAll(CollectionListModel<Object> model) { model.removeAll();}
    @Override public void addAll(CollectionListModel<Object> model, List<?> items) { model.addAll(model.getSize(), items);}
  };

  private static final ListModelExtension SORTED_MODEL = new ListModelExtension<Object, SortedListModel<Object>>() {
    @Override public Object get(SortedListModel<Object> model, int index) { return model.get(index);}
    @Override public void set(SortedListModel<Object> model, int index, Object item) { model.remove(index); model.add(item);}
    @Override public void remove(SortedListModel<Object> model, int index) { model.remove(index);}
    @Override public void removeAll(SortedListModel<Object> model) { model.clear();}
    @Override public void addAll(SortedListModel<Object> model, List<?> items) { model.addAll(items);}
  };

  private static final ListModelExtension FILTERED_MODEL = new ListModelExtension<Object, FilteringListModel<Object>>() {
    @Override public Object get(FilteringListModel<Object> model, int index) { return model.getElementAt(index);}
    @Override public void set(FilteringListModel<Object> model, int index, Object item) { getExtension(model.getOriginalModel()).set(model.getOriginalModel(), index, item);}
    @Override public void remove(FilteringListModel<Object> model, int index) { model.remove(index);}
    @Override public void removeAll(FilteringListModel<Object> model) { model.replaceAll(Collections.emptyList());}
    @Override public void addAll(FilteringListModel<Object> model, List<?> items) { model.addAll(items);}
  };
  //@formatter:on
}
