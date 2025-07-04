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
package consulo.language.editor.inspection.reference;

import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

/**
 * @author anna
 * @since 2005-12-28
 */
public interface SmartRefElementPointer {

  @NonNls String FILE = "file";
  @NonNls String MODULE = "module";
  @NonNls String PROJECT = "project";
  @NonNls String DIR = "dir";

  boolean isPersistent();

  String getFQName();

  RefEntity getRefElement();

  void writeExternal(Element parentNode);

  boolean resolve(RefManager manager);

  void freeReference();
}
