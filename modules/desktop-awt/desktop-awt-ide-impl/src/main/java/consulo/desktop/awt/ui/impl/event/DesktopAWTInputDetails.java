/*
 * Copyright 2013-2022 consulo.io
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
package consulo.desktop.awt.ui.impl.event;

import consulo.ui.Position2D;
import consulo.ui.event.details.*;
import consulo.util.lang.BitUtil;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * @author VISTALL
 * @since 06/11/2022
 */
public class DesktopAWTInputDetails {
    public static InputDetails convert(Component awtComponent, AWTEvent event) {
        Set<ModifiedInputDetails.Modifier> modifiers = new HashSet<>();

        if (event instanceof ActionEvent actionEvent) {
            Point point = awtComponent.getLocationOnScreen();

            Position2D relative = new Position2D(awtComponent.getX(), awtComponent.getY());
            Position2D absolete = new Position2D(point.x, point.y);

            if (BitUtil.isSet(actionEvent.getModifiers(), ActionEvent.CTRL_MASK)) {
                modifiers.add(ModifiedInputDetails.Modifier.CTRL);
            }

            if (BitUtil.isSet(actionEvent.getModifiers(), ActionEvent.ALT_MASK)) {
                modifiers.add(ModifiedInputDetails.Modifier.ALT);
            }

            if (BitUtil.isSet(actionEvent.getModifiers(), ActionEvent.SHIFT_MASK)) {
                modifiers.add(ModifiedInputDetails.Modifier.SHIFT);
            }

            EnumSet<MouseInputDetails.Modifier> enumModifiers = modifiers.isEmpty() ? EnumSet.noneOf(ModifiedInputDetails.Modifier.class) : EnumSet.copyOf(modifiers);
            return new MouseInputDetails(relative, absolete, enumModifiers, MouseInputDetails.MouseButton.LEFT);
        } else {
            if (event instanceof InputEvent inputEvent) {
                if (BitUtil.isSet(inputEvent.getModifiersEx(), MouseEvent.CTRL_DOWN_MASK)) {
                    modifiers.add(ModifiedInputDetails.Modifier.CTRL);
                }

                if (BitUtil.isSet(inputEvent.getModifiersEx(), MouseEvent.ALT_DOWN_MASK)) {
                    modifiers.add(ModifiedInputDetails.Modifier.ALT);
                }

                if (BitUtil.isSet(inputEvent.getModifiersEx(), MouseEvent.SHIFT_DOWN_MASK)) {
                    modifiers.add(ModifiedInputDetails.Modifier.SHIFT);
                }
            }

            EnumSet<MouseInputDetails.Modifier> enumModifiers = modifiers.isEmpty() ? EnumSet.noneOf(ModifiedInputDetails.Modifier.class) : EnumSet.copyOf(modifiers);

            if (event instanceof MouseEvent mouseEvent) {
                int x = mouseEvent.getX();

                MouseInputDetails.MouseButton button = MouseInputDetails.MouseButton.LEFT;
                if (mouseEvent.getButton() == MouseEvent.BUTTON2) {
                    button = MouseInputDetails.MouseButton.MIDDLE;
                }
                else if (mouseEvent.getButton() == MouseEvent.BUTTON3) {
                    button = MouseInputDetails.MouseButton.RIGHT;
                }

                Position2D relative = new Position2D(((MouseEvent) event).getX(), ((MouseEvent) event).getY());
                Position2D absolute = new Position2D(((MouseEvent) event).getXOnScreen(), ((MouseEvent) event).getYOnScreen());

                return new MouseInputDetails(relative, absolute, enumModifiers, button);
            }
            else if (event instanceof KeyEvent keyEvent) {
                Position2D pos = new Position2D(awtComponent.getX(), awtComponent.getY());
                Point locationOnScreen = awtComponent.getLocationOnScreen();
                Position2D posOnScreen = new Position2D(locationOnScreen.x, locationOnScreen.y);
                return new KeyboardInputDetails(pos, posOnScreen, enumModifiers, KeyCode.of(keyEvent.getKeyCode()));
            }
        }

        throw new UnsupportedOperationException("unknown event " + event);
    }
}
