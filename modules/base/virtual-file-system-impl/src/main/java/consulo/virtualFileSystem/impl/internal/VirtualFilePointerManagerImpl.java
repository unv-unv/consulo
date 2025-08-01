// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.virtualFileSystem.impl.internal;

import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.application.progress.ProgressManager;
import consulo.component.util.SimpleModificationTracker;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.logging.Logger;
import consulo.platform.Platform;
import consulo.util.collection.*;
import consulo.util.io.FileUtil;
import consulo.util.io.URLUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.*;
import consulo.virtualFileSystem.archive.ArchiveFileType;
import consulo.virtualFileSystem.event.AsyncFileListener.ChangeApplier;
import consulo.virtualFileSystem.event.*;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.virtualFileSystem.fileType.FileTypeRegistry;
import consulo.virtualFileSystem.archive.BaseArchiveFileSystem;
import consulo.virtualFileSystem.impl.internal.entry.VirtualFileSystemEntry;
import consulo.virtualFileSystem.internal.PersistentFS;
import consulo.virtualFileSystem.internal.VirtualFilePointerContainerImpl;
import consulo.virtualFileSystem.pointer.*;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import gnu.trove.TObjectHashingStrategy;
import gnu.trove.TObjectIntHashMap;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Singleton
@ServiceImpl
public final class VirtualFilePointerManagerImpl extends SimpleModificationTracker implements Disposable, VirtualFilePointerManager {
    private static final Logger LOG = Logger.getInstance(VirtualFilePointerManagerImpl.class);
    private static final Comparator<String> URL_COMPARATOR =
        Platform.current().fs().isCaseSensitive() ? String::compareTo : String::compareToIgnoreCase;
    static final boolean IS_UNDER_UNIT_TEST = Application.get().isUnitTestMode();

    private static final VirtualFilePointerListener NULL_LISTENER = new VirtualFilePointerListener() {
        @Override
        public int hashCode() {
            return 4; // chosen by fair dice roll. guaranteed to be random. see https://xkcd.com/221/ for details.
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this;
        }
    };

    private final Application myApplication;
    private final Map<VirtualFileSystem, Map<VirtualFilePointerListener, FilePointerPartNode>> myRoots =
        Maps.newHashMap(ContainerUtil.identityStrategy()); // guarded by this
    // compare by identity because VirtualFilePointerContainer has too smart equals
    private final Set<VirtualFilePointerContainerImpl> myContainers = Sets.newHashSet(ContainerUtil.identityStrategy());
    // guarded by myContainers

    private int myPointerSetModCount;

    @Inject
    public VirtualFilePointerManagerImpl(Application application) {
        myApplication = application;
    }

    @Override
    public void dispose() {
        assertAllPointersDisposed();
    }

    private static class EventDescriptor {
        @Nonnull
        private final VirtualFilePointerListener myListener;
        @Nonnull
        private final VirtualFilePointer[] myPointers;

        private EventDescriptor(@Nonnull VirtualFilePointerListener listener, @Nonnull VirtualFilePointer[] pointers) {
            myListener = listener;
            myPointers = pointers;
            if (pointers.length == 0) {
                throw new IllegalArgumentException();
            }
        }

        private void fireBefore() {
            myListener.beforeValidityChanged(myPointers);
        }

        private void fireAfter() {
            myListener.validityChanged(myPointers);
        }
    }

    @Nonnull
    private static VirtualFilePointer[] toPointers(@Nonnull Collection<? extends FilePointerPartNode> nodes) {
        if (nodes.isEmpty()) {
            return VirtualFilePointer.EMPTY_ARRAY;
        }
        List<VirtualFilePointer> list = new ArrayList<>(nodes.size());
        for (FilePointerPartNode node : nodes) {
            node.addAllPointersTo(list);
        }
        return list.toArray(VirtualFilePointer.EMPTY_ARRAY);
    }

    @TestOnly
    @Nonnull
    synchronized List<VirtualFilePointer> getPointersUnder(@Nonnull VirtualFile parent, @Nonnull String childName) {
        assert !StringUtil.isEmptyOrSpaces(childName);
        MultiMap<VirtualFilePointerListener, FilePointerPartNode> nodes = MultiMap.create();
        addRelevantPointers(parent, toNameId(childName), nodes, true, parent.getFileSystem());
        List<VirtualFilePointer> pointers = new ArrayList<>();
        for (FilePointerPartNode node : nodes.values()) {
            node.addAllPointersTo(pointers);
        }
        return pointers;
    }

    private void addRelevantPointers(
        VirtualFile parent,
        int childNameId,
        @Nonnull MultiMap<VirtualFilePointerListener, FilePointerPartNode> out,
        boolean addSubdirectoryPointers,
        @Nonnull VirtualFileSystem fs
    ) {
        if (childNameId <= 0) {
            throw new IllegalArgumentException("invalid argument childNameId: " + childNameId);
        }
        Map<VirtualFilePointerListener, FilePointerPartNode> myPointers = myRoots.get(fs);
        if (myPointers != null) {
            for (Map.Entry<VirtualFilePointerListener, FilePointerPartNode> entry : myPointers.entrySet()) {
                FilePointerPartNode root = entry.getValue();
                VirtualFilePointerListener listener = entry.getKey();
                List<FilePointerPartNode> outNodes = (List<FilePointerPartNode>) out.getModifiable(listener);
                root.addRelevantPointersFrom(parent, childNameId, outNodes, addSubdirectoryPointers, fs);
            }
        }
        if (fs instanceof LocalFileSystem) {
            // search in archive file systems because they might be changed too when the LFS is changed
            for (Map.Entry<VirtualFileSystem, Map<VirtualFilePointerListener, FilePointerPartNode>> entry : myRoots.entrySet()) {
                VirtualFileSystem rootFS = entry.getKey();
                if (rootFS instanceof BaseArchiveFileSystem) {
                    addRelevantPointers(parent, childNameId, out, addSubdirectoryPointers, rootFS);
                }
            }
        }
    }

    @Override
    @Nonnull
    public VirtualFilePointer create(@Nonnull String url, @Nonnull Disposable parent, @Nullable VirtualFilePointerListener listener) {
        return create(null, url, parent, listener, false);
    }

    @Override
    @Nonnull
    public VirtualFilePointer create(@Nonnull VirtualFile file, @Nonnull Disposable parent, @Nullable VirtualFilePointerListener listener) {
        return create(file, null, parent, listener, false);
    }

    /**
     * @param file             null means the pointer will be created from the (not null) url
     * @param url              null means url has to be computed from the (not-null) file path
     * @param parentDisposable
     * @param listener
     * @param recursive
     * @return
     */
    @Nonnull
    private VirtualFilePointer create(
        @Nullable VirtualFile file,
        @Nullable String url,
        @Nonnull Disposable parentDisposable,
        @Nullable VirtualFilePointerListener listener,
        boolean recursive
    ) {
        VirtualFileSystem fileSystem;
        String protocol;
        String path;
        if (file == null) {
            //noinspection ConstantConditions (guaranteed by callers)
            int protocolEnd = url.indexOf(URLUtil.SCHEME_SEPARATOR);
            if (protocolEnd == -1) {
                protocol = null;
                fileSystem = null;
                path = url;
            }
            else {
                protocol = url.substring(0, protocolEnd);
                fileSystem = VirtualFileManager.getInstance().getFileSystem(protocol);
                path = url.substring(protocolEnd + URLUtil.SCHEME_SEPARATOR.length());
            }
            if (fileSystem == null) {
                // will always be null
                return new LightFilePointer(url);
            }
        }
        else {
            fileSystem = file.getFileSystem();
            protocol = fileSystem.getProtocol();
            path = null;
            url = null;
        }

        if (fileSystem instanceof TempFileSystem && listener == null) {
            // Since VFS events work correctly in temp FS as well, ideally,
            // this branch shouldn't exist and normal VFPointer should be used in all tests
            // but we have so many tests that create pointers, not dispose and leak them,
            // so for now we create normal pointers only when there are listeners.
            // maybe, later we'll fix all those tests
            VirtualFile found = file == null ? VirtualFileManager.getInstance().findFileByUrl(url) : file;
            return found == null ? new LightFilePointer(url) : new LightFilePointer(found);
        }

        if (!(fileSystem instanceof VirtualFilePointerCapableFileSystem)) {
            // we are unable to track alien file systems for now
            VirtualFile found = file == null ? VirtualFileManager.getInstance().findFileByUrl(url) : file;
            // if file is null, this pointer will never be alive
            if (url == null) {
                url = file.getUrl();
            }
            return getOrCreateIdentity(url, found, recursive, parentDisposable);
        }

        if (file == null) {
            String cleanPath = cleanupPath(path);
            // if newly created path is the same as the one extracted from url then the url did not change, we can reuse it
            //noinspection StringEquality
            if (cleanPath != path) {
                url = VirtualFileManager.constructUrl(
                    protocol,
                    cleanPath + (fileSystem instanceof BaseArchiveFileSystem ? BaseArchiveFileSystem.ARCHIVE_SEPARATOR : "")
                );
                path = cleanPath;
            }
            if (url.contains("..")) {
                // the url of the form "/x/../y" should resolve to "/y" (or something else in the case of symlinks)
                file = VirtualFileManager.getInstance().findFileByUrl(url);
                if (file != null) {
                    url = file.getUrl();
                    path = file.getPath();
                }
                else {
                    // when someone is crazy enough to create VFP for not-yet existing file from the path with "..", ignore all symlinks
                    path = FileUtil.toCanonicalPath(path);
                    url = VirtualFileManager.constructUrl(
                        protocol,
                        path + (fileSystem instanceof BaseArchiveFileSystem ? BaseArchiveFileSystem.ARCHIVE_SEPARATOR : "")
                    );
                }
            }
            if (file == null && StringUtil.isEmptyOrSpaces(path)) {
                // somebody tries to create pointer to root which is pointless but damages our fake root node.
                return getOrCreateIdentity(url, VirtualFileManager.getInstance().findFileByUrl(url), recursive, parentDisposable);
            }
        }
        // else url has come from VirtualFile.getPath() and is good enough
        return getOrCreate(file, path, url, recursive, parentDisposable, listener, (NewVirtualFileSystem) fileSystem);
    }

    private final Map<String, IdentityVirtualFilePointer> myUrlToIdentity = new HashMap<>(); // guarded by this

    @Nonnull
    private synchronized IdentityVirtualFilePointer getOrCreateIdentity(
        @Nonnull String url,
        @Nullable VirtualFile found,
        boolean recursive,
        @Nonnull Disposable parentDisposable
    ) {
        IdentityVirtualFilePointer pointer = myUrlToIdentity.get(url);
        if (pointer == null) {
            pointer = new IdentityVirtualFilePointer(found, url) {
                @Override
                public void dispose() {
                    //noinspection SynchronizeOnThis
                    synchronized (VirtualFilePointerManagerImpl.this) {
                        super.dispose();
                        myUrlToIdentity.remove(url);
                    }
                }

                @Override
                public String toString() {
                    return "identity: url='" + url + "'; file=" + found;
                }
            };
            myUrlToIdentity.put(url, pointer);

            DelegatingDisposable.registerDisposable(parentDisposable, pointer);
        }
        pointer.incrementUsageCount(1);
        pointer.recursive = recursive;
        return pointer;
    }

    // convert \ -> /
    // convert // -> /
    // convert /. ->
    // trim trailing /
    @Nonnull
    private static String cleanupPath(@Nonnull String path) {
        path = FileUtil.toSystemIndependentName(path);
        path = trimTrailingSeparators(path);
        for (int i = 0; i < path.length(); ) {
            int slash = path.indexOf('/', i);
            if (slash == -1 || slash == path.length() - 1) {
                break;
            }
            char next = path.charAt(slash + 1);

            if (next == '/' && !(i == 0 && Platform.current().os().isWindows()) || // additional condition for Windows UNC
                next == '.' && (slash == path.length() - 2 || path.charAt(slash + 2) == '/')) {
                return cleanupTail(path, slash);
            }
            i = slash + 1;
        }
        return path;
    }

    // removes // and //. when we know for sure they are there, starting from 'slashIndex'
    @Nonnull
    private static String cleanupTail(@Nonnull String path, int slashIndex) {
        StringBuilder s = new StringBuilder(path.length());
        s.append(path, 0, slashIndex);
        for (int i = slashIndex; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '/') {
                char nextc = i == path.length() - 1 ? 0 : path.charAt(i + 1);
                if (nextc == '.') {
                    if (i == path.length() - 2) {
                        // ends with "/.", ignore
                        break;
                    }
                    char nextNextc = path.charAt(i + 2);
                    if (nextNextc == '/') {
                        i++;
                        // "/./" in the middle, ignore "/."
                        continue;
                    }
                    // "/.xxx", append
                }
                else if (nextc == '/') {
                    // ignore duplicate /
                    continue;
                }
            }
            s.append(c);
        }
        return s.toString();
    }

    @Nonnull
    private static String trimTrailingSeparators(@Nonnull String path) {
        path = StringUtil.trimEnd(path, consulo.virtualFileSystem.archive.ArchiveFileSystem.ARCHIVE_SEPARATOR);
        path = StringUtil.trimTrailing(path, '/');
        return path;
    }

    @Nonnull
    private synchronized VirtualFilePointerImpl getOrCreate(
        VirtualFile file,
        String path,
        String url,
        boolean recursive,
        @Nonnull Disposable parentDisposable,
        @Nullable VirtualFilePointerListener listener,
        @Nonnull NewVirtualFileSystem fs
    ) {
        VirtualFilePointerListener nl = ObjectUtil.notNull(listener, NULL_LISTENER);
        Map<VirtualFilePointerListener, FilePointerPartNode> myPointers = myRoots.computeIfAbsent(fs, __ -> new HashMap<>());
        FilePointerPartNode root = myPointers.computeIfAbsent(nl, __ -> FilePointerPartNode.createFakeRoot());

        FilePointerPartNode node = file == null
            ? FilePointerPartNode.findOrCreateNodeByPath(root, path, fs) : root.findOrCreateNodeByFile(file, fs);

        VirtualFilePointerImpl pointer = node.getAnyPointer();
        if (pointer == null) {
            pointer = new VirtualFilePointerImpl();
            Pair<VirtualFile, String> fileAndUrl = Pair.create(file, file == null ? url : file.getUrl());
            node.associate(pointer, fileAndUrl);
            for (FilePointerPartNode n = node; n != null; n = n.parent) {
                n.pointersUnder++;
            }
        }
        pointer.incrementUsageCount(1);
        pointer.recursive = recursive;

        root.checkConsistency();
        DelegatingDisposable.registerDisposable(parentDisposable, pointer);
        myPointerSetModCount++;
        return pointer;
    }

    @Override
    @Nonnull
    public VirtualFilePointer duplicate(
        @Nonnull VirtualFilePointer pointer,
        @Nonnull Disposable parent,
        @Nullable VirtualFilePointerListener listener
    ) {
        VirtualFile file = pointer.getFile();
        return file == null ? create(pointer.getUrl(), parent, listener) : create(file, parent, listener);
    }

    private synchronized void assertAllPointersDisposed() {
        List<VirtualFilePointer> leaked = new ArrayList<>(dumpAllPointers());
        Collections.sort(leaked, Comparator.comparing(VirtualFilePointer::getUrl));
        for (VirtualFilePointer pointer : leaked) {
            try {
                ((VirtualFilePointerImpl) pointer).getTraceableDisposable().throwDisposalError("Not disposed pointer: " + pointer);
            }
            finally {
                ((VirtualFilePointerImpl) pointer).dispose();
            }
        }

        synchronized (myContainers) {
            if (!myContainers.isEmpty()) {
                VirtualFilePointerContainerImpl container = myContainers.iterator().next();
                container.getTraceableDisposable().throwDisposalError("Not disposed container");
            }
        }
    }

    @Override
    @Nonnull
    public VirtualFilePointerContainer createContainer(@Nonnull Disposable parent) {
        return createContainer(parent, null);
    }

    @Override
    @Nonnull
    public synchronized VirtualFilePointerContainer createContainer(
        @Nonnull Disposable parent,
        @Nullable VirtualFilePointerListener listener
    ) {
        return registerContainer(parent, new VirtualFilePointerContainerImpl(this, parent, listener));
    }

    @Nonnull
    private VirtualFilePointerContainer registerContainer(@Nonnull Disposable parent, @Nonnull VirtualFilePointerContainerImpl container) {
        synchronized (myContainers) {
            myContainers.add(container);
        }
        Disposer.register(parent, new Disposable() {
            @Override
            public void dispose() {
                Disposer.dispose(container);
                boolean removed;
                synchronized (myContainers) {
                    removed = myContainers.remove(container);
                }
                if (!IS_UNDER_UNIT_TEST) {
                    assert removed;
                }
            }

            @Override
            public String toString() {
                return "Disposing container " + container;
            }
        });
        return container;
    }

    @Nonnull
    public ChangeApplier prepareChange(@Nonnull List<? extends VFileEvent> events) {
        long start = System.currentTimeMillis();
        MultiMap<VirtualFilePointerListener, FilePointerPartNode> toFireEvents = MultiMap.create();
        MultiMap<VirtualFilePointerListener, FilePointerPartNode> toUpdateUrl = MultiMap.create();
        List<EventDescriptor> eventList;
        List<VirtualFilePointer> allPointersToFire = new ArrayList<>();

        long startModCount;

        //noinspection SynchronizeOnThis
        synchronized (this) {
            startModCount = myPointerSetModCount;
            for (VFileEvent event : events) {
                ProgressManager.checkCanceled();
                VirtualFileSystem fs = event.getFileSystem();
                if (!(fs instanceof VirtualFilePointerCapableFileSystem)) {
                    continue;
                }
                if (event instanceof VFileDeleteEvent deleteEvent) {
                    VirtualFile file = deleteEvent.getFile();
                    addRelevantPointers(file.getParent(), ((VirtualFileSystemEntry) file).getNameId(), toFireEvents, true, fs);
                }
                else if (event instanceof VFileCreateEvent createEvent) {
                    boolean fireSubdirectoryPointers;
                    if (createEvent.isDirectory()) {
                        // when a new empty directory "/a/b" is created, there's no need to fire any deeper pointers like "/a/b/c/d.txt" - they're not created yet
                        // OTOH when refresh found a new directory "/a/b" which is non-empty, we must fire deeper pointers because they may exist already
                        fireSubdirectoryPointers = !createEvent.isEmptyDirectory();
                    }
                    else {
                        String createdFileName = createEvent.getChildName();
                        // if the .jar file created, there may be many files hiding inside
                        FileType fileType = FileTypeRegistry.getInstance().getFileTypeByExtension(FileUtil.getExtension(createdFileName));
                        fireSubdirectoryPointers = fileType instanceof ArchiveFileType;
                    }
                    addRelevantPointers(createEvent.getParent(), createEvent.getChildNameId(), toFireEvents, fireSubdirectoryPointers, fs);
                    // when new file created its UrlPartNode should be converted to id-based FilePointerPartNode to save memory
                    toUpdateUrl.putAllValues(toFireEvents);

                }
                else if (event instanceof VFileCopyEvent copyEvent) {
                    addRelevantPointers(copyEvent.getNewParent(), toNameId(copyEvent.getNewChildName()), toFireEvents, true, fs);
                }
                else if (event instanceof VFileMoveEvent moveEvent) {
                    VirtualFile eventFile = moveEvent.getFile();
                    int newNameId = ((VirtualFileSystemEntry) eventFile).getNameId();
                    addRelevantPointers(moveEvent.getNewParent(), newNameId, toFireEvents, true, fs);

                    MultiMap<VirtualFilePointerListener, FilePointerPartNode> nodes = MultiMap.create();
                    addRelevantPointers(eventFile.getParent(), newNameId, nodes, true, fs);
                    toFireEvents.putAllValues(nodes); // files deleted from eventFile and created in moveEvent.getNewParent()
                    collectNodes(nodes, toUpdateUrl);
                }
                else if (event instanceof VFilePropertyChangeEvent change) {
                    if (VirtualFile.PROP_NAME.equals(change.getPropertyName()) && !Comparing.equal(change.getOldValue(), change.getNewValue())) {
                        VirtualFile eventFile = change.getFile();
                        VirtualFile parent = eventFile.getParent(); // e.g. for LightVirtualFiles
                        int newNameId = toNameId(change.getNewValue().toString());
                        addRelevantPointers(parent, newNameId, toFireEvents, true, fs);

                        MultiMap<VirtualFilePointerListener, FilePointerPartNode> nodes = MultiMap.create();
                        addRelevantPointers(parent, ((VirtualFileSystemEntry) eventFile).getNameId(), nodes, true, fs);
                        collectNodes(nodes, toUpdateUrl);
                    }
                }
            }

            eventList = new ArrayList<>();
            for (Map.Entry<VirtualFilePointerListener, Collection<FilePointerPartNode>> entry : toFireEvents.entrySet()) {
                VirtualFilePointerListener listener = entry.getKey();
                if (listener == NULL_LISTENER) {
                    continue;
                }
                Collection<FilePointerPartNode> values = entry.getValue();
                VirtualFilePointer[] toFirePointers = toPointers(values);
                if (toFirePointers.length != 0) {
                    eventList.add(new EventDescriptor(listener, toFirePointers));
                    ContainerUtil.addAll(allPointersToFire, toFirePointers);
                }
            }
        }
        long prepareElapsedMs = System.currentTimeMillis() - start;
        VirtualFilePointer[] allPointers = allPointersToFire.isEmpty()
            ? VirtualFilePointer.EMPTY_ARRAY
            : allPointersToFire.toArray(VirtualFilePointer.EMPTY_ARRAY);

        return new ChangeApplier() {
            @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
            private ChangeApplier delegate;

            @Override
            public void beforeVfsChange() {
                //noinspection SynchronizeOnThis
                synchronized (VirtualFilePointerManagerImpl.this) {
                    if (startModCount != myPointerSetModCount) {
                        delegate = prepareChange(events);
                    }
                    else {
                        incModificationCount();
                    }
                }

                if (delegate != null) {
                    delegate.beforeVfsChange();
                    return;
                }

                for (EventDescriptor descriptor : eventList) {
                    descriptor.fireBefore();
                }

                if (allPointers.length != 0) {
                    myApplication.getMessageBus().syncPublisher(VirtualFilePointerListener.class).beforeValidityChanged(allPointers);
                }

                assertConsistency();
            }

            @Override
            public void afterVfsChange() {
                if (delegate != null) {
                    delegate.afterVfsChange();
                    return;
                }

                after(toFireEvents, toUpdateUrl, eventList, allPointers, prepareElapsedMs, events.size());
            }
        };
    }

    private static int toNameId(@Nonnull String name) {
        return FileNameCache.storeName(name);
    }

    private static void collectNodes(@Nonnull MultiMap<VirtualFilePointerListener, FilePointerPartNode> nodes, @Nonnull MultiMap<VirtualFilePointerListener, FilePointerPartNode> toUpdateUrl) {
        for (Map.Entry<VirtualFilePointerListener, Collection<FilePointerPartNode>> entry : nodes.entrySet()) {
            VirtualFilePointerListener listener = entry.getKey();
            Collection<FilePointerPartNode> values = entry.getValue();
            for (FilePointerPartNode node : values) {
                VirtualFilePointerImpl pointer = node.getAnyPointer();
                if (pointer != null) {
                    VirtualFile file = pointer.getFile();
                    if (file != null) {
                        toUpdateUrl.putValue(listener, node);
                    }
                }
            }
        }
    }

    synchronized void assertConsistency() {
        if (IS_UNDER_UNIT_TEST) {
            for (Map<VirtualFilePointerListener, FilePointerPartNode> myPointers : myRoots.values()) {
                for (FilePointerPartNode root : myPointers.values()) {
                    root.checkConsistency();
                }
            }
        }
    }

    private void after(
        @Nonnull MultiMap<VirtualFilePointerListener, FilePointerPartNode> toFireEvents,
        @Nonnull MultiMap<VirtualFilePointerListener, FilePointerPartNode> toUpdateUrls,
        @Nonnull List<? extends EventDescriptor> eventList,
        @Nonnull VirtualFilePointer[] allPointers,
        long prepareElapsedMs,
        int eventsSize
    ) {
        long start = System.currentTimeMillis();
        myApplication.assertIsWriteThread(); // guarantees no attempts to get read action lock under "this" lock
        incModificationCount();

        VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();

        //noinspection SynchronizeOnThis
        synchronized (this) {
            for (FilePointerPartNode node : toUpdateUrls.values()) {
                Pair<VirtualFile, String> pairBefore = node.myFileAndUrl;
                if (pairBefore == null) {
                    continue; // disposed in the meantime
                }
                String urlBefore = pairBefore.second;
                Pair<VirtualFile, String> after = node.update();
                assert after != null : "can't invalidate inside modification";
                String urlAfter = after.second;
                VirtualFile fileAfter = after.first;
                if (URL_COMPARATOR.compare(urlBefore, urlAfter) != 0 || !node.urlEndsWithName(urlAfter, fileAfter)) {
                    VirtualFileSystem fs = virtualFileManager.getFileSystem(VirtualFileManager.extractProtocol(urlAfter));
                    if (fs instanceof NewVirtualFileSystem nvfs) {
                        List<VirtualFilePointerImpl> myPointers = new SmartList<>();
                        node.addAllPointersTo(myPointers);

                        // url has changed, reinsert
                        int useCount = node.useCount;
                        FilePointerPartNode root = node.remove();

                        String path = trimTrailingSeparators(VirtualFileUtil.urlToPath(urlAfter));
                        FilePointerPartNode newNode = fileAfter == null
                            ? FilePointerPartNode.findOrCreateNodeByPath(root, path, nvfs)
                            : root.findOrCreateNodeByFile(fileAfter, nvfs);
                        newNode.addAllPointersTo(myPointers);
                        int pointersDelta = myPointers.size() - newNode.pointersUnder;
                        Object newMyPointers = myPointers.size() == 1 ? myPointers.get(0) : myPointers.toArray(new VirtualFilePointerImpl[0]);
                        newNode.associate(newMyPointers, after);
                        newNode.incrementUsageCount(useCount);
                        for (FilePointerPartNode n = newNode; n != null; n = n.parent) {
                            n.pointersUnder += pointersDelta;
                        }
                    }
                }
            }
        }
        for (FilePointerPartNode node : toFireEvents.values()) {
            node.update();
        }

        for (EventDescriptor event : eventList) {
            event.fireAfter();
        }

        if (allPointers.length != 0) {
            myApplication.getMessageBus().syncPublisher(VirtualFilePointerListener.class).validityChanged(allPointers);
        }

        assertConsistency();
        long afterElapsedMs = System.currentTimeMillis() - start;
        if (afterElapsedMs > 1000 || prepareElapsedMs > 1000) {
            int totalPointers;
            synchronized (this) {
                totalPointers = myRoots.values().stream()
                    .flatMapToInt(myPointers -> myPointers.values().stream().mapToInt(root -> root.pointersUnder))
                    .sum();
            }
            LOG.warn(
                "VirtualFilePointerManagerImpl.prepareChange(" + eventsSize +
                    " events): " + prepareElapsedMs + "ms." +
                    " after(toFireEvents: " + toFireEvents.size() +
                    ", toUpdateUrl: " + toUpdateUrls +
                    ", eventList: " + eventList +
                    "): " + afterElapsedMs + "ms." +
                    " total pointers: " + totalPointers
            );
        }
    }

    synchronized void removeNodeFrom(@Nonnull VirtualFilePointerImpl pointer) {
        FilePointerPartNode root = pointer.myNode.remove();
        boolean rootNodeEmpty = root.children.length == 0;
        if (rootNodeEmpty) {
            for (Map<VirtualFilePointerListener, FilePointerPartNode> myPointers : myRoots.values()) {
                myPointers.values().remove(root);
            }
        }
        pointer.myNode = null;
        assertConsistency();
        myPointerSetModCount++;
    }

    @Override
    public long getModificationCount() {
        // depends on PersistentFS.getStructureModificationCount() - because consulo.ide.impl.idea.openapi.vfs.impl.FilePointerPartNode.update
        // depends on its own modification counter - because we need to change both before and after VFS changes
        return super.getModificationCount() + PersistentFS.getInstance().getStructureModificationCount();
    }

    private static class DelegatingDisposable implements Disposable {
        private static final ConcurrentMap<Disposable, DelegatingDisposable> ourInstances = Maps.newConcurrentHashMap(ContainerUtil.identityStrategy());

        private final TObjectIntHashMap<VirtualFilePointerImpl> myCounts =
            new TObjectIntHashMap<>(TObjectHashingStrategy.IDENTITY); // guarded by this
        private final Disposable myParent;

        private DelegatingDisposable(@Nonnull Disposable parent, @Nonnull VirtualFilePointerImpl firstPointer) {
            myParent = parent;
            //noinspection SynchronizeOnThis
            synchronized (this) {
                myCounts.put(firstPointer, 1);
            }
        }

        private static void registerDisposable(@Nonnull Disposable parentDisposable, @Nonnull VirtualFilePointerImpl pointer) {
            DelegatingDisposable result = ourInstances.get(parentDisposable);
            if (result == null) {
                DelegatingDisposable newDisposable = new DelegatingDisposable(parentDisposable, pointer);
                result = Maps.cacheOrGet(ourInstances, parentDisposable, newDisposable);
                if (result == newDisposable) {
                    Disposer.register(parentDisposable, result);
                    return;
                }
            }
            result.increment(pointer);
        }

        synchronized void increment(@Nonnull VirtualFilePointerImpl pointer) {
            if (!myCounts.increment(pointer)) {
                myCounts.put(pointer, 1);
            }
        }

        @Override
        public void dispose() {
            ourInstances.remove(myParent);
            //noinspection SynchronizeOnThis
            synchronized (this) {
                myCounts.forEachEntry((pointer, disposeCount) -> {
                    boolean isDisposed = !(pointer instanceof IdentityVirtualFilePointer) && pointer.myNode == null;
                    if (isDisposed) {
                        pointer.getTraceableDisposable()
                            .throwDisposalError("Already disposed:\n" + pointer.getTraceableDisposable().getStackTrace());
                    }
                    int after = pointer.incrementUsageCount(-(disposeCount - 1));
                    LOG.assertTrue(after > 0, after);
                    pointer.dispose();
                    return true;
                });
                myCounts.clear();
            }
        }
    }

    @Nonnull
    @Override
    public VirtualFilePointer createDirectoryPointer(@Nonnull String url, boolean recursively, @Nonnull Disposable parent, @Nonnull VirtualFilePointerListener listener) {
        return create(null, url, parent, listener, true);
    }

    @TestOnly
    synchronized int numberOfPointers() {
        int number = 0;
        for (Map<VirtualFilePointerListener, FilePointerPartNode> myPointers : myRoots.values()) {
            for (FilePointerPartNode root : myPointers.values()) {
                number += root.numberOfPointersUnder();
            }
        }
        return number;
    }

    @TestOnly
    synchronized int numberOfListeners() {
        return myRoots.values().stream().flatMap(myPointers -> myPointers.keySet().stream()).collect(Collectors.toSet()).size();
    }

    @TestOnly
    synchronized int numberOfCachedUrlToIdentity() {
        return myUrlToIdentity.size();
    }

    // tests need to operate on the deterministic number of pointers, so we clear all of them out of the way during the test execution
    @TestOnly
    void shelveAllPointersIn(@Nonnull Runnable runnable) {
        Map<VirtualFileSystem, Map<VirtualFilePointerListener, FilePointerPartNode>> shelvedPointers;
        //noinspection SynchronizeOnThis
        synchronized (this) {
            shelvedPointers = new LinkedHashMap<>(myRoots);
            myRoots.clear();
        }
        try {
            runnable.run();
        }
        finally {
            //noinspection SynchronizeOnThis
            synchronized (this) {
                myRoots.clear();
                myRoots.putAll(shelvedPointers);
            }
        }
    }

    @Nonnull
    synchronized Collection<VirtualFilePointer> dumpAllPointers() {
        Collection<VirtualFilePointer> result = new HashSet<>();
        for (Map<VirtualFilePointerListener, FilePointerPartNode> myPointers : myRoots.values()) {
            for (FilePointerPartNode node : myPointers.values()) {
                dumpPointersRecursivelyTo(node, result);
            }
        }
        return result;
    }

    private static void dumpPointersRecursivelyTo(@Nonnull FilePointerPartNode node, @Nonnull Collection<? super VirtualFilePointer> result) {
        node.addAllPointersTo(result);
        for (FilePointerPartNode child : node.children) {
            dumpPointersRecursivelyTo(child, result);
        }
    }
}