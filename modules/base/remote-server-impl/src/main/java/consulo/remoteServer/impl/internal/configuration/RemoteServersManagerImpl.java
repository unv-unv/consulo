// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package consulo.remoteServer.impl.internal.configuration;

import consulo.application.ApplicationManager;
import consulo.component.persist.*;
import consulo.component.util.text.UniqueNameGenerator;
import consulo.remoteServer.ServerType;
import consulo.remoteServer.configuration.RemoteServer;
import consulo.remoteServer.configuration.RemoteServerListener;
import consulo.remoteServer.configuration.RemoteServersManager;
import consulo.remoteServer.configuration.ServerConfiguration;
import consulo.util.xml.serializer.SkipDefaultValuesSerializationFilters;
import consulo.util.xml.serializer.XmlSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@State(name = "RemoteServers",
    storages = @Storage(value = "remote-servers.xml", roamingType = RoamingType.DISABLED))
public final class RemoteServersManagerImpl extends RemoteServersManager implements PersistentStateComponent<RemoteServersManagerState> {
    private SkipDefaultValuesSerializationFilters myDefaultValuesFilter = new SkipDefaultValuesSerializationFilters();
    private final List<RemoteServer<?>> myServers = new CopyOnWriteArrayList<>();
    private final List<RemoteServerState> myUnknownServers = new ArrayList<>();

    public RemoteServersManagerImpl() {
    }

    @Override
    public List<RemoteServer<?>> getServers() {
        return Collections.unmodifiableList(myServers);
    }

    @Override
    public <C extends ServerConfiguration> List<RemoteServer<C>> getServers(@NotNull ServerType<C> type) {
        List<RemoteServer<C>> servers = new ArrayList<>();
        for (RemoteServer<?> server : myServers) {
            if (server.getType().equals(type)) {
                //noinspection unchecked
                servers.add((RemoteServer<C>) server);
            }
        }
        return servers;
    }

    @Override
    public @Nullable <C extends ServerConfiguration> RemoteServer<C> findByName(@NotNull String name, @NotNull ServerType<C> type) {
        for (RemoteServer<?> server : myServers) {
            if (server.getType().equals(type) && server.getName().equals(name)) {
                //noinspection unchecked
                return (RemoteServer<C>) server;
            }
        }
        return null;
    }

    @Override
    public @NotNull <C extends ServerConfiguration> RemoteServer<C> createServer(@NotNull ServerType<C> type, @NotNull String name) {
        return new RemoteServerImpl<>(name, type, type.createDefaultConfiguration());
    }

    @Override
    public @NotNull <C extends ServerConfiguration> RemoteServer<C> createServer(@NotNull ServerType<C> type) {
        String name = UniqueNameGenerator.generateUniqueName(
            type.getPresentableName().get(), s -> getServers(type).stream().map(RemoteServer::getName).noneMatch(s::equals));
        return createServer(type, name);
    }

    @Override
    public void addServer(RemoteServer<?> server) {
        myServers.add(server);
        ApplicationManager.getApplication().getMessageBus().syncPublisher(RemoteServerListener.class).serverAdded(server);
    }

    @Override
    public void removeServer(RemoteServer<?> server) {
        myServers.remove(server);
        ApplicationManager.getApplication().getMessageBus().syncPublisher(RemoteServerListener.class).serverRemoved(server);
    }

    @Override
    public @NotNull RemoteServersManagerState getState() {
        RemoteServersManagerState state = new RemoteServersManagerState();
        for (RemoteServer<?> server : myServers) {
            state.myServers.add(createServerState(server));
        }
        state.myServers.addAll(myUnknownServers);
        return state;
    }

    @Override
    public void loadState(@NotNull RemoteServersManagerState state) {
        myUnknownServers.clear();
        myServers.clear();

        List<CloudConfigurationBase<?>> needsMigration = new LinkedList<>();
        for (RemoteServerState server : state.myServers) {
            ServerType<?> type = findServerType(server.myTypeId);
            if (type == null) {
                myUnknownServers.add(server);
            }
            else {
                RemoteServer<? extends ServerConfiguration> nextServer = createConfiguration(type, server);
                myServers.add(nextServer);
                ServerConfiguration nextConfig = nextServer.getConfiguration();
                if (nextConfig instanceof CloudConfigurationBase && ((CloudConfigurationBase<?>) nextConfig).shouldMigrateToPasswordSafe()) {
                    needsMigration.add((CloudConfigurationBase<?>) nextConfig);
                }
            }
        }

        if (!needsMigration.isEmpty()) {
            ApplicationManager.getApplication().invokeLater(() -> {
                for (CloudConfigurationBase nextConfig : needsMigration) {
                    nextConfig.migrateToPasswordSafe();
                }
            });
        }
    }

    private @NotNull RemoteServerState createServerState(@NotNull RemoteServer<?> server) {
        RemoteServerState serverState = new RemoteServerState();
        serverState.myName = server.getName();
        serverState.myTypeId = server.getType().getId();
        serverState.myConfiguration = XmlSerializer.serialize(server.getConfiguration().getSerializer().getState(), myDefaultValuesFilter);
        return serverState;
    }

    private static @NotNull <C extends ServerConfiguration> RemoteServerImpl<C> createConfiguration(ServerType<C> type, RemoteServerState server) {
        C configuration = type.createDefaultConfiguration();
        PersistentStateComponent<?> serializer = configuration.getSerializer();
        ComponentSerializationUtil.loadComponentState(serializer, server.myConfiguration);
        return new RemoteServerImpl<>(server.myName, type, configuration);
    }

    private static @Nullable ServerType<?> findServerType(@NotNull String typeId) {
        return ServerType.EP_NAME.findFirstSafe(next -> typeId.equals(next.getId()));
    }
}
