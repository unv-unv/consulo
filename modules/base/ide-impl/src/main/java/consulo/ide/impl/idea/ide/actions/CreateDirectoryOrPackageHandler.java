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
package consulo.ide.impl.idea.ide.actions;

import consulo.application.Application;
import consulo.application.util.registry.Registry;
import consulo.ide.action.CreateElementActionBase;
import consulo.ide.action.CreateFileAction;
import consulo.ide.impl.actions.CreateDirectoryOrPackageType;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.ide.localize.IdeLocalize;
import consulo.language.file.FileTypeManager;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.PsiPackageManager;
import consulo.language.util.IncorrectOperationException;
import consulo.localHistory.LocalHistory;
import consulo.localHistory.LocalHistoryAction;
import consulo.localize.LocalizeValue;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.InputValidatorEx;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.image.Image;
import consulo.undoRedo.CommandProcessor;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.virtualFileSystem.fileType.UnknownFileType;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.awt.*;
import java.io.File;
import java.util.StringTokenizer;

public class CreateDirectoryOrPackageHandler implements InputValidatorEx {
    @Nullable
    private final Project myProject;
    @Nonnull
    private final PsiDirectory myDirectory;
    @Nonnull
    private final CreateDirectoryOrPackageType myType;
    @Nullable
    private PsiFileSystemItem myCreatedElement = null;
    @Nonnull
    private final String myDelimiters;
    @Nullable
    private final Component myDialogParent;
    private String myErrorText;

    public CreateDirectoryOrPackageHandler(
        @Nullable Project project,
        @Nonnull PsiDirectory directory,
        CreateDirectoryOrPackageType type,
        @Nonnull String delimiters
    ) {
        this(project, directory, type, delimiters, null);
    }

    public CreateDirectoryOrPackageHandler(
        @Nullable Project project,
        @Nonnull PsiDirectory directory,
        @Nonnull CreateDirectoryOrPackageType type,
        @Nonnull String delimiters,
        @Nullable Component dialogParent
    ) {
        myProject = project;
        myDirectory = directory;
        myType = type;
        myDelimiters = delimiters;
        myDialogParent = dialogParent;
    }

    @Override
    @RequiredUIAccess
    public boolean checkInput(String inputString) {
        StringTokenizer tokenizer = new StringTokenizer(inputString, myDelimiters);
        VirtualFile vFile = myDirectory.getVirtualFile();
        boolean firstToken = true;
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            if (!tokenizer.hasMoreTokens() && (token.equals(".") || token.equals(".."))) {
                myErrorText = "Can't create a directory with name '" + token + "'";
                return false;
            }
            if (vFile != null) {
                if (firstToken && "~".equals(token)) {
                    VirtualFile userHomeDir = VfsUtil.getUserHomeDir();
                    if (userHomeDir == null) {
                        myErrorText = "User home directory not found";
                        return false;
                    }
                    vFile = userHomeDir;
                }
                else if ("..".equals(token)) {
                    vFile = vFile.getParent();
                    if (vFile == null) {
                        myErrorText = "Not a valid directory";
                        return false;
                    }
                }
                else if (!".".equals(token)) {
                    VirtualFile child = vFile.findChild(token);
                    if (child != null) {
                        if (!child.isDirectory()) {
                            myErrorText = "A file with name '" + token + "' already exists";
                            return false;
                        }
                        else if (!tokenizer.hasMoreTokens()) {
                            myErrorText = "A directory with name '" + token + "' already exists";
                            return false;
                        }
                    }
                    vFile = child;
                }
            }

            boolean isDirectory = myType == CreateDirectoryOrPackageType.Directory;
            if (FileTypeManager.getInstance().isFileIgnored(token)) {
                myErrorText = "Trying to create a " + (isDirectory ? "directory" : "package") +
                    " with an ignored name, the result will not be visible";
                return true;
            }
            if (!isDirectory && token.length() > 0
                && !PsiPackageManager.getInstance(myDirectory.getProject()).isValidPackageName(myDirectory, token)) {
                myErrorText = "Not a valid package name, it will not be possible to create a class inside";
                return true;
            }
            firstToken = false;
        }
        myErrorText = null;
        return true;
    }

    @Override
    public String getErrorText(String inputString) {
        return myErrorText;
    }

    @Override
    @RequiredUIAccess
    public boolean canClose(String subDirName) {
        if (subDirName.isEmpty()) {
            showErrorDialog(IdeLocalize.errorNameShouldBeSpecified());
            return false;
        }

        boolean multiCreation = StringUtil.containsAnyChar(subDirName, myDelimiters);
        if (!multiCreation) {
            try {
                myDirectory.checkCreateSubdirectory(subDirName);
            }
            catch (IncorrectOperationException ex) {
                showErrorDialog(LocalizeValue.ofNullable(CreateElementActionBase.filterMessage(ex.getMessage())));
                return false;
            }
        }

        Boolean createFile = suggestCreatingFileInstead(subDirName);
        if (createFile == null) {
            return false;
        }

        doCreateElement(subDirName, createFile);

        return myCreatedElement != null;
    }

    @Nullable
    @RequiredUIAccess
    private Boolean suggestCreatingFileInstead(String subDirName) {
        boolean isDirectory = myType == CreateDirectoryOrPackageType.Directory;

        Boolean createFile = false;
        if (StringUtil.countChars(subDirName, '.') == 1 && Registry.is("ide.suggest.file.when.creating.filename.like.directory")) {
            FileType fileType = findFileTypeBoundToName(subDirName);
            if (fileType != null) {
                String message =
                    "The name you entered looks like a file name. Do you want to create a file named " + subDirName + " instead?";
                int ec = Messages.showYesNoCancelDialog(
                    myProject,
                    message,
                    LocalizeValue.localizeTODO("File Name Detected").get(),
                    LocalizeValue.localizeTODO("&Yes, create file").get(),
                    LocalizeValue.localizeTODO("&No, create " + (isDirectory ? "directory" : "packages")).get(),
                    CommonLocalize.buttonCancel().get(),
                    fileType.getIcon()
                );
                if (ec == Messages.CANCEL) {
                    createFile = null;
                }
                if (ec == Messages.YES) {
                    createFile = true;
                }
            }
        }
        return createFile;
    }

    @Nullable
    public static FileType findFileTypeBoundToName(String name) {
        FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(name);
        return fileType instanceof UnknownFileType ? null : fileType;
    }

    @RequiredUIAccess
    private void doCreateElement(String subDirName, boolean createFile) {
        boolean isDirectory = myType == CreateDirectoryOrPackageType.Directory;

        CommandProcessor.getInstance().newCommand()
            .project(myProject)
            .name(
                createFile
                    ? IdeLocalize.commandCreateFile()
                    : isDirectory
                    ? IdeLocalize.commandCreateDirectory()
                    : IdeLocalize.commandCreatePackage()
            )
            .inWriteAction()
            .run(() -> {
                String dirPath = myDirectory.getVirtualFile().getPresentableUrl();
                LocalizeValue actionName = IdeLocalize.progressCreatingDirectory(dirPath, File.separator, subDirName);
                LocalHistoryAction action = LocalHistory.getInstance().startAction(actionName);
                try {
                    if (createFile) {
                        CreateFileAction.MkDirs mkdirs = new CreateFileAction.MkDirs(subDirName, myDirectory);
                        myCreatedElement = myType.createDirectory(mkdirs.directory, mkdirs.newName);
                    }
                    else {
                        createDirectories(subDirName);
                    }
                }
                catch (IncorrectOperationException ex) {
                    Application.get().invokeLater(
                        () -> showErrorDialog(LocalizeValue.ofNullable(CreateElementActionBase.filterMessage(ex.getMessage())))
                    );
                }
                finally {
                    action.finish();
                }
            });
    }

    @RequiredUIAccess
    private void showErrorDialog(LocalizeValue message) {
        LocalizeValue title = CommonLocalize.titleError();
        Image icon = UIUtil.getErrorIcon();
        if (myDialogParent != null) {
            Messages.showMessageDialog(myDialogParent, message.get(), title.get(), icon);
        }
        else {
            Messages.showMessageDialog(myProject, message.get(), title.get(), icon);
        }
    }

    @RequiredUIAccess
    protected void createDirectories(String subDirName) {
        myCreatedElement = myType.createDirectory(myDirectory, subDirName);
    }

    @Nullable
    public PsiFileSystemItem getCreatedElement() {
        return myCreatedElement;
    }
}
