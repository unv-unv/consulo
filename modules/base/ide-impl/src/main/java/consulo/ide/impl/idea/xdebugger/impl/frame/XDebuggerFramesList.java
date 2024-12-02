/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package consulo.ide.impl.idea.xdebugger.impl.frame;

import consulo.annotation.access.RequiredReadAction;
import consulo.dataContext.DataProvider;
import consulo.execution.debug.XDebuggerBundle;
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.frame.XStackFrame;
import consulo.execution.debug.frame.XStackFrameWithSeparatorAbove;
import consulo.ide.impl.idea.util.ui.TextTransferable;
import consulo.ide.impl.psi.search.scope.NonProjectFilesScope;
import consulo.ide.setting.module.OrderEntryTypeEditor;
import consulo.language.content.ContentFoldersSupportUtil;
import consulo.language.editor.FileColorManager;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.layer.ContentFolder;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.ex.ColoredStringBuilder;
import consulo.ui.ex.ColoredTextContainer;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.awt.ColoredListCellRenderer;
import consulo.ui.ex.awt.JBCurrentTheme;
import consulo.ui.ex.awt.popup.GroupedItemsListRenderer;
import consulo.ui.ex.awt.popup.ListItemDescriptorAdapter;
import consulo.ui.image.Image;
import consulo.util.dataholder.Key;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author nik
 */
public class XDebuggerFramesList extends DebuggerFramesList {
  private final Project myProject;
  private final Map<VirtualFile, Color> myFileColors = new HashMap<>();

  private static final TransferHandler DEFAULT_TRANSFER_HANDLER = new TransferHandler() {
    @Override
    protected Transferable createTransferable(JComponent c) {
      if (!(c instanceof XDebuggerFramesList)) {
        return null;
      }
      XDebuggerFramesList list = (XDebuggerFramesList)c;
      //noinspection deprecation
      Object[] values = list.getSelectedValues();
      if (values == null || values.length == 0) {
        return null;
      }

      StringBuilder plainBuf = new StringBuilder();
      StringBuilder htmlBuf = new StringBuilder();
      ColoredStringBuilder coloredTextContainer = new ColoredStringBuilder();
      htmlBuf.append("<html>\n<body>\n<ul>\n");
      for (Object value : values) {
        htmlBuf.append("  <li>");
        if (value != null) {
          if (value instanceof XStackFrame) {
            ((XStackFrame)value).customizePresentation(coloredTextContainer);
            coloredTextContainer.appendTo(plainBuf, htmlBuf);
          }
          else {
            String text = value.toString();
            plainBuf.append(text);
            htmlBuf.append(text);
          }
        }
        plainBuf.append('\n');
        htmlBuf.append("</li>\n");
      }

      // remove the last newline
      plainBuf.setLength(plainBuf.length() - 1);
      htmlBuf.append("</ul>\n</body>\n</html>");
      return new TextTransferable(htmlBuf.toString(), plainBuf.toString());
    }

    @Override
    public int getSourceActions(@Nonnull JComponent c) {
      return COPY;
    }
  };

  private XStackFrame mySelectedFrame;

  public XDebuggerFramesList(@Nonnull Project project) {
    myProject = project;

    doInit();
    setTransferHandler(DEFAULT_TRANSFER_HANDLER);
    setDataProvider(new DataProvider() {
      @Nullable
      @Override
      @RequiredReadAction
      public Object getData(@Nonnull @NonNls Key dataId) {
        if (mySelectedFrame != null) {
          if (VirtualFile.KEY == dataId) {
            return getFile(mySelectedFrame);
          }
          else if (PsiFile.KEY == dataId) {
            VirtualFile file = getFile(mySelectedFrame);
            if (file != null && file.isValid()) {
              return PsiManager.getInstance(myProject).findFile(file);
            }
          }
        }
        return null;
      }
    });
  }

  @Override
  public void clear() {
    super.clear();
    myFileColors.clear();
  }

  @Nullable
  private static VirtualFile getFile(XStackFrame frame) {
    XSourcePosition position = frame.getSourcePosition();
    return position != null ? position.getFile() : null;
  }

  @Override
  protected ListCellRenderer createListRenderer() {
    return new XDebuggerGroupedFrameListRenderer();
  }

  @Override
  protected void onFrameChanged(final Object selectedValue) {
    if (mySelectedFrame != selectedValue) {
      SwingUtilities.invokeLater(() -> repaint());
      if (selectedValue instanceof XStackFrame) {
        mySelectedFrame = (XStackFrame)selectedValue;
      }
      else {
        mySelectedFrame = null;
      }
    }
  }

  private class XDebuggerGroupedFrameListRenderer extends GroupedItemsListRenderer<Object> {
    private final XDebuggerFrameListRenderer myOriginalRenderer = new XDebuggerFrameListRenderer(myProject);

    public XDebuggerGroupedFrameListRenderer() {
      super(new ListItemDescriptorAdapter<>() {
        @Nullable
        @Override
        public String getTextFor(Object value) {
          return null;
        }

        @Override
        public boolean hasSeparatorAboveOf(Object value) {
          return value instanceof XStackFrameWithSeparatorAbove frameWithSeparatorAbove && frameWithSeparatorAbove.hasSeparatorAbove();
        }
      });
    }

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      if (myDescriptor.hasSeparatorAboveOf(value)) {
        Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        ((XDebuggerFrameListRenderer)myComponent).getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        return component;
      }
      else {
        return myOriginalRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      }
    }

    @Override
    protected JComponent createItemComponent() {
      createLabel();
      return new XDebuggerFrameListRenderer(myProject);
    }
  }

  private class XDebuggerFrameListRenderer extends ColoredListCellRenderer {
    private final FileColorManager myColorsManager;

    public XDebuggerFrameListRenderer(@Nonnull Project project) {
      myColorsManager = FileColorManager.getInstance(project);
    }

    @Override
    protected void customizeCellRenderer(@Nonnull final JList list,
                                         final Object value,
                                         final int index,
                                         final boolean selected,
                                         final boolean hasFocus) {
      if (value == null) {
        append(XDebuggerBundle.message("stack.frame.loading.text"), SimpleTextAttributes.GRAY_ATTRIBUTES);
        return;
      }

      if (value instanceof String) {
        append((String)value, SimpleTextAttributes.ERROR_ATTRIBUTES);
        return;
      }

      XStackFrame stackFrame = (XStackFrame)value;
      if (!selected) {
        Color c = getFrameBgColor(stackFrame);
        if (c != null) {
          setBackground(c);
        }
      }
      stackFrame.customizePresentation(this);
      
      // override icon - fully ignored, from provider
      setIcon(getSourceIcon(stackFrame));
    }

    @SuppressWarnings("unchecked")
    Image getSourceIcon(XStackFrame stackFrame) {
      VirtualFile virtualFile = getFile(stackFrame);
      if (virtualFile == null) {
        return PlatformIconGroup.actionsHelp();
      }

      ProjectFileIndex index = ProjectFileIndex.getInstance(myProject);
      if (index.isInLibrary(virtualFile)) {
        List<OrderEntry> entries = index.getOrderEntriesForFile(virtualFile);
        for (OrderEntry entry : entries) {
          OrderEntryTypeEditor<OrderEntry> editor = OrderEntryTypeEditor.getEditor(entry.getType().getId());

          Consumer<ColoredTextContainer> render = editor.getRender(entry);
          ColoredStringBuilder builder = new ColoredStringBuilder();
          render.accept(builder);
          Image icon = builder.getIcon();
          if (icon != null) {
            return icon;
          }
        }
      }
      else {
        ContentFolder contentFolder = index.getContentFolder(virtualFile);
        if (contentFolder != null) {
          return ContentFoldersSupportUtil.getContentFolderIcon(contentFolder.getType(), contentFolder.getProperties());
        }
      }

      return PlatformIconGroup.nodesFolder();
    }

    Color getFrameBgColor(XStackFrame stackFrame) {
      VirtualFile virtualFile = getFile(stackFrame);
      if (virtualFile != null) {
        // handle null value
        if (myFileColors.containsKey(virtualFile)) {
          return myFileColors.get(virtualFile);
        }
        else if (virtualFile.isValid()) {
          Color color = myColorsManager.getFileColor(virtualFile);
          myFileColors.put(virtualFile, color);
          return color;
        }
      }
      else {
        return myColorsManager.getScopeColor(NonProjectFilesScope.NAME);
      }
      return null;
    }
  }
}
