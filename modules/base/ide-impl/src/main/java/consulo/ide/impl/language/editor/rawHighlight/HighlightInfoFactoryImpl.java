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
package consulo.ide.impl.language.editor.rawHighlight;

import consulo.annotation.component.ServiceImpl;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.editor.rawHighlight.HighlightInfoType;
import consulo.language.editor.internal.HighlightInfoFactory;
import jakarta.inject.Singleton;

/**
 * @author VISTALL
 * @since 01-Apr-22
 */
@Singleton
@ServiceImpl
public class HighlightInfoFactoryImpl implements HighlightInfoFactory {
  @Override
  public HighlightInfo.Builder createBuilder(HighlightInfoType infoType) {
    return HighlightInfoImpl.newHighlightInfo(infoType);
  }
}
