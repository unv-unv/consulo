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
package consulo.ide.impl.idea.refactoring.rename;

import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.dataContext.DataContext;
import consulo.language.editor.refactoring.TitledHandler;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.rename.PsiElementRenameHandler;
import consulo.language.editor.refactoring.rename.RenameDialog;
import consulo.language.editor.refactoring.rename.RenameHandler;
import consulo.language.editor.refactoring.rename.RenameUtil;
import consulo.language.impl.psi.PsiPackageBase;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiDirectoryContainer;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.ModuleUtilCore;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

/**
 * @author yole
 */
public abstract class DirectoryAsPackageRenameHandlerBase<T extends PsiDirectoryContainer> implements RenameHandler, TitledHandler {
    private static final Logger LOG = Logger.getInstance(DirectoryAsPackageRenameHandlerBase.class);

    protected abstract VirtualFile[] occursInPackagePrefixes(T aPackage);

    protected abstract boolean isIdentifier(String name, Project project);

    protected abstract String getQualifiedName(T aPackage);

    @Nullable
    protected abstract T getPackage(PsiDirectory psiDirectory);

    protected abstract BaseRefactoringProcessor createProcessor(
        final String newQName,
        final Project project,
        final PsiDirectory[] dirsToRename,
        boolean searchInComments,
        boolean searchInNonJavaFiles
    );

    @Override
    public boolean isAvailableOnDataContext(final DataContext dataContext) {
        PsiElement element = adjustForRename(dataContext, PsiElementRenameHandler.getElement(dataContext));
        if (element instanceof PsiDirectory directory) {
            final VirtualFile virtualFile = directory.getVirtualFile();
            final Project project = element.getProject();
            if (Comparing.equal(project.getBaseDir(), virtualFile)) {
                return false;
            }
            if (ProjectRootManager.getInstance(project).getFileIndex().isInContent(virtualFile)) {
                return true;
            }
        }
        return false;
    }

    private static PsiElement adjustForRename(DataContext dataContext, PsiElement element) {
        if (element instanceof PsiDirectoryContainer directoryContainer) {
            Module module = dataContext.getData(Module.KEY);
            if (module != null) {
                final PsiDirectory[] directories = directoryContainer.getDirectories(GlobalSearchScope.moduleScope(module));
                if (directories.length >= 1) {
                    element = directories[0];
                }
            }
        }
        return element;
    }

    @Override
    public boolean isRenaming(final DataContext dataContext) {
        return isAvailableOnDataContext(dataContext);
    }

    @Override
    public void invoke(@Nonnull final Project project, final Editor editor, final PsiFile file, final DataContext dataContext) {
        PsiElement element = adjustForRename(dataContext, PsiElementRenameHandler.getElement(dataContext));
        editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        final PsiElement nameSuggestionContext = file.findElementAt(editor.getCaretModel().getOffset());
        doRename(element, project, nameSuggestionContext, editor);
    }

    @Override
    public void invoke(@Nonnull final Project project, @Nonnull final PsiElement[] elements, final DataContext dataContext) {
        PsiElement element = elements.length == 1 ? elements[0] : null;
        if (element == null) {
            element = PsiElementRenameHandler.getElement(dataContext);
        }
        final PsiElement nameSuggestionContext = element;
        element = adjustForRename(dataContext, element);
        LOG.assertTrue(element != null);
        Editor editor = dataContext.getData(Editor.KEY);
        doRename(element, project, nameSuggestionContext, editor);
    }

    @NonNls
    private void doRename(PsiElement element, final Project project, PsiElement nameSuggestionContext, Editor editor) {
        final PsiDirectory psiDirectory = (PsiDirectory) element;
        final T aPackage = getPackage(psiDirectory);
        final String qualifiedName = aPackage != null ? getQualifiedName(aPackage) : "";
        if (aPackage == null || qualifiedName.length() == 0/*default package*/ ||
            !isIdentifier(psiDirectory.getName(), project)) {
            PsiElementRenameHandler.rename(element, project, nameSuggestionContext, editor);
        }
        else {
            PsiDirectory[] directories = aPackage.getDirectories();
            final VirtualFile[] virtualFiles = occursInPackagePrefixes(aPackage);
            if (virtualFiles.length == 0 && directories.length == 1) {
                PsiElementRenameHandler.rename(aPackage, project, nameSuggestionContext, editor);
            }
            else { // the directory corresponds to a package that has multiple associated directories
                final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
                boolean inLib = false;
                for (PsiDirectory directory : directories) {
                    inLib |= !projectFileIndex.isInContent(directory.getVirtualFile());
                }

                final PsiDirectory[] projectDirectories = aPackage.getDirectories(GlobalSearchScope.projectScope(project));
                if (inLib) {
                    final Module module = ModuleUtilCore.findModuleForPsiElement(psiDirectory);
                    LOG.assertTrue(module != null);
                    PsiDirectory[] moduleDirs = null;
                    if (nameSuggestionContext instanceof PsiPackageBase) {
                        moduleDirs = aPackage.getDirectories(GlobalSearchScope.moduleScope(module));
                        if (moduleDirs.length <= 1) {
                            moduleDirs = null;
                        }
                    }
                    final String promptMessage = "Package \'" + aPackage.getName() +
                        "\' contains directories in libraries which cannot be renamed. Do you want to rename " +
                        (moduleDirs == null ? "current directory" : "current module directories");
                    if (projectDirectories.length > 0) {
                        int ret = Messages.showYesNoCancelDialog(
                            project,
                            promptMessage + " or all directories in project?",
                            RefactoringLocalize.warningTitle().get(),
                            RefactoringLocalize.renameCurrentDirectory().get(),
                            RefactoringLocalize.renameDirectories().get(),
                            CommonLocalize.buttonCancel().get(),
                            UIUtil.getWarningIcon()
                        );
                        if (ret == 2) {
                            return;
                        }
                        renameDirs(project, nameSuggestionContext, editor, psiDirectory, aPackage,
                            ret == 0 ? (moduleDirs == null ? new PsiDirectory[]{psiDirectory} : moduleDirs) : projectDirectories);
                    }
                    else {
                        if (Messages.showOkCancelDialog(
                            project,
                            promptMessage + "?",
                            RefactoringLocalize.warningTitle().get(),
                            UIUtil.getWarningIcon()
                        ) == DialogWrapper.OK_EXIT_CODE) {
                            renameDirs(project, nameSuggestionContext, editor, psiDirectory, aPackage, psiDirectory);
                        }
                    }
                }
                else {
                    final StringBuffer message = new StringBuffer();
                    RenameUtil.buildPackagePrefixChangedMessage(virtualFiles, message, qualifiedName);
                    buildMultipleDirectoriesInPackageMessage(message, getQualifiedName(aPackage), directories);
                    message.append(
                        RefactoringLocalize.directoriesAndAllReferencesToPackageWillBeRenamed(psiDirectory.getVirtualFile().getPresentableUrl()).get()
                    );
                    int ret = Messages.showYesNoCancelDialog(
                        project,
                        message.toString(),
                        RefactoringLocalize.warningTitle().get(),
                        RefactoringLocalize.renamePackageButtonText().get(),
                        RefactoringLocalize.renameDirectoryButtonText().get(),
                        CommonLocalize.buttonCancel().get(),
                        UIUtil.getWarningIcon()
                    );
                    if (ret == 0) {
                        PsiElementRenameHandler.rename(aPackage, project, nameSuggestionContext, editor);
                    }
                    else if (ret == 1) {
                        renameDirs(project, nameSuggestionContext, editor, psiDirectory, aPackage, psiDirectory);
                    }
                }
            }
        }
    }

    private void renameDirs(
        final Project project,
        final PsiElement nameSuggestionContext,
        final Editor editor,
        final PsiDirectory contextDirectory,
        final T aPackage,
        final PsiDirectory... dirsToRename
    ) {
        final RenameDialog dialog = new RenameDialog(project, contextDirectory, nameSuggestionContext, editor) {
            @Override
            protected void doAction() {
                String newQName = StringUtil.getQualifiedName(StringUtil.getPackageName(getQualifiedName(aPackage)), getNewName());
                BaseRefactoringProcessor moveProcessor = createProcessor(newQName, project, dirsToRename, isSearchInComments(),
                    isSearchInNonJavaFiles());
                invokeRefactoring(moveProcessor);
            }
        };
        dialog.show();
    }

    public static void buildMultipleDirectoriesInPackageMessage(
        StringBuffer message,
        String packageQname,
        PsiDirectory[] directories
    ) {
        message.append(RefactoringLocalize.multipleDirectoriesCorrespondToPackage().get());
        message.append(packageQname);
        message.append(":\n\n");
        for (int i = 0; i < directories.length; i++) {
            PsiDirectory directory = directories[i];
            if (i > 0) {
                message.append("\n");
            }
            message.append(directory.getVirtualFile().getPresentableUrl());
        }
    }

    @Nonnull
    @Override
    public LocalizeValue getActionTitleValue() {
        return RefactoringLocalize.renameDirectoryTitle();
    }
}
