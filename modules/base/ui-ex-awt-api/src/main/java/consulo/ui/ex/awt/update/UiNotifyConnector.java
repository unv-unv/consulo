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
package consulo.ui.ex.awt.update;

import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.update.Activatable;

import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.lang.ref.WeakReference;

public class UiNotifyConnector implements Disposable, HierarchyListener {
    @Nonnull
    private final WeakReference<Component> myComponent;
    private Activatable myTarget;

    public UiNotifyConnector(@Nonnull Component component, @Nonnull Activatable target) {
        myComponent = new WeakReference<>(component);
        myTarget = target;
        if (component.isShowing()) {
            showNotify();
        }
        else {
            hideNotify();
        }

        if (isDisposed()) {
            return;
        }
        component.addHierarchyListener(this);
    }

    @Override
    public void hierarchyChanged(@Nonnull HierarchyEvent e) {
        if (isDisposed()) {
            return;
        }

        if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) > 0) {
            Runnable runnable = () -> {
                Component c = myComponent.get();
                if (isDisposed() || c == null) {
                    return;
                }

                if (c.isShowing()) {
                    showNotify();
                }
                else {
                    hideNotify();
                }
            };
            @SuppressWarnings("deprecation") Application app = ApplicationManager.getApplication();
            if (app != null && app.isDispatchThread()) {
                app.invokeLater(runnable, app.getCurrentModalityState());
            }
            else {
                //noinspection SSBasedInspection
                SwingUtilities.invokeLater(runnable);
            }
        }
    }

    protected void hideNotify() {
        myTarget.hideNotify();
    }

    protected void showNotify() {
        myTarget.showNotify();
    }

    protected void hideOnDispose() {
        myTarget.hideOnDispose();
    }

    @Override
    public void dispose() {
        if (isDisposed()) {
            return;
        }

        hideOnDispose();
        Component c = myComponent.get();
        if (c != null) {
            c.removeHierarchyListener(this);
        }

        myTarget = null;
        myComponent.clear();
    }

    private boolean isDisposed() {
        return myTarget == null;
    }

    public static class Once extends UiNotifyConnector {

        private boolean myShown;
        private boolean myHidden;

        public Once(Component component, Activatable target) {
            super(component, target);
        }

        @Override
        protected final void hideNotify() {
            super.hideNotify();
            myHidden = true;
            disposeIfNeeded();
        }

        @Override
        protected final void showNotify() {
            super.showNotify();
            myShown = true;
            disposeIfNeeded();
        }

        @Override
        protected void hideOnDispose() {
        }

        private void disposeIfNeeded() {
            if (myShown && myHidden) {
                Disposer.dispose(this);
            }
        }
    }

    public static void doWhenFirstShown(@Nonnull JComponent c, @RequiredUIAccess @Nonnull Runnable runnable) {
        doWhenFirstShown((Component)c, runnable);
    }

    public static void doWhenFirstShown(@Nonnull Component c, @RequiredUIAccess @Nonnull final Runnable runnable) {
        Activatable activatable = new Activatable() {
            @Override
            public void showNotify() {
                runnable.run();
            }

            @Override
            public void hideNotify() {
            }
        };

        new UiNotifyConnector(c, activatable) {
            @Override
            protected void showNotify() {
                super.showNotify();
                Disposer.dispose(this);
            }
        };
    }
}
