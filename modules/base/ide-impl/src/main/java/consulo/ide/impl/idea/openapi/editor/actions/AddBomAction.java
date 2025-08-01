// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.ide.impl.idea.openapi.editor.actions;

import consulo.application.WriteAction;
import consulo.application.dumb.DumbAware;
import consulo.ide.impl.idea.util.ArrayUtil;
import consulo.ide.localize.IdeLocalize;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.util.io.CharsetToolkit;
import consulo.virtualFileSystem.NewVirtualFile;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Adds <a href="http://unicode.org/faq/utf_bom.html">file's BOM</a> to files with UTF-XXX encoding.
 */
public class AddBomAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(AddBomAction.class);

  public AddBomAction() {
    super(IdeLocalize.addBom());
  }

  @Override
  public void update(@Nonnull AnActionEvent e) {
    VirtualFile file = e.getData(VirtualFile.KEY);
    boolean enabled = file != null && file.getBOM() == null && CharsetToolkit.getPossibleBom(file.getCharset()) != null; // support adding BOM to a single file only for the time being

    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(enabled || ActionPlaces.isMainMenuOrActionSearch(e.getPlace()));
    e.getPresentation().setDescriptionValue(IdeLocalize.addByteOrderMarkTo(enabled ? file.getName() : LocalizeValue.localizeTODO("<N/A>")));
  }

  @Override
  @RequiredUIAccess
  public void actionPerformed(@Nonnull AnActionEvent e) {
    VirtualFile file = e.getRequiredData(VirtualFile.KEY);
    doAddBOM(file);
  }

  private static void doAddBOM(@Nonnull VirtualFile virtualFile) {
    byte[] bom = virtualFile.getBOM();
    if (bom != null) return;
    Charset charset = virtualFile.getCharset();
    byte[] possibleBom = CharsetToolkit.getPossibleBom(charset);
    if (possibleBom == null) return;

    virtualFile.setBOM(possibleBom);
    NewVirtualFile file = (NewVirtualFile)virtualFile;
    try {
      byte[] bytes = file.contentsToByteArray();
      byte[] contentWithAddedBom = ArrayUtil.mergeArrays(possibleBom, bytes);
      WriteAction.runAndWait(() -> {
        try {
          file.setBinaryContent(contentWithAddedBom);
        }
        catch (IOException e) {
          LOG.warn("Unexpected exception occurred in file " + file, e);
        }
      });
    }
    catch (IOException ex) {
      LOG.warn("Unexpected exception occurred in file " + file, ex);
    }
  }
}
