/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/**
 * @author cdr
 */
package consulo.ide.impl.idea.ide.actions;

import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.dumb.DumbAware;
import consulo.application.impl.internal.store.IApplicationStore;
import consulo.container.boot.ContainerPathManager;
import consulo.ide.impl.idea.ide.startup.StartupActionScriptManager;
import consulo.ide.impl.idea.openapi.util.io.FileUtil;
import consulo.ide.impl.idea.openapi.util.io.FileUtilRt;
import consulo.ide.impl.idea.util.io.ZipUtil;
import consulo.externalService.impl.internal.update.UpdateSettingsImpl;
import consulo.localize.LocalizeValue;
import consulo.platform.base.localize.IdeLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIExAWTDataKey;
import consulo.util.collection.MultiMap;
import jakarta.annotation.Nonnull;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public class ImportSettingsAction extends AnAction implements DumbAware {
  private final Application myApplication;
  private final IApplicationStore myApplicationStore;

  public ImportSettingsAction(Application application, IApplicationStore applicationStore) {
    myApplication = application;
    myApplicationStore = applicationStore;
  }

  @RequiredUIAccess
  @Override
  public void actionPerformed(@Nonnull AnActionEvent e) {
    final Component component = e.getData(UIExAWTDataKey.CONTEXT_COMPONENT);
    ChooseComponentsToExportDialog.chooseSettingsFile(
      ContainerPathManager.get().getConfigPath(),
      component,
      IdeLocalize.titleImportFileLocation().get(),
      IdeLocalize.promptChooseImportFilePath().get()
    ).doWhenDone(this::doImport);
  }

  private void doImport(String path) {
    final File saveFile = new File(path);
    try {
      if (!saveFile.exists()) {
        Messages.showErrorDialog(
          IdeLocalize.errorCannotFindFile(presentableFileName(saveFile)).get(),
          IdeLocalize.titleFileNotFound().get()
        );
        return;
      }

      ZipEntry magicEntry;
      try (ZipFile zipFile = new ZipFile(saveFile)) {
        magicEntry = zipFile.getEntry(ImportSettingsFilenameFilter.SETTINGS_ZIP_MARKER);
      }

      if (magicEntry == null) {
        String fileName = presentableFileName(saveFile);
        Messages.showErrorDialog(
          IdeLocalize.errorFileContainsNoSettingsToImport(fileName, promptLocationMessage()).get(),
          IdeLocalize.titleInvalidFile().get()
        );
        return;
      }

      MultiMap<File, ExportSettingsAction.ExportableItem> fileToComponents =
        ExportSettingsAction.getExportableComponentsMap(myApplication, myApplicationStore, false);
      List<ExportSettingsAction.ExportableItem> components = getComponentsStored(saveFile, fileToComponents.values());
      fileToComponents.values().retainAll(components);
      final ChooseComponentsToExportDialog dialog = new ChooseComponentsToExportDialog(
        fileToComponents,
        false,
        IdeLocalize.titleSelectComponentsToImport().get(),
        IdeLocalize.promptCheckComponentsToImport().get()
      );
      if (!dialog.showAndGet()) {
        return;
      }

      final Set<ExportSettingsAction.ExportableItem> chosenComponents = dialog.getExportableComponents();
      Set<String> relativeNamesToExtract = new HashSet<>();
      for (ExportSettingsAction.ExportableItem chosenComponent : chosenComponents) {
        final File[] exportFiles = chosenComponent.getExportFiles();
        for (File exportFile : exportFiles) {
          final File configPath = new File(ContainerPathManager.get().getConfigPath());
          final String rPath = FileUtil.getRelativePath(configPath, exportFile);
          assert rPath != null;
          final String relativePath = FileUtil.toSystemIndependentName(rPath);
          relativeNamesToExtract.add(relativePath);
        }
      }

      relativeNamesToExtract.add(ExportSettingsAction.INSTALLED_TXT);

      final File tempFile = new File(ContainerPathManager.get().getPluginTempPath() + "/" + saveFile.getName());
      FileUtil.copy(saveFile, tempFile);
      File outDir = new File(ContainerPathManager.get().getConfigPath());
      final ImportSettingsFilenameFilter filenameFilter = new ImportSettingsFilenameFilter(relativeNamesToExtract);
      StartupActionScriptManager.ActionCommand unzip = new StartupActionScriptManager.UnzipCommand(tempFile, outDir, filenameFilter);

      StartupActionScriptManager.addActionCommand(unzip);
      // remove temp file
      StartupActionScriptManager.ActionCommand deleteTemp = new StartupActionScriptManager.DeleteCommand(tempFile);
      StartupActionScriptManager.addActionCommand(deleteTemp);

      UpdateSettingsImpl.getInstance().setLastTimeCheck(0);

      LocalizeValue applicationName = Application.get().getName();
      LocalizeValue message = ApplicationManager.getApplication().isRestartCapable()
        ? IdeLocalize.messageSettingsImportedSuccessfullyRestart(applicationName, applicationName)
        : IdeLocalize.messageSettingsImportedSuccessfully(applicationName, applicationName);
      final int ret = Messages.showOkCancelDialog(
        message.get(),
        IdeLocalize.titleRestartNeeded().get(),
        Messages.getQuestionIcon()
      );
      if (ret == Messages.OK) {
        ApplicationManager.getApplication().restart(true);
      }
    }
    catch (ZipException e1) {
      String fileName = presentableFileName(saveFile);
      Messages.showErrorDialog(
        IdeLocalize.errorReadingSettingsFile(fileName, e1.getMessage(), promptLocationMessage()).get(),
        IdeLocalize.titleInvalidFile().get()
      );
    }
    catch (IOException e1) {
      Messages.showErrorDialog(
        IdeLocalize.errorReadingSettingsFile2(presentableFileName(saveFile), e1.getMessage()).get(),
        IdeLocalize.titleErrorReadingFile().get()
      );
    }
  }

  private static String presentableFileName(final File file) {
    return "'" + FileUtil.toSystemDependentName(file.getPath()) + "'";
  }

  private static String promptLocationMessage() {
    return IdeLocalize.messagePleaseEnsureCorrectSettings().get();
  }

  @Nonnull
  private static List<ExportSettingsAction.ExportableItem> getComponentsStored(
    @Nonnull File zipFile,
    @Nonnull Collection<? extends ExportSettingsAction.ExportableItem> registeredComponents
  ) throws IOException {
    File configPath = new File(ContainerPathManager.get().getConfigPath());
    List<ExportSettingsAction.ExportableItem> components = new ArrayList<>();
    for (ExportSettingsAction.ExportableItem component : registeredComponents) {
      for (File exportFile : component.getExportFiles()) {
        String rPath = FileUtilRt.getRelativePath(configPath, exportFile);
        assert rPath != null;
        String relativePath = FileUtilRt.toSystemIndependentName(rPath);
        if (exportFile.isDirectory()) {
          relativePath += '/';
        }
        if (ZipUtil.isZipContainsEntry(zipFile, relativePath)) {
          components.add(component);
          break;
        }
      }
    }
    return components;
  }
}
