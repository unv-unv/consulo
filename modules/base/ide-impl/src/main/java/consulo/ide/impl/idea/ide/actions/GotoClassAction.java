// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.ide.impl.idea.ide.actions;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ActionImpl;
import consulo.application.Application;
import consulo.application.dumb.DumbAware;
import consulo.application.util.matcher.MinusculeMatcher;
import consulo.application.util.matcher.NameUtil;
import consulo.dataContext.DataContext;
import consulo.disposer.Disposer;
import consulo.externalService.statistic.FeatureUsageTracker;
import consulo.fileEditor.FileEditor;
import consulo.fileEditor.FileEditorManager;
import consulo.fileEditor.impl.internal.OpenFileDescriptorImpl;
import consulo.fileEditor.structureView.StructureView;
import consulo.fileEditor.structureView.StructureViewBuilder;
import consulo.fileEditor.structureView.StructureViewTreeElement;
import consulo.fileEditor.structureView.tree.TreeElement;
import consulo.ide.impl.idea.ide.actions.searcheverywhere.ClassSearchEverywhereContributor;
import consulo.ide.impl.idea.ide.util.gotoByName.*;
import consulo.ide.impl.idea.openapi.ui.playback.commands.ActionCommand;
import consulo.ide.navigation.GotoClassOrTypeContributor;
import consulo.language.Language;
import consulo.language.editor.structureView.PsiStructureViewFactory;
import consulo.language.editor.ui.PopupNavigationUtil;
import consulo.language.navigation.AnonymousElementProvider;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.util.EditSourceUtil;
import consulo.localize.LocalizeValue;
import consulo.navigation.Navigatable;
import consulo.navigation.NavigationItem;
import consulo.ide.localize.IdeLocalize;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.ui.ex.awt.UIExAWTDataKey;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.awt.*;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.List;

@ActionImpl(id = "GotoClass")
public class GotoClassAction extends GotoActionBase implements DumbAware {
    public GotoClassAction() {
        //we need to change the template presentation to show the proper text for the action in Settings | Keymap
        super(
            LocalizeValue.localizeTODO(GotoClassPresentationUpdater.getActionTitle() + "..."),
            IdeLocalize.goToClassActionDescription(StringUtil.join(
                GotoClassPresentationUpdater.getElementKinds(),
                "/"
            ))
        );
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(@Nonnull AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project == null) {
            return;
        }

        boolean dumb = DumbService.isDumb(project);
        if (!dumb || new ClassSearchEverywhereContributor(project, null).isDumbAware()) {
            showInSearchEverywherePopup(ClassSearchEverywhereContributor.class.getSimpleName(), e, true, true);
        }
        else {
            invokeGoToFile(project, e);
        }
    }

    static void invokeGoToFile(@Nonnull Project project, @Nonnull AnActionEvent e) {
        String actionTitle =
            StringUtil.trimEnd(ObjectUtil.notNull(e.getPresentation().getText(), GotoClassPresentationUpdater.getActionTitle()), "...");
        DumbService.getInstance(project).showDumbModeNotification(IdeLocalize.goToClassDumbModeMessage(actionTitle));
        AnAction action = ActionManager.getInstance().getAction(GotoFileAction.ID);
        InputEvent event = ActionCommand.getInputEvent(GotoFileAction.ID);
        Component component = e.getData(UIExAWTDataKey.CONTEXT_COMPONENT);
        ActionManager.getInstance().tryToExecute(action, event, component, e.getPlace(), true);
    }

    @Override
    public void gotoActionPerformed(@Nonnull AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project == null) {
            return;
        }

        FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.popup.class");

        PsiDocumentManager.getInstance(project).commitAllDocuments();

        GotoClassModel2 model = new GotoClassModel2(project);
        String pluralKinds =
            StringUtil.capitalize(StringUtil.join(GotoClassPresentationUpdater.getElementKinds(), StringUtil::pluralize, "/"));
        LocalizeValue title = IdeLocalize.goToClassToolwindowTitle(pluralKinds);
        showNavigationPopup(
            e,
            model,
            new GotoActionCallback<Language>() {
                @Override
                protected ChooseByNameFilter<Language> createFilter(@Nonnull ChooseByNamePopup popup) {
                    return new ChooseByNameLanguageFilter(popup, model, GotoClassSymbolConfiguration.getInstance(project), project);
                }

                @Override
                @RequiredReadAction
                public void elementChosen(ChooseByNamePopup popup, Object element) {
                    handleSubMemberNavigation(popup, element);
                }
            },
            title.get(),
            true
        );
    }

    @RequiredReadAction
    static void handleSubMemberNavigation(ChooseByNamePopup popup, Object element) {
        if (element instanceof PsiElement psiElement0 && psiElement0.isValid()) {
            PsiElement psiElement = getElement(psiElement0, popup);
            psiElement = psiElement.getNavigationElement();
            VirtualFile file = PsiUtilCore.getVirtualFile(psiElement);

            if (file != null && popup.getLinePosition() != -1) {
                OpenFileDescriptorImpl descriptor =
                    new OpenFileDescriptorImpl(psiElement.getProject(), file, popup.getLinePosition(), popup.getColumnPosition());
                Navigatable n = descriptor.setUseCurrentWindow(popup.isOpenInCurrentWindowRequested());
                if (n.canNavigate()) {
                    n.navigate(true);
                    return;
                }
            }

            if (file != null && popup.getMemberPattern() != null) {
                PopupNavigationUtil.activateFileWithPsiElement(psiElement, !popup.isOpenInCurrentWindowRequested());
                Navigatable member = findMember(popup.getMemberPattern(), popup.getTrimmedText(), psiElement, file);
                if (member != null) {
                    member.navigate(true);
                }
            }

            PopupNavigationUtil.activateFileWithPsiElement(psiElement, !popup.isOpenInCurrentWindowRequested());
        }
        else {
            EditSourceUtil.navigate(((NavigationItem) element), true, popup.isOpenInCurrentWindowRequested());
        }
    }

    @Nullable
    @RequiredReadAction
    public static Navigatable findMember(String memberPattern, String fullPattern, PsiElement psiElement, VirtualFile file) {
        StructureViewBuilder builder = PsiStructureViewFactory.createBuilderForFile(psiElement.getContainingFile());
        FileEditor[] editors = FileEditorManager.getInstance(psiElement.getProject()).getEditors(file);
        if (builder == null || editors.length == 0) {
            return null;
        }

        StructureView view = builder.createStructureView(editors[0], psiElement.getProject());
        try {
            StructureViewTreeElement element = findElement(view.getTreeModel().getRoot(), psiElement, 4);
            if (element == null) {
                return null;
            }

            MinusculeMatcher matcher = NameUtil.buildMatcher(memberPattern).build();
            int max = Integer.MIN_VALUE;
            Object target = null;
            for (TreeElement treeElement : element.getChildren()) {
                if (treeElement instanceof StructureViewTreeElement structureViewTreeElement) {
                    Object value = structureViewTreeElement.getValue();
                    if (value instanceof PsiElement valueElement && value instanceof Navigatable valueNavigatable
                        && fullPattern.equals(CopyReferenceAction.elementToFqn(valueElement))) {
                        return valueNavigatable;
                    }

                    String presentableText = structureViewTreeElement.getPresentation().getPresentableText();
                    if (presentableText != null) {
                        int degree = matcher.matchingDegree(presentableText);
                        if (degree > max) {
                            max = degree;
                            target = structureViewTreeElement.getValue();
                        }
                    }
                }
            }
            return target instanceof Navigatable navigatable ? navigatable : null;
        }
        finally {
            Disposer.dispose(view);
        }
    }

    @Nullable
    private static StructureViewTreeElement findElement(StructureViewTreeElement node, PsiElement element, int hopes) {
        Object value = node.getValue();
        if (value instanceof PsiElement psiElement) {
            if (psiElement.isEquivalentTo(element)) {
                return node;
            }
            if (hopes != 0) {
                for (TreeElement child : node.getChildren()) {
                    if (child instanceof StructureViewTreeElement treeElement) {
                        StructureViewTreeElement e = findElement(treeElement, element, hopes - 1);
                        if (e != null) {
                            return e;
                        }
                    }
                }
            }
        }
        return null;
    }

    @Nonnull
    private static PsiElement getElement(@Nonnull PsiElement element, ChooseByNamePopup popup) {
        String path = popup.getPathToAnonymous();
        if (path != null) {
            return getElement(element, path);
        }
        return element;
    }

    @Nonnull
    public static PsiElement getElement(@Nonnull PsiElement element, @Nonnull String path) {
        String[] classes = path.split("\\$");
        List<Integer> indexes = new ArrayList<>();
        for (String cls : classes) {
            if (cls.isEmpty()) {
                continue;
            }
            try {
                indexes.add(Integer.parseInt(cls) - 1);
            }
            catch (Exception e) {
                return element;
            }
        }
        PsiElement current = element;
        for (int index : indexes) {
            PsiElement[] anonymousClasses = getAnonymousClasses(current);
            if (index >= 0 && index < anonymousClasses.length) {
                current = anonymousClasses[index];
            }
            else {
                return current;
            }
        }
        return current;
    }

    @Nonnull
    private static PsiElement[] getAnonymousClasses(@Nonnull PsiElement element) {
        return element.getApplication().getExtensionPoint(AnonymousElementProvider.class).computeSafeIfAny(
            provider -> {
                PsiElement[] elems = provider.getAnonymousElements(element);
                return elems != null && elems.length > 0 ? elems : null;
            },
            PsiElement.EMPTY_ARRAY
        );
    }

    @Override
    protected boolean hasContributors(DataContext dataContext) {
        return Application.get().getExtensionPoint(GotoClassOrTypeContributor.class).hasAnyExtensions();
    }
}
