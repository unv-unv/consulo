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
package consulo.ide.impl.idea.openapi.actionSystem.ex;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.Service;
import consulo.annotation.component.ServiceImpl;
import consulo.application.ApplicationManager;
import consulo.component.persist.RoamingType;
import consulo.component.persist.StoragePathMacros;
import consulo.dataContext.DataContext;
import consulo.ide.impl.idea.ide.actions.QuickSwitchSchemeAction;
import consulo.ide.impl.idea.openapi.actionSystem.impl.BundledQuickListsProvider;
import consulo.ide.impl.idea.openapi.options.BaseSchemeProcessor;
import consulo.ide.impl.idea.openapi.options.SchemesManager;
import consulo.ide.impl.idea.openapi.options.SchemesManagerFactory;
import consulo.ide.impl.idea.openapi.util.JDOMUtil;
import consulo.ide.impl.idea.util.PathUtil;
import consulo.project.Project;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.DefaultActionGroup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jdom.Element;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Singleton
@Service(value = ComponentScope.APPLICATION, lazy = false)
@ServiceImpl
public class QuickListsManager {
  public static QuickListsManager getInstance() {
    return ApplicationManager.getApplication().getComponent(QuickListsManager.class);
  }

  static final String FILE_SPEC = StoragePathMacros.ROOT_CONFIG + "/quicklists";

  private static final String LIST_TAG = "list";

  private final ActionManager myActionManager;
  private final SchemesManager<QuickList, QuickList> mySchemesManager;

  @Inject
  public QuickListsManager(@Nonnull ActionManager actionManager, @Nonnull SchemesManagerFactory schemesManagerFactory) {
    myActionManager = actionManager;
    mySchemesManager = schemesManagerFactory.createSchemesManager(FILE_SPEC, new BaseSchemeProcessor<QuickList, QuickList>() {
      @Nonnull
      @Override
      public QuickList readScheme(@Nonnull Element element) {
        return createItem(element);
      }

      @Nonnull
      @Override
      public String getName(@Nonnull QuickList immutableElement) {
        return immutableElement.getName();
      }

      @Override
      public Element writeScheme(@Nonnull QuickList scheme) {
        Element element = new Element(LIST_TAG);
        scheme.writeExternal(element);
        return element;
      }
    }, RoamingType.DEFAULT);

    for (BundledQuickListsProvider provider : BundledQuickListsProvider.EP_NAME.getExtensionList()) {
      for (final String path : provider.getBundledListsRelativePaths()) {
        mySchemesManager.loadBundledScheme(path, provider, element -> {
          QuickList item = createItem(element);
          item.getExternalInfo().setHash(JDOMUtil.getTreeHash(element, true));
          item.getExternalInfo().setPreviouslySavedName(item.getName());
          item.getExternalInfo().setCurrentFileName(PathUtil.getFileName(path));
          return item;
        });
      }
    }
    mySchemesManager.loadSchemes();
    registerActions();
  }

  @Nonnull
  private static QuickList createItem(@Nonnull Element element) {
    QuickList item = new QuickList();
    item.readExternal(element);
    return item;
  }

  @Nonnull
  public QuickList[] getAllQuickLists() {
    Collection<QuickList> lists = mySchemesManager.getAllSchemes();
    return lists.toArray(new QuickList[lists.size()]);
  }

  private void registerActions() {
    // to prevent exception if 2 or more targets have the same name
    Set<String> registeredIds = new HashSet<String>();
    for (QuickList list : mySchemesManager.getAllSchemes()) {
      String actionId = list.getActionId();
      if (registeredIds.add(actionId)) {
        myActionManager.registerAction(actionId, new InvokeQuickListAction(list));
      }
    }
  }

  private void unregisterActions() {
    for (String oldId : myActionManager.getActionIds(QuickList.QUICK_LIST_PREFIX)) {
      myActionManager.unregisterAction(oldId);
    }
  }

  public void setQuickLists(@Nonnull QuickList[] quickLists) {
    mySchemesManager.clearAllSchemes();
    unregisterActions();
    for (QuickList quickList : quickLists) {
      mySchemesManager.addNewScheme(quickList, true);
    }
    registerActions();
  }

  private static class InvokeQuickListAction extends QuickSwitchSchemeAction {
    private final QuickList myQuickList;

    public InvokeQuickListAction(@Nonnull QuickList quickList) {
      myQuickList = quickList;
      myActionPlace = ActionPlaces.ACTION_PLACE_QUICK_LIST_POPUP_ACTION;
      getTemplatePresentation().setDescription(myQuickList.getDescription());
      getTemplatePresentation().setText(myQuickList.getName(), false);
    }

    @Override
    protected void fillActions(Project project, @Nonnull DefaultActionGroup group, @Nonnull DataContext dataContext) {
      ActionManager actionManager = ActionManager.getInstance();
      for (String actionId : myQuickList.getActionIds()) {
        if (QuickList.SEPARATOR_ID.equals(actionId)) {
          group.addSeparator();
        }
        else {
          AnAction action = actionManager.getAction(actionId);
          if (action != null) {
            group.add(action);
          }
        }
      }
    }

    @Override
    protected boolean isEnabled() {
      return true;
    }
  }
}
