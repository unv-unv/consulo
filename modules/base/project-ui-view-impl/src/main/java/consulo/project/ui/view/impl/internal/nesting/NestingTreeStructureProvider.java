// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package consulo.project.ui.view.impl.internal.nesting;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.language.psi.PsiFile;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.ui.view.internal.ProjectViewSharedSettings;
import consulo.project.ui.view.tree.*;
import consulo.util.collection.MultiMap;
import consulo.util.collection.SmartList;
import consulo.util.lang.Couple;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

import java.util.*;
import java.util.function.Function;

/**
 * {@code NestingTreeStructureProvider} moves some files in the Project View to be shown as children of another peer file. Standard use
 * case is to improve folder contents presentation when it contains both source file and its compiled output. For example generated
 * {@code foo.min.js} file will be shown as a child of {@code foo.js} file.<br/>
 * Nesting logic is based on file names only. Rules about files that should be nested are provided by
 * {@code com.intellij.projectViewNestingRulesProvider} extensions.
 *
 * @see ProjectViewNestingRulesProvider
 * @see ProjectViewFileNestingService
 * @see FileNestingInProjectViewDialog
 */
@ExtensionImpl(order = "last")
public final class NestingTreeStructureProvider implements TreeStructureProvider, DumbAware {
    private static final Logger LOG = Logger.getInstance(NestingTreeStructureProvider.class);

    @Override
    public @Nonnull Collection<AbstractTreeNode> modify(@Nonnull AbstractTreeNode parent,
                                                        @Nonnull Collection<AbstractTreeNode> children,
                                                        ViewSettings settings) {
        if (!(settings instanceof ProjectViewSettings)
            || !settings.getViewOption(ShowNestedProjectViewPaneOptionProvider.SHOW_NESTED_FILES_KEY)) {
            return children;
        }

        ProjectViewNode<?> parentNode = parent instanceof ProjectViewNode ? (ProjectViewNode) parent : null;
        VirtualFile virtualFile = parentNode == null ? null : parentNode.getVirtualFile();
        if (virtualFile == null || !virtualFile.isDirectory()) {
            return children;
        }

        final ArrayList<PsiFileNode> childNodes = new ArrayList<>();
        for (AbstractTreeNode<?> node : children) {
            if (!(node instanceof PsiFileNode)) {
                continue;
            }
            childNodes.add((PsiFileNode) node);
        }

        Function<PsiFileNode, String> fileNameFunc = psiFileNode -> {
            final PsiFile file = psiFileNode.getValue();
            if (file == null) {
                return null;
            }
            return file.getName();
        };
        FileNestingBuilder fileNestingBuilder = FileNestingBuilder.getInstance();
        final MultiMap<PsiFileNode, PsiFileNode> parentToChildren = fileNestingBuilder.mapParentToChildren(childNodes, fileNameFunc);
        if (parentToChildren.isEmpty()) {
            return children;
        }

        // initial ArrayList size may be not exact, not a big problem
        final Collection<AbstractTreeNode> newChildren = new ArrayList<>(children.size() - parentToChildren.size());

        final Set<PsiFileNode> childrenToMoveDown = new HashSet<>(parentToChildren.values());

        for (AbstractTreeNode<?> node : children) {
            if (!(node instanceof PsiFileNode)) {
                newChildren.add(node);
                continue;
            }

            if (childrenToMoveDown.contains(node)) {
                continue;
            }

            final Collection<PsiFileNode> childrenOfThisFile = parentToChildren.get((PsiFileNode) node);
            if (childrenOfThisFile.isEmpty()) {
                newChildren.add(node);
                continue;
            }

            newChildren.add(new NestingTreeNode((PsiFileNode) node, childrenOfThisFile));
        }

        return newChildren;
    }

    // Algorithm is similar to calcParentToChildren(), but a bit simpler, because we have one specific parentFile.
    public static Collection<ChildFileInfo> getFilesShownAsChildrenInProjectView(@Nonnull Project project,
                                                                                 @Nonnull VirtualFile parentFile) {
        LOG.assertTrue(!parentFile.isDirectory());

        if (!project
            .getApplication()
            .getInstance(ProjectViewSharedSettings.class)
            .getViewOption(ShowNestedProjectViewPaneOptionProvider.SHOW_NESTED_FILES_KEY)) {
            return Collections.emptyList();
        }

        final Collection<ProjectViewFileNestingService.NestingRule> rules = FileNestingBuilder.getInstance().getNestingRules();
        if (rules.isEmpty()) {
            return Collections.emptyList();
        }

        final Collection<ProjectViewFileNestingService.NestingRule> rulesWhereItCanBeParent = filterRules(rules, parentFile.getName(), true);
        if (rulesWhereItCanBeParent.isEmpty()) {
            return Collections.emptyList();
        }
        final Collection<ProjectViewFileNestingService.NestingRule> rulesWhereItCanBeChild = filterRules(rules, parentFile.getName(), false);

        final VirtualFile dir = parentFile.getParent();
        if (dir == null) {
            return Collections.emptyList();
        }
        final VirtualFile[] children = dir.getChildren();
        if (children.length <= 1) {
            return Collections.emptyList();
        }

        final SmartList<ChildFileInfo> result = new SmartList<>();

        for (VirtualFile child : children) {
            if (child.isDirectory()) {
                continue;
            }
            if (child.equals(parentFile)) {
                continue;
            }

            // if given parentFile itself appears to be a child of some other file, it means that it is not shown as parent node in Project View
            for (ProjectViewFileNestingService.NestingRule rule : rulesWhereItCanBeChild) {
                final String childName = child.getName();

                final Couple<Boolean> c = FileNestingBuilder.checkMatchingAsParentOrChild(rule, childName);
                final boolean matchesParent = c.first;

                if (matchesParent) {
                    final String baseName = childName.substring(0, childName.length() - rule.getParentFileSuffix().length());
                    if (parentFile.getName().equals(baseName + rule.getChildFileSuffix())) {
                        return Collections.emptyList(); // parentFile itself appears to be a child of childFile
                    }
                }
            }

            for (ProjectViewFileNestingService.NestingRule rule : rulesWhereItCanBeParent) {
                final String childName = child.getName();

                final Couple<Boolean> c = FileNestingBuilder.checkMatchingAsParentOrChild(rule, childName);
                final boolean matchesChild = c.second;

                if (matchesChild) {
                    final String baseName = childName.substring(0, childName.length() - rule.getChildFileSuffix().length());
                    if (parentFile.getName().equals(baseName + rule.getParentFileSuffix())) {
                        result.add(new ChildFileInfo(child, baseName));
                    }
                }
            }
        }

        return result;
    }

    /**
     * @return only those rules where given {@code fileName} can potentially be a parent (if {@code parentNotChild} is {@code true})
     * or only those rules where given {@code fileName} can potentially be a child (if {@code parentNotChild} is {@code false})
     */
    private static @Nonnull Collection<ProjectViewFileNestingService.NestingRule> filterRules(final @Nonnull Collection<? extends ProjectViewFileNestingService.NestingRule> rules,
                                                                                              final @Nonnull String fileName,
                                                                                              final boolean parentNotChild) {
        final SmartList<ProjectViewFileNestingService.NestingRule> result = new SmartList<>();
        for (ProjectViewFileNestingService.NestingRule rule : rules) {
            final Couple<Boolean> c = FileNestingBuilder.checkMatchingAsParentOrChild(rule, fileName);
            final boolean matchesParent = c.first;
            final boolean matchesChild = c.second;

            if (!matchesChild && !matchesParent) {
                continue;
            }

            if (matchesParent && parentNotChild) {
                result.add(rule);
            }

            if (matchesChild && !parentNotChild) {
                result.add(rule);
            }
        }

        return result;
    }

    public record ChildFileInfo(@Nonnull VirtualFile file, @Nonnull String namePartCommonWithParentFile) {
    }
}
