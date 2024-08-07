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
package consulo.diff.dir;

import consulo.application.progress.ProgressIndicator;
import consulo.component.ProcessCanceledException;
import consulo.diff.DiffContentFactory;
import consulo.diff.chain.DiffRequestProducer;
import consulo.diff.chain.DiffRequestProducerException;
import consulo.diff.content.DiffContent;
import consulo.navigation.Navigatable;
import consulo.project.Project;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.encoding.EncodingManager;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.virtualFileSystem.fileType.FileTypeRegistry;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.Callable;

/**
 * @author Konstantin Bulenkov
 */
public abstract class DiffElement<T> {
  public static final DiffElement[] EMPTY_ARRAY = new DiffElement[0];

  public abstract String getPath();

  @Nonnull
  public abstract String getName();

  public String getPresentablePath() {
    return getName();
  }

  public abstract long getSize();

  public abstract long getTimeStamp();

  public FileType getFileType() {
    return FileTypeRegistry.getInstance().getFileTypeByFileName(getName());
  }

  public abstract boolean isContainer();

  public abstract DiffElement[] getChildren() throws IOException;

  @Nullable
  public Navigatable getNavigatable(@Nullable Project project) {
    return null;
  }

  /**
   * Returns content data as byte array. Can be null, if element for example is a container
   * @return content byte array
   * @throws IOException when reading
   */
  @Nullable
  public abstract byte[] getContent() throws IOException;

  @Nonnull
  public Charset getCharset() {
    return EncodingManager.getInstance().getDefaultCharset();
  }

  public abstract T getValue();

  public String getSeparator() {
    return "/";
  }

  @Nullable
  public Image getIcon() {
    return null;
  }

  /**
   * Called in background thread without ReadLock OR in EDT
   *
   * @see DiffRequestProducer#process
   */
  @Nonnull
  public DiffContent createDiffContent(@Nullable Project project, @Nonnull ProgressIndicator indicator)
          throws DiffRequestProducerException, ProcessCanceledException {
    try {
      final T src = getValue();
      if (src instanceof VirtualFile) {
        return DiffContentFactory.getInstance().create(project, (VirtualFile)src);
      }

      byte[] content = getContent();
      if (content == null) throw new DiffRequestProducerException("Can't get content");

      return DiffContentFactory.getInstance().create(new String(content, getCharset()), getFileType());
    }
    catch (IOException e) {
      throw new DiffRequestProducerException(e);
    }
  }

  @Nullable
  public Callable<DiffElement<T>> getElementChooser(Project project) {
    return null;
  }

  /**
   * Defines is it possible to perform such operations as copy or delete through Diff Panel
   *
   * @return <code>true</code> if copy, delete, etc operations are allowed,
   *        <code>false</code> otherwise
   */
  public boolean isOperationsEnabled() {
    return false;
  }

  /**
   * Copies element to the container.
   *
   * @param container file directory or other container
   * @param relativePath relative path from root
   * @return <code>true</code> if coping was completed successfully,
   *        <code>false</code> otherwise
   */
  @Nullable
  public DiffElement<?> copyTo(DiffElement<T> container, String relativePath) {
    return null;
  }

  /**
   * Deletes element
   * @return <code>true</code> if deletion was completed successfully,
   *        <code>false</code> otherwise
   */
  public boolean delete() {
    return false;
  }

  public void refresh(boolean userInitiated) throws IOException{
  }
}
