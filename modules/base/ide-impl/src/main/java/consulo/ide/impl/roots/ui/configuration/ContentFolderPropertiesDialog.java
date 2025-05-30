/*
 * Copyright 2013-2016 consulo.io
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
package consulo.ide.impl.roots.ui.configuration;

import consulo.application.Application;
import consulo.component.extension.ExtensionPoint;
import consulo.content.ContentFolderPropertyProvider;
import consulo.ide.impl.idea.util.ui.ComboBoxCellEditor;
import consulo.localize.LocalizeValue;
import consulo.module.content.layer.ContentFolder;
import consulo.project.Project;
import consulo.project.localize.ProjectLocalize;
import consulo.ui.ex.awt.ChooseElementsDialog;
import consulo.ui.ex.awt.ColumnInfo;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.ToolbarDecorator;
import consulo.ui.ex.awt.table.ComboBoxTableRenderer;
import consulo.ui.ex.awt.table.ListTableModel;
import consulo.ui.ex.awt.table.TableView;
import consulo.ui.image.Image;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author VISTALL
 * @since 2014-02-05
 */
public class ContentFolderPropertiesDialog extends DialogWrapper {
    private static class Item {
        public ContentFolderPropertyProvider myProvider;
        public Key myKey;
        public Object myValue;

        private Item(ContentFolderPropertyProvider provider, Key key, Object value) {
            myProvider = provider;
            myKey = key;
            myValue = value;
        }
    }

    private static class ChooseProvidersDialog extends ChooseElementsDialog<ContentFolderPropertyProvider<?>> {
        public ChooseProvidersDialog(
            Project project,
            List<? extends ContentFolderPropertyProvider<?>> items,
            @Nonnull LocalizeValue title,
            @Nonnull LocalizeValue description
        ) {
            super(project, items, title, description);
        }

        @Override
        protected String getItemText(ContentFolderPropertyProvider<?> item) {
            return item.getKey().toString();
        }

        @Nullable
        @Override
        protected Image getItemIcon(ContentFolderPropertyProvider<?> item) {
            return null;
        }
    }

    private static final ColumnInfo[] ourColumns = new ColumnInfo[]{
        new ColumnInfo<Item, String>("Name") {
            @Nullable
            @Override
            public String valueOf(Item info) {
                return info.myKey.toString();
            }
        },
        new ColumnInfo<Item, String>("Value") {
            @Nullable
            @Override
            public String valueOf(Item info) {
                return String.valueOf(info.myValue);
            }

            @Nullable
            @Override
            public TableCellRenderer getRenderer(Item item) {
                return new ComboBoxTableRenderer<>(item.myProvider.getValues());
            }

            @Nullable
            @Override
            public TableCellEditor getEditor(Item o) {
                return new ComboBoxCellEditor() {
                    @Override
                    protected List<String> getComboBoxItems() {
                        Object[] values = o.myProvider.getValues();
                        List<String> items = new ArrayList<>();
                        for (Object value : values) {
                            items.add(String.valueOf(value));
                        }
                        return items;
                    }
                };
            }

            @Override
            public boolean isCellEditable(Item item) {
                return item.myProvider != null;
            }

            @Override
            public void setValue(Item item, String value) {
                item.myValue = item.myProvider.fromString(value);
            }
        }
    };

    @Nullable
    private final Project myProject;
    private final ContentFolder myContentFolder;
    private List<Item> myItems = new ArrayList<>();

    public ContentFolderPropertiesDialog(@Nullable Project project, ContentFolder contentFolder) {
        super(project);
        myProject = project;
        myContentFolder = contentFolder;

        for (Map.Entry<Key, Object> entry : contentFolder.getProperties().entrySet()) {
            ContentFolderPropertyProvider provider = Application.get().getExtensionPoint(ContentFolderPropertyProvider.class)
                .findFirstSafe(propertyProvider -> propertyProvider.getKey() == entry.getKey());
            myItems.add(new Item(provider, entry.getKey(), entry.getValue()));
        }

        setTitle(ProjectLocalize.modulePathsPropertiesTooltip());

        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        ListTableModel<Item> model = new ListTableModel<>(ourColumns, myItems, 0);
        TableView<Item> table = new TableView<>(model);

        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(table);
        ExtensionPoint<ContentFolderPropertyProvider> extensionPoint =
            Application.get().getExtensionPoint(ContentFolderPropertyProvider.class);

        decorator.setAddAction(anActionButton -> {
            List<ContentFolderPropertyProvider<?>> list = extensionPoint.collectMapped(propertyProvider -> {
                for (Item item : myItems) {
                    if (item.myProvider == propertyProvider) {
                        return null;
                    }
                }

                return propertyProvider;
            });

            ChooseProvidersDialog d = new ChooseProvidersDialog(
                myProject,
                list,
                ProjectLocalize.modulePathsAddPropertiesTitle(),
                ProjectLocalize.modulePathsAddPropertiesDesc()
            );

            List<ContentFolderPropertyProvider<?>> temp = d.showAndGetResult();
            for (ContentFolderPropertyProvider<?> propertyProvider : temp) {
                model.addRow(new Item(propertyProvider, propertyProvider.getKey(), propertyProvider.getValues()[0]));
            }
        });
        decorator.disableUpDownActions();
        return decorator.createPanel();
    }

    @Override
    protected void doOKAction() {
        Map<Key, Object> properties = myContentFolder.getProperties();
        for (Key<?> key : properties.keySet()) {
            myContentFolder.setPropertyValue(key, null);
        }

        for (Item item : myItems) {
            myContentFolder.setPropertyValue(item.myKey, item.myValue);
        }
        super.doOKAction();
    }
}
