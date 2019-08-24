/*
 * Copyright 2013-2016 consulo.io
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
package consulo.ide.newProject.actions;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.impl.util.NewProjectUtilPlatform;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DefaultProjectOpenProcessor;
import com.intellij.util.ui.JBUI;
import consulo.application.WriteThreadOption;
import consulo.ide.newProject.NewModuleBuilderProcessor2;
import consulo.ide.newProject.NewProjectDialog;
import consulo.ide.newProject.NewProjectPanel;
import consulo.ide.welcomeScreen.WelcomeScreenSlideAction;
import consulo.ide.welcomeScreen.WelcomeScreenSlider;
import consulo.ide.wizard.newModule.NewModuleWizardContext;
import consulo.logging.Logger;
import consulo.ui.RequiredUIAccess;
import consulo.ui.UIAccess;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Objects;

/**
 * @author VISTALL
 */
public class NewProjectAction extends WelcomeScreenSlideAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(NewProjectAction.class);

  static class SlideNewProjectPanel extends NewProjectPanel {
    private final WelcomeScreenSlider owner;
    private JButton myBackButton;
    private JButton myOkButton;

    private Runnable myOkAction;
    private Runnable myBackAction;

    @RequiredUIAccess
    public SlideNewProjectPanel(@Nonnull Disposable parentDisposable, WelcomeScreenSlider owner, @Nullable Project project, @Nullable VirtualFile virtualFile) {
      super(parentDisposable, project, virtualFile);
      this.owner = owner;
    }

    @Override
    public void setOKActionEnabled(boolean enabled) {
      myOkButton.setEnabled(enabled);
    }

    @Override
    public void setOKActionText(@Nonnull String text) {
      myOkButton.setText(text);
    }

    @Override
    public void setBackAction(Runnable backAction) {
      myBackAction = backAction;
      myBackButton.setVisible(myBackAction != null);
    }

    @Override
    public void setOKAction(@Nullable Runnable action) {
      myOkAction = action;
    }

    @Nonnull
    @Override
    protected JPanel createSouthPanel() {
      JPanel buttonsPanel = new JPanel(new GridLayout(1, 3, SystemInfo.isMacOSLeopard ? 0 : 5, 0));

      myBackButton = new JButton(CommonBundle.message("button.back"));
      myBackButton.addActionListener(e -> Objects.requireNonNull(myBackAction).run());
      myBackButton.setVisible(false);
      buttonsPanel.add(myBackButton);

      myOkButton = new JButton(CommonBundle.getOkButtonText()) {
        @Override
        public boolean isDefaultButton() {
          return true;
        }
      };
      myOkButton.setMargin(JBUI.insets(2, 16));
      myOkButton.setEnabled(false);

      myOkButton.addActionListener(e -> doOkAction());
      buttonsPanel.add(myOkButton);

      JButton cancelButton = new JButton(CommonBundle.getCancelButtonText());
      cancelButton.setMargin(JBUI.insets(2, 16));
      cancelButton.addActionListener(e -> owner.removeSlide(this));
      buttonsPanel.add(cancelButton);

      return JBUI.Panels.simplePanel().addToRight(buttonsPanel);
    }

    @RequiredUIAccess
    protected void doOkAction() {
      if (myOkAction != null) {
        myOkAction.run();
      }
      else {
        generateProject(null, this);
      }
    }
  }

  @RequiredUIAccess
  @Override
  public void actionPerformed(@Nonnull final AnActionEvent e) {
    Project project = e.getProject();
    NewProjectDialog dialog = new NewProjectDialog(project, null);

    if (dialog.showAndGet()) {
      generateProject(project, dialog.getProjectPanel());
    }
  }

  @Nonnull
  @Override
  public JComponent createSlide(@Nonnull Disposable parentDisposable, @Nonnull WelcomeScreenSlider owner) {
    owner.setTitle(IdeBundle.message("title.new.project"));

    return new SlideNewProjectPanel(parentDisposable, owner, null, null);
  }

  @RequiredUIAccess
  protected static void generateProject(Project project, @Nonnull final NewProjectPanel projectPanel) {
    NewModuleWizardContext context = projectPanel.getWizardContext();
    NewModuleBuilderProcessor2<NewModuleWizardContext> processor = projectPanel.getProcessor();
    if (processor == null || context == null) {
      LOG.error("Impossible situation. Calling generate project with null data: " + processor + "/" + context);
      return;
    }

    if (WriteThreadOption.isSubWriteThreadSupported()) {
      generateProjectAsync(project, context, processor);
    }
    else {
      generateProjectOld(project, context, processor);
    }
  }

  @RequiredUIAccess
  private static void generateProjectOld(Project project, @Nonnull NewModuleWizardContext context, @Nonnull NewModuleBuilderProcessor2 processor) {
    final File location = new File(context.getPath());
    final int childCount = location.exists() ? location.list().length : 0;
    if (!location.exists() && !location.mkdirs()) {
      Messages.showErrorDialog(project, "Cannot create directory '" + location + "'", "Create Project");
      return;
    }

    final VirtualFile baseDir = WriteAction.compute(() -> LocalFileSystem.getInstance().refreshAndFindFileByIoFile(location));
    baseDir.refresh(false, true);

    if (childCount > 0) {
      int rc = Messages.showYesNoDialog(project, "The directory '" + location + "' is not empty. Continue?", "Create New Project", Messages.getQuestionIcon());
      if (rc == Messages.NO) {
        return;
      }
    }

    RecentProjectsManager.getInstance().setLastProjectCreationLocation(location.getParent());
    DefaultProjectOpenProcessor.doOpenProject(baseDir, null, false, -1, project1 -> NewProjectUtilPlatform.doCreate(context, processor, project1, baseDir));
  }

  @RequiredUIAccess
  private static void generateProjectAsync(Project project, @Nonnull NewModuleWizardContext context, @Nonnull NewModuleBuilderProcessor2 processor) {
    final File location = new File(context.getPath());
    final int childCount = location.exists() ? location.list().length : 0;
    if (!location.exists() && !location.mkdirs()) {
      Messages.showErrorDialog(project, "Cannot create directory '" + location + "'", "Create Project");
      return;
    }

    final VirtualFile baseDir = WriteAction.compute(() -> LocalFileSystem.getInstance().refreshAndFindFileByIoFile(location));
    baseDir.refresh(false, true);

    if (childCount > 0) {
      int rc = Messages.showYesNoDialog(project, "The directory '" + location + "' is not empty. Continue?", "Create New Project", Messages.getQuestionIcon());
      if (rc == Messages.NO) {
        return;
      }
    }

    RecentProjectsManager.getInstance().setLastProjectCreationLocation(location.getParent());

    UIAccess uiAccess = UIAccess.current();
    ProjectManager.getInstance().openProjectAsync(baseDir, uiAccess).doWhenDone((openedProject) -> {
      uiAccess.give(() -> NewProjectUtilPlatform.doCreate(context, processor, openedProject, baseDir));
    });
  }
}
