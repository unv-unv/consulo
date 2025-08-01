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
package consulo.execution.internal.action;

import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.execution.ProgramRunnerUtil;
import consulo.execution.RunnerAndConfigurationSettings;
import consulo.execution.action.ConfigurationContext;
import consulo.execution.action.ConfigurationFromContext;
import consulo.execution.configuration.ConfigurationType;
import consulo.execution.configuration.LocatableConfiguration;
import consulo.execution.configuration.LocatableConfigurationBase;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.localize.ExecutionLocalize;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.RelativePoint;
import consulo.ui.ex.action.ActionGroup;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.ui.ex.popup.BaseListPopupStep;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.ex.popup.ListPopup;
import consulo.ui.ex.popup.PopupStep;
import consulo.ui.image.Image;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class BaseRunConfigurationAction extends ActionGroup {
    protected static final Logger LOG = Logger.getInstance(BaseRunConfigurationAction.class);

    @Deprecated
    protected BaseRunConfigurationAction(final String text, final String description, final Image icon) {
        super(text, description, icon);
        setPopup(true);
        setEnabledInModalContext(true);
    }

    protected BaseRunConfigurationAction(@Nonnull LocalizeValue text, @Nonnull LocalizeValue description, @Nullable Image icon) {
        super(text, description, icon);
        setPopup(true);
        setEnabledInModalContext(true);
    }

    @Nonnull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
        return e != null ? getChildren(e.getDataContext()) : EMPTY_ARRAY;
    }

    private AnAction[] getChildren(DataContext dataContext) {
        final ConfigurationContext context = ConfigurationContext.getFromContext(dataContext);
        final RunnerAndConfigurationSettings existing = context.findExisting();
        if (existing == null) {
            final List<ConfigurationFromContext> producers = getConfigurationsFromContext(context);
            if (producers.size() > 1) {
                final AnAction[] children = new AnAction[producers.size()];
                int chldIdx = 0;
                for (final ConfigurationFromContext fromContext : producers) {
                    final ConfigurationType configurationType = fromContext.getConfigurationType();
                    final RunConfiguration configuration = fromContext.getConfiguration();
                    final LocalizeValue actionName = configuration instanceof LocatableConfiguration
                        ? LocalizeValue.of(StringUtil.unquoteString(suggestRunActionName((LocatableConfiguration) configuration)))
                        : configurationType.getDisplayName();
                    final AnAction anAction = new AnAction(actionName, configurationType.getDisplayName(), configurationType.getIcon()) {
                        @RequiredUIAccess
                        @Override
                        public void actionPerformed(AnActionEvent e) {
                            perform(fromContext, context);
                        }
                    };
                    anAction.getTemplatePresentation().setTextValue(actionName.map(Presentation.NO_MNEMONIC));
                    children[chldIdx++] = anAction;
                }
                return children;
            }
        }
        return EMPTY_ARRAY;
    }

    @Nonnull
    private List<ConfigurationFromContext> getConfigurationsFromContext(ConfigurationContext context) {
        final List<ConfigurationFromContext> fromContext = context.getConfigurationsFromContext();
        if (fromContext == null) {
            return Collections.emptyList();
        }

        final List<ConfigurationFromContext> enabledConfigurations = new ArrayList<ConfigurationFromContext>();
        for (ConfigurationFromContext configurationFromContext : fromContext) {
            if (isEnabledFor(configurationFromContext.getConfiguration())) {
                enabledConfigurations.add(configurationFromContext);
            }
        }
        return enabledConfigurations;
    }

    protected boolean isEnabledFor(RunConfiguration configuration) {
        return true;
    }

    @Override
    public boolean canBePerformed(DataContext dataContext) {
        final ConfigurationContext context = ConfigurationContext.getFromContext(dataContext);
        final RunnerAndConfigurationSettings existing = context.findExisting();
        if (existing == null) {
            final List<ConfigurationFromContext> fromContext = getConfigurationsFromContext(context);
            return fromContext.size() <= 1;
        }
        return true;
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(@Nonnull AnActionEvent e) {
        final DataContext dataContext = e.getDataContext();
        final ConfigurationContext context = ConfigurationContext.getFromContext(dataContext);
        final RunnerAndConfigurationSettings existing = context.findExisting();
        if (existing == null) {
            final List<ConfigurationFromContext> producers = getConfigurationsFromContext(context);
            if (producers.isEmpty()) {
                return;
            }
            if (producers.size() > 1) {
                final Editor editor = dataContext.getData(Editor.KEY);
                Collections.sort(producers, ConfigurationFromContext.NAME_COMPARATOR);
                final ListPopup popup = JBPopupFactory.getInstance()
                    .createListPopup(new BaseListPopupStep<ConfigurationFromContext>(ExecutionLocalize.configurationActionChooserTitle()
                        .get(), producers) {
                        @Override
                        @Nonnull
                        public String getTextFor(final ConfigurationFromContext producer) {
                            return producer.getConfigurationType().getDisplayName().get();
                        }

                        @Override
                        public Image getIconFor(final ConfigurationFromContext producer) {
                            return producer.getConfigurationType().getIcon();
                        }

                        @Override
                        public PopupStep onChosen(final ConfigurationFromContext producer, final boolean finalChoice) {
                            perform(producer, context);
                            return FINAL_CHOICE;
                        }
                    });
                final InputEvent event = e.getInputEvent();
                if (event instanceof MouseEvent) {
                    popup.show(new RelativePoint((MouseEvent) event));
                }
                else if (editor != null) {
                    editor.showPopupInBestPositionFor(popup);
                }
                else {
                    popup.showInBestPositionFor(dataContext);
                }
            }
            else {
                perform(producers.get(0), context);
            }
            return;
        }

        perform(context);
    }

    private void perform(final ConfigurationFromContext configurationFromContext, final ConfigurationContext context) {
        RunnerAndConfigurationSettings configurationSettings = configurationFromContext.getConfigurationSettings();
        context.setConfiguration(configurationSettings);
        configurationFromContext.onFirstRun(context, new Runnable() {
            @Override
            public void run() {
                perform(context);
            }
        });
    }

    protected abstract void perform(ConfigurationContext context);

    @Override
    public void update(@Nonnull AnActionEvent event) {
        final ConfigurationContext context = ConfigurationContext.getFromContext(event.getDataContext());
        final Presentation presentation = event.getPresentation();
        final RunnerAndConfigurationSettings existing = context.findExisting();
        RunnerAndConfigurationSettings configuration = existing;
        if (configuration == null) {
            configuration = context.getConfiguration();
        }
        if (configuration == null) {
            presentation.setEnabledAndVisible(false);
        }
        else {
            presentation.setEnabledAndVisible(true);
            final List<ConfigurationFromContext> fromContext = getConfigurationsFromContext(context);
            if (existing == null && !fromContext.isEmpty()) {
                //todo[nik,anna] it's dirty fix. Otherwise wrong configuration will be returned from context.getConfiguration()
                context.setConfiguration(fromContext.get(0).getConfigurationSettings());
            }
            final String name = suggestRunActionName((LocatableConfiguration) configuration.getConfiguration());
            updatePresentation(presentation, existing != null || fromContext.size() <= 1 ? name : "", context);
        }
    }

    @Override
    public boolean isDumbAware() {
        return false;
    }

    public static String suggestRunActionName(final LocatableConfiguration configuration) {
        if (configuration instanceof LocatableConfigurationBase && configuration.isGeneratedName()) {
            String actionName = ((LocatableConfigurationBase) configuration).getActionName();
            if (actionName != null) {
                return actionName;
            }
        }
        return ProgramRunnerUtil.shortenName(configuration.getName(), 0);
    }

    protected abstract void updatePresentation(Presentation presentation, @Nonnull String actionText, ConfigurationContext context);
}
