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
package consulo.compiler;

import consulo.virtualFileSystem.fileType.FileType;
import consulo.module.Module;
import consulo.virtualFileSystem.VirtualFile;
import consulo.util.collection.Chunk;
import jakarta.annotation.Nonnull;

import java.util.Collection;

/**
 * A tag interface indicating that the compiler will translate one type of files into another (e.g. .java -> .class).
 * This affects the order of compiler calls.
 * The sequence in which compilers are called:
 * SourceGeneratingCompiler -> SourceInstrumentingCompiler -> TranslatingCompiler ->  ClassInstrumentingCompiler ->
 * ClassPostProcessingCompiler -> PackagingCompiler -> Validator
 */
public interface TranslatingCompiler extends Compiler {
    /**
     * Defines a single file compiled by the compiler.
     */
    interface OutputItem {
        /**
         * Returns the path to the output file.
         *
         * @return absolute path of the output file ('/' slashes used)
         */
        String getOutputPath();

        /**
         * Returns the path to the source file.
         *
         * @return the source file to be compiled
         */
        VirtualFile getSourceFile();
    }

    interface OutputSink {
        /**
         * @param outputRoot       output directory
         * @param items            output items that were successfully compiled.
         * @param filesToRecompile virtual files that should be considered as "modified" next time compilation is invoked.
         */
        void add(String outputRoot, Collection<OutputItem> items, VirtualFile[] filesToRecompile);
    }


    /**
     * Checks if the compiler can compile the specified file.
     *
     * @param file    the file to check.
     * @param context the context for the current compile operation.
     * @return true if can compile the file, false otherwise. If the method returns false, <code>file</code>
     * will not be included in the list of files passed to
     * {@link #compile(CompileContext, Chunk<Module>, VirtualFile[], TranslatingCompiler.OutputSink)}.
     */
    boolean isCompilableFile(VirtualFile file, CompileContext context);

    /**
     * Compiles the specified files.
     *
     * @param context     the context for the current compile operation.
     * @param moduleChunk contains modules that form a cycle. If project module graph has no cycles, a chunk corresponds to a single module
     * @param files       the source files to compile that correspond to the module chunk
     * @param sink        storage that accepts compiler output results
     */
    void compile(CompileContext context, Chunk<Module> moduleChunk, VirtualFile[] files, OutputSink sink);

    @Nonnull
    FileType[] getInputFileTypes();

    @Nonnull
    FileType[] getOutputFileTypes();
}
