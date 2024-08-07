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
package consulo.language.lexer;

import consulo.language.ast.IElementType;

/**
 * @author peter
 */
public abstract class LookAheadLexer extends LexerBase {
    private int myLastOffset;
    private int myLastState;

    private final Lexer myBaseLexer;
    private int myTokenStart;
    private final MutableRandomAccessQueue<IElementType> myTypeCache;
    private final MutableRandomAccessQueue<Integer> myEndOffsetCache;

    public LookAheadLexer(final Lexer baseLexer, int capacity) {
        myBaseLexer = baseLexer;
        myTypeCache = new MutableRandomAccessQueue<>(capacity);
        myEndOffsetCache = new MutableRandomAccessQueue<>(capacity);
    }

    public LookAheadLexer(final Lexer baseLexer) {
        this(baseLexer, 64);
    }

    protected void addToken(IElementType type) {
        addToken(myBaseLexer.getTokenEnd(), type);
    }

    protected void addToken(int endOffset, IElementType type) {
        myTypeCache.addLast(type);
        myEndOffsetCache.addLast(endOffset);
    }

    protected void lookAhead(Lexer baseLexer) {
        advanceLexer(baseLexer);
    }

    @Override
    public void advance() {
        if (!myTypeCache.isEmpty()) {
            myTypeCache.pullFirst();
            myTokenStart = myEndOffsetCache.pullFirst();
        }
        if (myTypeCache.isEmpty()) {
            doLookAhead();
        }
    }

    private void doLookAhead() {
        myLastOffset = myTokenStart;
        myLastState = myBaseLexer.getState();

        lookAhead(myBaseLexer);
        assert !myTypeCache.isEmpty();
    }

    @Override
    public CharSequence getBufferSequence() {
        return myBaseLexer.getBufferSequence();
    }

    @Override
    public int getBufferEnd() {
        return myBaseLexer.getBufferEnd();
    }

    protected int getCacheSize() {
        return myTypeCache.size();
    }

    protected void resetCacheSize(int size) {
        while (myTypeCache.size() > size) {
            myTypeCache.removeLast();
            myEndOffsetCache.removeLast();
        }
    }

    public IElementType replaceCachedType(int index, IElementType token) {
        return myTypeCache.set(index, token);
    }

    @Override
    public int getState() {
        int offset = myTokenStart - myLastOffset;
        return myLastState | (offset << 16);
    }

    @Override
    public int getTokenEnd() {
        return myEndOffsetCache.peekFirst();
    }

    @Override
    public int getTokenStart() {
        return myTokenStart;
    }

    @Override
    public LookAheadLexerPosition getCurrentPosition() {
        return new LookAheadLexerPosition(this, ImmutableUserMap.EMPTY);
    }

    @Override
    public final void restore(final LexerPosition _position) {
        restore((LookAheadLexerPosition)_position);
    }

    protected void restore(final LookAheadLexerPosition position) {
        start(myBaseLexer.getBufferSequence(), position.lastOffset, myBaseLexer.getBufferEnd(), position.lastState);
        for (int i = 0; i < position.advanceCount; i++) {
            advance();
        }
    }

    @Override
    public IElementType getTokenType() {
        return myTypeCache.peekFirst();
    }

    @Override
    public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
        myBaseLexer.start(buffer, startOffset, endOffset, initialState & 0xFFFF);
        myTokenStart = startOffset;
        myTypeCache.clear();
        myEndOffsetCache.clear();
        doLookAhead();
    }

    protected static class LookAheadLexerPosition implements LexerPosition {
        final int lastOffset;
        final int lastState;
        final int tokenStart;
        final int advanceCount;
        final ImmutableUserMap customMap;

        public LookAheadLexerPosition(final LookAheadLexer lookAheadLexer, final ImmutableUserMap map) {
            customMap = map;
            lastOffset = lookAheadLexer.myLastOffset;
            lastState = lookAheadLexer.myLastState;
            tokenStart = lookAheadLexer.myTokenStart;
            advanceCount = lookAheadLexer.myTypeCache.size() - 1;
        }

        public ImmutableUserMap getCustomMap() {
            return customMap;
        }

        @Override
        public int getOffset() {
            return tokenStart;
        }

        @Override
        public int getState() {
            return lastState;
        }
    }

    protected final void advanceLexer(Lexer lexer) {
        advanceAs(lexer, lexer.getTokenType());
    }

    protected final void advanceAs(Lexer lexer, IElementType type) {
        addToken(type);
        lexer.advance();
    }
}
