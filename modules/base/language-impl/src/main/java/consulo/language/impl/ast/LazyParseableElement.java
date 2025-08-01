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
package consulo.language.impl.ast;

import consulo.application.progress.ProgressManager;
import consulo.language.ast.IElementType;
import consulo.language.ast.ILazyParseableElementTypeBase;
import consulo.language.impl.DebugUtil;
import consulo.logging.Logger;
import consulo.logging.attachment.AttachmentFactory;
import consulo.util.lang.CharArrayUtil;
import consulo.util.lang.ImmutableCharSequence;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.ref.SoftReference;
import org.jetbrains.annotations.TestOnly;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.function.Supplier;

/**
 * @author max
 */
public class LazyParseableElement extends CompositeElement {
  private static final Logger LOG = Logger.getInstance(LazyParseableElement.class);
  private static final Supplier<CharSequence> NO_TEXT = () -> null;

  private static class ChameleonLock {
    private ChameleonLock() {
    }

    @Override
    public String toString() {
      return "chameleon parsing lock";
    }
  }

  // Lock which protects expanding chameleon for this node.
  // Under no circumstances should you grab the PSI_LOCK while holding this lock.
  private final ChameleonLock lock = new ChameleonLock();
  /**
   * Cached or non-parsed text of this element. Must be non-null if {@link #myParsed} is false.
   * Coordinated writes to (myParsed, myText) are guarded by {@link #lock}
   */
  @Nonnull
  private volatile Supplier<CharSequence> myText;
  private volatile boolean myParsed;

  public LazyParseableElement(@Nonnull IElementType type, @Nullable CharSequence text) {
    super(type);
    synchronized (lock) {
      if (text == null) {
        myParsed = true;
        myText = NO_TEXT;
      }
      else {
        myText = () -> ImmutableCharSequence.asImmutable(text);
        setCachedLength(text.length());
      }
    }
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    synchronized (lock) {
      if (myParsed) {
        myText = NO_TEXT;
      }
      else {
        setCachedLength(myText.get().length());
      }
    }
  }

  @Nonnull
  @Override
  public String getText() {
    CharSequence text = myText();
    if (text != null) {
      return text.toString();
    }
    String s = super.getText();
    myText = new SoftReference<>(s);
    return s;
  }

  @Override
  @Nonnull
  public CharSequence getChars() {
    CharSequence text = myText();
    if (text == null) {
      // use super.getText() instead of super.getChars() to avoid extra myText() call
      text = super.getText();
      myText = new SoftReference<>(text);
    }
    return text;
  }

  @Override
  public int getTextLength() {
    CharSequence text = myText();
    if (text != null) {
      return text.length();
    }
    return super.getTextLength();
  }

  @Override
  public int hc() {
    CharSequence text = myText();
    return text == null ? super.hc() : LeafElement.leafHC(text);
  }

  @Override
  public int textMatches(@Nonnull CharSequence buffer, int start) {
    CharSequence text = myText();
    if (text != null) {
      return LeafElement.leafTextMatches(text, buffer, start);
    }
    return super.textMatches(buffer, start);
  }

  public boolean isParsed() {
    return myParsed;
  }

  private CharSequence myText() {
    return myText.get();
  }

  @Override
  final void setFirstChildNode(TreeElement child) {
    if (!isParsed()) {
      LOG.error("Mutating collapsed chameleon");
    }
    super.setFirstChildNode(child);
  }

  @Override
  final void setLastChildNode(TreeElement child) {
    if (!isParsed()) {
      LOG.error("Mutating collapsed chameleon");
    }
    super.setLastChildNode(child);
  }

  private void ensureParsed() {
    if (!ourParsingAllowed) {
      LOG.error("Parsing not allowed!!!");
    }
    if (myParsed) return;

    CharSequence text;
    synchronized (lock) {
      if (myParsed) return;

      text = myText.get();
      assert text != null;

      FileElement fileElement = TreeUtil.getFileElement(this);
      if (fileElement == null) {
        LOG.error("Chameleons must not be parsed till they're in file tree: " + this);
      }
      else {
        fileElement.assertReadAccessAllowed();
      }

      if (rawFirstChild() != null) {
        LOG.error("Reentrant parsing?");
      }

      DebugUtil.performPsiModification("lazy-parsing", () -> {
        TreeElement parsedNode = (TreeElement)((ILazyParseableElementTypeBase)getElementType()).parseContents(this);
        assertTextLengthIntact(text, parsedNode);

        if (parsedNode != null) {
          setChildren(parsedNode);
        }

        myParsed = true;
        myText = new SoftReference<>(text);
      });
    }
  }

  private void assertTextLengthIntact(CharSequence text, TreeElement child) {
    int length = 0;
    while (child != null) {
      length += child.getTextLength();
      child = child.getTreeNext();
    }
    if (length != text.length()) {
      LOG.error("Text mismatch in " + ObjectUtil.objectInfo(getElementType()), AttachmentFactory.get().create("code.txt", text.toString()));
    }
  }

  private void setChildren(@Nonnull TreeElement parsedNode) {
    ProgressManager.getInstance().executeNonCancelableSection(() -> {
      try {
        TreeElement last = CompositeElement.rawSetParents(parsedNode, this);
        super.setFirstChildNode(parsedNode);
        super.setLastChildNode(last);
      }
      catch (Throwable e) {
        LOG.error("Chameleon expansion may not be interrupted by exceptions", e);
      }
    });
  }

  @Override
  public void rawAddChildrenWithoutNotifications(@Nonnull TreeElement first) {
    if (!isParsed()) {
      LOG.error("Mutating collapsed chameleon " + this.getClass());
    }
    super.rawAddChildrenWithoutNotifications(first);
  }

  @Override
  public TreeElement getFirstChildNode() {
    ensureParsed();
    return super.getFirstChildNode();
  }

  @Override
  public TreeElement getLastChildNode() {
    ensureParsed();
    return super.getLastChildNode();
  }

  public int copyTo(@Nullable char[] buffer, int start) {
    CharSequence text = myText();
    if (text == null) return -1;

    if (buffer != null) {
      CharArrayUtil.getChars(text, buffer, start);
    }
    return start + text.length();
  }

  private static boolean ourParsingAllowed = true;

  @TestOnly
  public static void setParsingAllowed(boolean allowed) {
    ourParsingAllowed = allowed;
  }

}
