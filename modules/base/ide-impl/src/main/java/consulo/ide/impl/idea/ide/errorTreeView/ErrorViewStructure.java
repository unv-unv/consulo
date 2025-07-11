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
package consulo.ide.impl.idea.ide.errorTreeView;

import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.ide.impl.idea.ide.errorTreeView.impl.ErrorTreeViewConfiguration;
import consulo.ui.ex.errorTreeView.ErrorTreeElementKind;
import consulo.ui.ex.errorTreeView.HotfixData;
import consulo.ui.ex.errorTreeView.SimpleErrorData;
import consulo.ui.ex.tree.AbstractTreeStructure;
import consulo.ui.ex.tree.NodeDescriptor;
import consulo.application.ApplicationManager;
import consulo.fileEditor.impl.internal.OpenFileDescriptorImpl;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.navigation.Navigatable;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.ide.impl.idea.ui.CustomizeColoredTreeCellRenderer;
import consulo.ui.ex.awt.SimpleColoredComponent;
import consulo.ide.impl.idea.util.ArrayUtil;
import consulo.ui.ex.errorTreeView.MutableErrorTreeView;
import consulo.ui.image.Image;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 * @since 2004-11-12
 */
public class ErrorViewStructure extends AbstractTreeStructure {
  private final ErrorTreeElement myRoot = new MyRootElement();

  private final List<String> myGroupNames = new ArrayList<String>();
  private final Map<String, GroupingElement> myGroupNameToElementMap = new HashMap<String, GroupingElement>();
  private final Map<String, List<NavigatableMessageElement>> myGroupNameToMessagesMap = new HashMap<String, List<NavigatableMessageElement>>();
  private final Map<ErrorTreeElementKind, List<ErrorTreeElement>> mySimpleMessages =
          new EnumMap<ErrorTreeElementKind, List<ErrorTreeElement>>(ErrorTreeElementKind.class);
  private final Object myLock = new Object();

  private static final ErrorTreeElementKind[] ourMessagesOrder =
          {ErrorTreeElementKind.INFO, ErrorTreeElementKind.ERROR, ErrorTreeElementKind.WARNING, ErrorTreeElementKind.NOTE, ErrorTreeElementKind.GENERIC};
  private final Project myProject;
  private final ErrorTreeViewConfiguration myConfiguration;

  public ErrorViewStructure(Project project, final boolean canHideInfosOrWarnings) {
    myProject = project;
    myConfiguration = canHideInfosOrWarnings ? ErrorTreeViewConfiguration.getInstance(project) : null;
  }

  @Override
  public Object getRootElement() {
    return myRoot;
  }

  @Override
  public ErrorTreeElement[] getChildElements(Object element) {
    if (element == myRoot) {
      final List<ErrorTreeElement> children = new ArrayList<ErrorTreeElement>();
      // simple messages
      synchronized (myLock) {
        for (final ErrorTreeElementKind kind : ourMessagesOrder) {
          if(!canShowKind(kind)) {
            continue;
          }
          final List<ErrorTreeElement> elems = mySimpleMessages.get(kind);
          if (elems != null) {
            children.addAll(elems);
          }
        }
        // files
        for (final String myGroupName : myGroupNames) {
          final GroupingElement groupingElement = myGroupNameToElementMap.get(myGroupName);
          if (shouldShowFileElement(groupingElement)) {
            children.add(groupingElement);
          }
        }
      }
      return ArrayUtil.toObjectArray(children, ErrorTreeElement.class);
    }

    if (element instanceof GroupingElement) {
      synchronized (myLock) {
        final List<NavigatableMessageElement> children = myGroupNameToMessagesMap.get(((GroupingElement)element).getName());
        if (children != null && !children.isEmpty()) {
          if(myConfiguration != null) {
            final List<ErrorTreeElement> filtered = new ArrayList<ErrorTreeElement>(children.size());
            for (final NavigatableMessageElement navigatableMessageElement : children) {
              ErrorTreeElementKind kind = navigatableMessageElement.getKind();
              if(!canShowKind(kind)) {
                continue;
              }
              filtered.add(navigatableMessageElement);
            }
            return ArrayUtil.toObjectArray(filtered, ErrorTreeElement.class);
          }
          return ArrayUtil.toObjectArray(children, NavigatableMessageElement.class);
        }
      }
    }

    return ErrorTreeElement.EMPTY_ARRAY;
  }

  private boolean shouldShowFileElement(GroupingElement groupingElement) {
    if (myConfiguration == null) {
      return getChildCount(groupingElement) > 0;
    }
    synchronized (myLock) {
      final List<NavigatableMessageElement> children = myGroupNameToMessagesMap.get(groupingElement.getName());
      if (children != null) {
        for (final NavigatableMessageElement child : children) {
          ErrorTreeElementKind kind = child.getKind();
          if (canShowKind(kind)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @Override
  public Object getParentElement(Object element) {
    if (element instanceof GroupingElement || element instanceof SimpleMessageElement) {
      return myRoot;
    }
    if (element instanceof NavigatableMessageElement) {
      GroupingElement result = ((NavigatableMessageElement)element).getParent();
      return result == null ? myRoot : result;
    }
    return null;
  }

  @Override
  @Nonnull
  public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
    return new ErrorTreeNodeDescriptor(myProject, parentDescriptor, (ErrorTreeElement)element);
  }

  @Override
  public final void commit() {
  }

  @Override
  public final boolean hasSomethingToCommit() {
    return false;
  }

  public void addMessage(@Nonnull ErrorTreeElementKind kind,
                         @Nonnull String[] text,
                         @Nullable VirtualFile underFileGroup,
                         @Nullable VirtualFile file,
                         int line,
                         int column,
                         @Nullable Object data) {
    if (underFileGroup != null || file != null) {
      if (file == null) line = column = -1;

      final int guiline = line < 0 ? -1 : line + 1;
      final int guicolumn = column < 0 ? -1 : column + 1;

      VirtualFile group = underFileGroup != null ? underFileGroup : file;
      VirtualFile nav = file != null ? file : underFileGroup;

      addNavigatableMessage(group.getPresentableUrl(), new OpenFileDescriptorImpl(myProject, nav, line, column), kind, text, data,
                            NewErrorTreeViewPanelImpl.createExportPrefix(guiline), NewErrorTreeViewPanelImpl.createRendererPrefix(guiline, guicolumn), group);
    }
    else {
      addSimpleMessage(kind, text, data);
    }
  }

  public List<Object> getGroupChildrenData(final String groupName) {
    synchronized (myLock) {
      final List<NavigatableMessageElement> children = myGroupNameToMessagesMap.get(groupName);
      if (children == null || children.isEmpty()) {
        return Collections.emptyList();
      }
      final List<Object> result = new ArrayList<Object>();
      for (NavigatableMessageElement child : children) {
        final Object data = child.getData();
        if (data != null) {
          result.add(data);
        }
      }
      return result;
    }
  }

  public void addFixedHotfixGroup(final String text, final List<SimpleErrorData> children) {
    final FixedHotfixGroupElement group = new FixedHotfixGroupElement(text, null, null);

    addGroupPlusElements(text, group, children);
  }

  public void addHotfixGroup(final HotfixData hotfixData, final List<SimpleErrorData> children, final MutableErrorTreeView view) {
    final String text = hotfixData.getErrorText();
    final HotfixGroupElement group = new HotfixGroupElement(text, null, null, hotfixData.getFix(), hotfixData.getFixComment(), view);

    addGroupPlusElements(text, group, children);
  }

  private void addGroupPlusElements(String text, GroupingElement group, List<SimpleErrorData> children) {
    final List<NavigatableMessageElement> elements = new ArrayList<NavigatableMessageElement>();
    for (SimpleErrorData child : children) {
      elements.add(new MyNavigatableWithDataElement(myProject, child.getKind(), group, child.getMessages(), child.getVf(),
                                                    NewErrorTreeViewPanelImpl.createExportPrefix(-1), NewErrorTreeViewPanelImpl.createRendererPrefix(-1, -1)));
    }

    synchronized (myLock) {
      myGroupNames.add(text);
      myGroupNameToElementMap.put(text, group);
      myGroupNameToMessagesMap.put(text, elements);
    }
  }

  public void addMessage(@Nonnull ErrorTreeElementKind kind, String[] text, Object data) {
    addSimpleMessage(kind, text, data);
  }

  public void addNavigatableMessage(@Nullable String groupName,
                                    Navigatable navigatable,
                                    @Nonnull ErrorTreeElementKind kind,
                                    final String[] message,
                                    final Object data,
                                    String exportText,
                                    String rendererTextPrefix,
                                    VirtualFile file) {
    if (groupName == null) {
      addSimpleMessageElement(new NavigatableMessageElement(kind, null, message, navigatable, exportText, rendererTextPrefix));
    }
    else {
      synchronized (myLock) {
        List<NavigatableMessageElement> elements = myGroupNameToMessagesMap.get(groupName);
        if (elements == null) {
          elements = new ArrayList<NavigatableMessageElement>();
          myGroupNameToMessagesMap.put(groupName, elements);
        }
        elements.add(new NavigatableMessageElement(kind, getGroupingElement(groupName, data, file), message, navigatable, exportText, rendererTextPrefix));
      }
    }
  }

  public void addNavigatableMessage(@Nonnull String groupName, @Nonnull NavigatableMessageElement navigatableMessageElement) {
    synchronized (myLock) {
      List<NavigatableMessageElement> elements = myGroupNameToMessagesMap.get(groupName);
      if (elements == null) {
        elements = new ArrayList<NavigatableMessageElement>();
        myGroupNameToMessagesMap.put(groupName, elements);
      }
      if (!myGroupNameToElementMap.containsKey(groupName)) {
        myGroupNames.add(groupName);
        myGroupNameToElementMap.put(groupName, navigatableMessageElement.getParent());
      }
      elements.add(navigatableMessageElement);
    }
  }

  private void addSimpleMessage(@Nonnull ErrorTreeElementKind kind, final String[] text, final Object data) {
    addSimpleMessageElement(new SimpleMessageElement(kind, text, data));
  }

  private void addSimpleMessageElement(ErrorTreeElement element) {
    synchronized (myLock) {
      List<ErrorTreeElement> elements = mySimpleMessages.get(element.getKind());
      if (elements == null) {
        elements = new ArrayList<ErrorTreeElement>();
        mySimpleMessages.put(element.getKind(), elements);
      }
      elements.add(element);
    }
  }

  @Nullable
  public GroupingElement lookupGroupingElement(String groupName) {
    synchronized (myLock) {
      return myGroupNameToElementMap.get(groupName);
    }
  }

  public GroupingElement getGroupingElement(String groupName, Object data, VirtualFile file) {
    synchronized (myLock) {
      GroupingElement element = myGroupNameToElementMap.get(groupName);
      if (element != null) {
        return element;
      }
      element = new GroupingElement(groupName, data, file);
      myGroupNames.add(groupName);
      myGroupNameToElementMap.put(groupName, element);
      return element;
    }
  }

  public int getChildCount(GroupingElement groupingElement) {
    synchronized (myLock) {
      final List<NavigatableMessageElement> children = myGroupNameToMessagesMap.get(groupingElement.getName());
      return children == null ? 0 : children.size();
    }
  }

  public void clear() {
    synchronized (myLock) {
      myGroupNames.clear();
      myGroupNameToElementMap.clear();
      myGroupNameToMessagesMap.clear();
      mySimpleMessages.clear();
    }
  }

  @Nullable
  public ErrorTreeElement getFirstMessage(@Nonnull ErrorTreeElementKind kind) {
    if (!canShowKind(kind)) {
      return null; // no warnings are available
    }
    synchronized (myLock) {
      final List<ErrorTreeElement> simpleMessages = mySimpleMessages.get(kind);
      if (simpleMessages != null && !simpleMessages.isEmpty()) {
        return simpleMessages.get(0);
      }
      for (final String path : myGroupNames) {
        final List<NavigatableMessageElement> messages = myGroupNameToMessagesMap.get(path);
        if (messages != null) {
          for (final NavigatableMessageElement navigatableMessageElement : messages) {
            if (kind.equals(navigatableMessageElement.getKind())) {
              return navigatableMessageElement;
            }
          }
        }
      }
    }
    return null;
  }

  private boolean canShowKind(@Nonnull ErrorTreeElementKind kind) {
    if (myConfiguration == null) {
      return true;
    }
    if (ErrorTreeElementKind.WARNING.equals(kind) || ErrorTreeElementKind.NOTE.equals(kind)) {
      return myConfiguration.SHOW_WARNINGS;
    }
    if (ErrorTreeElementKind.INFO.equals(kind)) {
      return myConfiguration.SHOW_INFOS;
    }
    return true;
  }

  private static class MyRootElement extends ErrorTreeElement {
    @Override
    public String[] getText() {
      return null;
    }

    @Override
    public Object getData() {
      return null;
    }

    @Override
    public String getExportTextPrefix() {
      return "";
    }
  }

  public void removeGroup(final String name) {
    synchronized (myLock) {
      myGroupNames.remove(name);
      myGroupNameToElementMap.remove(name);
      myGroupNameToMessagesMap.remove(name);
    }
  }

  public void removeElement(final ErrorTreeElement element) {
    if (element == myRoot) {
      return;
    }
    if (element instanceof GroupingElement) {
      GroupingElement groupingElement = (GroupingElement)element;
      removeGroup(groupingElement.getName());
      final VirtualFile virtualFile = groupingElement.getFile();
      if (virtualFile != null) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            final PsiFile psiFile = virtualFile.isValid() ? PsiManager.getInstance(myProject).findFile(virtualFile) : null;
            if (psiFile != null) {
              DaemonCodeAnalyzer.getInstance(myProject).restart(psiFile); // urge the daemon to re-highlight the file despite no modification has been made
            }
          }
        });
      }
    }
    else if (element instanceof NavigatableMessageElement) {
      final NavigatableMessageElement navElement = (NavigatableMessageElement)element;
      final GroupingElement parent = navElement.getParent();
      if (parent != null) {
        synchronized (myLock) {
          final List<NavigatableMessageElement> groupMessages = myGroupNameToMessagesMap.get(parent.getName());
          if (groupMessages != null) {
            groupMessages.remove(navElement);
          }
        }
      }
    }
    else {
      synchronized (myLock) {
        final List<ErrorTreeElement> simples = mySimpleMessages.get(element.getKind());
        if (simples != null) {
          simples.remove(element);
        }
      }
    }
  }

  private static class MyNavigatableWithDataElement extends NavigatableMessageElement {
    private final VirtualFile myVf;
    private final CustomizeColoredTreeCellRenderer myCustomizeColoredTreeCellRenderer;

    private MyNavigatableWithDataElement(final Project project,
                                         @Nonnull ErrorTreeElementKind kind,
                                         GroupingElement parent,
                                         String[] message,
                                         @Nonnull final VirtualFile vf,
                                         String exportText,
                                         String rendererTextPrefix) {
      super(kind, parent, message, new OpenFileDescriptorImpl(project, vf, -1, -1), exportText, rendererTextPrefix);
      myVf = vf;
      myCustomizeColoredTreeCellRenderer = new CustomizeColoredTreeCellRenderer() {
        @Override
        public void customizeCellRenderer(SimpleColoredComponent renderer,
                                          JTree tree,
                                          Object value,
                                          boolean selected,
                                          boolean expanded,
                                          boolean leaf,
                                          int row,
                                          boolean hasFocus) {
          final Image icon = myVf.getFileType().getIcon();
          renderer.setIcon(icon);
          final String[] messages = getText();
          final String text = messages == null || messages.length == 0 ? vf.getPath() : messages[0];
          renderer.append(text);
        }
      };
    }

    @Override
    public Object getData() {
      return myVf;
    }

    @Override
    public CustomizeColoredTreeCellRenderer getLeftSelfRenderer() {
      return myCustomizeColoredTreeCellRenderer;
    }
  }
}
