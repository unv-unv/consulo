// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.*;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.provided.StubProvidedIndexExtension;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.util.CachedValueImpl;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.hash.MergedInvertedIndex;
import com.intellij.util.indexing.impl.*;
import com.intellij.util.indexing.provided.ProvidedIndexExtension;
import com.intellij.util.io.*;
import com.intellij.util.io.DataOutputStream;
import consulo.logging.Logger;
import gnu.trove.*;
import javax.annotation.Nonnull;

import javax.annotation.Nullable;
import javax.inject.Singleton;
import java.io.*;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;

@Singleton
@State(name = "FileBasedIndex", storages = @Storage(value = "stubIndex.xml", roamingType = RoamingType.DISABLED))
public final class StubIndexImpl extends StubIndex implements PersistentStateComponent<StubIndexState> {
  private static final AtomicReference<Boolean> ourForcedClean = new AtomicReference<>(null);
  private static final Logger LOG = Logger.getInstance(StubIndexImpl.class);

  private static class AsyncState {
    private final Map<StubIndexKey<?, ?>, UpdatableIndex<?, Void, FileContent>> myIndices = new THashMap<>();
    private final Map<StubIndexKey<?, ?>, TObjectHashingStrategy<?>> myKeyHashingStrategies = new THashMap<>();
    private final TObjectIntHashMap<ID<?, ?>> myIndexIdToVersionMap = new TObjectIntHashMap<>();
  }

  private final Map<StubIndexKey<?, ?>, CachedValue<Map<CompositeKey, StubIdList>>> myCachedStubIds = FactoryMap.createMap(k -> {
    UpdatableIndex<Integer, SerializedStubTree, FileContent> index = getStubUpdatingIndex();
    ModificationTracker tracker = index::getModificationStamp;
    return new CachedValueImpl<>(() -> new CachedValueProvider.Result<>(ContainerUtil.newConcurrentMap(), tracker));
  }, ContainerUtil::newConcurrentMap);

  private final StubProcessingHelper myStubProcessingHelper;
  private final IndexAccessValidator myAccessValidator = new IndexAccessValidator();
  private volatile Future<AsyncState> myStateFuture;
  private volatile AsyncState myState;
  private volatile boolean myInitialized;

  private StubIndexState myPreviouslyRegistered;

  public StubIndexImpl() {
    myStubProcessingHelper = new StubProcessingHelper();
  }

  @Nullable
  static StubIndexImpl getInstanceOrInvalidate() {
    if (ourForcedClean.compareAndSet(null, Boolean.TRUE)) {
      return null;
    }
    return (StubIndexImpl)getInstance();
  }

  private AsyncState getAsyncState() {
    AsyncState state = myState; // memory barrier
    if (state == null) {
      try {
        myState = state = myStateFuture.get();
      }
      catch (Throwable t) {
        throw new RuntimeException(t);
      }
    }
    return state;
  }

  @Nonnull
  public static <K, I> FileBasedIndexExtension<K, Void> wrapStubIndexExtension(StubIndexExtension<K, ?> extension) {
    return new FileBasedIndexExtension<K, Void>() {
      @Nonnull
      @Override
      public ID<K, Void> getName() {
        return (ID<K, Void>)extension.getKey();
      }

      @Nonnull
      @Override
      public FileBasedIndex.InputFilter getInputFilter() {
        return (p, f) -> {
          throw new UnsupportedOperationException();
        };
      }

      @Override
      public boolean dependsOnFileContent() {
        return true;
      }

      @Nonnull
      @Override
      public DataIndexer<K, Void, FileContent> getIndexer() {
        return i -> {
          throw new AssertionError();
        };
      }

      @Nonnull
      @Override
      public KeyDescriptor<K> getKeyDescriptor() {
        return extension.getKeyDescriptor();
      }

      @Nonnull
      @Override
      public DataExternalizer<Void> getValueExternalizer() {
        return VoidDataExternalizer.INSTANCE;
      }

      @Override
      public int getVersion() {
        return extension.getVersion();
      }

      @Override
      public boolean traceKeyHashToVirtualFileMapping() {
        return extension instanceof StringStubIndexExtension && ((StringStubIndexExtension)extension).traceKeyHashToVirtualFileMapping();
      }
    };
  }

  private static <K> void registerIndexer(@Nonnull final StubIndexExtension<K, ?> extension, final boolean forceClean,
                                          @Nonnull AsyncState state, @Nonnull IndicesRegistrationResult registrationResultSink) throws IOException {
    final StubIndexKey<K, ?> indexKey = extension.getKey();
    final int version = extension.getVersion();
    FileBasedIndexExtension<K, Void> wrappedExtension = wrapStubIndexExtension(extension);
    synchronized (state) {
      state.myIndexIdToVersionMap.put(indexKey, version);
    }

    final File indexRootDir = IndexInfrastructure.getIndexRootDir(indexKey);

    if (forceClean || IndexingStamp.versionDiffers(indexKey, version)) {
      final File versionFile = IndexInfrastructure.getVersionFile(indexKey);
      final boolean versionFileExisted = versionFile.exists();

      final String[] children = indexRootDir.list();
      // rebuild only if there exists what to rebuild
      boolean indexRootHasChildren = children != null && children.length > 0;
      boolean needRebuild = !forceClean && (versionFileExisted || indexRootHasChildren);

      if (needRebuild) registrationResultSink.registerIndexAsChanged(indexKey);
      else registrationResultSink.registerIndexAsInitiallyBuilt(indexKey);
      if (indexRootHasChildren) FileUtil.deleteWithRenaming(indexRootDir);
      IndexingStamp.rewriteVersion(indexKey, version); // todo snapshots indices
    }
    else {
      registrationResultSink.registerIndexAsUptoDate(indexKey);
    }
    UpdatableIndex<Integer, SerializedStubTree, FileContent> stubUpdatingIndex = getStubUpdatingIndex();
    ReadWriteLock lock = stubUpdatingIndex.getLock();

    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        final VfsAwareMapIndexStorage<K, Void> storage =
                new VfsAwareMapIndexStorage<>(IndexInfrastructure.getStorageFile(indexKey), wrappedExtension.getKeyDescriptor(), wrappedExtension.getValueExternalizer(), wrappedExtension.getCacheSize(),
                                              wrappedExtension.keyIsUniqueForIndexedFile(), wrappedExtension.traceKeyHashToVirtualFileMapping());
        final MemoryIndexStorage<K, Void> memStorage = new MemoryIndexStorage<>(storage, indexKey);
        UpdatableIndex<K, Void, FileContent> index = new VfsAwareMapReduceIndex<>(wrappedExtension, memStorage, null, null, null, lock);

        if (stubUpdatingIndex instanceof MergedInvertedIndex) {
          ProvidedIndexExtension<Integer, SerializedStubTree> ex = ((MergedInvertedIndex<Integer, SerializedStubTree>)stubUpdatingIndex).getProvidedExtension();
          if (ex instanceof StubProvidedIndexExtension) {
            ProvidedIndexExtension<K, Void> providedStubIndexExtension = ((StubProvidedIndexExtension)ex).findProvidedStubIndex(extension);
            if (providedStubIndexExtension != null) {
              index = ProvidedIndexExtension.wrapWithProvidedIndex(providedStubIndexExtension, wrappedExtension, index);
            }
          }
        }

        TObjectHashingStrategy<K> keyHashingStrategy = new TObjectHashingStrategy<K>() {
          private final KeyDescriptor<K> descriptor = extension.getKeyDescriptor();

          @Override
          public int computeHashCode(K object) {
            return descriptor.getHashCode(object);
          }

          @Override
          public boolean equals(K o1, K o2) {
            return descriptor.isEqual(o1, o2);
          }
        };

        synchronized (state) {
          state.myIndices.put(indexKey, index);
          state.myKeyHashingStrategies.put(indexKey, keyHashingStrategy);
        }
        break;
      }
      catch (IOException e) {
        registrationResultSink.registerIndexAsInitiallyBuilt(indexKey);
        onExceptionInstantiatingIndex(indexKey, version, indexRootDir, e);
      }
      catch (RuntimeException e) {
        Throwable cause = FileBasedIndexImpl.getCauseToRebuildIndex(e);
        if (cause == null) throw e;
        onExceptionInstantiatingIndex(indexKey, version, indexRootDir, e);
      }
    }
  }

  @Nonnull
  <K> TObjectHashingStrategy<K> getKeyHashingStrategy(StubIndexKey<K, ?> stubIndexKey) {
    return (TObjectHashingStrategy<K>)getAsyncState().myKeyHashingStrategies.get(stubIndexKey);
  }

  private static <K> void onExceptionInstantiatingIndex(@Nonnull StubIndexKey<K, ?> indexKey, int version, @Nonnull File indexRootDir, @Nonnull Exception e) throws IOException {
    LOG.info(e);
    FileUtil.deleteWithRenaming(indexRootDir);
    IndexingStamp.rewriteVersion(indexKey, version); // todo snapshots indices
  }

  public long getIndexModificationStamp(@Nonnull StubIndexKey<?, ?> indexId, @Nonnull Project project) {
    UpdatableIndex<?, Void, FileContent> index = getAsyncState().myIndices.get(indexId);
    if (index != null) {
      FileBasedIndex.getInstance().ensureUpToDate(StubUpdatingIndex.INDEX_ID, project, GlobalSearchScope.allScope(project));
      return index.getModificationStamp();
    }
    return -1;
  }

  public void flush() throws StorageException {
    if (!myInitialized) {
      return;
    }
    for (UpdatableIndex<?, Void, FileContent> index : getAsyncState().myIndices.values()) {
      index.flush();
    }
  }

  public static class StubIdExternalizer implements DataExternalizer<StubIdList> {
    public static final StubIdExternalizer INSTANCE = new StubIdExternalizer();

    @Override
    public void save(@Nonnull final DataOutput out, @Nonnull final StubIdList value) throws IOException {
      int size = value.size();
      if (size == 0) {
        DataInputOutputUtil.writeINT(out, Integer.MAX_VALUE);
      }
      else if (size == 1) {
        DataInputOutputUtil.writeINT(out, value.get(0)); // most often case
      }
      else {
        DataInputOutputUtil.writeINT(out, -size);
        for (int i = 0; i < size; ++i) {
          DataInputOutputUtil.writeINT(out, value.get(i));
        }
      }
    }

    @Nonnull
    @Override
    public StubIdList read(@Nonnull final DataInput in) throws IOException {
      int size = DataInputOutputUtil.readINT(in);
      if (size == Integer.MAX_VALUE) {
        return new StubIdList();
      }
      if (size >= 0) {
        return new StubIdList(size);
      }
      size = -size;
      int[] result = new int[size];
      for (int i = 0; i < size; ++i) {
        result[i] = DataInputOutputUtil.readINT(in);
      }
      return new StubIdList(result, size);
    }
  }

  <K> void serializeIndexValue(@Nonnull DataOutput out, @Nonnull StubIndexKey<K, ?> stubIndexKey, @Nonnull Map<K, StubIdList> map) throws IOException {
    UpdatableIndex<K, Void, FileContent> index = getIndex(stubIndexKey);
    if (index == null) return;
    KeyDescriptor<K> keyDescriptor = index.getExtension().getKeyDescriptor();

    BufferExposingByteArrayOutputStream indexOs = new BufferExposingByteArrayOutputStream();
    DataOutputStream indexDos = new DataOutputStream(indexOs);
    for (K key : map.keySet()) {
      keyDescriptor.save(indexDos, key);
      StubIdExternalizer.INSTANCE.save(indexDos, map.get(key));
    }
    DataInputOutputUtil.writeINT(out, indexDos.size());
    out.write(indexOs.getInternalBuffer(), 0, indexOs.size());
  }

  @Nonnull
  <K> Map<K, StubIdList> deserializeIndexValue(@Nonnull DataInput in, @Nonnull StubIndexKey<K, ?> stubIndexKey, @Nullable K requestedKey) throws IOException {
    UpdatableIndex<K, Void, FileContent> index = getIndex(stubIndexKey);
    KeyDescriptor<K> keyDescriptor = index.getExtension().getKeyDescriptor();

    int bufferSize = DataInputOutputUtil.readINT(in);
    byte[] buffer = new byte[bufferSize];
    in.readFully(buffer);
    UnsyncByteArrayInputStream indexIs = new UnsyncByteArrayInputStream(buffer);
    DataInputStream indexDis = new DataInputStream(indexIs);
    TObjectHashingStrategy<K> hashingStrategy = getKeyHashingStrategy(stubIndexKey);
    Map<K, StubIdList> result = new THashMap<>(hashingStrategy);
    while (indexDis.available() > 0) {
      K key = keyDescriptor.read(indexDis);
      StubIdList read = StubIdExternalizer.INSTANCE.read(indexDis);
      if (requestedKey != null) {
        if (hashingStrategy.equals(requestedKey, key)) {
          result.put(key, read);
          return result;
        }
      }
      else {
        result.put(key, read);
      }
    }
    return result;
  }

  <K> void skipIndexValue(@Nonnull DataInput in) throws IOException {
    int bufferSize = DataInputOutputUtil.readINT(in);
    in.skipBytes(bufferSize);
  }

  @Override
  public <Key, Psi extends PsiElement> boolean processElements(@Nonnull final StubIndexKey<Key, Psi> indexKey,
                                                               @Nonnull final Key key,
                                                               @Nonnull final Project project,
                                                               @Nullable final GlobalSearchScope scope,
                                                               @Nullable IdFilter idFilter,
                                                               @Nonnull final Class<Psi> requiredClass,
                                                               @Nonnull final Processor<? super Psi> processor) {
    IdIterator ids = getContainingIds(indexKey, key, project, idFilter, scope);
    UpdatableIndex<Integer, SerializedStubTree, FileContent> stubUpdatingIndex = getStubUpdatingIndex();
    if (stubUpdatingIndex == null) return true;
    PersistentFS fs = (PersistentFS)ManagingFS.getInstance();
    // already ensured up-to-date in getContainingIds() method
    try {
      while (ids.hasNext()) {
        int id = ids.next();
        ProgressManager.checkCanceled();
        VirtualFile file = IndexInfrastructure.findFileByIdIfCached(fs, id);
        if (file == null || (scope != null && !scope.contains(file))) {
          continue;
        }

        StubIdList list = myCachedStubIds.get(indexKey).getValue().computeIfAbsent(new CompositeKey(key, id), __ -> {
          try {
            Map<Integer, SerializedStubTree> data = stubUpdatingIndex.getIndexedFileData(id);
            LOG.assertTrue(data.size() == 1);
            SerializedStubTree tree = data.values().iterator().next();
            return tree.restoreIndexedStubs(StubForwardIndexExternalizer.IdeStubForwardIndexesExternalizer.INSTANCE, indexKey, key);
          }
          catch (StorageException | IOException e) {
            forceRebuild(e);
            return null;
          }
        });
        if (list == null) {
          LOG.error("StubUpdatingIndex & " + indexKey + " stub index mismatch. No stub index key is present");
          return true;
        }
        if (!myStubProcessingHelper.processStubsInFile(project, file, list, processor, scope, requiredClass)) {
          return false;
        }
      }
    }
    catch (RuntimeException e) {
      final Throwable cause = FileBasedIndexImpl.getCauseToRebuildIndex(e);
      if (cause != null) {
        forceRebuild(cause);
      }
      else {
        throw e;
      }
    }
    return true;
  }

  @SuppressWarnings("unchecked")
  private <Key> UpdatableIndex<Key, Void, FileContent> getIndex(@Nonnull StubIndexKey<Key, ?> indexKey) {
    return (UpdatableIndex<Key, Void, FileContent>)getAsyncState().myIndices.get(indexKey);
  }

  // Self repair for IDEA-181227, caused by (yet) unknown file event processing problem in indices
  // FileBasedIndex.requestReindex doesn't handle the situation properly because update requires old data that was lost
  private <Key> void wipeProblematicFileIdsForParticularKeyAndStubIndex(@Nonnull StubIndexKey<Key, ?> indexKey,
                                                                        @Nonnull Key key,
                                                                        @Nonnull UpdatableIndex<Integer, SerializedStubTree, FileContent> stubUpdatingIndex) {
    Set<VirtualFile> filesWithProblems = myStubProcessingHelper.takeAccumulatedFilesWithIndexProblems();

    if (filesWithProblems != null) {
      ((FileBasedIndexImpl)FileBasedIndex.getInstance()).runCleanupAction(() -> {
        boolean locked = stubUpdatingIndex.getWriteLock().tryLock();
        if (!locked) return; // nested indices invocation, can not cleanup without deadlock
        try {
          Map<Key, StubIdList> artificialOldValues = new THashMap<>();
          artificialOldValues.put(key, new StubIdList());

          for (VirtualFile file : filesWithProblems) {
            updateIndex(indexKey, FileBasedIndex.getFileId(file), artificialOldValues, Collections.emptyMap());
          }
        }
        finally {
          stubUpdatingIndex.getWriteLock().unlock();
        }
      });
    }
  }

  @Override
  public void forceRebuild(@Nonnull Throwable e) {
    FileBasedIndex.getInstance().scheduleRebuild(StubUpdatingIndex.INDEX_ID, e);
  }

  private static void requestRebuild() {
    FileBasedIndex.getInstance().requestRebuild(StubUpdatingIndex.INDEX_ID);
  }

  @Override
  @Nonnull
  public <K> Collection<K> getAllKeys(@Nonnull StubIndexKey<K, ?> indexKey, @Nonnull Project project) {
    Set<K> allKeys = new THashSet<>();
    processAllKeys(indexKey, project, Processors.cancelableCollectProcessor(allKeys));
    return allKeys;
  }

  @Override
  public <K> boolean processAllKeys(@Nonnull StubIndexKey<K, ?> indexKey, @Nonnull Processor<? super K> processor, @Nonnull GlobalSearchScope scope, @Nullable IdFilter idFilter) {
    final UpdatableIndex<K, Void, FileContent> index = getIndex(indexKey); // wait for initialization to finish
    if (index == null) return true;
    FileBasedIndex.getInstance().ensureUpToDate(StubUpdatingIndex.INDEX_ID, scope.getProject(), scope);

    try {
      return myAccessValidator.validate(StubUpdatingIndex.INDEX_ID, () -> FileBasedIndexImpl.disableUpToDateCheckIn(() -> index.processAllKeys(processor, scope, idFilter)));
    }
    catch (StorageException e) {
      forceRebuild(e);
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof IOException || cause instanceof StorageException) {
        forceRebuild(e);
      }
      throw e;
    }
    return true;
  }

  @Nonnull
  @Override
  public <Key> IdIterator getContainingIds(@Nonnull StubIndexKey<Key, ?> indexKey, @Nonnull Key dataKey, @Nonnull final Project project, @Nullable final GlobalSearchScope scope) {
    return getContainingIds(indexKey, dataKey, project, null, scope);
  }

  @Nonnull
  private <Key> IdIterator getContainingIds(@Nonnull StubIndexKey<Key, ?> indexKey,
                                            @Nonnull Key dataKey,
                                            @Nonnull final Project project,
                                            @Nullable IdFilter idFilter,
                                            @Nullable final GlobalSearchScope scope) {
    final FileBasedIndexImpl fileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
    ID<Integer, SerializedStubTree> stubUpdatingIndexId = StubUpdatingIndex.INDEX_ID;
    final UpdatableIndex<Key, Void, FileContent> index = getIndex(indexKey);   // wait for initialization to finish
    if (index == null) return IdIterator.EMPTY;

    if (idFilter == null) {
      idFilter = ((FileBasedIndexImpl)FileBasedIndex.getInstance()).projectIndexableFiles(project);
    }

    fileBasedIndex.ensureUpToDate(stubUpdatingIndexId, project, scope);

    UpdatableIndex<Integer, SerializedStubTree, FileContent> stubUpdatingIndex = fileBasedIndex.getIndex(stubUpdatingIndexId);

    try {
      final TIntArrayList result = new TIntArrayList();
      IdFilter finalIdFilter = idFilter;
      myAccessValidator.validate(stubUpdatingIndexId, () -> {
        try {
          // disable up-to-date check to avoid locks on attempt to acquire index write lock while holding at the same time the readLock for this index
          return FileBasedIndexImpl.disableUpToDateCheckIn(() -> ConcurrencyUtil.withLock(stubUpdatingIndex.getReadLock(), () -> {
            return index.getData(dataKey).forEach((id, value) -> {
              if (finalIdFilter == null || finalIdFilter.containsFileId(id)) {
                result.add(id);
              }
              return true;
            });
          }));
        }
        finally {
          wipeProblematicFileIdsForParticularKeyAndStubIndex(indexKey, dataKey, stubUpdatingIndex);
        }
      });
      return new IdIterator() {
        int cursor;

        @Override
        public boolean hasNext() {
          return cursor < result.size();
        }

        @Override
        public int next() {
          return result.get(cursor++);
        }

        @Override
        public int size() {
          return result.size();
        }
      };
    }
    catch (StorageException e) {
      forceRebuild(e);
    }
    catch (RuntimeException e) {
      final Throwable cause = FileBasedIndexImpl.getCauseToRebuildIndex(e);
      if (cause != null) {
        forceRebuild(cause);
      }
      else {
        throw e;
      }
    }

    return IdIterator.EMPTY;
  }

  @Override
  public void afterLoadState() {
    // ensure that FileBasedIndex task "FileIndexDataInitialization" submitted first
    FileBasedIndex.getInstance();
    myStateFuture = IndexInfrastructure.submitGenesisTask(new StubIndexInitialization());

    if (!IndexInfrastructure.ourDoAsyncIndicesInitialization) {
      try {
        myStateFuture.get();
      }
      catch (Throwable t) {
        LOG.error(t);
      }
    }
  }

  static void initExtensions() {
    // initialize stub index keys
    for (StubIndexExtension<?, ?> extension : StubIndexExtension.EP_NAME.getExtensionList()) {
      extension.getKey();
    }
  }

  public void dispose() {
    for (UpdatableIndex<?, ?, ?> index : getAsyncState().myIndices.values()) {
      index.dispose();
    }
  }

  void setDataBufferingEnabled(final boolean enabled) {
    for (UpdatableIndex<?, ?, ?> index : getAsyncState().myIndices.values()) {
      index.setBufferingEnabled(enabled);
    }
  }

  void cleanupMemoryStorage() {
    UpdatableIndex<Integer, SerializedStubTree, FileContent> stubUpdatingIndex = getStubUpdatingIndex();
    stubUpdatingIndex.getWriteLock().lock();

    try {
      for (UpdatableIndex<?, ?, ?> index : getAsyncState().myIndices.values()) {
        index.cleanupMemoryStorage();
      }
    }
    finally {
      stubUpdatingIndex.getWriteLock().unlock();
    }
  }

  void clearAllIndices() {
    if (!myInitialized) return;
    for (UpdatableIndex<?, ?, ?> index : getAsyncState().myIndices.values()) {
      try {
        index.clear();
      }
      catch (StorageException e) {
        LOG.error(e);
        throw new RuntimeException(e);
      }
    }
  }

  <K> void removeTransientDataForFile(@Nonnull StubIndexKey<K, ?> key, int inputId, @Nonnull Collection<? extends K> keys) {
    UpdatableIndex<K, Void, FileContent> index = getIndex(key);
    index.removeTransientDataForKeys(inputId, keys);
  }

  private boolean dropUnregisteredIndices(@Nonnull AsyncState state) {
    if (ApplicationManager.getApplication().isDisposed() || !IndexInfrastructure.hasIndices()) {
      return false;
    }

    Set<String> indicesToDrop = new HashSet<>(myPreviouslyRegistered != null ? myPreviouslyRegistered.registeredIndices : Collections.emptyList());
    for (ID<?, ?> key : state.myIndices.keySet()) {
      indicesToDrop.remove(key.getName());
    }

    if (!indicesToDrop.isEmpty()) {
      LOG.info("Dropping indices:" + StringUtil.join(indicesToDrop, ","));

      for (String s : indicesToDrop) {
        FileUtil.delete(IndexInfrastructure.getIndexRootDir(StubIndexKey.createIndexKey(s)));
      }
      return true;
    }
    return false;
  }

  @Override
  public StubIndexState getState() {
    if (!myInitialized) return null;
    return new StubIndexState(getAsyncState().myIndices.keySet());
  }

  @Override
  public void loadState(@Nonnull final StubIndexState state) {
    myPreviouslyRegistered = state;
  }

  public <K> void updateIndex(@Nonnull StubIndexKey<K, ?> key, int fileId, @Nonnull final Map<K, StubIdList> oldInputData, @Nonnull final Map<K, StubIdList> newInputData) {
    try {
      final UpdatableIndex<K, Void, FileContent> index = getIndex(key);
      if (index == null) return;
      index.updateWithMap(new AbstractUpdateData<K, Void>(fileId) {
        @Override
        protected boolean iterateKeys(@Nonnull KeyValueUpdateProcessor<? super K, ? super Void> addProcessor,
                                      @Nonnull KeyValueUpdateProcessor<? super K, ? super Void> updateProcessor,
                                      @Nonnull RemovedKeyProcessor<? super K> removeProcessor) throws StorageException {
          boolean modified = false;

          for (K oldKey : oldInputData.keySet()) {
            if (!newInputData.containsKey(oldKey)) {
              removeProcessor.process(oldKey, fileId);
              if (!modified) modified = true;
            }
          }

          for (K oldKey : newInputData.keySet()) {
            if (!oldInputData.containsKey(oldKey)) {
              addProcessor.process(oldKey, null, fileId);
              if (!modified) modified = true;
            }
          }

          return modified;
        }

        @Override
        public boolean newDataIsEmpty() {
          return newInputData.isEmpty();
        }
      });
    }
    catch (StorageException e) {
      LOG.info(e);
      requestRebuild();
    }
  }

  private class StubIndexInitialization extends IndexInfrastructure.DataInitialization<AsyncState> {
    private final AsyncState state = new AsyncState();
    private final IndicesRegistrationResult indicesRegistrationSink = new IndicesRegistrationResult();

    @Override
    protected void prepare() {
      Iterator<StubIndexExtension<?, ?>> extensionsIterator =
              IndexInfrastructure.hasIndices() ? StubIndexExtension.EP_NAME.getExtensionList().iterator() : Collections.emptyIterator();

      boolean forceClean = Boolean.TRUE == ourForcedClean.getAndSet(Boolean.FALSE);
      while (extensionsIterator.hasNext()) {
        StubIndexExtension extension = extensionsIterator.next();
        if (extension == null) break;
        extension.getKey(); // initialize stub index keys

        addNestedInitializationTask(() -> {
          registerIndexer(extension, forceClean, state, indicesRegistrationSink);
        });
      }
    }

    @Override
    protected void onThrowable(@Nonnull Throwable t) {
      LOG.error(t);
    }

    @Override
    protected AsyncState finish() {
      boolean someIndicesWereDropped = dropUnregisteredIndices(state);

      StringBuilder updated = new StringBuilder();
      String updatedIndices = indicesRegistrationSink.changedIndices();
      if (!updatedIndices.isEmpty()) updated.append(updatedIndices);
      if (someIndicesWereDropped) updated.append(" and some indices were dropped");
      indicesRegistrationSink.logChangedAndFullyBuiltIndices(LOG, "Following stub indices will be updated:", "Following stub indices will be built:");

      if (updated.length() > 0) {
        final Throwable e = new Throwable(updated.toString());
        // avoid direct forceRebuild as it produces dependency cycle (IDEA-105485)
        ApplicationManager.getApplication().invokeLater(() -> forceRebuild(e), ModalityState.NON_MODAL);
      }

      myInitialized = true;
      return state;
    }
  }

  private static UpdatableIndex<Integer, SerializedStubTree, FileContent> getStubUpdatingIndex() {
    return ((FileBasedIndexImpl)FileBasedIndex.getInstance()).getIndex(StubUpdatingIndex.INDEX_ID);
  }

  private static class CompositeKey<K> {
    private final K key;
    private final int fileId;

    private CompositeKey(K key, int id) {
      this.key = key;
      fileId = id;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      CompositeKey<?> key1 = (CompositeKey<?>)o;
      return fileId == key1.fileId && Objects.equals(key, key1.key);
    }

    @Override
    public int hashCode() {
      return Objects.hash(key, fileId);
    }
  }
}
