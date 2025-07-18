// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.ui.ex.awt;

import consulo.application.dumb.DumbAware;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.awt.util.ComponentUtil;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.function.Function;

import static consulo.ui.ex.awt.UIExAWTDataKey.CONTEXT_COMPONENT;

/**
 * @author Sergey.Malenkov
 */
public class SwingActionDelegate extends AnAction implements DumbAware {
  private static final Key<Function<String, JComponent>> FUNCTION = Key.create("SwingActionsMapping");
  private final String mySwingActionId;

  protected SwingActionDelegate(String actionId) {
    setEnabledInModalContext(true);
    mySwingActionId = actionId;
  }

  protected JComponent getComponent(AnActionEvent event) {
    JComponent component = ComponentUtil.getParentOfType((Class<? extends JComponent>)JComponent.class, event.getData(CONTEXT_COMPONENT));
    Function<String, JComponent> function = UIUtil.getClientProperty(component, FUNCTION);
    return function == null ? component : function.apply(mySwingActionId);
  }

  @Override
  public final void update(@Nonnull AnActionEvent event) {
    event.getPresentation().setEnabled(null != getSwingAction(getComponent(event)));
  }

  @Override
  @RequiredUIAccess
  public final void actionPerformed(@Nonnull AnActionEvent event) {
    JComponent component = getComponent(event);
    Action action = getSwingAction(component);
    if (action != null) action.actionPerformed(new ActionEvent(component, ActionEvent.ACTION_PERFORMED, mySwingActionId));
  }

  private Action getSwingAction(JComponent component) {
    if (component == null) return null;
    ActionMap map = component.getActionMap();
    return map == null ? null : map.get(mySwingActionId);
  }

  /**
   * @param component the base component that delegates performing of actions to a dependant component
   * @param mapping   a function that returns a component that able to perform a named action,
   *                  or {@code null} to remove mapping for the given base component
   */
  //@ApiStatus.Experimental
  public static void configureMapping(@Nonnull JComponent component, @Nullable Function<String, JComponent> mapping) {
    component.putClientProperty(FUNCTION, mapping);
  }

  /**
   * @param base      the base component that delegates performing of actions to the dependant component
   * @param dependant the dependant component that should perform suported actions instead of the base component
   * @param actions   a list of supported actions
   */
  //@ApiStatus.Experimental
  public static void configureMapping(@Nonnull JComponent base, @Nonnull JComponent dependant, @Nonnull String... actions) {
    HashMap<String, JComponent> map = new HashMap<>();
    for (String action : actions) map.put(action, dependant);
    configureMapping(base, map::get);
  }

  /**
   * @param component the base component that should not perform actions
   */
  //@ApiStatus.Experimental
  public static void disableFor(@Nonnull JComponent component) {
    configureMapping(component, action -> null);
  }
}
