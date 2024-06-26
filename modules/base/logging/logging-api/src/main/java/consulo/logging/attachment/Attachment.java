/*
 * Copyright 2013-2019 consulo.io
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
package consulo.logging.attachment;

import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 2019-08-10
 */
public interface Attachment {
  Attachment[] EMPTY_ARRAY = new Attachment[0];

  String getDisplayText();

  String getPath();

  String getName();

  String getEncodedBytes();

  boolean isIncluded();

  void setIncluded(Boolean included);

  @Nonnull
  Attachment copy(String newPath);
}
