// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.undoRedo;

import consulo.annotation.DeprecationInfo;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.application.Application;
import consulo.disposer.Disposable;
import consulo.document.Document;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.undoRedo.builder.CommandBuilder;
import consulo.undoRedo.event.CommandListener;
import consulo.util.lang.EmptyRunnable;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * A class for defining 'command' scopes. Every undoable change should be executed as part of a command. Commands can nest, in such a case
 * only the outer-most command is taken into account. Commands with the same 'group id' are merged for undo/redo purposes. 'Transparent'
 * actions (commands) are similar to usual commands but don't create a separate undo/redo step - they are undone/redone together with a
 * 'adjacent' non-transparent commands.
 */
@ServiceAPI(ComponentScope.APPLICATION)
public abstract class CommandProcessor {
    public interface ExecutableCommandBuilder extends CommandBuilder<ExecutableCommandBuilder> {
        @RequiredUIAccess
        void execute();

        @RequiredUIAccess
        default void executeInWriteAction() {
            Application.get().runWriteAction(this::execute);
        }
    }

    @Nonnull
    public static CommandProcessor getInstance() {
        return Application.get().getInstance(CommandProcessor.class);
    }

    @Nonnull
    public abstract ExecutableCommandBuilder newCommand(@Nonnull Runnable command);

    @Deprecated
    @DeprecationInfo("Use #newCommand(Runnable).execute()")
    @RequiredUIAccess
    public void executeCommand(@Nonnull Runnable runnable, @Nullable String name, @Nullable Object groupId) {
        newCommand(runnable)
            .withName(LocalizeValue.ofNullable(name))
            .withGroupId(groupId)
            .execute();
    }

    @Deprecated
    @DeprecationInfo("Use #newCommand(Runnable).execute()")
    @RequiredUIAccess
    public void executeCommand(
        @Nullable Project project,
        @Nonnull Runnable runnable,
        @Nullable String name,
        @Nullable Object groupId
    ) {
        newCommand(runnable)
            .withProject(project)
            .withName(LocalizeValue.ofNullable(name))
            .withGroupId(groupId)
            .execute();
    }

    @Deprecated
    @DeprecationInfo("Use #newCommand(Runnable).execute()")
    @RequiredUIAccess
    public void executeCommand(
        @Nullable Project project,
        @Nonnull Runnable runnable,
        @Nullable String name,
        @Nullable Object groupId,
        @Nullable Document document
    ) {
        newCommand(runnable)
            .withProject(project)
            .withName(LocalizeValue.ofNullable(name))
            .withGroupId(groupId)
            .withDocument(document)
            .execute();
    }

    @Deprecated
    @DeprecationInfo("Use #newCommand(Runnable).execute()")
    @RequiredUIAccess
    public void executeCommand(
        @Nullable Project project,
        @Nonnull Runnable runnable,
        @Nullable String name,
        @Nullable Object groupId,
        @Nonnull UndoConfirmationPolicy confirmationPolicy
    ) {
        newCommand(runnable)
            .withProject(project)
            .withName(LocalizeValue.ofNullable(name))
            .withGroupId(groupId)
            .withUndoConfirmationPolicy(confirmationPolicy)
            .execute();
    }

    @Deprecated
    @DeprecationInfo("Use #newCommand(Runnable).execute()")
    @RequiredUIAccess
    public void executeCommand(
        @Nullable Project project,
        @Nonnull Runnable command,
        @Nullable String name,
        @Nullable Object groupId,
        @Nonnull UndoConfirmationPolicy confirmationPolicy,
        @Nullable Document document
    ) {
        newCommand(command)
            .withProject(project)
            .withName(LocalizeValue.ofNullable(name))
            .withGroupId(groupId)
            .withUndoConfirmationPolicy(confirmationPolicy)
            .withDocument(document)
            .execute();
    }

    /**
     * @param shouldRecordCommandForActiveDocument {@code false} if the action is not supposed to be recorded
     *                                             into the currently open document's history.
     *                                             Examples of such actions: Create New File, Change Project Settings etc.
     *                                             Default is {@code true}.
     */
    @Deprecated
    @DeprecationInfo("Use #newCommand(Runnable).execute()")
    @RequiredUIAccess
    public void executeCommand(
        @Nullable Project project,
        @Nonnull Runnable command,
        @Nullable String name,
        @Nullable Object groupId,
        @Nonnull UndoConfirmationPolicy confirmationPolicy,
        boolean shouldRecordCommandForActiveDocument
    ) {
        newCommand(command)
            .withProject(project)
            .withName(LocalizeValue.ofNullable(name))
            .withGroupId(groupId)
            .withUndoConfirmationPolicy(confirmationPolicy)
            .withShouldRecordActionForActiveDocument(shouldRecordCommandForActiveDocument)
            .execute();
    }

    public void setCurrentCommandName(@Nonnull LocalizeValue name) {
        setCurrentCommandName(name == LocalizeValue.empty() ? null : name.get());
    }

    @Deprecated
    @DeprecationInfo("Use variant with LocalizeValue")
    public void setCurrentCommandName(@Nullable String name) {
        setCurrentCommandName(LocalizeValue.ofNullable(name));
    }

    public abstract void setCurrentCommandGroupId(@Nullable Object groupId);

    @Nullable
    @Deprecated
    @DeprecationInfo("Use #hasCurrentCommand()")
    public final Runnable getCurrentCommand() {
        return hasCurrentCommand() ? EmptyRunnable.getInstance() : null;
    }

    public abstract boolean hasCurrentCommand();

    //TODO: rename into #getCurrentCommandName() after deprecation removal
    @Nonnull
    public LocalizeValue getCurrentCommandNameValue() {
        return LocalizeValue.ofNullable(getCurrentCommandName());
    }

    @Deprecated
    @DeprecationInfo("Use #getCurrentCommandNameValue()")
    @Nullable
    public String getCurrentCommandName() {
        LocalizeValue currentCommandName = getCurrentCommandNameValue();
        return currentCommandName == LocalizeValue.empty() ? null : currentCommandName.get();
    }

    @Nullable
    public abstract Object getCurrentCommandGroupId();

    @Nullable
    public abstract Project getCurrentCommandProject();

    /**
     * Defines a scope which contains undoable actions, for which there won't be a separate undo/redo step - they will be undone/redone along
     * with 'adjacent' command.
     */
    public abstract void runUndoTransparentAction(@Nonnull Runnable action);

    /**
     * @see #runUndoTransparentAction(Runnable)
     */
    public abstract boolean isUndoTransparentActionInProgress();

    public abstract void markCurrentCommandAsGlobal(@Nullable Project project);

    public abstract void addAffectedDocuments(@Nullable Project project, @Nonnull Document... docs);

    public abstract void addAffectedFiles(@Nullable Project project, @Nonnull VirtualFile... files);

    /**
     * @deprecated use {@link CommandListener#class}
     */
    @Deprecated
    public abstract void addCommandListener(@Nonnull CommandListener listener);

    /**
     * @deprecated use {@link CommandListener#class}
     */
    @Deprecated
    public void addCommandListener(@Nonnull CommandListener listener, @Nonnull Disposable parentDisposable) {
        Application.get().getMessageBus().connect(parentDisposable).subscribe(CommandListener.class, listener);
    }

    /**
     * @deprecated use {@link CommandListener#class}
     */
    @Deprecated
    public abstract void removeCommandListener(@Nonnull CommandListener listener);
}
