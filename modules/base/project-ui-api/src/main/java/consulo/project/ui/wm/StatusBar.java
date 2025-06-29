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
package consulo.project.ui.wm;

import consulo.annotation.DeprecationInfo;
import consulo.disposer.Disposable;
import consulo.project.Project;
import consulo.ui.NotificationType;
import consulo.ui.ex.popup.BalloonHandler;
import consulo.ui.image.Image;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * @author spleaner
 */
public interface StatusBar extends Disposable {
    Key<StatusBar> KEY = Key.create(StatusBar.class);

    void updateWidget(@Nonnull String id);

    void updateWidget(@Nonnull Predicate<StatusBarWidget> widgetPredicate);

    @Nonnull
    <W extends StatusBarWidget> Optional<W> findWidget(@Nonnull Predicate<StatusBarWidget> predicate);

    void fireNotificationPopup(@Nonnull JComponent content, Color backgroundColor);

    StatusBar createChild();

    StatusBar findChild(Component c);

    IdeFrame getFrame();

    void install(IdeFrame frame);

    @Nullable
    Project getProject();

    @Nonnull
    default consulo.ui.Component getUIComponent() {
        throw new AbstractMethodError();
    }

    @Deprecated
    @DeprecationInfo("AWT Dependency")
    default JComponent getComponent() {
        // override isUnified() too
        throw new AbstractMethodError();
    }

    default boolean isUnified() {
        return false;
    }

    default BalloonHandler notifyProgressByBalloon(@Nonnull NotificationType type, @Nonnull String htmlBody) {
        return notifyProgressByBalloon(type, htmlBody, null, null);
    }

    BalloonHandler notifyProgressByBalloon(
        @Nonnull NotificationType type,
        @Nonnull String htmlBody,
        @Nullable Image icon,
        @Nullable HyperlinkListener listener
    );
}
