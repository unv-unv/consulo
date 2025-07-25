/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package consulo.application.progress;

import consulo.component.ProcessCanceledException;
import consulo.localize.LocalizeValue;
import consulo.ui.ModalityState;
import jakarta.annotation.Nonnull;

/**
 * @author Eugene Zhuravlev
 * @since 2007-07-12
 */
public class DelegatingProgressIndicator implements WrappedProgressIndicator, StandardProgressIndicator {
  private final ProgressIndicator myIndicator;

  public DelegatingProgressIndicator(@Nonnull ProgressIndicator indicator) {
    myIndicator = indicator;
  }

  public DelegatingProgressIndicator() {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    myIndicator = indicator == null ? new EmptyProgressIndicator() : indicator;
  }

  @Override
  public void start() {
    myIndicator.start();
  }

  @Override
  public void stop() {
    myIndicator.stop();
  }

  @Override
  public boolean isRunning() {
    return myIndicator.isRunning();
  }

  @Override
  public final void cancel() {
    myIndicator.cancel();
  }

  @Override
  public final boolean isCanceled() {
    return myIndicator.isCanceled();
  }

  @Override
  public void setTextValue(final LocalizeValue text) {
    myIndicator.setTextValue(text);
  }

  @Override
  public LocalizeValue getTextValue() {
    return myIndicator.getTextValue();
  }

  @Override
  public void setText2Value(final LocalizeValue text) {
    myIndicator.setText2Value(text);
  }

  @Override
  public LocalizeValue getText2Value() {
    return myIndicator.getText2Value();
  }

  @Override
  public double getFraction() {
    return myIndicator.getFraction();
  }

  @Override
  public void setFraction(final double fraction) {
    myIndicator.setFraction(fraction);
  }

  @Override
  public void pushState() {
    myIndicator.pushState();
  }

  @Override
  public void popState() {
    myIndicator.popState();
  }

  @Override
  public void startNonCancelableSection() {
    myIndicator.startNonCancelableSection();
  }

  @Override
  public void finishNonCancelableSection() {
    myIndicator.finishNonCancelableSection();
  }

  @Override
  public boolean isModal() {
    return myIndicator.isModal();
  }

  @Override
  @Nonnull
  public ModalityState getModalityState() {
    return myIndicator.getModalityState();
  }

  @Override
  public void setModalityProgress(final ProgressIndicator modalityProgress) {
    myIndicator.setModalityProgress(modalityProgress);
  }

  @Override
  public boolean isIndeterminate() {
    return myIndicator.isIndeterminate();
  }

  @Override
  public void setIndeterminate(final boolean indeterminate) {
    myIndicator.setIndeterminate(indeterminate);
  }

  @Override
  public final void checkCanceled() throws ProcessCanceledException {
    myIndicator.checkCanceled();
  }

  protected final ProgressIndicator getDelegate() {
    return myIndicator;
  }

  @Nonnull
  @Override
  public ProgressIndicator getOriginalProgressIndicator() {
    return myIndicator;
  }

  @Override
  public boolean isPopupWasShown() {
    return myIndicator.isPopupWasShown();
  }

  @Override
  public boolean isShowing() {
    return myIndicator.isShowing();
  }
}
