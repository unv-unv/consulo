/*
 * Copyright 2013-2018 consulo.io
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
package consulo.ui.desktop.internal.image;

import com.intellij.openapi.util.IconLoader;
import consulo.awt.TargetAWT;
import consulo.ui.image.Image;
import consulo.awt.impl.ToSwingIconWrapper;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author VISTALL
 * @since 6/22/18
 */
public class DesktopDisabledImageImpl implements ToSwingIconWrapper, Image {
  private static ConcurrentMap<Image, Image> ourCache = new ConcurrentHashMap<>();

  public static Image of(Image original) {
    return ourCache.computeIfAbsent(original, DesktopDisabledImageImpl::new);
  }

  private final Icon myIcon;

  private DesktopDisabledImageImpl(Image original) {
    myIcon = IconLoader.getDisabledIcon(TargetAWT.to(original));
  }

  @Nonnull
  @Override
  public Icon toSwingIcon() {
    return myIcon;
  }

  @Override
  public int getHeight() {
    return myIcon.getIconHeight();
  }

  @Override
  public int getWidth() {
    return myIcon.getIconWidth();
  }
}
