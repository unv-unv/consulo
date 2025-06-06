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
package consulo.desktop.awt.settings;

import consulo.application.Application;
import consulo.application.ApplicationProperties;
import consulo.application.ui.UISettings;
import consulo.application.ui.event.UISettingsListener;
import consulo.application.ui.wm.IdeFocusManager;
import consulo.configurable.*;
import consulo.configurable.internal.ConfigurableUIMigrationUtil;
import consulo.configurable.internal.ConfigurableWrapper;
import consulo.configurable.internal.FullContentConfigurable;
import consulo.configurable.localize.ConfigurableLocalize;
import consulo.container.plugin.PluginId;
import consulo.container.plugin.PluginIds;
import consulo.container.plugin.PluginManager;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataProvider;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.ide.impl.base.BaseShowSettingsUtil;
import consulo.ide.impl.configurable.ConfigurablePreselectStrategy;
import consulo.ide.impl.configurable.ProjectStructureSelectorOverSettings;
import consulo.ide.impl.idea.ide.ui.search.SearchUtil;
import consulo.ide.impl.idea.openapi.options.ex.GlassPanel;
import consulo.ide.impl.idea.util.ReflectionUtil;
import consulo.ide.impl.roots.ui.configuration.session.internal.ConfigurableSessionImpl;
import consulo.ide.setting.ProjectStructureSelector;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.event.AnActionListener;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.ui.ex.awt.internal.AbstractPainter;
import consulo.ui.ex.awt.speedSearch.ElementFilter;
import consulo.ui.ex.awt.tree.SimpleNode;
import consulo.ui.ex.awt.update.UiNotifyConnector;
import consulo.ui.ex.awt.util.IdeGlassPaneUtil;
import consulo.ui.ex.awt.util.MergingUpdateQueue;
import consulo.ui.ex.awt.util.Update;
import consulo.util.concurrent.AsyncResult;
import consulo.util.concurrent.Promise;
import consulo.util.concurrent.Promises;
import consulo.util.dataholder.Key;
import consulo.util.lang.ControlFlowException;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.util.lang.function.Predicates;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.Field;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public class OptionsEditor implements DataProvider, Disposable, AWTEventListener, UISettingsListener, Settings {
    private static class SearchableWrapper implements SearchableConfigurable {
        private final Configurable myConfigurable;

        private SearchableWrapper(Configurable configurable) {
            myConfigurable = configurable;
        }

        @Override
        @Nonnull
        public String getId() {
            return myConfigurable.getClass().getName();
        }

        @Override
        @Nls
        public String getDisplayName() {
            return myConfigurable.getDisplayName();
        }

        @Override
        public String getHelpTopic() {
            return myConfigurable.getHelpTopic();
        }

        @RequiredUIAccess
        @Override
        public JComponent createComponent(@Nonnull Disposable parent) {
            return myConfigurable.createComponent(parent);
        }

        @RequiredUIAccess
        @Nullable
        @Override
        public consulo.ui.Component createUIComponent(@Nonnull Disposable parentDisposable) {
            return myConfigurable.createUIComponent(parentDisposable);
        }

        @RequiredUIAccess
        @Override
        public boolean isModified() {
            return myConfigurable.isModified();
        }

        @RequiredUIAccess
        @Override
        public void apply() throws ConfigurationException {
            myConfigurable.apply();
        }

        @RequiredUIAccess
        @Override
        public void reset() {
            myConfigurable.reset();
        }

        @RequiredUIAccess
        @Override
        public void disposeUIResources() {
            myConfigurable.disposeUIResources();
        }
    }


    private static class ContentWrapper extends NonOpaquePanel {
        private final JLabel myErrorLabel;

        private JComponent mySimpleContent;
        private ConfigurationException myException;

        private ContentWrapper() {
            setLayout(new BorderLayout());
            myErrorLabel = new JLabel();
            myErrorLabel.setOpaque(true);
            myErrorLabel.setBackground(LightColors.RED);
        }

        void setContent(JComponent component, ConfigurationException e, @Nonnull Configurable configurable) {
            if (component != null && mySimpleContent == component && myException == e) {
                return;
            }

            removeAll();

            if (component != null) {
                boolean noMargin = ConfigurableWrapper.isNoMargin(configurable);
                JComponent wrapComponent = component;
                if (!noMargin) {
                    wrapComponent = JBUI.Panels.simplePanel().addToCenter(wrapComponent);
                    wrapComponent.setBorder(new EmptyBorder(UIUtil.PANEL_SMALL_INSETS));
                }


                boolean noScroll = ConfigurableWrapper.isNoScroll(configurable);
                if (!noScroll) {
                    JScrollPane scroll = ScrollPaneFactory.createScrollPane(wrapComponent, true);
                    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
                    scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
                    add(scroll, BorderLayout.CENTER);
                }
                else {
                    add(wrapComponent, BorderLayout.CENTER);
                }
            }

            if (e != null) {
                myErrorLabel.setText(UIUtil.toHtml(e.getMessage()));
                add(myErrorLabel, BorderLayout.NORTH);
            }

            mySimpleContent = component;
            myException = e;
        }

        @Override
        public boolean isNull() {
            boolean superNull = super.isNull();
            if (superNull) {
                return superNull;
            }
            return Check.isNull(mySimpleContent);
        }
    }

    private class ConfigurableContext implements Disposable {
        JComponent myComponent;
        Configurable myConfigurable;

        @RequiredUIAccess
        ConfigurableContext(Configurable configurable) {
            myConfigurable = configurable;
            myComponent = ConfigurableUIMigrationUtil.createComponent(configurable, this);

            if (myComponent != null) {
                Object clientProperty = myComponent.getClientProperty(NOT_A_NEW_COMPONENT);
                if (clientProperty != null && ApplicationProperties.isInSandbox()) {
                    LOG.warn(String.format(
                        "Settings component for '%s' MUST be recreated, please dispose it in disposeUIResources() and create a new instance in createComponent()!",
                        configurable
                    ));
                }
                else {
                    myComponent.putClientProperty(NOT_A_NEW_COMPONENT, Boolean.TRUE);
                }
            }
        }

        void set(ContentWrapper wrapper) {
            myOwnDetails.setDetailsModeEnabled(true);
            wrapper.setContent(myComponent, getContext().getErrors().get(myConfigurable), myConfigurable);
        }

        boolean isShowing() {
            return myComponent != null && myComponent.isShowing();
        }

        @Override
        public void dispose() {

        }
    }

    private static class MySearchField extends SearchTextField {
        private boolean myDelegatingNow;

        private MySearchField() {
            super(false);
            addKeyListener(new KeyAdapter() {
            });
        }

        @Override
        protected boolean preprocessEventForTextField(KeyEvent e) {
            KeyStroke stroke = KeyStroke.getKeyStrokeForEvent(e);
            if (!myDelegatingNow) {
                if ("pressed ESCAPE".equals(stroke.toString()) && getText().length() > 0) {
                    setText(""); // reset filter on ESC
                    return true;
                }

                if (getTextEditor().isFocusOwner()) {
                    try {
                        myDelegatingNow = true;
                        boolean treeNavigation =
                            stroke.getModifiers() == 0 && (stroke.getKeyCode() == KeyEvent.VK_UP || stroke.getKeyCode() == KeyEvent.VK_DOWN);

                        if ("pressed ENTER".equals(stroke.toString())) {
                            return true; // avoid closing dialog on ENTER
                        }

                        Object action = getTextEditor().getInputMap().get(stroke);
                        if (action == null || treeNavigation) {
                            onTextKeyEvent(e);
                            return true;
                        }
                    }
                    finally {
                        myDelegatingNow = false;
                    }
                }
            }
            return false;
        }

        protected void onTextKeyEvent(KeyEvent e) {
        }
    }

    private class SpotlightPainter extends AbstractPainter {
        Map<Configurable, String> myConfigurableToLastOption = new HashMap<>();

        GlassPanel myGP = new GlassPanel(myOwnDetails.getContentGutter());
        boolean myVisible;

        @Override
        public void executePaint(Component component, Graphics2D g) {
            if (myVisible && myGP.isVisible()) {
                myGP.paintSpotlight(g, myOwnDetails.getContentGutter());
            }
        }

        public boolean updateForCurrentConfigurable() {
            Configurable current = getContext().getCurrentConfigurable();

            if (current != null && !myConfigurable2Content.containsKey(current)) {
                return Application.get().isUnitTestMode();
            }

            String text = getFilterText();

            try {
                boolean sameText = myConfigurableToLastOption.containsKey(current) && text.equals(myConfigurableToLastOption.get(current));

                if (current == null) {
                    myVisible = false;
                    myGP.clear();
                    return true;
                }

                SearchableConfigurable searchable = current instanceof SearchableConfigurable searchableConfigurable
                    ? searchableConfigurable
                    : new SearchableWrapper(current);

                myGP.clear();

                Runnable runnable = SearchUtil.lightOptions(searchable, myContentWrapper, text, myGP);
                if (runnable != null) {
                    myVisible = true;//myContext.isHoldingFilter();
                    runnable.run();

                    boolean pushFilteringFurther = true;
                    if (sameText) {
                        pushFilteringFurther = false;
                    }
                    else {
                        if (myFilter.myHits != null) {
                            pushFilteringFurther = !myFilter.myHits.getNameHits().contains(current);
                        }
                    }

                    Runnable ownSearch = searchable.enableSearch(text);
                    if (pushFilteringFurther && ownSearch != null) {
                        ownSearch.run();
                    }
                    fireNeedsRepaint(myOwnDetails.getComponent());
                }
                else {
                    myVisible = false;
                }
            }
            finally {
                myConfigurableToLastOption.put(current, text);
            }

            return true;
        }

        @Override
        public boolean needsRepaint() {
            return true;
        }
    }

    private class MyColleague implements OptionsEditorColleague {
        @Override
        @RequiredUIAccess
        public AsyncResult<Void> onSelected(Configurable configurable, Configurable oldConfigurable) {
            return processSelected(configurable, oldConfigurable);
        }

        @Override
        public AsyncResult<Void> onModifiedRemoved(Configurable configurable) {
            return updateIfCurrent(configurable);
        }

        @Override
        public AsyncResult<Void> onModifiedAdded(Configurable configurable) {
            return updateIfCurrent(configurable);
        }

        @Override
        public AsyncResult<Void> onErrorsChanged() {
            return updateIfCurrent(getContext().getCurrentConfigurable());
        }

        private AsyncResult<Void> updateIfCurrent(Configurable configurable) {
            if (getContext().getCurrentConfigurable() == configurable && configurable != null) {
                updateContent();
                return AsyncResult.resolved();
            }
            else {
                return AsyncResult.rejected();
            }
        }
    }

    private class Filter extends ElementFilter.Active.Impl<SimpleNode> {
        SearchableOptionsRegistrar myIndex = SearchableOptionsRegistrar.getInstance();
        Set<Configurable> myFiltered = null;
        ConfigurableHit myHits;

        boolean myUpdateEnabled = true;
        private Configurable myLastSelected;

        @Override
        public boolean shouldBeShowing(SimpleNode value) {
            return myFiltered == null
                || !(value instanceof OptionsTree.ConfigurableNode node)
                || myFiltered.contains(node.getConfigurable())
                || isChildOfNameHit(node);
        }

        private boolean isChildOfNameHit(OptionsTree.ConfigurableNode node) {
            if (myHits != null) {
                OptionsTree.Base eachParent = node;
                while (eachParent != null) {
                    if (eachParent instanceof OptionsTree.ConfigurableNode eachConfigurableNode
                        && myHits.getNameFullHits().contains(eachConfigurableNode.myConfigurable)) {
                        return true;
                    }
                    eachParent = (OptionsTree.Base)eachParent.getParent();
                }

                return false;
            }

            return false;
        }

        public Promise<?> refilterFor(String text, boolean adjustSelection, boolean now) {
            try {
                myUpdateEnabled = false;
                mySearch.setText(text);
            }
            finally {
                myUpdateEnabled = true;
            }

            return update(DocumentEvent.EventType.CHANGE, adjustSelection, now);
        }

        public void clearTemporary() {
            myContext.setHoldingFilter(false);
            updateSpotlight(false);
        }

        public void reenable() {
            myContext.setHoldingFilter(true);
            updateSpotlight(false);
        }

        public Promise<?> update(DocumentEvent.EventType type, boolean adjustSelection, boolean now) {
            if (!myUpdateEnabled) {
                return Promises.rejectedPromise();
            }

            String text = mySearch.getText();
            if (getFilterText().length() == 0) {
                myContext.setHoldingFilter(false);
                myFiltered = null;
            }
            else {
                myContext.setHoldingFilter(true);
                Configurable[] buildConfigurables = Objects.requireNonNullElse(myBuildConfigurables, Configurable.EMPTY_ARRAY);
                myHits = myIndex.getConfigurables(buildConfigurables, type == DocumentEvent.EventType.CHANGE, myFiltered, text, myProject);
                myFiltered = myHits.getAll();
            }

            if (myFiltered != null && myFiltered.isEmpty()) {
                mySearch.getTextEditor().setBackground(LightColors.RED);
            }
            else {
                mySearch.getTextEditor().setBackground(UIUtil.getTextFieldBackground());
            }

            Configurable current = getContext().getCurrentConfigurable();

            boolean shouldMoveSelection = true;

            if (myHits != null && (myHits.getNameFullHits().contains(current) || myHits.getContentHits().contains(current))) {
                shouldMoveSelection = false;
            }

            if (shouldMoveSelection && type != DocumentEvent.EventType.INSERT && (myFiltered == null || myFiltered.contains(current))) {
                shouldMoveSelection = false;
            }

            Configurable toSelect = adjustSelection ? current : null;
            if (shouldMoveSelection && myHits != null) {
                if (!myHits.getNameHits().isEmpty()) {
                    toSelect = suggestToSelect(myHits.getNameHits(), myHits.getNameFullHits());
                }
                else if (!myHits.getContentHits().isEmpty()) {
                    toSelect = suggestToSelect(myHits.getContentHits(), null);
                }
            }

            updateSpotlight(false);

            if ((myFiltered == null || !myFiltered.isEmpty()) && toSelect == null && myLastSelected != null) {
                toSelect = myLastSelected;
                myLastSelected = null;
            }

            if (toSelect == null && current != null) {
                myLastSelected = current;
            }

            Promise<?> callback = fireUpdate(adjustSelection ? myTree.findNodeFor(toSelect) : null, adjustSelection, now);

            myFilterDocumentWasChanged = true;

            return callback;
        }

        private boolean isEmptyParent(Configurable configurable) {
            return configurable instanceof SearchableConfigurable.Parent && !((SearchableConfigurable.Parent)configurable).hasOwnContent();
        }

        @Nullable
        private Configurable suggestToSelect(Set<Configurable> set, Set<Configurable> fullHits) {
            Configurable candidate = null;
            for (Configurable each : set) {
                if (fullHits != null && fullHits.contains(each)) {
                    return each;
                }
                if (!isEmptyParent(each) && candidate == null) {
                    candidate = each;
                }
            }

            return candidate;
        }

    }

    private static final Logger LOG = Logger.getInstance(OptionsEditor.class);

    public static final String MAIN_SPLITTER_PROPORTION = "options.splitter.main.proportions";

    private static final String NOT_A_NEW_COMPONENT = "component.was.already.instantiated";

    private final Project myProject;
    private final Function<Project, Configurable[]> myConfigurablesBuilder;
    private final ConfigurablePreselectStrategy myConfigurablePreselectStrategy;

    private final OptionsEditorContext myContext;

    private final OptionsTree myTree;
    private final MySearchField mySearch;

    private final DetailsComponent myOwnDetails =
        new DetailsComponent(true, false).setEmptyContentText("Select configuration element in the tree to edit its settings");
    private final ContentWrapper myContentWrapper = new ContentWrapper();

    private final Map<Configurable, ConfigurableContext> myConfigurable2Content = new HashMap<>();
    private final Map<Configurable, AsyncResult<Void>> myConfigurable2LoadCallback = new HashMap<>();

    private final MergingUpdateQueue myModificationChecker;
    private JPanel myRootPanel;
    private final Runnable myAfterTreeLoad;

    private final SpotlightPainter mySpotlightPainter = new SpotlightPainter();
    private final MergingUpdateQueue mySpotlightUpdate;
    private final LoadingDecorator myLoadingDecorator;
    private final Filter myFilter;

    private final Wrapper mySearchWrapper = new Wrapper();
    private final JBLoadingPanel myTreeDecoratorPanel;

    private boolean myFilterDocumentWasChanged;
    private Window myWindow;
    private volatile boolean myDisposed;

    private ConfigurableSessionImpl myConfigurableSession;

    private boolean myConfigurablesLoaded;
    private Configurable[] myBuildConfigurables = Configurable.EMPTY_ARRAY;

    public OptionsEditor(
        Project project,
        Function<Project, Configurable[]> configurablesBuilder,
        ConfigurablePreselectStrategy configurablePreselectStrategy,
        JPanel rootPanel,
        Runnable afterTreeLoad
    ) {
        myProject = project;
        myConfigurablesBuilder = configurablesBuilder;
        myConfigurablePreselectStrategy = configurablePreselectStrategy;
        myRootPanel = rootPanel;
        myAfterTreeLoad = afterTreeLoad;

        myConfigurableSession = new ConfigurableSessionImpl(project);

        myFilter = new Filter();
        myContext = new OptionsEditorContext(myFilter);

        mySearch = new MySearchField() {
            @Override
            protected void onTextKeyEvent(KeyEvent e) {
                myTree.processTextEvent(e);
            }
        };

        mySearch.getTextEditor().addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                boolean hasText = mySearch.getText().length() > 0;
                if (!myContext.isHoldingFilter() && hasText) {
                    myFilter.reenable();
                }

                if (!isSearchFieldFocused() && hasText) {
                    mySearch.selectText();
                }
            }
        });

        myTree = new OptionsTree(() -> myBuildConfigurables, getContext()) {
            @Override
            protected void onTreeKeyEvent(KeyEvent e) {
                myFilterDocumentWasChanged = false;
                try {
                    mySearch.keyEventToTextField(e);
                }
                finally {
                    if (myFilterDocumentWasChanged && !isFilterFieldVisible()) {
                        setFilterFieldVisible(false, false);
                    }
                }
            }
        };

        getContext().addColleague(myTree);
        Disposer.register(this, myTree);
        mySearch.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                myFilter.update(e.getType(), true, false);
            }
        });

        myTreeDecoratorPanel = new JBLoadingPanel(
            new BorderLayout(),
            panel -> new LoadingDecorator(panel, this, -1, true, new AsyncProcessIcon("Options")) {
                @Override
                protected NonOpaquePanel customizeLoadingLayer(JPanel parent, JLabel text, AsyncProcessIcon icon) {
                    NonOpaquePanel panel = super.customizeLoadingLayer(parent, text, icon);
                    text.setFont(UIUtil.getLabelFont());
                    return panel;
                }
            }
        );

        mySearchWrapper.setContent(mySearch);
        mySearchWrapper.setBackground(MorphColor.of(UIUtil::getTreeBackground));
        mySearchWrapper.setBorder(JBUI.Borders.empty(8));
        mySearchWrapper.setOpaque(true);

        mySearchWrapper.setVisible(false);
        myTree.getComponent().setVisible(false);

        myTreeDecoratorPanel.add(mySearchWrapper, BorderLayout.NORTH);
        myTreeDecoratorPanel.add(myTree.getComponent(), BorderLayout.CENTER);

        myLoadingDecorator = new LoadingDecorator(myOwnDetails.getComponent(), this, 150);

        MyColleague colleague = new MyColleague();
        getContext().addColleague(colleague);

        mySpotlightUpdate = new MergingUpdateQueue("OptionsSpotlight", 200, false, rootPanel, this, rootPanel);

        Toolkit.getDefaultToolkit()
            .addAWTEventListener(this, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);

        ActionManager.getInstance().addAnActionListener(
            new AnActionListener() {
                @Override
                public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
                }

                @Override
                public void afterActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
                    queueModificationCheck();
                }
            },
            this
        );

        myModificationChecker = new MergingUpdateQueue("OptionsModificationChecker", 1000, false, rootPanel, this, rootPanel);

        IdeGlassPaneUtil.installPainter(myOwnDetails.getContentGutter(), mySpotlightPainter, this);

        setFilterFieldVisible(false, false);

        uiSettingsChanged(UISettings.getInstance());

        UiNotifyConnector.doWhenFirstShown(myRootPanel, () -> {
            myWindow = SwingUtilities.getWindowAncestor(rootPanel);

            myTreeDecoratorPanel.startLoading();

            project.getApplication().executeOnPooledThread(this::run);
        });
    }

    private void run() {
        try {
            myBuildConfigurables = myConfigurablesBuilder.apply(myProject);

            Configurable preselectedConfigurable = myConfigurablePreselectStrategy.get(myBuildConfigurables);

            myConfigurablesLoaded = true;

            myProject.getUIAccess().give(() -> {
                myTreeDecoratorPanel.invalidate();
                myTreeDecoratorPanel.stopLoading();

                myTree.rebuild(() -> {
                    if (preselectedConfigurable != BaseShowSettingsUtil.SKIP_SELECTION_CONFIGURATION) {
                        if (preselectedConfigurable != null) {
                            myTree.select(preselectedConfigurable);
                        }
                        else {
                            myTree.selectFirst();
                        }
                    }

                    myAfterTreeLoad.run();
                });

                mySearchWrapper.setVisible(true);
                myTree.getComponent().setVisible(true);
            });
        }
        catch (Exception e) {
            if (e instanceof ControlFlowException) {
                throw ControlFlowException.rethrow(e);
            }

            LOG.error(e);
        }
    }

    public boolean isConfigurablesLoaded() {
        return myConfigurablesLoaded;
    }

    @Override
    public void uiSettingsChanged(UISettings source) {
        mySearch.setBackground(MorphColor.of(UIUtil::getPanelBackground));
        myTreeDecoratorPanel.setBackground(MorphColor.of(UIUtil::getPanelBackground));
    }

    public JPanel getLeftSide() {
        return myTreeDecoratorPanel;
    }

    public JComponent getRightSide() {
        return myLoadingDecorator.getComponent();
    }

    @Override
    @Nullable
    public <T extends Configurable> T findConfigurable(Class<T> configurableClass) {
        return myTree.findConfigurable(configurableClass);
    }

    @Override
    @Nullable
    public SearchableConfigurable findConfigurableById(@Nonnull String configurableId) {
        return myTree.findConfigurableById(configurableId);
    }

    @Override
    public AsyncResult<Void> clearSearchAndSelect(Configurable configurable) {
        clearFilter();
        return select(configurable, "");
    }

    @Override
    public AsyncResult<Void> select(Configurable configurable) {
        if (StringUtil.isEmpty(mySearch.getText())) {
            return select(configurable, "");
        }
        else {
            return Promises.toAsyncResult(myFilter.refilterFor(mySearch.getText(), true, true));
        }
    }

    @Override
    public AsyncResult<Void> select(Configurable configurable, String text) {
        AsyncResult<Void> callback = AsyncResult.undefined();
        Promises.toActionCallback(myFilter.refilterFor(text, false, true)).doWhenDone(() -> myTree.select(configurable).notify(callback));
        return callback;
    }

    @Nonnull
    @Override
    public <T extends UnnamedConfigurable> AsyncResult<T> select(@Nonnull Class<T> clazz) {
        Pair<Configurable, T> configurableInfo = myTree.findConfigurableInfo(clazz);
        if (configurableInfo == null) {
            return AsyncResult.rejected();
        }

        AsyncResult<T> callback = AsyncResult.undefined();
        Promises.toActionCallback(myFilter.refilterFor("", false, true))
            .doWhenDone(() -> myTree.select(configurableInfo.getFirst()).doWhenDone(() -> callback.setDone(configurableInfo.getSecond())));
        return callback;
    }

    @RequiredUIAccess
    private AsyncResult<Void> processSelected(Configurable configurable, Configurable oldConfigurable) {
        if (isShowing(configurable)) {
            return AsyncResult.resolved();
        }

        AsyncResult<Void> result = AsyncResult.undefined();

        if (configurable == null) {
            myOwnDetails.setContent(null);
            myOwnDetails.setProjectIconDescription(null);

            updateSpotlight(true);
            checkModified(oldConfigurable);

            result.setDone();

        }
        else {
            getUiFor(configurable).doWhenDone(() -> SwingUtilities.invokeLater(() -> {
                if (myDisposed) {
                    return;
                }

                Configurable current = getContext().getCurrentConfigurable();
                if (current != configurable) {
                    result.setRejected();
                    return;
                }

                updateContent();

                String[] bannerText = getBannerText(configurable);
                myOwnDetails.setText(bannerText);

                FullContentConfigurable fullContent = ConfigurableWrapper.cast(configurable, FullContentConfigurable.class);
                if (fullContent != null) {
                    myOwnDetails.setFullContent(myContentWrapper, fullContent::setBannerComponent);
                }
                else {
                    myOwnDetails.setContent(myContentWrapper);
                }

                if (isProjectConfigurable(configurable) && myProject != null) {
                    myOwnDetails.setProjectIconDescription(
                        myProject.isDefault()
                            ? ConfigurableLocalize.configurableDefaultProjectTooltip().get()
                            : ConfigurableLocalize.configurableCurrentProjectTooltip().get()
                    );
                }
                else {
                    myOwnDetails.setProjectIconDescription(null);
                }

                myLoadingDecorator.stopLoading();

                updateSpotlight(false);

                checkModified(oldConfigurable);
                checkModified(configurable);

                if (myTree.myBuilder.getSelectedElements().size() == 0) {
                    select(configurable).notify(result);
                }
                else {
                    result.setDone();
                }
            }));
        }

        return result;
    }

    public static boolean isProjectConfigurable(@Nonnull Configurable configurable) {
        return ConfigurableWrapper.cast(configurable, ProjectConfigurable.class) != null;
    }

    @RequiredUIAccess
    private AsyncResult<Void> getUiFor(Configurable target) {
        UIAccess.assertIsUIThread();

        if (myDisposed) {
            return AsyncResult.rejected();
        }

        UIAccess uiAccess = UIAccess.current();
        if (!myConfigurable2Content.containsKey(target)) {

            return myConfigurable2LoadCallback.computeIfAbsent(target, configurable -> {
                AsyncResult<Void> result = AsyncResult.undefined();

                myLoadingDecorator.startLoading(false);

                uiAccess.give(() -> {
                    if (myProject.isDisposed()) {
                        result.setRejected();
                        return;
                    }

                    initConfigurable(configurable, result);
                });

                return result;
            });
        }

        return AsyncResult.resolved();
    }

    @RequiredUIAccess
    private void initConfigurable(@Nonnull Configurable configurable, AsyncResult<Void> result) {
        UIAccess.assertIsUIThread();

        if (myDisposed) {
            result.setRejected();
            return;
        }

        try {
            myConfigurable2Content.computeIfAbsent(
                configurable,
                it -> {
                    ConfigurableContext content = new ConfigurableContext(it);
                    it.initialize();
                    it.reset();
                    return content;
                }
            );
            result.setDone();
        }
        catch (Throwable e) {
            LOG.warn(e);
            result.rejectWithThrowable(e);
        }
    }

    private void updateSpotlight(boolean now) {
        if (now) {
            boolean success = mySpotlightPainter.updateForCurrentConfigurable();
            if (!success) {
                updateSpotlight(false);
            }
        }
        else {
            mySpotlightUpdate.queue(new Update(this) {
                @Override
                public void run() {
                    boolean success = mySpotlightPainter.updateForCurrentConfigurable();
                    if (!success) {
                        updateSpotlight(false);
                    }
                }
            });
        }
    }

    private String[] getBannerText(Configurable configurable) {
        List<Configurable> list = myTree.getPathToRoot(configurable);
        String[] result = new String[list.size()];
        int add = 0;
        for (int i = list.size() - 1; i >= 0; i--) {
            result[add++] = list.get(i).getDisplayName().replace('\n', ' ');
        }
        return result;
    }

    private void checkModified(Configurable configurable) {
        fireModification(configurable);
    }

    private void fireModification(Configurable actual) {
        Collection<Configurable> toCheck = collectAllParentsAndSiblings(actual);

        for (Configurable configurable : toCheck) {
            fireModificationForItem(configurable);
        }
    }

    private Collection<Configurable> collectAllParentsAndSiblings(Configurable actual) {
        ArrayList<Configurable> result = new ArrayList<>();
        Configurable nearestParent = getContext().getParentConfigurable(actual);

        if (nearestParent != null) {
            Configurable parent = nearestParent;
            while (parent != null) {
                result.add(parent);
                parent = getContext().getParentConfigurable(parent);
            }

            result.addAll(getContext().getChildren(nearestParent));
        }
        else {
            result.add(actual);
        }

        return result;
    }

    private void fireModificationForItem(Configurable configurable) {
        if (configurable != null) {
            if (!myConfigurable2Content.containsKey(configurable) && ConfigurableWrapper.hasOwnContent(configurable)) {
                Application.get().invokeLater(() -> {
                    if (myDisposed) {
                        return;
                    }
                    AsyncResult<Void> result = AsyncResult.undefined();
                    initConfigurable(configurable, result);
                    result.doWhenDone(() -> {
                        if (myDisposed) {
                            return;
                        }
                        fireModificationInt(configurable);
                    });
                });
            }
            else if (myConfigurable2Content.containsKey(configurable)) {
                fireModificationInt(configurable);
            }
        }
    }

    @RequiredUIAccess
    private void fireModificationInt(Configurable configurable) {
        if (configurable.isModified()) {
            getContext().fireModifiedAdded(configurable, null);
        }
        else if (!configurable.isModified() && !getContext().getErrors().containsKey(configurable)) {
            getContext().fireModifiedRemoved(configurable, null);
        }
    }

    private void updateContent() {
        Configurable current = getContext().getCurrentConfigurable();

        assert current != null;

        ConfigurableContext content = myConfigurable2Content.get(current);
        if (content != null) {
            content.set(myContentWrapper);
        }
    }

    private boolean isShowing(Configurable configurable) {
        ConfigurableContext content = myConfigurable2Content.get(configurable);
        return content != null && content.isShowing();
    }

    @Nullable
    public String getHelpTopic() {
        Configurable current = getContext().getCurrentConfigurable();
        if (current == null || Configurable.DISABLED_HELP_ID.equals(current.getHelpTopic())) {
            return null;
        }

        Configurable target = current;
        List<String> ids = new ArrayList<>();
        while (target != null) {
            String helpTopic = target.getHelpTopic();
            if (Configurable.DISABLED_HELP_ID.equals(helpTopic)) {
                return null;
            }

            String id = null;
            if (target instanceof SearchableConfigurable) {
                id = target.getId();
            }

            // override for target id by help topic
            if (target == current && helpTopic != null) {
                id = helpTopic;
            }

            id = formatHelpId(id);

            if (StringUtil.isEmptyOrSpaces(id)) {
                return null;
            }

            ids.add(id);

            target = getContext().getParentConfigurable(target);
        }

        Collections.reverse(ids);

        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);

            for (int j = i + 1; j < ids.size(); j++) {
                String childId = ids.get(j);

                String idWithDot = id + ".";
                if (childId.startsWith(idWithDot)) {
                    ids.set(j, childId.substring(idWithDot.length(), childId.length()));
                }
            }
        }

        StringBuilder builder = new StringBuilder();

        PluginId pluginId = getPluginId(current);
        if (PluginIds.isPlatformPlugin(pluginId)) {
            builder.append("platform/settings/");
        }
        else {
            builder.append("plugins/").append(pluginId).append("/settings/");
        }
        builder.append(String.join("/", ids));

        return builder.toString();
    }

    @Nonnull
    private static PluginId getPluginId(@Nonnull Configurable configurable) {
        return configurable instanceof ConfigurableWrapper configurableWrapper
            ? PluginManager.getPluginId(configurableWrapper.getConfigurable().getClass())
            : PluginManager.getPluginId(configurable.getClass());
    }

    private static String formatHelpId(@Nullable String id) {
        return StringUtil.isEmptyOrSpaces(id) || id.contains(" ") ? null : id;
    }

    public boolean isFilterFieldVisible() {
        return mySearch.getParent() == mySearchWrapper;
    }

    public void setFilterFieldVisible(boolean requestFocus, boolean checkFocus) {
        if (isFilterFieldVisible() && checkFocus && requestFocus && !isSearchFieldFocused()) {
            IdeFocusManager.getGlobalInstance()
                .doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(mySearch, true));
            return;
        }

        myTreeDecoratorPanel.revalidate();
        myTreeDecoratorPanel.repaint();

        if (requestFocus) {
            IdeFocusManager.getGlobalInstance()
                .doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(mySearch, true));
        }
    }

    public boolean isSearchFieldFocused() {
        return mySearch.getTextEditor().isFocusOwner();
    }

    public void repaint() {
        myRootPanel.invalidate();
        myRootPanel.repaint();
    }

    @RequiredUIAccess
    public void reset(Configurable configurable, boolean notify) {
        configurable.reset();
        if (notify) {
            getContext().fireReset(configurable);
        }
    }

    @RequiredUIAccess
    public void apply() {
        Map<Configurable, ConfigurationException> errors = new LinkedHashMap<>();
        Set<Configurable> modified = getContext().getModified();
        for (Configurable each : modified) {
            try {
                each.apply();
                if (!each.isModified()) {
                    getContext().fireModifiedRemoved(each, null);
                }
            }
            catch (ConfigurationException e) {
                errors.put(each, e);
                LOG.debug(e);
            }
        }

        getContext().fireErrorsChanged(errors, null);

        if (!errors.isEmpty()) {
            myTree.select(errors.keySet().iterator().next());
        }

        myConfigurableSession.commit();
    }


    @Override
    public Object getData(@Nonnull Key<?> dataId) {
        if (Settings.KEY == dataId) {
            return this;
        }
        else if (ProjectStructureSelector.KEY == dataId) {
            return new ProjectStructureSelectorOverSettings(this);
        }
        return null;
    }

    public JTree getPreferredFocusedComponent() {
        return myTree.getTree();
    }

    @Override
    @RequiredUIAccess
    public void dispose() {
        UIAccess.assertIsUIThread();

        if (myDisposed) {
            return;
        }

        myDisposed = true;

        myConfigurableSession.drop();
        myConfigurableSession = null;

        Toolkit.getDefaultToolkit().removeAWTEventListener(this);

        visitRecursive(
            myBuildConfigurables,
            each -> {
                AsyncResult<Void> loadCb = myConfigurable2LoadCallback.get(each);
                if (loadCb != null) {
                    loadCb.doWhenProcessed(() -> {
                        UIAccess.assertIsUIThread();
                        each.disposeUIResources();
                    });
                }
                else {
                    each.disposeUIResources();
                }

                ConfigurableContext context = myConfigurable2Content.get(each);
                if (context != null) {
                    context.disposeWithTree();
                }
            }
        );

        myConfigurable2Content.clear();
        myConfigurable2LoadCallback.clear();

        ReflectionUtil.clearOwnFields(this, Predicates.<Field>alwaysTrue());
    }

    private static void visitRecursive(Configurable[] configurables, Consumer<Configurable> consumer) {
        for (Configurable configurable : configurables) {
            try {
                consumer.accept(configurable);
            }
            catch (Exception e) {
                LOG.error(e);
            }

            if (configurable instanceof Configurable.Composite) {
                visitRecursive(((Configurable.Composite)configurable).getConfigurables(), consumer);
            }
        }
    }

    public OptionsEditorContext getContext() {
        return myContext;
    }

    public void flushModifications() {
        fireModification(getContext().getCurrentConfigurable());
    }

    public boolean canApply() {
        return !getContext().getModified().isEmpty();
    }

    @Override
    public void eventDispatched(AWTEvent event) {
        if (event.getID() == MouseEvent.MOUSE_PRESSED || event.getID() == MouseEvent.MOUSE_RELEASED || event.getID() == MouseEvent.MOUSE_DRAGGED) {
            MouseEvent me = (MouseEvent)event;
            if (SwingUtilities.isDescendingFrom(me.getComponent(), SwingUtilities.getWindowAncestor(myContentWrapper)) || isPopupOverEditor(
                me.getComponent())) {
                queueModificationCheck();
                myFilter.clearTemporary();
            }
        }
        else if (event.getID() == KeyEvent.KEY_PRESSED || event.getID() == KeyEvent.KEY_RELEASED) {
            KeyEvent ke = (KeyEvent)event;
            if (SwingUtilities.isDescendingFrom(ke.getComponent(), myContentWrapper)) {
                queueModificationCheck();
            }
        }
    }

    private void queueModificationCheck() {
        Configurable configurable = getContext().getCurrentConfigurable();
        myModificationChecker.queue(new Update(this) {
            @Override
            public void run() {
                checkModified(configurable);
            }

            @Override
            public boolean isExpired() {
                return getContext().getCurrentConfigurable() != configurable;
            }
        });
    }

    private boolean isPopupOverEditor(Component c) {
        Window wnd = SwingUtilities.getWindowAncestor(c);
        return (wnd instanceof JWindow || wnd instanceof JDialog && ((JDialog)wnd).getModalityType() == Dialog.ModalityType.MODELESS) && myWindow != null && wnd.getParent() == myWindow;
    }

    private String getFilterText() {
        return mySearch.getText() != null ? mySearch.getText().trim() : "";
    }

    public void clearFilter() {
        mySearch.setText("");
    }
}
