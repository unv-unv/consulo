/*
 * Copyright 2013-2020 consulo.io
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
package consulo.web.internal.ui.image;

import consulo.ui.impl.image.BaseIconLibraryImpl;
import consulo.ui.impl.image.BaseIconLibraryManager;
import consulo.ui.impl.image.ImageReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 2020-10-03
 */
public class WebIconLibrary extends BaseIconLibraryImpl {
  public WebIconLibrary(@Nonnull String id, @Nonnull BaseIconLibraryManager baseIconLibraryManager) {
    super(id, baseIconLibraryManager);
  }

  @Nonnull
  @Override
  protected ImageReference createImageReference(@Nonnull byte[] _1xData,
                                                @Nullable byte[] _2xdata,
                                                boolean isSVG,
                                                String groupId,
                                                String imageId) {
    return new WebImageReference(_1xData, _2xdata, isSVG);
  }
}
