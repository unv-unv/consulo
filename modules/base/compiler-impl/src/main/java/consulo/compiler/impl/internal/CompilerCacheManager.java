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

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.compiler.*;
import consulo.compiler.generic.GenericCompiler;
import consulo.compiler.impl.internal.generic.GenericCompilerCache;
import consulo.compiler.impl.internal.generic.GenericCompilerRunner;
import consulo.compiler.localize.CompilerLocalize;
import consulo.disposer.Disposable;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 * @since 2008-05-04
 */
@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class CompilerCacheManager implements Disposable {
    private static final Logger LOG = Logger.getInstance(CompilerCacheManager.class);
    private final Map<Compiler, Object> myCompilerToCacheMap = new HashMap<>();
    private final Map<GenericCompiler<?, ?, ?>, GenericCompilerCache<?, ?, ?>> myGenericCachesMap = new HashMap<>();
    private final List<Disposable> myCacheDisposables = new ArrayList<>();
    private final File myCachesRoot;
    private final Project myProject;

    @Inject
    public CompilerCacheManager(Project project) {
        myProject = project;
        myCachesRoot = CompilerPaths.getCacheStoreDirectory(project);
    }

    public static CompilerCacheManager getInstance(Project project) {
        return project.getInstance(CompilerCacheManager.class);
    }

    @Override
    public void dispose() {
        flushCaches();
    }

    private File getCompilerRootDir(Compiler compiler) {
        File dir = new File(myCachesRoot, getCompilerIdString(compiler));
        dir.mkdirs();
        return dir;
    }

    public synchronized <Key, SourceState, OutputState> GenericCompilerCache<Key, SourceState, OutputState>
    getGenericCompilerCache(GenericCompiler<Key, SourceState, OutputState> compiler) throws IOException {
        GenericCompilerCache<?, ?, ?> cache = myGenericCachesMap.get(compiler);
        if (cache == null) {
            GenericCompilerCache<?, ?, ?> genericCache = new GenericCompilerCache<>(
                compiler,
                GenericCompilerRunner.getGenericCompilerCacheDir(myProject, compiler)
            );
            myGenericCachesMap.put(compiler, genericCache);
            myCacheDisposables.add(() -> {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Closing cache for feneric compiler " + compiler.getId());
                }
                genericCache.close();
            });
            cache = genericCache;
        }
        //noinspection unchecked
        return (GenericCompilerCache<Key, SourceState, OutputState>) cache;
    }

    public synchronized FileProcessingCompilerStateCache getFileProcessingCompilerCache(FileProcessingCompiler compiler)
        throws IOException {
        Object cache = myCompilerToCacheMap.get(compiler);
        if (cache == null) {
            File compilerRootDir = getCompilerRootDir(compiler);
            FileProcessingCompilerStateCache stateCache = new FileProcessingCompilerStateCache(compilerRootDir, compiler);
            myCompilerToCacheMap.put(compiler, stateCache);
            myCacheDisposables.add(() -> {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Closing cache for compiler " + compiler.getDescription() + "; cache root dir: " + compilerRootDir);
                }
                stateCache.close();
            });
            cache = stateCache;
        }
        else {
            LOG.assertTrue(cache instanceof FileProcessingCompilerStateCache);
        }
        return (FileProcessingCompilerStateCache) cache;
    }

    public synchronized StateCache<ValidityState> getGeneratingCompilerCache(final GeneratingCompiler compiler) throws IOException {
        Object cache = myCompilerToCacheMap.get(compiler);
        if (cache == null) {
            final File cacheDir = getCompilerRootDir(compiler);
            StateCache<ValidityState> stateCache = new StateCache<>(new File(cacheDir, "timestamps")) {
                @Override
                public ValidityState read(DataInput stream) throws IOException {
                    return compiler.createValidityState(stream);
                }

                @Override
                public void write(ValidityState validityState, DataOutput out) throws IOException {
                    validityState.save(out);
                }
            };
            myCompilerToCacheMap.put(compiler, stateCache);
            myCacheDisposables.add(() -> {
                try {
                    stateCache.close();
                }
                catch (IOException e) {
                    LOG.info(e);
                }
            });
            cache = stateCache;
        }
        return (StateCache<ValidityState>) cache;
    }

    public static String getCompilerIdString(Compiler compiler) {
        String description = compiler.getDescription();
        return description.replaceAll("\\s+", "_").replaceAll("[\\.\\?]", "_").toLowerCase();
    }

    public synchronized void flushCaches() {
        for (Disposable disposable : myCacheDisposables) {
            try {
                disposable.dispose();
            }
            catch (Throwable e) {
                LOG.info(e);
            }
        }
        myCacheDisposables.clear();
        myGenericCachesMap.clear();
        myCompilerToCacheMap.clear();
    }

    public void clearCaches(CompileContext context) {
        flushCaches();
        File[] children = myCachesRoot.listFiles();
        if (children != null) {
            for (File child : children) {
                boolean deleteOk = FileUtil.delete(child);
                if (!deleteOk) {
                    context.addMessage(
                        CompilerMessageCategory.ERROR,
                        CompilerLocalize.compilerErrorFailedToDelete(child.getPath()).get(),
                        null,
                        -1,
                        -1
                    );
                }
            }
        }
    }
}
