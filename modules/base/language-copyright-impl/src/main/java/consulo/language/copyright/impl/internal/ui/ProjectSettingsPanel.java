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
package consulo.language.copyright.impl.internal.ui;

import consulo.configurable.Configurable;
import consulo.configurable.Settings;
import consulo.content.internal.scope.AllScopeHolder;
import consulo.content.internal.scope.CustomScopesProviderEx;
import consulo.content.scope.NamedScope;
import consulo.content.scope.NamedScopesHolder;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.language.copyright.config.CopyrightManager;
import consulo.language.copyright.config.CopyrightProfile;
import consulo.language.editor.packageDependency.DependencyValidationManager;
import consulo.language.editor.ui.awt.scope.ReadOnlyPackageSetChooserCombo;
import consulo.project.Project;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awt.table.ListTableModel;
import consulo.ui.ex.awt.table.TableView;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.List;
import java.util.*;

public class ProjectSettingsPanel {
  private final Project myProject;
  private final CopyrightProfilesPanel myProfilesModel;
  private final CopyrightManager myManager;

  private final TableView<ScopeSetting> myScopeMappingTable;
  private final ListTableModel<ScopeSetting> myScopeMappingModel;
  private final ComboBox<CopyrightProfile> myProfilesComboBox = new ComboBox<>();

  private final HyperlinkLabel myScopesLink = new HyperlinkLabel();

  public ProjectSettingsPanel(Project project, CopyrightProfilesPanel profilesModel) {
    myProject = project;
    myProfilesModel = profilesModel;
    myProfilesModel.addItemsChangeListener(new Runnable() {
      @Override
      public void run() {
        final Object selectedItem = myProfilesComboBox.getSelectedItem();
        reloadCopyrightProfiles();
        myProfilesComboBox.setSelectedItem(selectedItem);
        final ArrayList<ScopeSetting> toRemove = new ArrayList<ScopeSetting>();
        for (ScopeSetting setting : myScopeMappingModel.getItems()) {
          if (setting.getProfile() == null) {
            toRemove.add(setting);
          }
        }
        for (ScopeSetting setting : toRemove) {
          myScopeMappingModel.removeRow(myScopeMappingModel.indexOf(setting));
        }
      }
    });
    myManager = CopyrightManager.getInstance(project);

    ColumnInfo[] columns = {new ScopeColumn(), new SettingColumn()};
    myScopeMappingModel = new ListTableModel<ScopeSetting>(columns, new ArrayList<ScopeSetting>(), 0);
    myScopeMappingTable = new TableView<ScopeSetting>(myScopeMappingModel);

    reloadCopyrightProfiles();
    myProfilesComboBox.setRenderer(new ColoredListCellRenderer<CopyrightProfile>() {
      @Override
      protected void customizeCellRenderer(@Nonnull JList list, CopyrightProfile value, int index, boolean selected, boolean hasFocus) {
        append(value == null ? "No copyright" : value.getName());
      }
    });

    myScopesLink.setVisible(!myProject.isDefault());
    myScopesLink.setHyperlinkText("Select Scopes to add new scopes or modify existing ones");
    myScopesLink.addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(final HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          final DataContext dataContext = DataManager.getInstance().getDataContextFromFocus().getResult();
          final Settings optionsEditor = dataContext.getData(Settings.KEY);
          if (optionsEditor != null) {
            Configurable configurable = optionsEditor.findConfigurableById("project.scopes");
            if (configurable != null) {
              optionsEditor.clearSearchAndSelect(configurable);
            }
          }
        }
      }
    });
  }

  public void reloadCopyrightProfiles() {
    final DefaultComboBoxModel boxModel = (DefaultComboBoxModel)myProfilesComboBox.getModel();
    boxModel.removeAllElements();
    boxModel.addElement(null);
    for (CopyrightProfile profile : myProfilesModel.getAllProfiles().values()) {
      boxModel.addElement(profile);
    }
  }

  public JComponent getMainComponent() {
    final JPanel panel = new JPanel(new BorderLayout(0, 10));
    final LabeledComponent<JComboBox> component = new LabeledComponent<JComboBox>();
    component.setText("Default &project copyright:");
    component.setLabelLocation(BorderLayout.WEST);
    component.setComponent(myProfilesComboBox);
    panel.add(component, BorderLayout.NORTH);
    ElementProducer<ScopeSetting> producer = new ElementProducer<ScopeSetting>() {
      @Override
      public ScopeSetting createElement() {
        return new ScopeSetting(AllScopeHolder.getInstance().getAllScope(), myProfilesModel.getAllProfiles().values().iterator().next());
      }

      @Override
      public boolean canCreateElement() {
        return !myProfilesModel.getAllProfiles().isEmpty();
      }
    };
    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myScopeMappingTable, producer);
    panel.add(decorator.createPanel(), BorderLayout.CENTER);
    panel.add(myScopesLink, BorderLayout.SOUTH);
    return panel;
  }

  public boolean isModified() {
    final CopyrightProfile defaultCopyright = myManager.getDefaultCopyright();
    final Object selected = myProfilesComboBox.getSelectedItem();
    if (defaultCopyright != selected) {
      if (selected == null) return true;
      if (defaultCopyright == null) return true;
      if (!defaultCopyright.equals(selected)) return true;
    }
    final Map<String, String> map = myManager.getCopyrightsMapping();
    if (map.size() != myScopeMappingModel.getItems().size()) return true;
    final Iterator<String> iterator = map.keySet().iterator();
    for (ScopeSetting setting : myScopeMappingModel.getItems()) {
      final NamedScope scope = setting.getScope();
      if (!iterator.hasNext()) return true;
      final String scopeName = iterator.next();
      if (scope == null || !Comparing.strEqual(scopeName, scope.getName())) return true;
      final String profileName = map.get(scope.getName());
      if (profileName == null) return true;
      if (!profileName.equals(setting.getProfileName())) return true;
    }
    return false;
  }

  public void apply() {
    Collection<CopyrightProfile> profiles = new ArrayList<CopyrightProfile>(myManager.getCopyrights());
    myManager.clearCopyrights();
    for (CopyrightProfile profile : profiles) {
      myManager.addCopyright(profile);
    }
    final List<ScopeSetting> settingList = myScopeMappingModel.getItems();
    for (ScopeSetting scopeSetting : settingList) {
      myManager.mapCopyright(scopeSetting.getScope().getName(), scopeSetting.getProfileName());
    }
    myManager.setDefaultCopyright((CopyrightProfile)myProfilesComboBox.getSelectedItem());
  }

  public void reset() {
    myProfilesComboBox.setSelectedItem(myManager.getDefaultCopyright());
    final List<ScopeSetting> mappings = new ArrayList<ScopeSetting>();
    final Map<String, String> copyrights = myManager.getCopyrightsMapping();
    final DependencyValidationManager manager = DependencyValidationManager.getInstance(myProject);
    final Set<String> scopes2Unmap = new HashSet<String>();
    for (final String scopeName : copyrights.keySet()) {
      final NamedScope scope = manager.getScope(scopeName);
      if (scope != null) {
        mappings.add(new ScopeSetting(scope, copyrights.get(scopeName)));
      }
      else {
        scopes2Unmap.add(scopeName);
      }
    }
    for (String scopeName : scopes2Unmap) {
      myManager.unmapCopyright(scopeName);
    }
    myScopeMappingModel.setItems(mappings);
  }


  private class ScopeSetting {
    private NamedScope myScope;
    private CopyrightProfile myProfile;
    private String myProfileName;

    private ScopeSetting(NamedScope scope, CopyrightProfile profile) {
      myScope = scope;
      myProfile = profile;
      if (myProfile != null) {
        myProfileName = myProfile.getName();
      }
    }

    public ScopeSetting(NamedScope scope, String profile) {
      myScope = scope;
      myProfileName = profile;
    }

    public CopyrightProfile getProfile() {
      if (myProfileName != null) {
        myProfile = myProfilesModel.getAllProfiles().get(getProfileName());
      }
      return myProfile;
    }

    public void setProfile(@Nonnull CopyrightProfile profile) {
      myProfile = profile;
      myProfileName = profile.getName();
    }

    public NamedScope getScope() {
      return myScope;
    }

    public void setScope(NamedScope scope) {
      myScope = scope;
    }

    public String getProfileName() {
      return myProfile != null ? myProfile.getName() : myProfileName;
    }
  }

  private class SettingColumn extends MyColumnInfo<CopyrightProfile> {
    private SettingColumn() {
      super("Copyright");
    }

    @Override
    public TableCellRenderer getRenderer(final ScopeSetting scopeSetting) {
      return new DefaultTableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
          final Component rendererComponent = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          if (!isSelected) {
            final CopyrightProfile profile = myProfilesModel.getAllProfiles().get(scopeSetting.getProfileName());
            setForeground(profile == null ? JBColor.RED : UIUtil.getTableForeground());
          }
          setText(scopeSetting.getProfileName());
          return rendererComponent;
        }
      };
    }

    @Override
    public TableCellEditor getEditor(final ScopeSetting scopeSetting) {
      return new AbstractTableCellEditor() {
        private final JBComboBoxTableCellEditorComponent myProfilesChooser = new JBComboBoxTableCellEditorComponent();

        @Override
        public Object getCellEditorValue() {
          return myProfilesChooser.getEditorValue();
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
          final List<CopyrightProfile> copyrights = new ArrayList<CopyrightProfile>(myProfilesModel.getAllProfiles().values());
          Collections.sort(copyrights, new Comparator<CopyrightProfile>() {
            @Override
            public int compare(CopyrightProfile o1, CopyrightProfile o2) {
              return o1.getName().compareToIgnoreCase(o2.getName());
            }
          });
          myProfilesChooser.setCell(table, row, column);
          myProfilesChooser.setOptions(copyrights.toArray());
          myProfilesChooser.setDefaultValue(scopeSetting.getProfile());
          myProfilesChooser.setToString(o -> ((CopyrightProfile)o).getName());
          return myProfilesChooser;
        }
      };
    }

    @Override
    public CopyrightProfile valueOf(final ScopeSetting object) {
      return object.getProfile();
    }

    @Override
    public void setValue(final ScopeSetting scopeSetting, final CopyrightProfile copyrightProfile) {
      if (copyrightProfile != null) {
        scopeSetting.setProfile(copyrightProfile);
      }
    }
  }

  private class ScopeColumn extends MyColumnInfo<NamedScope> {
    private ScopeColumn() {
      super("Scope");
    }

    @Override
    public TableCellRenderer getRenderer(final ScopeSetting mapping) {
      return new DefaultTableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
          super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          if (value == null) {
            setText("");
          }
          else {
            final String scopeName = ((NamedScope)value).getName();
            if (!isSelected) {
              final NamedScope scope = NamedScopesHolder.getScope(myProject, scopeName);
              if (scope == null) setForeground(JBColor.RED);
            }
            setText(scopeName);
          }
          return this;
        }
      };
    }

    @Override
    public TableCellEditor getEditor(final ScopeSetting mapping) {
      return new AbstractTableCellEditor() {
        private ReadOnlyPackageSetChooserCombo myScopeChooser;

        @Override
        @Nullable
        public Object getCellEditorValue() {
          return myScopeChooser.getSelectedScope();
        }

        @Override
        public Component getTableCellEditorComponent(final JTable table, Object value, boolean isSelected, int row, int column) {
          myScopeChooser = new ReadOnlyPackageSetChooserCombo(myProject, value == null ? null : ((NamedScope)value).getName(), false) {
            @Override
            protected NamedScope[] createModel() {
              final NamedScope[] model = super.createModel();
              final ArrayList<NamedScope> filteredScopes = new ArrayList<NamedScope>(Arrays.asList(model));
              CustomScopesProviderEx.filterNoSettingsScopes(myProject, filteredScopes);
              return filteredScopes.toArray(new NamedScope[filteredScopes.size()]);
            }
          };

          ((JBComboBoxTableCellEditorComponent)myScopeChooser.getChildComponent()).setCell(table, row, column);
          return myScopeChooser;
        }
      };
    }

    @Override
    public NamedScope valueOf(final ScopeSetting mapping) {
      return mapping.getScope();
    }

    @Override
    public void setValue(final ScopeSetting mapping, final NamedScope set) {
      mapping.setScope(set);
    }
  }

  private static abstract class MyColumnInfo<T> extends ColumnInfo<ScopeSetting, T> {
    protected MyColumnInfo(final String name) {
      super(name);
    }

    @Override
    public boolean isCellEditable(final ScopeSetting item) {
      return true;
    }
  }
}
