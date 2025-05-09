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
package consulo.content.scope;

import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

public class ComplementPackageSet extends PackageSetBase {
    private final PackageSet myComplementarySet;

    public ComplementPackageSet(PackageSet set) {
        myComplementarySet = set;
    }

    @Override
    public boolean contains(VirtualFile file, @Nonnull Project project, NamedScopesHolder holder) {
        return !myComplementarySet.contains(file, project, holder);
    }

    @Override
    @Nonnull
    public PackageSet createCopy() {
        return new ComplementPackageSet(myComplementarySet.createCopy());
    }

    @Override
    @Nonnull
    public String getText() {
        StringBuilder buf = new StringBuilder();
        boolean needParen = myComplementarySet.getNodePriority() > getNodePriority();
        buf.append('!');
        if (needParen) {
            buf.append('(');
        }
        buf.append(myComplementarySet.getText());
        if (needParen) {
            buf.append(')');
        }
        return buf.toString();
    }

    @Override
    public int getNodePriority() {
        return 1;
    }

    public PackageSet getComplementarySet() {
        return myComplementarySet;
    }
}
