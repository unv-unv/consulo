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
package consulo.ide.impl.idea.codeInsight.completion;

import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import consulo.document.Document;
import consulo.language.editor.completion.CompletionInitializationContext;
import consulo.language.editor.completion.OffsetMap;
import consulo.language.psi.PsiFile;

/**
 * @author peter
 */
public class DummyIdentifierPatcher extends FileCopyPatcher {
  private final String myDummyIdentifier;

  public DummyIdentifierPatcher(final String dummyIdentifier) {
    myDummyIdentifier = dummyIdentifier;
  }

  @Override
  public void patchFileCopy(@Nonnull final PsiFile fileCopy, @Nonnull final Document document, @Nonnull final OffsetMap map) {
    if (StringUtil.isEmpty(myDummyIdentifier)) return;
    int startOffset = map.getOffset(CompletionInitializationContext.START_OFFSET);
    int endOffset = map.getOffset(CompletionInitializationContext.SELECTION_END_OFFSET);
    document.replaceString(startOffset, endOffset, myDummyIdentifier);
  }

  @Override
  public String toString() {
    return "Insert \"" + myDummyIdentifier + "\"";
  }
}
