/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import consulo.annotation.DeprecationInfo;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.util.Comparator;
import java.util.Objects;

public abstract class ColumnInfo<Item, Aspect> {
    public static class StringColumn extends ColumnInfo<String, String> {
        public StringColumn(@Nonnull LocalizeValue name) {
            super(name);
        }

        @Deprecated
        @DeprecationInfo("Use variant with LocalizeValue")
        public StringColumn(String name) {
            super(name);
        }

        @Override
        public String valueOf(String item) {
            return item;
        }
    }

    private String myName;
    public static final ColumnInfo[] EMPTY_ARRAY = new ColumnInfo[0];

    public ColumnInfo(@Nonnull LocalizeValue name) {
        myName = name.get();
    }

    @Deprecated
    @DeprecationInfo("Use variant with LocalizeValue")
    public ColumnInfo(String name) {
        myName = name;
    }

    @Nullable
    public Icon getIcon() {
        return null;
    }

    @Override
    public String toString() {
        return getName();
    }

    @Nullable
    public abstract Aspect valueOf(Item item);

    public final boolean isSortable() {
        return getComparator() != null;
    }

    @Nullable
    public Comparator<Item> getComparator() {
        return null;
    }

    public String getName() {
        return myName;
    }

    public Class getColumnClass() {
        return String.class;
    }

    public boolean isCellEditable(Item item) {
        return false;
    }

    public void setValue(Item item, Aspect value) {
    }

    @Nullable
    public TableCellRenderer getRenderer(Item item) {
        return null;
    }

    public TableCellRenderer getCustomizedRenderer(Item o, TableCellRenderer renderer) {
        return renderer;
    }

    @Nullable
    public TableCellEditor getEditor(Item o) {
        return null;
    }

    @Nullable
    public String getMaxStringValue() {
        return null;
    }

    @Nullable
    public String getPreferredStringValue() {
        return null;
    }

    public int getAdditionalWidth() {
        return 0;
    }

    public int getWidth(JTable table) {
        return -1;
    }

    public void setName(@Nonnull LocalizeValue name) {
        myName = name.get();
    }

    @Deprecated
    @DeprecationInfo("Use variant with LocalizeValue")
    public void setName(String s) {
        myName = s;
    }

    @Nullable
    public String getTooltipText() {
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ColumnInfo that = (ColumnInfo) o;

        return Objects.equals(myName, that.myName);
    }

    @Override
    public int hashCode() {
        return myName != null ? myName.hashCode() : 0;
    }

    public boolean hasError() {
        return false;
    }
}
