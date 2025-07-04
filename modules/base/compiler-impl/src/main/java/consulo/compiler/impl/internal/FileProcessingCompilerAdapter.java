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
package consulo.compiler.impl.internal;

import consulo.compiler.CompileContext;
import consulo.compiler.FileProcessingCompiler;
import consulo.compiler.ValidityState;
import jakarta.annotation.Nullable;

import java.io.File;

/**
 * @author Eugene Zhuravlev
 * @since 2003-10-16
 */
public class FileProcessingCompilerAdapter {
    private final CompileContext myCompileContext;
    private final FileProcessingCompiler myCompiler;

    protected FileProcessingCompilerAdapter(CompileContext compileContext, FileProcessingCompiler compiler) {
        myCompileContext = compileContext;
        myCompiler = compiler;
    }

    public CompileContext getCompileContext() {
        return myCompileContext;
    }

    public FileProcessingCompiler.ProcessingItem[] getProcessingItems() {
        return myCompiler.getProcessingItems(getCompileContext());
    }

    public FileProcessingCompiler.ProcessingItem[] process(FileProcessingCompiler.ProcessingItem[] items) {
        return myCompiler.process(getCompileContext(), items);
    }

    public final FileProcessingCompiler getCompiler() {
        return myCompiler;
    }

    public void processOutdatedItem(CompileContext context, File file, @Nullable ValidityState state) {
    }
}
