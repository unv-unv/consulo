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
import consulo.util.collection.Iterators;

import java.util.Iterator;
import java.util.function.Predicate;

@Deprecated(forRemoval = true)
@DeprecationInfo("Old diff impl, must be removed")
public interface FragmentList {
    FragmentList shift(
        TextRange rangeShift1,
        TextRange rangeShift2,
        int startLine1,
        int startLine2
    );

    FragmentList EMPTY = new FragmentList() {
        @Override
        public FragmentList shift(TextRange rangeShift1, TextRange rangeShift2, int startLine1, int startLine2) {
            return EMPTY;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public Iterator<Fragment> iterator() {
            return Iterators.empty();
        }

        @Override
        public Fragment getFragmentAt(int offset, FragmentSide side, Predicate<Fragment> condition) {
            return null;
        }
    };

    boolean isEmpty();

    Iterator<Fragment> iterator();

    Fragment getFragmentAt(int offset, FragmentSide side, Predicate<Fragment> condition);
}
