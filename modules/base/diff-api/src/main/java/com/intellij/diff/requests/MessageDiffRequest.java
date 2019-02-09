/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.requests;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import consulo.ui.RequiredUIAccess;

public class MessageDiffRequest extends DiffRequest {
  @Nullable private String myTitle;
  @Nonnull
  private String myMessage;

  public MessageDiffRequest(@Nonnull String message) {
    this(null, message);
  }

  public MessageDiffRequest(@Nullable String title, @Nonnull String message) {
    myTitle = title;
    myMessage = message;
  }

  @Nullable
  @Override
  public String getTitle() {
    return myTitle;
  }

  @Nonnull
  public String getMessage() {
    return myMessage;
  }

  public void setTitle(@Nullable String title) {
    myTitle = title;
  }

  public void setMessage(@Nonnull String message) {
    myMessage = message;
  }

  @RequiredUIAccess
  @Override
  public final void onAssigned(boolean isAssigned) {
  }
}
