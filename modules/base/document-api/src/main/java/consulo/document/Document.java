// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.document;

import consulo.application.util.function.Processor;
import consulo.disposer.Disposable;
import consulo.document.event.DocumentListener;
import consulo.document.util.TextRange;
import consulo.util.dataholder.UserDataHolder;
import consulo.util.lang.CharArrayUtil;
import consulo.virtualFileSystem.VirtualFile;
import kava.beans.PropertyChangeListener;
import org.jetbrains.annotations.Contract;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Represents the contents of a text file loaded into memory, and possibly opened in an IDE
 * text editor. Line breaks in the document text are always normalized as single {@code \n} characters,
 * and are converted to proper format when the document is saved.
 * <p/>
 * Please see <a href="https://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview.html">IntelliJ Platform Architectural Overview</a>
 * for high-level overview.
 *
 * @see Editor#getDocument()
 * @see com.intellij.psi.PsiDocumentManager
 * @see FileDocumentManager
 * @see EditorFactory#createDocument(CharSequence)
 */
public interface Document extends UserDataHolder {
    Document[] EMPTY_ARRAY = new Document[0];
    String PROP_WRITABLE = "writable";

    /**
     * Retrieves a copy of the document content. For obvious performance reasons use
     * {@link #getCharsSequence()} whenever it's possible.
     *
     * @return document content.
     */
    @Nonnull
    @Contract(pure = true)
    default String getText() {
        return getImmutableCharSequence().toString();
    }

    @Nonnull
    @Contract(pure = true)
    default String getText(@Nonnull TextRange range) {
        return range.substring(getText());
    }

    /**
     * Use this method instead of {@link #getText()} if you do not need to create a copy of the content.
     * Content represented by returned CharSequence is subject to change whenever document is modified via delete/replace/insertString method
     * calls. It is necessary to obtain Application.runWriteAction() to modify content of the document though so threading issues won't
     * arise.
     *
     * @return inplace document content.
     * @see #getTextLength()
     */
    @Contract(pure = true)
    @Nonnull
    default CharSequence getCharsSequence() {
        return getImmutableCharSequence();
    }

    /**
     * @return a char sequence representing document content that's guaranteed to be immutable. No read- or write-action is necessary.
     * @see consulo.ide.impl.idea.util.text.ImmutableCharSequence
     */
    @Nonnull
    @Contract(pure = true)
    CharSequence getImmutableCharSequence();

    /**
     * @deprecated Use {@link #getCharsSequence()} or {@link #getText()} instead.
     */
    @Nonnull
    @Deprecated
    default char[] getChars() {
        return CharArrayUtil.fromSequence(getImmutableCharSequence());
    }

    /**
     * Returns the length of the document text.
     *
     * @return the length of the document text.
     * @see #getCharsSequence()
     */
    @Contract(pure = true)
    default int getTextLength() {
        return getImmutableCharSequence().length();
    }

    /**
     * Returns the number of lines in the document.
     *
     * @return the number of lines in the document.
     */
    @Contract(pure = true)
    int getLineCount();

    /**
     * Returns the line number (0-based) corresponding to the specified offset in the document.
     *
     * @param offset the offset to get the line number for (must be in the range from 0 to
     *               getTextLength()-1)
     * @return the line number corresponding to the offset.
     */
    @Contract(pure = true)
    int getLineNumber(int offset);

    /**
     * Returns the start offset for the line with the specified number.
     *
     * @param line the line number (from 0 to getLineCount()-1)
     * @return the start offset for the line.
     */
    @Contract(pure = true)
    int getLineStartOffset(int line);

    /**
     * Returns the end offset for the line with the specified number.
     *
     * @param line the line number (from 0 to getLineCount()-1)
     * @return the end offset for the line.
     */
    @Contract(pure = true)
    int getLineEndOffset(int line);

    /**
     * @return whether the line with the given index has been modified since the document has been saved
     */
    default boolean isLineModified(int line) {
        return false;
    }

    /**
     * Inserts the specified text at the specified offset in the document. Line breaks in
     * the inserted text must be normalized as \n.
     *
     * @param offset the offset to insert the text at.
     * @param s      the text to insert.
     * @throws ReadOnlyModificationException         if the document is read-only.
     * @throws ReadOnlyFragmentModificationException if the fragment to be modified is covered by a guarded block.
     */
    void insertString(int offset, @Nonnull CharSequence s);

    /**
     * Deletes the specified range of text from the document.
     *
     * @param startOffset the start offset of the range to delete.
     * @param endOffset   the end offset of the range to delete.
     * @throws ReadOnlyModificationException         if the document is read-only.
     * @throws ReadOnlyFragmentModificationException if the fragment to be modified is covered by a guarded block.
     */
    void deleteString(int startOffset, int endOffset);

    /**
     * Moves text from the {@code [srcStart; srcEnd)} range to the {@code dstOffset} offset.
     * <p/>
     * The benefit to use this method over usual {@link #deleteString(int, int)} and {@link #replaceString(int, int, CharSequence)}
     * is that {@link #createRangeMarker(int, int, boolean) range markers} from the {@code [srcStart; srcEnd)} range have
     * a chance to be preserved. Default implementation doesn't preserve range markers, but has the same effect in terms of resulting
     * text content.
     *
     * @param srcStart  start offset of the text to move (inclusive)
     * @param srcEnd    end offset of the text to move (exclusive)
     * @param dstOffset the offset to insert the text to. Must be outside of the (srcStart, srcEnd) range.
     */
    default void moveText(int srcStart, int srcEnd, int dstOffset) {
        assert srcStart <= srcEnd && (dstOffset <= srcStart || dstOffset >= srcEnd);
        if (srcStart < srcEnd && (dstOffset < srcStart || dstOffset > srcEnd)) {
            String fragment = getText(new TextRange(srcStart, srcEnd));
            insertString(dstOffset, fragment);
            int shift = dstOffset < srcStart ? srcEnd - srcStart : 0;
            deleteString(srcStart + shift, srcEnd + shift);
        }
    }

    /**
     * Replaces the specified range of text in the document with the specified string.
     * Line breaks in the text to replace with must be normalized as \n.
     *
     * @param startOffset the start offset of the range to replace.
     * @param endOffset   the end offset of the range to replace.
     * @param s           the text to replace with.
     * @throws ReadOnlyModificationException         if the document is read-only.
     * @throws ReadOnlyFragmentModificationException if the fragment to be modified is covered by a guarded block.
     */
    void replaceString(int startOffset, int endOffset, @Nonnull CharSequence s);

    /**
     * Checks if the document text is read-only.
     *
     * @return {@code true} if the document text is writable, {@code false} if it is read-only.
     * @see #fireReadOnlyModificationAttempt()
     */
    @Contract(pure = true)
    boolean isWritable();

    /**
     * Gets the modification stamp value. Modification stamp is a value changed by any modification
     * of the content of the file. Note that it is not related to the file modification time.
     *
     * @return the modification stamp value.
     * @see PsiFile#getModificationStamp()
     * @see VirtualFile#getModificationStamp()
     */
    @Contract(pure = true)
    long getModificationStamp();

    /**
     * Fires a notification that the user would like to remove the read-only state
     * from the document (the read-only state can be removed by checking the file out
     * from the version control system, or by clearing the read-only attribute on the file).
     */
    default void fireReadOnlyModificationAttempt() {
    }

    /**
     * Adds a listener for receiving notifications about changes in the document content.
     *
     * @param listener the listener instance.
     */
    default void addDocumentListener(@Nonnull DocumentListener listener) {
    }

    default void addDocumentListener(@Nonnull DocumentListener listener, @Nonnull Disposable parentDisposable) {
    }

    /**
     * Removes a listener for receiving notifications about changes in the document content, previously added via {@link #addDocumentListener(DocumentListener)}.
     * Don't call this method for listeners added via {@link #addDocumentListener(DocumentListener, Disposable)}, as that might cause memory leaks.
     *
     * @param listener the listener instance.
     */
    default void removeDocumentListener(@Nonnull DocumentListener listener) {
    }

    /**
     * Creates a range marker which points to the specified range of text in the document and
     * is automatically adjusted when the document text is changed. The marker is invalidated
     * by external changes to the document text (for example, reloading the file from disk).
     *
     * @param startOffset the start offset for the range of text covered by the marker.
     * @param endOffset   the end offset for the range of text covered by the marker.
     * @return the marker instance.
     */
    @Nonnull
    default RangeMarker createRangeMarker(int startOffset, int endOffset) {
        return createRangeMarker(startOffset, endOffset, false);
    }

    /**
     * Creates a range marker which points to the specified range of text in the document and
     * is automatically adjusted when the document text is changed. The marker is optionally
     * invalidated by external changes to the document text (for example, reloading the file from disk).
     *
     * @param startOffset             the start offset for the range of text covered by the marker.
     * @param endOffset               the end offset for the range of text covered by the marker.
     * @param surviveOnExternalChange if true, the marker is not invalidated by external changes.
     * @return the marker instance.
     */
    @Nonnull
    RangeMarker createRangeMarker(int startOffset, int endOffset, boolean surviveOnExternalChange);

    /**
     * Adds a listener for receiving notifications about changes in the properties of the document
     * (for example, its read-only state).
     *
     * @param listener the listener instance.
     */
    default void addPropertyChangeListener(@Nonnull PropertyChangeListener listener) {
    }

    /**
     * Removes a listener for receiving notifications about changes in the properties of the document
     * (for example, its read-only state).
     *
     * @param listener the listener instance.
     */
    default void removePropertyChangeListener(@Nonnull PropertyChangeListener listener) {
    }

    /**
     * Marks the document as read-only or read/write. This method only modifies the flag stored
     * in the document instance - no checkouts or file changes are performed.
     *
     * @param isReadOnly the new value of the read-only flag.
     * @see #isWritable()
     * @see #fireReadOnlyModificationAttempt()
     */
    default void setReadOnly(boolean isReadOnly) {
    }

    /**
     * Marks a range of text in the document as read-only (attempts to modify text in the
     * range cause {@link ReadOnlyFragmentModificationException} to be thrown).
     *
     * @param startOffset the start offset of the text range to mark as read-only.
     * @param endOffset   the end offset of the text range to mark as read-only.
     * @return the marker instance.
     * @see #removeGuardedBlock(RangeMarker)
     * @see #startGuardedBlockChecking()
     * @see consulo.ide.impl.idea.openapi.editor.actionSystem.EditorActionManager#setReadonlyFragmentModificationHandler(consulo.ide.impl.idea.openapi.editor.actionSystem.ReadonlyFragmentModificationHandler)
     */
    @Nonnull
    RangeMarker createGuardedBlock(int startOffset, int endOffset);

    /**
     * Removes a marker marking a range of text in the document as read-only.
     *
     * @param block the marker to remove.
     * @see #createGuardedBlock(int, int)
     */
    default void removeGuardedBlock(@Nonnull RangeMarker block) {
    }

    /**
     * Returns the read-only marker covering the specified offset in the document.
     *
     * @param offset the offset for which the marker is requested.
     * @return the marker instance, or {@code null} if the specified offset is not covered by a read-only marker.
     */
    @Nullable
    default RangeMarker getOffsetGuard(int offset) {
        return getRangeGuard(offset, offset);
    }

    /**
     * Returns the read-only marker covering the specified range in the document.
     *
     * @param start the start offset of the range for which the marker is requested.
     * @param end   the end offset of the range for which the marker is requested.
     * @return the marker instance, or {@code null} if the specified range is not covered by a read-only marker.
     */
    @Nullable
    default RangeMarker getRangeGuard(int start, int end) {
        return null;
    }

    /**
     * Enables checking for read-only markers when the document is modified. Checking is disabled by default.
     *
     * @see #createGuardedBlock(int, int)
     * @see #stopGuardedBlockChecking()
     */
    default void startGuardedBlockChecking() {
    }

    /**
     * Disables checking for read-only markers when the document is modified. Checking is disabled by default.
     *
     * @see #createGuardedBlock(int, int)
     * @see #startGuardedBlockChecking()
     */
    default void stopGuardedBlockChecking() {
    }

    /**
     * Sets the maximum size of the cyclic buffer used for the document. If the document uses
     * a cyclic buffer, text added to the end of the document exceeding the maximum size causes
     * text to be removed from the beginning of the document.
     *
     * @param bufferSize the cyclic buffer size, or 0 if the document should not use a cyclic buffer.
     */
    default void setCyclicBufferSize(int bufferSize) {
    }

    void setText(@Nonnull CharSequence text);

    @Nonnull
    default RangeMarker createRangeMarker(@Nonnull TextRange textRange) {
        return createRangeMarker(textRange.getStartOffset(), textRange.getEndOffset());
    }

    @Contract(pure = true)
    default int getLineSeparatorLength(int line) {
        return 0;
    }

    /**
     * @see #setInBulkUpdate(boolean)
     */
    default boolean isInBulkUpdate() {
        return false;
    }

    /**
     * Enters or exits 'bulk' mode for processing of document changes. Bulk mode should be used when a large number of document changes
     * are applied in batch (without user interaction for each change), to improve performance. E.g. this mode is sometimes used by the
     * platform code during code formatting. In this mode some activities that usually happen on each document change will be muted, with
     * reconciliation happening on bulk mode exit.
     * <p>
     * As the reconciliation after exiting bulk mode implies some additional overhead, bulk mode shouldn't be used if the number of document
     * changes to be performed is relatively small. The number of changes which justifies switching to bulk mode is usually determined
     * empirically, but typically it's around hundred(s) of changes.
     * <p>
     * In bulk mode editor(s) associated with the document will stop updating internal caches on each document change. As a result, certain
     * operations with editor can return invalid results or lead to exception, if they are preformed in bulk mode. They include: querying
     * or updating folding or soft wrap data, editor position recalculation functions (offset to logical position, logical to visual position,
     * etc), querying or updating caret position or selection state.
     * <p>
     * Bulk mode shouldn't span more than one thread or EDT event. Typically it should turned on/off in a try/finally statement.
     *
     * @see consulo.ide.impl.idea.util.DocumentUtil#executeInBulk(Document, boolean, Runnable)
     * @see BulkAwareDocumentListener
     */
    default void setInBulkUpdate(boolean value) {
    }

    /**
     * Get all range markers
     * and hand them to the {@code processor} in their {@link RangeMarker#getStartOffset()} order
     */
    default boolean processRangeMarkers(@Nonnull Processor<? super RangeMarker> processor) {
        return processRangeMarkersOverlappingWith(0, getTextLength(), processor);
    }

    /**
     * Get range markers which {@link TextRange#intersects(int, int)} the specified range
     * and hand them to the {@code processor} in their {@link RangeMarker#getStartOffset()} order
     */
    default boolean processRangeMarkersOverlappingWith(int start, int end, @Nonnull Processor<? super RangeMarker> processor) {
        return true;
    }
}
