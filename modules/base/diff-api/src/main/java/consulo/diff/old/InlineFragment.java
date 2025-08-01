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
package consulo.diff.old;

import consulo.annotation.DeprecationInfo;
import consulo.document.util.TextRange;
import consulo.logging.Logger;

import java.util.function.Predicate;

@Deprecated(forRemoval = true)
@DeprecationInfo("Old diff impl, must be removed")
public class InlineFragment implements Fragment {
    private static final Logger LOG = Logger.getInstance(InlineFragment.class);
    private final TextRange myRange1;
    private final TextRange myRange2;
    private final TextDiffTypeEnum myType;

    public InlineFragment(TextDiffTypeEnum type, TextRange range1, TextRange range2) {
        myType = type;
        myRange1 = range1;
        myRange2 = range2;
    }

    @Override
    public TextDiffTypeEnum getType() {
        return myType;
    }

    @Override
    public TextRange getRange(FragmentSide side) {
        if (side == FragmentSide.SIDE1) {
            return myRange1;
        }
        if (side == FragmentSide.SIDE2) {
            return myRange2;
        }
        throw new IllegalArgumentException(String.valueOf(side));
    }

    @Override
    public Fragment shift(TextRange range1, TextRange range2, int startingLine1, int startingLine2) {
        return new InlineFragment(
            myType,
            LineFragment.shiftRange(range1, myRange1),
            LineFragment.shiftRange(range2, myRange2)
        );
    }

    @Override
    public void highlight(FragmentHighlighter fragmentHighlighter) {
        fragmentHighlighter.highlightInline(this);
    }

    @Override
    public Fragment getSubfragmentAt(int offset, FragmentSide side, Predicate<Fragment> condition) {
        LOG.assertTrue(getRange(side).getStartOffset() <= offset
            && offset < getRange(side).getEndOffset() && condition.test(this));
        return this;
    }
}
