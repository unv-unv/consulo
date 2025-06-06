/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package consulo.execution.impl.internal.configuration;

import consulo.component.extension.ExtensionException;
import consulo.execution.ExecutionTarget;
import consulo.execution.RunnerAndConfigurationSettings;
import consulo.execution.RuntimeConfigurationException;
import consulo.execution.configuration.*;
import consulo.execution.executor.Executor;
import consulo.execution.localize.ExecutionLocalize;
import consulo.execution.runner.ProgramRunner;
import consulo.execution.runner.RunnerRegistry;
import consulo.logging.Logger;
import consulo.util.collection.SmartList;
import consulo.util.jdom.JDOMUtil;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.JDOMExternalizable;
import consulo.util.xml.serializer.WriteExternalException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jdom.Element;

import java.util.*;
import java.util.function.Supplier;

/**
 * @author dyoma
 */
public class RunnerAndConfigurationSettingsImpl implements JDOMExternalizable, Cloneable, RunnerAndConfigurationSettings, Comparable<RunnerAndConfigurationSettingsImpl> {
    private static final Logger LOG = Logger.getInstance(RunnerAndConfigurationSettingsImpl.class);

    private static final String RUNNER_ELEMENT = "RunnerSettings";
    private static final String CONFIGURATION_ELEMENT = "ConfigurationWrapper";
    private static final String RUNNER_ID = "RunnerId";

    private static final Comparator<Element> RUNNER_COMPARATOR = new Comparator<>() {
        @Override
        public int compare(@Nonnull Element o1, @Nonnull Element o2) {
            String attributeValue1 = o1.getAttributeValue(RUNNER_ID);
            if (attributeValue1 == null) {
                return 1;
            }
            return StringUtil.compare(attributeValue1, o2.getAttributeValue(RUNNER_ID), false);
        }
    };

    private static final String CONFIGURATION_TYPE_ATTRIBUTE = "type";
    private static final String FACTORY_NAME_ATTRIBUTE = "factoryName";
    private static final String FOLDER_NAME = "folderName";
    private static final String TEMPLATE_FLAG_ATTRIBUTE = "default";
    public static final String NAME_ATTR = "name";
    protected static final String DUMMY_ELEMENT_NAME = "dummy";
    private static final String TEMPORARY_ATTRIBUTE = "temporary";
    private static final String EDIT_BEFORE_RUN = "editBeforeRun";
    public static final String SINGLETON = "singleton";

    /**
     * for compatibility
     */
    private static final String TEMP_CONFIGURATION = "tempConfiguration";

    private final RunManagerImpl myManager;
    private RunConfiguration myConfiguration;
    private boolean myIsTemplate;

    private final Map<ProgramRunner, RunnerSettings> myRunnerSettings = new HashMap<>();
    private List<Element> myUnloadedRunnerSettings;
    // to avoid changed files
    private final Set<String> myLoadedRunnerSettings = new HashSet<>();

    private final Map<ProgramRunner, ConfigurationPerRunnerSettings> myConfigurationPerRunnerSettings = new HashMap<>();
    private List<Element> myUnloadedConfigurationPerRunnerSettings;

    private boolean myTemporary;
    private boolean myEditBeforeRun;
    private boolean mySingleton;
    private boolean myWasSingletonSpecifiedExplicitly;
    private String myFolderName;
    //private String myID = null;

    private String myFilePathIfRunningCurrentFile;

    public RunnerAndConfigurationSettingsImpl(RunManagerImpl manager) {
        myManager = manager;
    }

    public RunnerAndConfigurationSettingsImpl(RunManagerImpl manager, @Nonnull RunConfiguration configuration, boolean isTemplate) {
        myManager = manager;
        myConfiguration = configuration;
        myIsTemplate = isTemplate;
    }

    @Override
    @Nullable
    public ConfigurationFactory getFactory() {
        return myConfiguration == null ? null : myConfiguration.getFactory();
    }

    @Override
    public boolean isTemplate() {
        return myIsTemplate;
    }

    @Override
    public boolean isTemporary() {
        return myTemporary;
    }

    @Override
    public void setTemporary(boolean temporary) {
        myTemporary = temporary;
    }

    @Override
    public RunConfiguration getConfiguration() {
        return myConfiguration;
    }

    @Override
    public Supplier<RunnerAndConfigurationSettings> createFactory() {
        return () -> {
            RunConfiguration configuration = myConfiguration.getFactory()
                .createConfiguration(ExecutionLocalize.defaultRunConfigurationName().get(), myConfiguration);
            return new RunnerAndConfigurationSettingsImpl(myManager, configuration, false);
        };
    }

    @Override
    public void setName(String name) {
        myConfiguration.setName(name);
    }

    @Override
    public String getName() {
        return myConfiguration.getName();
    }

    @Override
    public String getUniqueID() {
        //noinspection deprecation
        return myConfiguration.getType().getDisplayName() + "." + myConfiguration.getName() +
            (myConfiguration instanceof UnknownRunConfiguration ? myConfiguration.getUniqueID() : "");
        //if (myID == null) {
        //  myID = UUID.randomUUID().toString();
        //}
        //return myID;
    }

    @Override
    public void setEditBeforeRun(boolean b) {
        myEditBeforeRun = b;
    }

    @Override
    public boolean isEditBeforeRun() {
        return myEditBeforeRun;
    }

    @Override
    public void setSingleton(boolean singleton) {
        mySingleton = singleton;
    }

    @Override
    public boolean isSingleton() {
        return mySingleton;
    }

    @Override
    public void setFolderName(@Nullable String folderName) {
        myFolderName = folderName;
    }

    @Nullable
    @Override
    public String getFolderName() {
        return myFolderName;
    }

    @Nullable
    private ConfigurationFactory getFactory(final Element element) {
        final String typeName = element.getAttributeValue(CONFIGURATION_TYPE_ATTRIBUTE);
        String factoryName = element.getAttributeValue(FACTORY_NAME_ATTRIBUTE);
        return myManager.getFactory(typeName, factoryName, !myIsTemplate);
    }

    @Override
    public void readExternal(Element element) throws InvalidDataException {
        myIsTemplate = Boolean.valueOf(element.getAttributeValue(TEMPLATE_FLAG_ATTRIBUTE));
        myTemporary = Boolean.valueOf(element.getAttributeValue(TEMPORARY_ATTRIBUTE)) || TEMP_CONFIGURATION.equals(element.getName());
        myEditBeforeRun = Boolean.valueOf(element.getAttributeValue(EDIT_BEFORE_RUN));
        myFolderName = element.getAttributeValue(FOLDER_NAME);
        //assert myID == null: "myId must be null at readExternal() stage";
        //myID = element.getAttributeValue(UNIQUE_ID, UUID.randomUUID().toString());
        final ConfigurationFactory factory = getFactory(element);
        if (factory == null) {
            return;
        }

        myWasSingletonSpecifiedExplicitly = false;
        if (myIsTemplate) {
            mySingleton = factory.isConfigurationSingletonByDefault();
        }
        else {
            String singletonStr = element.getAttributeValue(SINGLETON);
            if (StringUtil.isEmpty(singletonStr)) {
                mySingleton = factory.isConfigurationSingletonByDefault();
            }
            else {
                myWasSingletonSpecifiedExplicitly = true;
                mySingleton = Boolean.parseBoolean(singletonStr);
            }
        }

        if (myIsTemplate) {
            myConfiguration = myManager.getConfigurationTemplate(factory).getConfiguration();
        }
        else {
            // shouldn't call createConfiguration since it calls StepBeforeRunProviders that
            // may not be loaded yet. This creates initialization order issue.
            myConfiguration = myManager.doCreateConfiguration(element.getAttributeValue(NAME_ATTR), factory, false);
        }

        myConfiguration.readExternal(element);
        if (myUnloadedRunnerSettings != null) {
            myUnloadedRunnerSettings.clear();
        }
        myLoadedRunnerSettings.clear();
        for (Element runnerElement : element.getChildren(RUNNER_ELEMENT)) {
            String id = runnerElement.getAttributeValue(RUNNER_ID);
            ProgramRunner runner = RunnerRegistry.getInstance().findRunnerById(id);
            if (runner != null) {
                myLoadedRunnerSettings.add(id);
                RunnerSettings settings = createRunnerSettings(runner);
                if (settings != null) {
                    settings.readExternal(runnerElement);
                }
                myRunnerSettings.put(runner, settings);
            }
            else {
                if (myUnloadedRunnerSettings == null) {
                    myUnloadedRunnerSettings = new SmartList<>();
                }
                myUnloadedRunnerSettings.add(runnerElement);
            }
        }

        myUnloadedConfigurationPerRunnerSettings = null;
        for (Iterator<Element> iterator = element.getChildren(CONFIGURATION_ELEMENT).iterator(); iterator.hasNext(); ) {
            Element configurationElement = iterator.next();
            ProgramRunner runner = RunnerRegistry.getInstance().findRunnerById(configurationElement.getAttributeValue(RUNNER_ID));
            if (runner != null) {
                ConfigurationPerRunnerSettings settings = myConfiguration.createRunnerSettings(new InfoProvider(runner));
                if (settings != null) {
                    settings.readExternal(configurationElement);
                }
                myConfigurationPerRunnerSettings.put(runner, settings);
            }
            else {
                if (myUnloadedConfigurationPerRunnerSettings == null) {
                    myUnloadedConfigurationPerRunnerSettings = new SmartList<>();
                }

                iterator.remove();
                myUnloadedConfigurationPerRunnerSettings.add(configurationElement);
            }
        }
    }

    @Override
    public void writeExternal(Element element) throws WriteExternalException {
        final ConfigurationFactory factory = myConfiguration.getFactory();
        if (!(myConfiguration instanceof UnknownRunConfiguration)) {
            element.setAttribute(TEMPLATE_FLAG_ATTRIBUTE, String.valueOf(myIsTemplate));
            if (!myIsTemplate) {
                element.setAttribute(NAME_ATTR, myConfiguration.getName());
            }
            element.setAttribute(CONFIGURATION_TYPE_ATTRIBUTE, factory.getType().getId());
            element.setAttribute(FACTORY_NAME_ATTRIBUTE, factory.getId());
            if (myFolderName != null) {
                element.setAttribute(FOLDER_NAME, myFolderName);
            }
            //element.setAttribute(UNIQUE_ID, getUniqueID());

            if (isEditBeforeRun()) {
                element.setAttribute(EDIT_BEFORE_RUN, String.valueOf(true));
            }
            if (myWasSingletonSpecifiedExplicitly || mySingleton != factory.isConfigurationSingletonByDefault()) {
                element.setAttribute(SINGLETON, String.valueOf(mySingleton));
            }
            if (myTemporary) {
                element.setAttribute(TEMPORARY_ATTRIBUTE, Boolean.toString(true));
            }
        }

        myConfiguration.writeExternal(element);

        if (!(myConfiguration instanceof UnknownRunConfiguration)) {
            writeRunnerSettings(RUNNER_COMPARATOR, element);
            writeConfigurationPerRunnerSettings(RUNNER_COMPARATOR, element);
        }
    }

    private void writeConfigurationPerRunnerSettings(final Comparator<Element> runnerComparator, final Element element)
        throws WriteExternalException {
        final ArrayList<Element> configurationPerRunnerSettings = new ArrayList<>();
        for (ProgramRunner runner : myConfigurationPerRunnerSettings.keySet()) {
            ConfigurationPerRunnerSettings settings = myConfigurationPerRunnerSettings.get(runner);
            Element runnerElement = new Element(CONFIGURATION_ELEMENT);
            if (settings != null) {
                settings.writeExternal(runnerElement);
            }
            runnerElement.setAttribute(RUNNER_ID, runner.getRunnerId());
            configurationPerRunnerSettings.add(runnerElement);
        }
        if (myUnloadedConfigurationPerRunnerSettings != null) {
            for (Element unloadedCRunnerSetting : myUnloadedConfigurationPerRunnerSettings) {
                configurationPerRunnerSettings.add(unloadedCRunnerSetting.clone());
            }
        }
        Collections.sort(configurationPerRunnerSettings, runnerComparator);
        for (Element runnerConfigurationSetting : configurationPerRunnerSettings) {
            element.addContent(runnerConfigurationSetting);
        }
    }

    private void writeRunnerSettings(@Nonnull Comparator<Element> runnerComparator, @Nonnull Element element) throws WriteExternalException {
        List<Element> runnerSettings = new SmartList<>();
        for (ProgramRunner runner : myRunnerSettings.keySet()) {
            RunnerSettings settings = myRunnerSettings.get(runner);
            boolean wasLoaded = myLoadedRunnerSettings.contains(runner.getRunnerId());
            if (settings == null && !wasLoaded) {
                continue;
            }

            Element runnerElement = new Element(RUNNER_ELEMENT);
            if (settings != null) {
                settings.writeExternal(runnerElement);
            }
            if (wasLoaded || !JDOMUtil.isEmpty(runnerElement)) {
                runnerElement.setAttribute(RUNNER_ID, runner.getRunnerId());
                runnerSettings.add(runnerElement);
            }
        }
        if (myUnloadedRunnerSettings != null) {
            for (Element unloadedRunnerSetting : myUnloadedRunnerSettings) {
                runnerSettings.add(unloadedRunnerSetting.clone());
            }
        }
        Collections.sort(runnerSettings, runnerComparator);
        for (Element runnerSetting : runnerSettings) {
            element.addContent(runnerSetting);
        }
    }

    @Override
    public void checkSettings() throws RuntimeConfigurationException {
        checkSettings(null);
    }

    @Override
    public void checkSettings(@Nullable Executor executor) throws RuntimeConfigurationException {
        myConfiguration.checkConfiguration();
        if (myConfiguration instanceof RunConfigurationBase) {
            final RunConfigurationBase runConfigurationBase = (RunConfigurationBase) myConfiguration;
            Set<ProgramRunner> runners = new HashSet<>();
            runners.addAll(myRunnerSettings.keySet());
            runners.addAll(myConfigurationPerRunnerSettings.keySet());
            for (ProgramRunner runner : runners) {
                if (executor == null || runner.canRun(executor.getId(), myConfiguration)) {
                    runConfigurationBase.checkRunnerSettings(runner, myRunnerSettings.get(runner), myConfigurationPerRunnerSettings.get(runner));
                }
            }
            if (executor != null) {
                runConfigurationBase.checkSettingsBeforeRun();
            }
        }
    }

    @Override
    public boolean canRunOn(@Nonnull ExecutionTarget target) {
        if (myConfiguration instanceof TargetAwareRunProfile) {
            return ((TargetAwareRunProfile) myConfiguration).canRunOn(target);
        }
        return true;
    }

    @Override
    public RunnerSettings getRunnerSettings(@Nonnull ProgramRunner runner) {
        if (!myRunnerSettings.containsKey(runner)) {
            try {
                RunnerSettings runnerSettings = createRunnerSettings(runner);
                myRunnerSettings.put(runner, runnerSettings);
                return runnerSettings;
            }
            catch (AbstractMethodError ignored) {
                LOG.error("Update failed for: " + myConfiguration.getType().getDisplayName() + ", runner: " + runner.getRunnerId(), new ExtensionException(runner.getClass()));
            }
        }
        return myRunnerSettings.get(runner);
    }

    @Override
    @Nullable
    public ConfigurationPerRunnerSettings getConfigurationSettings(@Nonnull ProgramRunner runner) {
        if (!myConfigurationPerRunnerSettings.containsKey(runner)) {
            ConfigurationPerRunnerSettings settings = myConfiguration.createRunnerSettings(new InfoProvider(runner));
            myConfigurationPerRunnerSettings.put(runner, settings);
            return settings;
        }
        return myConfigurationPerRunnerSettings.get(runner);
    }

    @Override
    @Nullable
    public ConfigurationType getType() {
        return myConfiguration == null ? null : myConfiguration.getType();
    }

    @Override
    public RunnerAndConfigurationSettings clone() {
        RunnerAndConfigurationSettingsImpl copy = new RunnerAndConfigurationSettingsImpl(myManager, myConfiguration.clone(), false);
        copy.importRunnerAndConfigurationSettings(this);
        return copy;
    }

    public void importRunnerAndConfigurationSettings(RunnerAndConfigurationSettingsImpl template) {
        try {
            for (ProgramRunner runner : template.myRunnerSettings.keySet()) {
                RunnerSettings data = createRunnerSettings(runner);
                myRunnerSettings.put(runner, data);
                if (data != null) {
                    Element temp = new Element(DUMMY_ELEMENT_NAME);
                    RunnerSettings templateSettings = template.myRunnerSettings.get(runner);
                    if (templateSettings != null) {
                        templateSettings.writeExternal(temp);
                        data.readExternal(temp);
                    }
                }
            }

            for (ProgramRunner runner : template.myConfigurationPerRunnerSettings.keySet()) {
                ConfigurationPerRunnerSettings data = myConfiguration.createRunnerSettings(new InfoProvider(runner));
                myConfigurationPerRunnerSettings.put(runner, data);
                if (data != null) {
                    Element temp = new Element(DUMMY_ELEMENT_NAME);
                    ConfigurationPerRunnerSettings templateSettings = template.myConfigurationPerRunnerSettings.get(runner);
                    if (templateSettings != null) {
                        templateSettings.writeExternal(temp);
                        data.readExternal(temp);
                    }
                }
            }
            setSingleton(template.isSingleton());
            setEditBeforeRun(template.isEditBeforeRun());
        }
        catch (WriteExternalException | InvalidDataException e) {
            LOG.error(e);
        }
    }

    private RunnerSettings createRunnerSettings(final ProgramRunner runner) {
        return runner.createConfigurationData(new InfoProvider(runner));
    }

    @Override
    public int compareTo(@Nonnull final RunnerAndConfigurationSettingsImpl r) {
        return getName().compareTo(r.getName());
    }

    public void setFilePathIfRunningCurrentFile(String filePathIfRunningCurrentFile) {
        myFilePathIfRunningCurrentFile = filePathIfRunningCurrentFile;
    }

    public String getFilePathIfRunningCurrentFile() {
        return myFilePathIfRunningCurrentFile;
    }

    @Override
    public String toString() {
        ConfigurationType type = getType();
        return (type != null ? type.getDisplayName() + ": " : "") + (isTemplate() ? "<template>" : getName());
    }

    private class InfoProvider implements ConfigurationInfoProvider {
        private final ProgramRunner myRunner;

        public InfoProvider(ProgramRunner runner) {
            myRunner = runner;
        }

        @Override
        public ProgramRunner getRunner() {
            return myRunner;
        }

        @Override
        public RunConfiguration getConfiguration() {
            return myConfiguration;
        }

        @Override
        public RunnerSettings getRunnerSettings() {
            return RunnerAndConfigurationSettingsImpl.this.getRunnerSettings(myRunner);
        }

        @Override
        public ConfigurationPerRunnerSettings getConfigurationSettings() {
            return RunnerAndConfigurationSettingsImpl.this.getConfigurationSettings(myRunner);
        }
    }
}
