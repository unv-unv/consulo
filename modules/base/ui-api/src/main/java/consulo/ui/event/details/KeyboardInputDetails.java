/*
 * Copyright 2013-2024 consulo.io
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
package consulo.ui.event.details;

import consulo.ui.Position2D;
import jakarta.annotation.Nonnull;

import java.util.EnumSet;

/**
 * @author VISTALL
 * @since 2024-09-12
 */
public class KeyboardInputDetails extends ModifiedInputDetails {
    @Nonnull
    private final KeyCode myKeyCode;

    public KeyboardInputDetails(@Nonnull Position2D position,
                                @Nonnull Position2D positionOnScreen,
                                @Nonnull EnumSet<Modifier> modifiers,
                                @Nonnull KeyCode keyCode) {
        super(position, positionOnScreen, modifiers);
        myKeyCode = keyCode;
    }

    @Nonnull
    public KeyCode getKeyCode() {
        return myKeyCode;
    }
}
