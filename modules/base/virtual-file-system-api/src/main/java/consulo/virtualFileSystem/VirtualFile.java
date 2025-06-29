/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package consulo.virtualFileSystem;

import consulo.application.ApplicationManager;
import consulo.component.util.ModificationTracker;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.util.collection.ArrayFactory;
import consulo.util.dataholder.Key;
import consulo.util.dataholder.UserDataHolder;
import consulo.util.dataholder.UserDataHolderBase;
import consulo.util.io.CharsetToolkit;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.encoding.EncodingRegistry;
import consulo.virtualFileSystem.event.VirtualFileEvent;
import consulo.virtualFileSystem.event.VirtualFileListener;
import consulo.virtualFileSystem.event.VirtualFilePropertyEvent;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.virtualFileSystem.fileType.FileTypeRegistry;
import consulo.virtualFileSystem.localize.VirtualFileSystemLocalize;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;

/**
 * Represents a file in <code>{@link VirtualFileSystem}</code>. A particular file is represented by equal
 * <code>VirtualFile</code> instances for the entire lifetime of the IntelliJ IDEA process, unless the file
 * is deleted, in which case {@link #isValid()} will return <code>false</code>.
 * <p/>
 * VirtualFile instances are created on request, so there can be several instances corresponding to the same file.
 * All of them are equal, have the same hashCode and use shared storage for all related data, including user data (see {@link UserDataHolder}).
 * <p/>
 * If an in-memory implementation of VirtualFile is required, {@link LightVirtualFile}
 * can be used.
 * <p/>
 * Please see <a href="http://confluence.jetbrains.net/display/IDEADEV/IntelliJ+IDEA+Virtual+File+System">IntelliJ IDEA Virtual File System</a>
 * for high-level overview.
 *
 * @see VirtualFileSystem
 * @see VirtualFileManager
 */
public abstract class VirtualFile extends UserDataHolderBase implements ModificationTracker {

  public static final Key<Object> REQUESTOR_MARKER = Key.create("REQUESTOR_MARKER");
  public static final VirtualFile[] EMPTY_ARRAY = new VirtualFile[0];
  public static final ArrayFactory<VirtualFile> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new VirtualFile[count];

  public static final Key<VirtualFile> KEY = Key.create(VirtualFile.class);
  public static final Key<VirtualFile[]> KEY_OF_ARRAY = Key.create(VirtualFile[].class);

  /**
   * Used as a property name in the {@link VirtualFilePropertyEvent} fired when the name of a
   * {@link VirtualFile} changes.
   *
   * @see VirtualFileListener#propertyChanged
   * @see VirtualFilePropertyEvent#getPropertyName
   */
  public static final String PROP_NAME = "name";

  /**
   * Used as a property name in the {@link VirtualFilePropertyEvent} fired when the encoding of a
   * {@link VirtualFile} changes.
   *
   * @see VirtualFileListener#propertyChanged
   * @see VirtualFilePropertyEvent#getPropertyName
   */
  public static final String PROP_ENCODING = "encoding";

  /**
   * Used as a property name in the {@link VirtualFilePropertyEvent} fired when the write permission of a
   * {@link VirtualFile} changes.
   *
   * @see VirtualFileListener#propertyChanged
   * @see VirtualFilePropertyEvent#getPropertyName
   */
  public static final String PROP_WRITABLE = "writable";

  /**
   * Used as a property name in the {@link VirtualFilePropertyEvent} fired when a visibility of a
   * {@link VirtualFile} changes.
   *
   * @see VirtualFileListener#propertyChanged
   * @see VirtualFilePropertyEvent#getPropertyName
   */
  public static final String PROP_HIDDEN = "HIDDEN";

  /**
   * Used as a property name in the {@link VirtualFilePropertyEvent} fired when a symlink target of a
   * {@link VirtualFile} changes.
   *
   * @see VirtualFileListener#propertyChanged
   * @see VirtualFilePropertyEvent#getPropertyName
   */
  public static final String PROP_SYMLINK_TARGET = "symlink";

  /**
   * Acceptable values for "propertyName" argument of {@link VFilePropertyChangeEvent#VFilePropertyChangeEvent VFilePropertyChangeEvent()}.
   */
  //@MagicConstant(stringValues = {PROP_NAME, PROP_ENCODING, PROP_HIDDEN, PROP_WRITABLE, PROP_SYMLINK_TARGET})
  public @interface PropName {
  }

  private static final Logger LOG = Logger.getInstance(VirtualFile.class);
  private static final Key<byte[]> BOM_KEY = Key.create("BOM");
  private static final Key<Charset> CHARSET_KEY = Key.create("CHARSET");

  protected VirtualFile() {
  }

  @Nonnull
  public LocalizeValue getLocalizedName() {
    return LocalizeValue.of(getName());
  }

  /**
   * Gets the name of this file.
   *
   * @see #getNameSequence()
   */
  @Nonnull
  public abstract String getName();

  @Nonnull
  public CharSequence getNameSequence() {
    return getName();
  }

  /**
   * Gets the {@link VirtualFileSystem} this file belongs to.
   *
   * @return the {@link VirtualFileSystem}
   */
  @Nonnull
  public abstract VirtualFileSystem getFileSystem();

  /**
   * Gets the path of this file. Path is a string which uniquely identifies file within given
   * <code>{@link VirtualFileSystem}</code>. Format of the path depends on the concrete file system.
   * For <code>{@link consulo.ide.impl.idea.openapi.vfs.LocalFileSystem}</code> it is an absolute file path with file separator characters
   * (File.separatorChar) replaced to the forward slash ('/').
   *
   * @return the path
   */
  @SuppressWarnings("JavadocReference")
  @Nonnull
  public abstract String getPath();

  /**
   * Gets the URL of this file. The URL is a string which uniquely identifies file in all file systems.
   * It has the following format: <code>&lt;protocol&gt;://&lt;path&gt;</code>.
   * <p>
   * File can be found by its URL using {@link VirtualFileManager#findFileByUrl} method.
   * <p>
   * Please note these URLs are intended for use withing VFS - meaning they are not necessarily RFC-compliant.
   *
   * @return the URL consisting of protocol and path
   * @see VirtualFileManager#findFileByUrl
   * @see VirtualFile#getPath
   * @see VirtualFileSystem#getProtocol
   */
  @Nonnull
  public String getUrl() {
    return VirtualFileManager.constructUrl(getFileSystem().getProtocol(), getPath());
  }

  /**
   * Fetches "presentable URL" of this file. "Presentable URL" is a string to be used for displaying this
   * file in the UI.
   *
   * @return the presentable URL.
   * @see VirtualFileSystem#extractPresentableUrl
   */
  @Nonnull
  public final String getPresentableUrl() {
    return getFileSystem().extractPresentableUrl(getPath());
  }

  /**
   * Gets the extension of this file. If file name contains '.' extension is the substring from the last '.'
   * to the end of the name, otherwise extension is null.
   *
   * @return the extension or null if file name doesn't contain '.'
   */
  @Nullable
  public String getExtension() {
    String name = getName();
    int index = name.lastIndexOf('.');
    if (index < 0) return null;
    return name.substring(index + 1);
  }

  /**
   * Gets the file name without the extension. If file name contains '.' the substring till the last '.' is returned.
   * Otherwise the same value as <code>{@link #getName}</code> method returns is returned.
   *
   * @return the name without extension
   * if there is no '.' in it
   */
  @Nonnull
  public String getNameWithoutExtension() {
    return StringUtil.trimExtension(getName());
  }


  /**
   * Renames this file to the <code>newName</code>.<p>
   * This method should be only called within write-action.
   * See {@link Application#runWriteAction(Runnable)}.
   *
   * @param requestor any object to control who called this method. Note that
   *                  it is considered to be an external change if <code>requestor</code> is <code>null</code>.
   *                  See {@link VirtualFileEvent#getRequestor}
   * @param newName   the new file name
   * @throws IOException if file failed to be renamed
   */
  public void rename(Object requestor, @Nonnull String newName) throws IOException {
    if (getName().equals(newName)) return;
    if (!getFileSystem().isValidName(newName)) {
      throw new IOException(VirtualFileSystemLocalize.fileInvalidNameError(newName).get());
    }

    getFileSystem().renameFile(requestor, this, newName);
  }

  /**
   * Checks whether this file has write permission. Note that this value may be cached and may differ from
   * the write permission of the physical file.
   *
   * @return <code>true</code> if this file is writable, <code>false</code> otherwise
   */
  public abstract boolean isWritable();

  public void setWritable(boolean writable) throws IOException {
    throw new IOException("Not supported");
  }

  /**
   * Checks whether this file is a directory.
   *
   * @return <code>true</code> if this file is a directory, <code>false</code> otherwise
   */
  public abstract boolean isDirectory();

  /**
   * Checks whether this file has a specific property.
   *
   * @return <code>true</code> if the file has a specific property, <code>false</code> otherwise
   * @since 13.0
   */
  public boolean is(@Nonnull VFileProperty property) {
    return false;
  }

  /**
   * Resolves all symbolic links containing in a path to this file and returns a path to a link target (in platform-independent format).
   * <p/>
   * <b>Note</b>: please use this method judiciously. In most cases VFS clients don't need to resolve links in paths and should
   * work with those provided by a user.
   *
   * @return <code>getPath()</code> if there are no symbolic links in a file's path;
   * <code>getCanonicalFile().getPath()</code> if the link was successfully resolved;
   * <code>null</code> otherwise
   * @since 11.1
   */
  @Nullable
  public String getCanonicalPath() {
    return getPath();
  }

  /**
   * Resolves all symbolic links containing in a path to this file and returns a link target.
   * <p/>
   * <b>Note</b>: please use this method judiciously. In most cases VFS clients don't need to resolve links in paths and should
   * work with those provided by a user.
   *
   * @return <code>this</code> if there are no symbolic links in a file's path;
   * instance of <code>VirtualFile</code> if the link was successfully resolved;
   * <code>null</code> otherwise
   * @since 11.1
   */
  @Nullable
  public VirtualFile getCanonicalFile() {
    return this;
  }

  /**
   * Checks whether this <code>VirtualFile</code> is valid. File can be invalidated either by deleting it or one of its
   * parents with {@link #delete} method or by an external change.
   * If file is not valid only {@link #equals}, {@link #hashCode} and methods from
   * {@link UserDataHolder} can be called for it. Using any other methods for an invalid {@link VirtualFile} instance
   * produce unpredictable results.
   *
   * @return <code>true</code> if this is a valid file, <code>false</code> otherwise
   */
  public abstract boolean isValid();

  /**
   * Gets the parent <code>VirtualFile</code>.
   *
   * @return the parent file or <code>null</code> if this file is a root directory
   */
  public abstract VirtualFile getParent();

  /**
   * Gets the child files.
   *
   * @return array of the child files or <code>null</code> if this file is not a directory
   */
  public abstract VirtualFile[] getChildren();

  /**
   * Finds child of this file with the given name.
   *
   * @param name the file name to search by
   * @return the file if found any, <code>null</code> otherwise
   */
  @Nullable
  public VirtualFile findChild(@Nonnull String name) {
    VirtualFile[] children = getChildren();
    if (children == null) return null;
    for (VirtualFile child : children) {
      if (child.nameEquals(name)) {
        return child;
      }
    }
    return null;
  }

  @Nonnull
  public VirtualFile findOrCreateChildData(Object requestor, @Nonnull String name) throws IOException {
    final VirtualFile child = findChild(name);
    if (child != null) return child;
    return createChildData(requestor, name);
  }

  /**
   * @return the {@link FileType} of this file.
   * When IDEA has no idea what the file type is (i.e. file type is not registered via {@link FileTypeRegistry}),
   * it returns {@link consulo.ide.impl.idea.openapi.fileTypes.FileTypes#UNKNOWN}
   */
  @SuppressWarnings("JavadocReference")
  @Nonnull
  public FileType getFileType() {
    return FileTypeRegistry.getInstance().getFileTypeByFile(this);
  }

  /**
   * Finds file by path relative to this file.
   *
   * @param relPath the relative path with / used as separators
   * @return the file if found any, <code>null</code> otherwise
   */
  @Nullable
  public VirtualFile findFileByRelativePath(@Nonnull String relPath) {
    if (relPath.isEmpty()) return this;
    relPath = StringUtil.trimStart(relPath, "/");

    int index = relPath.indexOf('/');
    if (index < 0) index = relPath.length();
    String name = relPath.substring(0, index);

    VirtualFile child;
    if (name.equals(".")) {
      child = this;
    }
    else if (name.equals("..")) {
      if (is(VFileProperty.SYMLINK)) {
        final VirtualFile canonicalFile = getCanonicalFile();
        child = canonicalFile != null ? canonicalFile.getParent() : null;
      }
      else {
        child = getParent();
      }
    }
    else {
      child = findChild(name);
    }

    if (child == null) return null;

    if (index < relPath.length()) {
      return child.findFileByRelativePath(relPath.substring(index + 1));
    }
    return child;
  }

  /**
   * Creates a subdirectory in this directory. This method should be only called within write-action.
   * See {@link Application#runWriteAction}.
   *
   * @param requestor any object to control who called this method. Note that
   *                  it is considered to be an external change if <code>requestor</code> is <code>null</code>.
   *                  See {@link VirtualFileEvent#getRequestor}
   * @param name      directory name
   * @return <code>VirtualFile</code> representing the created directory
   * @throws IOException if directory failed to be created
   */
  @Nonnull
  public VirtualFile createChildDirectory(Object requestor, @Nonnull String name) throws IOException {
    if (!isDirectory()) {
      throw new IOException(VirtualFileSystemLocalize.directoryCreateWrongParentError().get());
    }

    if (!isValid()) {
      throw new IOException(VirtualFileSystemLocalize.invalidDirectoryCreateFiles().get());
    }

    if (!getFileSystem().isValidName(name)) {
      throw new IOException(VirtualFileSystemLocalize.directoryInvalidNameError(name).get());
    }

    if (findChild(name) != null) {
      throw new IOException(VirtualFileSystemLocalize.fileCreateAlreadyExistsError(getUrl(), name).get());
    }

    return getFileSystem().createChildDirectory(requestor, this, name);
  }

  /**
   * Creates a new file in this directory. This method should be only called within write-action.
   * See {@link Application#runWriteAction}.
   *
   * @param requestor any object to control who called this method. Note that
   *                  it is considered to be an external change if <code>requestor</code> is <code>null</code>.
   *                  See {@link VirtualFileEvent#getRequestor}
   * @return <code>VirtualFile</code> representing the created file
   * @throws IOException if file failed to be created
   */
  @Nonnull
  public VirtualFile createChildData(Object requestor, @Nonnull String name) throws IOException {
    if (!isDirectory()) {
      throw new IOException(VirtualFileSystemLocalize.fileCreateWrongParentError().get());
    }

    if (!isValid()) {
      throw new IOException(VirtualFileSystemLocalize.invalidDirectoryCreateFiles().get());
    }

    if (!getFileSystem().isValidName(name)) {
      throw new IOException(VirtualFileSystemLocalize.fileInvalidNameError(name).get());
    }

    if (findChild(name) != null) {
      throw new IOException(VirtualFileSystemLocalize.fileCreateAlreadyExistsError(getUrl(), name).get());
    }

    return getFileSystem().createChildFile(requestor, this, name);
  }

  /**
   * Deletes this file. This method should be only called within write-action.
   * See {@link Application#runWriteAction}.
   *
   * @param requestor any object to control who called this method. Note that
   *                  it is considered to be an external change if <code>requestor</code> is <code>null</code>.
   *                  See {@link VirtualFileEvent#getRequestor}
   * @throws IOException if file failed to be deleted
   */
  public void delete(Object requestor) throws IOException {
    LOG.assertTrue(isValid(), "Deleting invalid file");
    getFileSystem().deleteFile(requestor, this);
  }

  /**
   * Moves this file to another directory. This method should be only called within write-action.
   * See {@link Application#runWriteAction}.
   *
   * @param requestor any object to control who called this method. Note that
   *                  it is considered to be an external change if <code>requestor</code> is <code>null</code>.
   *                  See {@link VirtualFileEvent#getRequestor}
   * @param newParent the directory to move this file to
   * @throws IOException if file failed to be moved
   */
  public void move(final Object requestor, @Nonnull final VirtualFile newParent) throws IOException {
    if (getFileSystem() != newParent.getFileSystem()) {
      throw new IOException(VirtualFileSystemLocalize.fileMoveError(newParent.getPresentableUrl()).get());
    }

    EncodingRegistry.doActionAndRestoreEncoding(this, () -> {
      getFileSystem().moveFile(requestor, VirtualFile.this, newParent);
      return VirtualFile.this;
    });
  }

  public VirtualFile copy(final Object requestor, @Nonnull final VirtualFile newParent, @Nonnull final String copyName) throws IOException {
    if (getFileSystem() != newParent.getFileSystem()) {
      throw new IOException(VirtualFileSystemLocalize.fileCopyError(newParent.getPresentableUrl()).get());
    }

    if (!newParent.isDirectory()) {
      throw new IOException(VirtualFileSystemLocalize.fileCopyTargetMustBeDirectory().get());
    }

    return EncodingRegistry.doActionAndRestoreEncoding(
      this,
      () -> getFileSystem().copyFile(requestor, VirtualFile.this, newParent, copyName)
    );
  }

  /**
   * @return Retrieve the charset file has been loaded with (if loaded) and would be saved with (if would).
   */
  @Nonnull
  public Charset getCharset() {
    Charset charset = getStoredCharset();
    if (charset == null) {
      charset = EncodingRegistry.getInstance().getDefaultCharset();
      setCharset(charset);
    }
    return charset;
  }

  @Nullable
  protected Charset getStoredCharset() {
    return getUserData(CHARSET_KEY);
  }

  protected void storeCharset(Charset charset) {
    putUserData(CHARSET_KEY, charset);
  }

  public void setCharset(final Charset charset) {
    setCharset(charset, null);
  }

  public void setCharset(final Charset charset, @Nullable Runnable whenChanged) {
    setCharset(charset, whenChanged, true);
  }

  public void setCharset(final Charset charset, @Nullable Runnable whenChanged, boolean fireEventsWhenChanged) {
    final Charset old = getStoredCharset();
    storeCharset(charset);
    if (Comparing.equal(charset, old)) return;
    byte[] bom = charset == null ? null : CharsetToolkit.getMandatoryBom(charset);
    byte[] existingBOM = getBOM();
    if (bom == null && charset != null && existingBOM != null) {
      bom = CharsetToolkit.canHaveBom(charset, existingBOM) ? existingBOM : null;
    }
    setBOM(bom);

    if (old != null) { //do not send on detect
      if (whenChanged != null) whenChanged.run();
      if (fireEventsWhenChanged) {
        VirtualFileManager.getInstance().notifyPropertyChanged(this, PROP_ENCODING, old, charset);
      }
    }
  }

  public boolean isCharsetSet() {
    return getStoredCharset() != null;
  }

  public final void setBinaryContent(@Nonnull byte[] content) throws IOException {
    setBinaryContent(content, -1, -1);
  }

  public void setBinaryContent(@Nonnull byte[] content, long newModificationStamp, long newTimeStamp) throws IOException {
    setBinaryContent(content, newModificationStamp, newTimeStamp, this);
  }

  public void setBinaryContent(@Nonnull byte[] content, long newModificationStamp, long newTimeStamp, Object requestor) throws IOException {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    try (OutputStream outputStream = getOutputStream(requestor, newModificationStamp, newTimeStamp)) {
      outputStream.write(content);
      outputStream.flush();
    }
  }

  /**
   * Creates the <code>OutputStream</code> for this file.
   * Writes BOM first, if there is any. See <a href=http://unicode.org/faq/utf_bom.html>Unicode Byte Order Mark FAQ</a> for an explanation.
   *
   * @param requestor any object to control who called this method. Note that
   *                  it is considered to be an external change if <code>requestor</code> is <code>null</code>.
   *                  See {@link VirtualFileEvent#getRequestor}
   * @return <code>OutputStream</code>
   * @throws IOException if an I/O error occurs
   */
  public final OutputStream getOutputStream(Object requestor) throws IOException {
    return getOutputStream(requestor, -1, -1);
  }

  /**
   * Gets the <code>OutputStream</code> for this file and sets modification stamp and time stamp to the specified values
   * after closing the stream.<p>
   * <p/>
   * Normally you should not use this method.
   * <p>
   * Writes BOM first, if there is any. See <a href=http://unicode.org/faq/utf_bom.html>Unicode Byte Order Mark FAQ</a> for an explanation.
   *
   * @param requestor            any object to control who called this method. Note that
   *                             it is considered to be an external change if <code>requestor</code> is <code>null</code>.
   *                             See {@link VirtualFileEvent#getRequestor}
   * @param newModificationStamp new modification stamp or -1 if no special value should be set
   * @param newTimeStamp         new time stamp or -1 if no special value should be set
   * @return <code>OutputStream</code>
   * @throws IOException if an I/O error occurs
   * @see #getModificationStamp()
   */
  @Nonnull
  public abstract OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException;

  /**
   * Returns file content as an array of bytes.
   * Has the same effect as contentsToByteArray(true).
   *
   * @return file content
   * @throws IOException if an I/O error occurs
   * @see #contentsToByteArray(boolean)
   * @see #getInputStream()
   */
  @Nonnull
  public abstract byte[] contentsToByteArray() throws IOException;

  /**
   * Returns file content as an array of bytes.
   *
   * @param cacheContent set true to
   * @return file content
   * @throws IOException if an I/O error occurs
   * @see #contentsToByteArray()
   */
  @Nonnull
  public byte[] contentsToByteArray(boolean cacheContent) throws IOException {
    return contentsToByteArray();
  }

  @Nonnull
  public CharSequence loadText() {
    try {
      return new String(contentsToByteArray(), getCharset());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Gets modification stamp value. Modification stamp is a value changed by any modification
   * of the content of the file. Note that it is not related to the file modification time.
   *
   * @return modification stamp
   * @see #getTimeStamp()
   */
  public long getModificationStamp() {
    throw new UnsupportedOperationException(this.getClass().getName());
  }

  /**
   * Gets the timestamp for this file. Note that this value may be cached and may differ from
   * the timestamp of the physical file.
   *
   * @return timestamp
   * @see File#lastModified
   */
  public abstract long getTimeStamp();

  /**
   * File length in bytes.
   *
   * @return the length of this file.
   */
  public abstract long getLength();

  /**
   * Refreshes the cached file information from the physical file system. If this file is not a directory
   * the timestamp value is refreshed and <code>contentsChanged</code> event is fired if it is changed.<p>
   * If this file is a directory the set of its children is refreshed. If recursive value is <code>true</code> all
   * children are refreshed recursively.
   * <p/>
   * When invoking synchronous refresh from a thread other than the event dispatch thread, the current thread must
   * NOT be in a read action, otherwise a deadlock may occur.
   *
   * @param asynchronous if <code>true</code>, the method will return immediately and the refresh will be processed
   *                     in the background. If <code>false</code>, the method will return only after the refresh
   *                     is done and the VFS change events caused by the refresh have been fired and processed
   *                     in the event dispatch thread. Instead of synchronous refreshes, it's recommended to use
   *                     asynchronous refreshes with a <code>postRunnable</code> whenever possible.
   * @param recursive    whether to refresh all the files in this directory recursively
   */
  public void refresh(boolean asynchronous, boolean recursive) {
    refresh(asynchronous, recursive, null);
  }

  /**
   * The same as {@link #refresh(boolean, boolean)} but also runs <code>postRunnable</code>
   * after the operation is completed.
   */
  public abstract void refresh(boolean asynchronous, boolean recursive, @Nullable Runnable postRunnable);

  public String getPresentableName() {
    return getName();
  }

  @Override
  public long getModificationCount() {
    return isValid() ? getTimeStamp() : -1;
  }

  /**
   * @return whether file name equals to this name
   * result depends on the filesystem specifics
   */
  protected boolean nameEquals(@Nonnull String name) {
    return getName().equals(name);
  }

  /**
   * Gets the <code>InputStream</code> for this file.
   * Skips BOM if there is any. See <a href=http://unicode.org/faq/utf_bom.html>Unicode Byte Order Mark FAQ</a> for an explanation.
   *
   * @return <code>InputStream</code>
   * @throws IOException if an I/O error occurs
   * @see #contentsToByteArray
   */
  public abstract InputStream getInputStream() throws IOException;

  @Nullable
  public byte[] getBOM() {
    return getUserData(BOM_KEY);
  }

  public void setBOM(@Nullable byte[] BOM) {
    putUserData(BOM_KEY, BOM);
  }

  @Override
  public String toString() {
    return "VirtualFile: " + getPresentableUrl();
  }

  public boolean exists() {
    return isValid();
  }

  public boolean isInLocalFileSystem() {
    return false;
  }

  /**
   * @deprecated use {@link VirtualFileSystem#isValidName(String)} (to be removed in IDEA 18)
   */
  @SuppressWarnings("unused")
  public static boolean isValidName(@Nonnull String name) {
    return name.length() > 0 && name.indexOf('\\') < 0 && name.indexOf('/') < 0;
  }

  private static final Key<String> DETECTED_LINE_SEPARATOR_KEY = Key.create("DETECTED_LINE_SEPARATOR_KEY");

  /**
   * @return Line separator for this file.
   * It is always null for directories and binaries, and possibly null if a separator isn't yet known.
   * @see LineSeparator
   */
  public String getDetectedLineSeparator() {
    return getUserData(DETECTED_LINE_SEPARATOR_KEY);
  }

  public void setDetectedLineSeparator(@Nullable String separator) {
    putUserData(DETECTED_LINE_SEPARATOR_KEY, separator);
  }

  public void setPreloadedContentHint(byte[] preloadedContentHint) {
  }

  /**
   * Returns {@code true} if this file is a symlink that is either <i>recursive</i> (i.e. points to this file' parent) or
   * <i>circular</i> (i.e. its path has a form of "/.../linkX/.../linkX").
   */
  public boolean isRecursiveOrCircularSymLink() {
    if (!is(VFileProperty.SYMLINK)) return false;
    VirtualFile resolved = getCanonicalFile();
    // invalid symlink
    if (resolved == null) return false;
    // if it's recursive
    if (VirtualFileUtil.isAncestor(resolved, this, false)) return true;

    // check if it's circular - any symlink above resolves to my target too
    for (VirtualFile p = getParent(); p != null; p = p.getParent()) {
      if (p.is(VFileProperty.SYMLINK)) {
        VirtualFile parentResolved = p.getCanonicalFile();
        if (resolved.equals(parentResolved)) return true;
      }
    }
    return false;
  }

  @Nonnull
  public Path toNioPath() {
    VirtualFileSystem fileSystem = getFileSystem();
    if (StandardFileSystems.FILE_PROTOCOL.equals(fileSystem.getProtocol())) {
      return Path.of(getPath());
    }

    throw new UnsupportedOperationException("Impossible convert " + this + " to " + Path.class);
  }
}