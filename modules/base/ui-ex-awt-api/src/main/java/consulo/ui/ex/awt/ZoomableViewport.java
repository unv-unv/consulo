/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package consulo.ui.ex.awt;

import jakarta.annotation.Nullable;

import java.awt.*;

/**
 * @author max
 */
public interface ZoomableViewport {
    @Nullable
    Magnificator getMagnificator();

    void magnificationStarted(Point at);

    void magnificationFinished(double magnification);

    void magnify(double magnification);
}
