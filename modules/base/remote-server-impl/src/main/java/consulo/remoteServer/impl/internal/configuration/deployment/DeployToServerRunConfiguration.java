// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package consulo.remoteServer.impl.internal.configuration.deployment;

import consulo.application.Application;
import consulo.component.extension.ExtensionPoint;
import consulo.component.persist.ComponentSerializationUtil;
import consulo.execution.RuntimeConfigurationException;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.LocatableConfiguration;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.execution.configuration.ui.SettingsEditorGroup;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.logging.Logger;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.remoteServer.ServerType;
import consulo.remoteServer.configuration.RemoteServer;
import consulo.remoteServer.configuration.RemoteServersManager;
import consulo.remoteServer.configuration.ServerConfiguration;
import consulo.remoteServer.configuration.deployment.DeploymentConfiguration;
import consulo.remoteServer.configuration.deployment.DeploymentConfigurator;
import consulo.remoteServer.configuration.deployment.DeploymentSource;
import consulo.remoteServer.configuration.deployment.DeploymentSourceType;
import consulo.remoteServer.localize.RemoteServerLocalize;
import consulo.remoteServer.runtime.deployment.DeployToServerStateProvider;
import consulo.remoteServer.runtime.deployment.SingletonDeploymentSourceType;
import consulo.util.collection.ContainerUtil;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.SkipDefaultValuesSerializationFilters;
import consulo.util.xml.serializer.WriteExternalException;
import consulo.util.xml.serializer.XmlSerializer;
import consulo.util.xml.serializer.annotation.Attribute;
import consulo.util.xml.serializer.annotation.Tag;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jdom.Element;

import java.util.List;

public class DeployToServerRunConfiguration<S extends ServerConfiguration, D extends DeploymentConfiguration>
    extends consulo.remoteServer.configuration.deployment.DeployToServerRunConfiguration<S, D>
    implements LocatableConfiguration {
    private static final Logger LOG = Logger.getInstance(DeployToServerRunConfiguration.class);
    private static final String DEPLOYMENT_SOURCE_TYPE_ATTRIBUTE = "type";
    public static final String SETTINGS_ELEMENT = "settings";
    private static final SkipDefaultValuesSerializationFilters SERIALIZATION_FILTERS = new SkipDefaultValuesSerializationFilters();
    private final String myServerTypeId;
    private final DeploymentConfigurator<D, S> myDeploymentConfigurator;
    private String myServerName;
    private boolean myDeploymentSourceIsLocked;
    private DeploymentSource myDeploymentSource;
    private D myDeploymentConfiguration;

    public DeployToServerRunConfiguration(Project project,
                                          ConfigurationFactory factory,
                                          String name,
                                          ServerType<S> serverType,
                                          DeploymentConfigurator<D, S> deploymentConfigurator) {
        super(project, factory, name);
        myServerTypeId = serverType.getId();
        myDeploymentConfigurator = deploymentConfigurator;
    }

    void lockDeploymentSource(@Nonnull SingletonDeploymentSourceType theOnlySourceType) {
        myDeploymentSourceIsLocked = true;
        myDeploymentSource = theOnlySourceType.getSingletonSource();
    }

    public @Nonnull ServerType<S> getServerType() {
        //noinspection unchecked
        ServerType<S> result = (ServerType<S>) ServerType.EP_NAME.findFirstSafe(next -> next.getId().equals(myServerTypeId));
        assert result != null : "Server type `" + myServerTypeId + "` had been unloaded already";
        return result;
    }

    public String getServerName() {
        return myServerName;
    }

    private @Nonnull DeploymentConfigurator<D, S> getDeploymentConfigurator() {
        return myDeploymentConfigurator;
    }

    @Override
    public @Nonnull SettingsEditor<DeployToServerRunConfiguration> getConfigurationEditor() {
        ServerType<S> serverType = getServerType();
        //noinspection unchecked
        SettingsEditor<DeployToServerRunConfiguration> commonEditor =
            myDeploymentSourceIsLocked ? new DeployToServerSettingsEditor.LockedSource(serverType, myDeploymentConfigurator, getProject(), myDeploymentSource)
                : new DeployToServerSettingsEditor.AnySource(serverType, myDeploymentConfigurator, getProject());


        SettingsEditorGroup<DeployToServerRunConfiguration> group = new SettingsEditorGroup<>();
        group.addEditor(RemoteServerLocalize.deploytoserverrunconfigurationTabTitleDeployment().get(), commonEditor);
        DeployToServerRunConfigurationExtensionsManager.getInstance().appendEditors(this, group);
        commonEditor.addSettingsEditorListener(e -> group.bulkUpdate(() -> {
        }));
        return group;
    }

    @Override
    public @Nullable RunProfileState getState(@Nonnull Executor executor, @Nonnull ExecutionEnvironment env) throws ExecutionException {
        String serverName = getServerName();
        if (serverName == null) {
            throw new ExecutionException(RemoteServerLocalize.deploytoserverrunconfigurationErrorServerRequired().get());
        }

        RemoteServer<S> server = findServer();
        if (server == null) {
            throw new ExecutionException(RemoteServerLocalize.deploytoserverrunconfigurationErrorServerNotFound(serverName).get());
        }

        if (myDeploymentSource == null) {
            throw new ExecutionException(RemoteServerLocalize.deploytoserverrunconfigurationErrorDeploymentNotSelected().get());
        }

        ExtensionPoint<DeployToServerStateProvider> point = Application.get().getExtensionPoint(DeployToServerStateProvider.class);
        for (DeployToServerStateProvider provider : point) {
            RunProfileState state = provider.getState(server, executor, env, myDeploymentSource, myDeploymentConfiguration);
            if (state != null) {
                return state;
            }
        }
        return null;
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
        RemoteServer<S> server = findServer();
        if (server == null) {
            return;
        }

        if (myDeploymentSource == null) {
            return;
        }

        myDeploymentConfiguration.checkConfiguration(server, myDeploymentSource, getProject());
    }

    private RemoteServer<S> findServer() {
        String serverName = getServerName();
        if (serverName == null) {
            return null;
        }

        return RemoteServersManager.getInstance().findByName(serverName, getServerType());
    }

    public void setServerName(String serverName) {
        myServerName = serverName;
    }

    public DeploymentSource getDeploymentSource() {
        return myDeploymentSource;
    }

    public void setDeploymentSource(DeploymentSource deploymentSource) {
        if (myDeploymentSourceIsLocked) {
            assert deploymentSource != null && deploymentSource == myDeploymentSource
                : "Can't replace locked " + myDeploymentSource + " with " + deploymentSource;
        }
        myDeploymentSource = deploymentSource;
    }

    public D getDeploymentConfiguration() {
        return myDeploymentConfiguration;
    }

    public void setDeploymentConfiguration(D deploymentConfiguration) {
        myDeploymentConfiguration = deploymentConfiguration;
    }

    @Override
    public boolean isGeneratedName() {
        return getDeploymentSource() != null && getDeploymentConfiguration() != null &&
            getDeploymentConfigurator().isGeneratedConfigurationName(getName(), getDeploymentSource(), getDeploymentConfiguration());
    }

    @Override
    public @Nullable String suggestedName() {
        if (getDeploymentSource() == null || getDeploymentConfiguration() == null) {
            return null;
        }
        return getDeploymentConfigurator().suggestConfigurationName(getDeploymentSource(), getDeploymentConfiguration());
    }

    @Override
    public void readExternal(@Nonnull Element element) throws InvalidDataException {
        super.readExternal(element);
        ConfigurationState state = XmlSerializer.deserialize(element, ConfigurationState.class);
        myServerName = null;
        myDeploymentSource = null;
        myServerName = state.myServerName;
        Element deploymentTag = state.myDeploymentTag;
        if (deploymentTag != null) {
            String typeId = deploymentTag.getAttributeValue(DEPLOYMENT_SOURCE_TYPE_ATTRIBUTE);
            DeploymentSourceType<?> type = findDeploymentSourceType(typeId);
            if (type != null) {
                myDeploymentSource = type.load(deploymentTag, getProject());
                myDeploymentConfiguration = myDeploymentConfigurator.createDefaultConfiguration(myDeploymentSource);
                ComponentSerializationUtil.loadComponentState(myDeploymentConfiguration.getSerializer(), deploymentTag.getChild(SETTINGS_ELEMENT));
            }
            else {
                LOG.warn("Cannot load deployment source for '" + getName() + "' run configuration: unknown deployment type '" + typeId + "'");
            }
        }

        DeployToServerRunConfigurationExtensionsManager.getInstance().readExternal(this, element);
    }

    private static @Nullable DeploymentSourceType<?> findDeploymentSourceType(@Nullable String id) {
        for (DeploymentSourceType<?> type : DeploymentSourceType.EP_NAME.getExtensionList()) {
            if (type.getId().equals(id)) {
                return type;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void writeExternal(@Nonnull Element element) throws WriteExternalException {
        ConfigurationState state = new ConfigurationState();
        state.myServerName = myServerName;
        if (myDeploymentSource != null) {
            DeploymentSourceType type = myDeploymentSource.getType();
            Element deploymentTag = new Element("deployment").setAttribute(DEPLOYMENT_SOURCE_TYPE_ATTRIBUTE, type.getId());
            type.save(myDeploymentSource, deploymentTag);
            if (myDeploymentConfiguration != null) {
                Object configurationState = myDeploymentConfiguration.getSerializer().getState();
                if (configurationState != null) {
                    Element settingsTag = new Element(SETTINGS_ELEMENT);
                    XmlSerializer.serializeInto(configurationState, settingsTag, SERIALIZATION_FILTERS);
                    deploymentTag.addContent(settingsTag);
                }
            }
            state.myDeploymentTag = deploymentTag;
        }
        XmlSerializer.serializeInto(state, element, SERIALIZATION_FILTERS);
        super.writeExternal(element);

        DeployToServerRunConfigurationExtensionsManager.getInstance().writeExternal(this, element);
    }

    @Override
    public RunConfiguration clone() {
        Element element = new Element("tag");
        try {
            writeExternal(element);
        }
        catch (WriteExternalException e) {
            LOG.error(e);
        }

        DeployToServerRunConfiguration result = (DeployToServerRunConfiguration) super.clone();
        if (myDeploymentSourceIsLocked) {
            result.lockDeploymentSource((SingletonDeploymentSourceType) myDeploymentSource.getType());
        }

        try {
            result.readExternal(element);
        }
        catch (InvalidDataException e) {
            LOG.error(e);
        }
        return result;
    }

    @Override
    public void onNewConfigurationCreated() {
        if (getServerName() == null) {
            RemoteServer<?> server = ContainerUtil.getFirstItem(RemoteServersManager.getInstance().getServers(getServerType()));
            if (server != null) {
                setServerName(server.getName());
            }
        }

        if (getDeploymentSource() == null) {
            DeploymentConfigurator<D, S> deploymentConfigurator = getDeploymentConfigurator();
            List<DeploymentSource> sources = deploymentConfigurator.getAvailableDeploymentSources();
            DeploymentSource source = ContainerUtil.getFirstItem(sources);
            if (source != null) {
                setDeploymentSource(source);
                setDeploymentConfiguration(deploymentConfigurator.createDefaultConfiguration(source));
                DeploymentSourceType type = source.getType();
                //noinspection unchecked
                type.setBuildBeforeRunTask(this, source);
            }
        }
    }

    public static class ConfigurationState {
        @Attribute("server-name")
        public String myServerName;

        @Tag("deployment")
        public Element myDeploymentTag;
    }
}
