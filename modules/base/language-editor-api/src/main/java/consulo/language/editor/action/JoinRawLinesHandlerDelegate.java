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
package consulo.language.editor.action;

import consulo.document.Document;
import consulo.language.psi.PsiFile;

/**
 * A delegate which is called when "Join lines" is selected.
 *
 * @author dcheryasov
 * @since 2011-01-25
 */
public interface JoinRawLinesHandlerDelegate extends JoinLinesHandlerDelegate {
  /**
   * Tries to join lines at the specified position of the specified file. <br/>
   * In contrast to {@link JoinLinesHandlerDelegate#tryJoinLines(Document, PsiFile, int, int) tryJoinLines()}, this method
   * is called on an unmodified document.
   *
   * @param document where the lines are
   * @param file where the lines are
   * @param start offset right after the last non-space char of first line;
   * @param end offset of first non-space char of next line.
   * @return the position to place the caret after the operation, or -1 if this handler was not able
   *         to perform the operation.
   */
  int tryJoinRawLines(Document document, PsiFile file, int start, final int end);
}
