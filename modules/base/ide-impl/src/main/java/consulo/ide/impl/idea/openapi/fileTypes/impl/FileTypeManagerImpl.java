// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.ide.impl.idea.openapi.fileTypes.impl;

import com.google.common.annotations.VisibleForTesting;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.ApplicationPropertiesComponent;
import consulo.application.ReadAction;
import consulo.application.impl.internal.IdeaModalityState;
import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.application.util.concurrent.PooledThreadExecutor;
import consulo.component.messagebus.MessageBus;
import consulo.component.messagebus.MessageBusConnection;
import consulo.component.persist.*;
import consulo.container.PluginException;
import consulo.container.plugin.PluginId;
import consulo.disposer.Disposable;
import consulo.document.util.FileContentUtilCore;
import consulo.ide.impl.idea.ide.highlighter.custom.SyntaxTable;
import consulo.ide.impl.idea.ide.plugins.PluginManagerCore;
import consulo.ide.impl.idea.ide.util.PropertiesComponent;
import consulo.ide.impl.idea.openapi.fileTypes.*;
import consulo.ide.impl.idea.openapi.fileTypes.ex.ExternalizableFileType;
import consulo.ide.impl.idea.openapi.fileTypes.ex.FileTypeChooser;
import consulo.ide.impl.idea.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile;
import consulo.ide.impl.idea.openapi.fileTypes.ex.FileTypeManagerEx;
import consulo.ide.impl.idea.openapi.options.BaseSchemeProcessor;
import consulo.ide.impl.idea.openapi.options.SchemesManager;
import consulo.ide.impl.idea.openapi.options.SchemesManagerFactory;
import consulo.ide.impl.idea.openapi.util.Comparing;
import consulo.ide.impl.idea.openapi.util.JDOMExternalizer;
import consulo.ide.impl.idea.openapi.util.JDOMUtil;
import consulo.ide.impl.idea.openapi.util.io.FileUtil;
import consulo.ide.impl.idea.openapi.util.io.FileUtilRt;
import consulo.ide.impl.idea.openapi.util.text.StringUtil;
import consulo.ide.impl.idea.openapi.util.text.StringUtilRt;
import consulo.ide.impl.idea.openapi.vfs.newvfs.FileAttribute;
import consulo.ide.impl.idea.openapi.vfs.newvfs.impl.StubVirtualFile;
import consulo.ide.impl.idea.util.*;
import consulo.ide.impl.idea.util.containers.ConcurrentPackedBitsArray;
import consulo.ide.impl.idea.util.containers.ContainerUtil;
import consulo.ide.impl.idea.util.containers.HashSetQueue;
import consulo.ide.impl.idea.util.io.URLUtil;
import consulo.language.Language;
import consulo.language.file.LanguageFileType;
import consulo.language.file.event.FileTypeEvent;
import consulo.language.file.event.FileTypeListener;
import consulo.language.file.light.LightVirtualFile;
import consulo.language.impl.internal.psi.LoadTextUtil;
import consulo.language.plain.PlainTextFileType;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.awt.internal.GuiUtils;
import consulo.ui.ex.concurrent.EdtExecutorService;
import consulo.util.collection.MultiValuesMap;
import consulo.util.concurrent.BoundedTaskExecutor;
import consulo.util.dataholder.Key;
import consulo.util.io.ByteArraySequence;
import consulo.util.io.ByteSequence;
import consulo.util.lang.DeprecatedMethodException;
import consulo.virtualFileSystem.*;
import consulo.virtualFileSystem.event.BulkFileListener;
import consulo.virtualFileSystem.event.VFileCreateEvent;
import consulo.virtualFileSystem.event.VFileEvent;
import consulo.virtualFileSystem.fileType.*;
import consulo.virtualFileSystem.impl.internal.RawFileLoaderImpl;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.StreamSupport;

@Singleton
@ServiceImpl
@State(name = "FileTypeManager", storages = @Storage("filetypes.xml"), additionalExportFile = FileTypeManagerImpl.FILE_SPEC)
public class FileTypeManagerImpl extends FileTypeManagerEx implements PersistentStateComponent<Element>, Disposable {
  private static final Logger LOG = Logger.getInstance(FileTypeManagerImpl.class);

  // You must update all existing default configurations accordingly
  private static final int VERSION = 17;
  private static final ThreadLocal<consulo.util.lang.Pair<VirtualFile, FileType>> FILE_TYPE_FIXED_TEMPORARILY = new ThreadLocal<>();

  // cached auto-detected file type. If the file was auto-detected as plain text or binary
  // then the value is null and AUTO_DETECTED_* flags stored in packedFlags are used instead.
  static final Key<FileType> DETECTED_FROM_CONTENT_FILE_TYPE_KEY = Key.create("DETECTED_FROM_CONTENT_FILE_TYPE_KEY");

  // must be sorted
  @SuppressWarnings("SpellCheckingInspection")
  static final String DEFAULT_IGNORED = "*.hprof;*.pyc;*.pyo;*.rbc;*.yarb;*~;.DS_Store;.git;.hg;.svn;CVS;__pycache__;_svn;vssver.scc;vssver2.scc;";

  private static boolean RE_DETECT_ASYNC = !ApplicationManager.getApplication().isUnitTestMode();
  private final Set<FileType> myDefaultTypes = new HashSet<>();
  private FileTypeIdentifiableByVirtualFile[] mySpecialFileTypes = FileTypeIdentifiableByVirtualFile.EMPTY_ARRAY;

  private FileTypeAssocTable<FileType> myPatternsTable = new FileTypeAssocTable<>();
  private final IgnoredPatternSet myIgnoredPatterns = new IgnoredPatternSet();
  private final IgnoredFileCache myIgnoredFileCache = new IgnoredFileCache(myIgnoredPatterns);

  private final FileTypeAssocTable<FileType> myInitialAssociations = new FileTypeAssocTable<>();
  private final Map<FileNameMatcher, String> myUnresolvedMappings = new HashMap<>();
  private final RemovedMappingTracker myRemovedMappingTracker = new RemovedMappingTracker();
  private final Map<String, FileTypeBean> myPendingFileTypes = new HashMap<>();
  private final FileTypeAssocTable<FileTypeBean> myPendingAssociations = new FileTypeAssocTable<>();

  @NonNls
  private static final String ELEMENT_FILETYPE = "filetype";
  @NonNls
  private static final String ELEMENT_IGNORE_FILES = "ignoreFiles";
  @NonNls
  private static final String ATTRIBUTE_LIST = "list";

  @NonNls
  private static final String ATTRIBUTE_VERSION = "version";
  @NonNls
  private static final String ATTRIBUTE_NAME = "name";
  @NonNls
  private static final String ATTRIBUTE_DESCRIPTION = "description";

  private static class StandardFileType {
    @Nonnull
    private final FileType fileType;
    @Nonnull
    private final List<FileNameMatcher> matchers;

    private StandardFileType(@Nonnull FileType fileType, @Nonnull List<FileNameMatcher> matchers) {
      this.fileType = fileType;
      this.matchers = matchers;
    }
  }

  private final MessageBus myMessageBus;
  private final Map<String, StandardFileType> myStandardFileTypes = new LinkedHashMap<>();
  @NonNls
  private static final String[] FILE_TYPES_WITH_PREDEFINED_EXTENSIONS = {"JSP", "JSPX", "DTD", "HTML", "Properties", "XHTML"};
  private final SchemesManager<FileType, AbstractFileType> mySchemeManager;

  static final String FILE_SPEC = StoragePathMacros.ROOT_CONFIG + "/filetypes";

  // these flags are stored in 'packedFlags' as chunks of four bits
  private static final byte AUTO_DETECTED_AS_TEXT_MASK = 1;        // set if the file was auto-detected as text
  private static final byte AUTO_DETECTED_AS_BINARY_MASK = 1 << 1;   // set if the file was auto-detected as binary

  // set if auto-detection was performed for this file.
  // if some detector returned some custom file type, it's stored in DETECTED_FROM_CONTENT_FILE_TYPE_KEY file key.
  // otherwise if auto-detected as text or binary, the result is stored in AUTO_DETECTED_AS_TEXT_MASK|AUTO_DETECTED_AS_BINARY_MASK bits
  private static final byte AUTO_DETECT_WAS_RUN_MASK = 1 << 2;
  private static final byte ATTRIBUTES_WERE_LOADED_MASK = 1 << 3;    // set if AUTO_* bits above were loaded from the file persistent attributes and saved to packedFlags
  private final ConcurrentPackedBitsArray packedFlags = new ConcurrentPackedBitsArray(4);

  private final AtomicInteger counterAutoDetect = new AtomicInteger();
  private final AtomicLong elapsedAutoDetect = new AtomicLong();

  private final Object PENDING_INIT_LOCK = new Object();

  private MultiValuesMap<FileType, FileTypeDetector> myFileTypeDetectorMap;
  private final List<FileTypeDetector> myUntypedFileTypeDetectors = new ArrayList<>();
  private final Object FILE_TYPE_DETECTOR_MAP_LOCK = new Object();

  @Inject
  public FileTypeManagerImpl(Application application, SchemesManagerFactory schemesManagerFactory, ApplicationPropertiesComponent propertiesComponent) {
    int fileTypeChangedCounter = propertiesComponent.getInt("fileTypeChangedCounter", 0);
    fileTypeChangedCount = new AtomicInteger(fileTypeChangedCounter);
    autoDetectedAttribute = new FileAttribute("AUTO_DETECTION_CACHE_ATTRIBUTE", fileTypeChangedCounter + getVersionFromDetectors(), true);

    myMessageBus = application.getMessageBus();
    mySchemeManager = schemesManagerFactory.createSchemesManager(FILE_SPEC, new BaseSchemeProcessor<FileType, AbstractFileType>() {
      @Nonnull
      @Override
      public AbstractFileType readScheme(@Nonnull Element element, boolean duringLoad) {
        if (!duringLoad) {
          fireBeforeFileTypesChanged();
        }
        AbstractFileType type = (AbstractFileType)loadFileType(element, false);
        if (!duringLoad) {
          fireFileTypesChanged(type, null);
        }
        return type;
      }

      @Nonnull
      @Override
      public State getState(@Nonnull AbstractFileType fileType) {
        if (!shouldSave(fileType)) {
          return State.NON_PERSISTENT;
        }
        if (!myDefaultTypes.contains(fileType)) {
          return State.POSSIBLY_CHANGED;
        }
        return fileType.isModified() ? State.POSSIBLY_CHANGED : State.NON_PERSISTENT;
      }

      @Nonnull
      @Override
      public Element writeScheme(@Nonnull AbstractFileType fileType) {
        Element root = new Element(ELEMENT_FILETYPE);

        root.setAttribute("binary", String.valueOf(fileType.isBinary()));
        if (!StringUtil.isEmpty(fileType.getDefaultExtension())) {
          root.setAttribute("default_extension", fileType.getDefaultExtension());
        }
        root.setAttribute(ATTRIBUTE_DESCRIPTION, fileType.getDescription().get());
        root.setAttribute(ATTRIBUTE_NAME, fileType.getId());

        fileType.writeExternal(root);

        Element map = new Element(AbstractFileType.ELEMENT_EXTENSION_MAP);
        writeExtensionsMap(map, fileType, false);
        if (!map.getChildren().isEmpty()) {
          root.addContent(map);
        }
        return root;
      }

      @Override
      public void onSchemeDeleted(@Nonnull AbstractFileType scheme) {
        GuiUtils.invokeLaterIfNeeded(() -> {
          Application app = ApplicationManager.getApplication();
          app.runWriteAction(() -> fireBeforeFileTypesChanged());
          myPatternsTable.removeAllAssociations(scheme);
          app.runWriteAction(() -> fireFileTypesChanged(null, scheme));
        }, IdeaModalityState.NON_MODAL);
      }

      @Nonnull
      @Override
      public String getName(@Nonnull FileType immutableElement) {
        return immutableElement.getId();
      }
    }, RoamingType.DEFAULT);

    initStandardFileTypes();

    myMessageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@Nonnull List<? extends VFileEvent> events) {
        Collection<VirtualFile> files = ContainerUtil.map2Set(events, (Function<VFileEvent, VirtualFile>)event -> {
          VirtualFile file = event instanceof VFileCreateEvent ? /* avoid expensive find child here */ null : event.getFile();
          VirtualFile filtered = file != null && wasAutoDetectedBefore(file) && isDetectable(file) ? file : null;
          if (toLog()) {
            log("F: after() VFS event " +
                event +
                "; filtered file: " +
                filtered +
                " (file: " +
                file +
                "; wasAutoDetectedBefore(file): " +
                (file == null ? null : wasAutoDetectedBefore(file)) +
                "; isDetectable(file): " +
                (file == null ? null : isDetectable(file)) +
                "; file.getLength(): " +
                (file == null ? null : file.getLength()) +
                "; file.isValid(): " +
                (file == null ? null : file.isValid()) +
                "; file.is(VFileProperty.SPECIAL): " +
                (file == null ? null : file.is(VFileProperty.SPECIAL)) +
                "; packedFlags.get(id): " +
                (file instanceof VirtualFileWithId ? readableFlags(packedFlags.get(((VirtualFileWithId)file).getId())) : null) +
                "; file.getFileSystem():" +
                (file == null ? null : file.getFileSystem()) +
                ")");
          }
          return filtered;
        });
        files.remove(null);
        if (toLog()) {
          log("F: after() VFS events: " + events + "; files: " + files);
        }
        if (!files.isEmpty() && RE_DETECT_ASYNC) {
          if (toLog()) {
            log("F: after() queued to redetect: " + files);
          }

          synchronized (filesToRedetect) {
            if (filesToRedetect.addAll(files)) {
              awakeReDetectExecutor();
            }
          }
        }
      }
    });

    myIgnoredPatterns.setIgnoreMasks(DEFAULT_IGNORED);

    //FileTypeDetector.EP_NAME.addExtensionPointListener(new ExtensionPointListener<FileTypeDetector>() {
    //  @Override
    //  public void extensionAdded(@Nonnull FileTypeDetector extension, @Nonnull PluginDescriptor pluginDescriptor) {
    //    synchronized (FILE_TYPE_DETECTOR_MAP_LOCK) {
    //      myFileTypeDetectorMap = null;
    //    }
    //  }
    //
    //  @Override
    //  public void extensionRemoved(@Nonnull FileTypeDetector extension, @Nonnull PluginDescriptor pluginDescriptor) {
    //    synchronized (FILE_TYPE_DETECTOR_MAP_LOCK) {
    //      myFileTypeDetectorMap = null;
    //    }
    //  }
    //}, this);
    //
    //EP_NAME.addExtensionPointListener(new ExtensionPointListener<FileTypeBean>() {
    //  @Override
    //  public void extensionAdded(@Nonnull FileTypeBean extension, @Nonnull PluginDescriptor pluginDescriptor) {
    //    fireBeforeFileTypesChanged();
    //    initializeMatchers(extension);
    //    FileType fileType = instantiateFileTypeBean(extension);
    //    fireFileTypesChanged(fileType, null);
    //  }
    //
    //  @Override
    //  public void extensionRemoved(@Nonnull FileTypeBean extension, @Nonnull PluginDescriptor pluginDescriptor) {
    //    final FileType fileType = findFileTypeByName(extension.name);
    //    unregisterFileType(fileType);
    //    if (fileType instanceof LanguageFileType) {
    //      final LanguageFileType languageFileType = (LanguageFileType)fileType;
    //      if (!languageFileType.isSecondary()) {
    //        Language.unregisterLanguage(languageFileType.getLanguage());
    //      }
    //    }
    //  }
    //}, this);
  }

  @VisibleForTesting
  void initStandardFileTypes() {
    FileTypeConsumer consumer = new FileTypeConsumer() {
      @Override
      public void consume(@Nonnull FileType fileType) {
        register(fileType, parse(fileType.getDefaultExtension()));
      }

      @Override
      public void consume(@Nonnull final FileType fileType, String extensions) {
        register(fileType, parse(extensions));
      }

      @Override
      public void consume(@Nonnull final FileType fileType, @Nonnull final FileNameMatcher... matchers) {
        register(fileType, new ArrayList<>(Arrays.asList(matchers)));
      }

      @Override
      public FileType getStandardFileTypeByName(@Nonnull final String name) {
        final StandardFileType type = myStandardFileTypes.get(name);
        return type != null ? type.fileType : null;
      }

      private void register(@Nonnull FileType fileType, @Nonnull List<FileNameMatcher> fileNameMatchers) {
        instantiatePendingFileTypeByName(fileType.getName());
        for (FileNameMatcher matcher : fileNameMatchers) {
          FileTypeBean pendingTypeByMatcher = myPendingAssociations.findAssociatedFileType(matcher);
          if (pendingTypeByMatcher != null) {
            PluginId id = pendingTypeByMatcher.getPluginId();
            if (id == null || id.getIdString().equals(PluginManagerCore.CORE_PLUGIN_ID)) {
              instantiateFileTypeBean(pendingTypeByMatcher);
            }
          }
        }

        final StandardFileType type = myStandardFileTypes.get(fileType.getName());
        if (type != null) {
          type.matchers.addAll(fileNameMatchers);
        }
        else {
          myStandardFileTypes.put(fileType.getName(), new StandardFileType(fileType, fileNameMatchers));
        }
      }
    };

    FileTypeFactory.FILE_TYPE_FACTORY_EP.forEachExtensionSafe(factory -> factory.createFileTypes(consumer));

    for (StandardFileType pair : myStandardFileTypes.values()) {
      registerFileTypeWithoutNotification(pair.fileType, pair.matchers, true);
    }

    try {
      URL defaultFileTypesUrl = FileTypeManagerImpl.class.getResource("/defaultFileTypes.xml");
      if (defaultFileTypesUrl != null) {
        Element defaultFileTypesElement = JDOMUtil.load(URLUtil.openStream(defaultFileTypesUrl));
        for (Element e : defaultFileTypesElement.getChildren()) {
          if ("filetypes".equals(e.getName())) {
            for (Element element : e.getChildren(ELEMENT_FILETYPE)) {
              String fileTypeName = element.getAttributeValue(ATTRIBUTE_NAME);
              if (myPendingFileTypes.get(fileTypeName) != null) continue;
              loadFileType(element, true);
            }
          }
          else if (AbstractFileType.ELEMENT_EXTENSION_MAP.equals(e.getName())) {
            readGlobalMappings(e, true);
          }
        }
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  private static void initializeMatchers(FileTypeBean bean) {
    bean.addMatchers(ContainerUtil.concat(parse(bean.extensions), parse(bean.fileNames, token -> new ExactFileNameMatcher(token)),
                                          parse(bean.fileNamesCaseInsensitive, token -> new ExactFileNameMatcher(token, true)),
                                          parse(bean.patterns, token -> FileNameMatcherFactory.getInstance().createMatcher(token))));
  }

  private void instantiatePendingFileTypes() {
    final Collection<FileTypeBean> fileTypes = new ArrayList<>(myPendingFileTypes.values());
    for (FileTypeBean fileTypeBean : fileTypes) {
      final StandardFileType type = myStandardFileTypes.get(fileTypeBean.name);
      if (type != null) {
        type.matchers.addAll(fileTypeBean.getMatchers());
      }
      else {
        instantiateFileTypeBean(fileTypeBean);
      }
    }
  }

  private FileType instantiateFileTypeBean(@Nonnull FileTypeBean fileTypeBean) {
    FileType fileType;
    PluginId pluginId = fileTypeBean.getPluginDescriptor().getPluginId();
    try {
      @SuppressWarnings("unchecked") Class<FileType> beanClass = (Class<FileType>)Class.forName(fileTypeBean.implementationClass, true, fileTypeBean.getPluginDescriptor().getPluginClassLoader());
      if (fileTypeBean.fieldName != null) {
        Field field = beanClass.getDeclaredField(fileTypeBean.fieldName);
        field.setAccessible(true);
        fileType = (FileType)field.get(null);
      }
      else {
        // uncached - cached by FileTypeManagerImpl and not by bean
        fileType = ReflectionUtil.newInstance(beanClass);
      }
    }
    catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
      LOG.error(new PluginException(e, pluginId));
      return null;
    }

    if (!fileType.getName().equals(fileTypeBean.name)) {
      LOG.error(new PluginException("Incorrect name specified in <fileType>, should be " + fileType.getName() + ", actual " + fileTypeBean.name, pluginId));
    }
    if (fileType instanceof LanguageFileType) {
      final LanguageFileType languageFileType = (LanguageFileType)fileType;
      String expectedLanguage = languageFileType.isSecondary() ? null : languageFileType.getLanguage().getID();
      if (!Comparing.equal(fileTypeBean.language, expectedLanguage)) {
        LOG.error(new PluginException("Incorrect language specified in <fileType> for " + fileType.getName() + ", should be " + expectedLanguage + ", actual " + fileTypeBean.language, pluginId));
      }
    }

    final StandardFileType standardFileType = new StandardFileType(fileType, fileTypeBean.getMatchers());
    myStandardFileTypes.put(fileTypeBean.name, standardFileType);
    registerFileTypeWithoutNotification(standardFileType.fileType, standardFileType.matchers, true);

    myPendingAssociations.removeAllAssociations(fileTypeBean);
    myPendingFileTypes.remove(fileTypeBean.name);

    return fileType;
  }

  @TestOnly
  boolean toLog;

  private boolean toLog() {
    return toLog;
  }

  private static void log(String message) {
    LOG.debug(message + " - " + Thread.currentThread());
  }

  private final Executor reDetectExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("FileTypeManager Redetect Pool", PooledThreadExecutor.INSTANCE, 1, this);
  private final HashSetQueue<VirtualFile> filesToRedetect = new HashSetQueue<>();

  private static final int CHUNK_SIZE = 10;

  private void awakeReDetectExecutor() {
    reDetectExecutor.execute(() -> {
      List<VirtualFile> files = new ArrayList<>(CHUNK_SIZE);
      synchronized (filesToRedetect) {
        for (int i = 0; i < CHUNK_SIZE; i++) {
          VirtualFile file = filesToRedetect.poll();
          if (file == null) break;
          files.add(file);
        }
      }
      if (files.size() == CHUNK_SIZE) {
        awakeReDetectExecutor();
      }
      reDetect(files);
    });
  }

  @TestOnly
  public void drainReDetectQueue() {
    try {
      ((BoundedTaskExecutor)reDetectExecutor).waitAllTasksExecuted(1, TimeUnit.MINUTES);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @TestOnly
  @Nonnull
  Collection<VirtualFile> dumpReDetectQueue() {
    synchronized (filesToRedetect) {
      return new ArrayList<>(filesToRedetect);
    }
  }

  @TestOnly
  static void reDetectAsync(boolean enable) {
    RE_DETECT_ASYNC = enable;
  }

  private void reDetect(@Nonnull Collection<? extends VirtualFile> files) {
    List<VirtualFile> changed = new ArrayList<>();
    List<VirtualFile> crashed = new ArrayList<>();
    for (VirtualFile file : files) {
      boolean shouldRedetect = wasAutoDetectedBefore(file) && isDetectable(file);
      if (toLog()) {
        log("F: reDetect(" + file.getName() + ") " + file.getName() + "; shouldRedetect: " + shouldRedetect);
      }
      if (shouldRedetect) {
        int id = ((VirtualFileWithId)file).getId();
        long flags = packedFlags.get(id);
        FileType before = ObjectUtil.notNull(textOrBinaryFromCachedFlags(flags), ObjectUtil.notNull(file.getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY), PlainTextFileType.INSTANCE));
        FileType after = getByFile(file);

        if (toLog()) {
          log("F: reDetect(" +
              file.getName() +
              ") prepare to redetect. flags: " +
              readableFlags(flags) +
              "; beforeType: " +
              before.getId() +
              "; afterByFileType: " +
              (after == null ? null : after.getId()));
        }

        if (after == null || mightBeReplacedByDetectedFileType(after)) {
          try {
            after = detectFromContentAndCache(file, null);
          }
          catch (IOException e) {
            crashed.add(file);
            if (toLog()) {
              log("F: reDetect(" +
                  file.getName() +
                  ") " +
                  "before: " +
                  before.getId() +
                  "; after: crashed with " +
                  e.getMessage() +
                  "; now getFileType()=" +
                  file.getFileType().getId() +
                  "; getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY): " +
                  file.getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY));
            }
            continue;
          }
        }
        else {
          // back to standard file type
          // detected by conventional methods, no need to run detect-from-content
          file.putUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY, null);
          flags = 0;
          packedFlags.set(id, flags);
        }
        if (toLog()) {
          log("F: reDetect(" +
              file.getName() +
              ") " +
              "before: " +
              before.getId() +
              "; after: " +
              after.getId() +
              "; now getFileType()=" +
              file.getFileType().getId() +
              "; getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY): " +
              file.getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY));
        }

        if (before != after) {
          changed.add(file);
        }
      }
    }
    if (!changed.isEmpty()) {
      ApplicationManager.getApplication().invokeLater(() -> FileContentUtilCore.reparseFiles(changed), ApplicationManager.getApplication().getDisposed());
    }
    if (!crashed.isEmpty()) {
      // do not re-scan locked or invalid files too often to avoid constant disk thrashing if that condition is permanent
      EdtExecutorService.getScheduledExecutorInstance().schedule(() -> FileContentUtilCore.reparseFiles(crashed), 10, TimeUnit.SECONDS);
    }
  }

  private boolean wasAutoDetectedBefore(@Nonnull VirtualFile file) {
    if (file.getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY) != null) {
      return true;
    }
    if (file instanceof VirtualFileWithId) {
      int id = ((VirtualFileWithId)file).getId();
      // do not re-detect binary files
      return (packedFlags.get(id) & (AUTO_DETECT_WAS_RUN_MASK | AUTO_DETECTED_AS_BINARY_MASK)) == AUTO_DETECT_WAS_RUN_MASK;
    }
    return false;
  }

  @Override
  @Nonnull
  public FileType getStdFileType(@Nonnull @NonNls String name) {
    StandardFileType stdFileType;
    synchronized (PENDING_INIT_LOCK) {
      instantiatePendingFileTypeByName(name);

      stdFileType = myStandardFileTypes.get(name);
    }
    return stdFileType != null ? stdFileType.fileType : PlainTextFileType.INSTANCE;
  }

  private void instantiatePendingFileTypeByName(@NonNls @Nonnull String name) {
    final FileTypeBean bean = myPendingFileTypes.get(name);
    if (bean != null) {
      instantiateFileTypeBean(bean);
    }
  }

  @Override
  public void afterLoadState() {
    if (!myUnresolvedMappings.isEmpty()) {
      instantiatePendingFileTypes();
    }

    if (!myUnresolvedMappings.isEmpty()) {
      for (StandardFileType pair : myStandardFileTypes.values()) {
        registerReDetectedMappings(pair);
      }
    }

    // resolve unresolved mappings initialized before certain plugin initialized
    if (!myUnresolvedMappings.isEmpty()) {
      for (StandardFileType pair : myStandardFileTypes.values()) {
        bindUnresolvedMappings(pair.fileType);
      }
    }

    boolean isAtLeastOneStandardFileTypeHasBeenRead = false;
    for (FileType fileType : mySchemeManager.loadSchemes()) {
      isAtLeastOneStandardFileTypeHasBeenRead |= myInitialAssociations.hasAssociationsFor(fileType);
    }
    if (isAtLeastOneStandardFileTypeHasBeenRead) {
      restoreStandardFileExtensions();
    }
  }

  @Override
  @Nonnull
  public FileType getFileTypeByFileName(@Nonnull String fileName) {
    return getFileTypeByFileName((CharSequence)fileName);
  }

  @Override
  @Nonnull
  public FileType getFileTypeByFileName(@Nonnull CharSequence fileName) {
    synchronized (PENDING_INIT_LOCK) {
      final FileTypeBean pendingFileType = myPendingAssociations.findAssociatedFileType(fileName);
      if (pendingFileType != null) {
        return ObjectUtils.notNull(instantiateFileTypeBean(pendingFileType), UnknownFileType.INSTANCE);
      }
      FileType type = myPatternsTable.findAssociatedFileType(fileName);
      return ObjectUtils.notNull(type, UnknownFileType.INSTANCE);
    }
  }

  public void freezeFileTypeTemporarilyIn(@Nonnull VirtualFile file, @Nonnull Runnable runnable) {
    FileType fileType = file.getFileType();
    consulo.util.lang.Pair<VirtualFile, FileType> old = FILE_TYPE_FIXED_TEMPORARILY.get();
    FILE_TYPE_FIXED_TEMPORARILY.set(consulo.util.lang.Pair.create(file, fileType));
    if (toLog()) {
      log("F: freezeFileTypeTemporarilyIn(" + file.getName() + ") to " + fileType.getName() + " in " + Thread.currentThread());
    }
    try {
      runnable.run();
    }
    finally {
      if (old == null) {
        FILE_TYPE_FIXED_TEMPORARILY.remove();
      }
      else {
        FILE_TYPE_FIXED_TEMPORARILY.set(old);
      }
      if (toLog()) {
        log("F: unfreezeFileType(" + file.getName() + ") in " + Thread.currentThread());
      }
    }
  }

  @Override
  @Nonnull
  public FileType getFileTypeByFile(@Nonnull VirtualFile file) {
    return getFileTypeByFile(file, null);
  }

  @Override
  @Nonnull
  public FileType getFileTypeByFile(@Nonnull VirtualFile file, @Nullable byte[] content) {
    FileType overriddenFileType = FileTypeOverrider.EP_NAME.computeSafeIfAny((overrider) -> overrider.getOverriddenFileType(file));
    if (overriddenFileType != null) {
      return overriddenFileType;
    }

    FileType fileType = getByFile(file);
    if (!(file instanceof StubVirtualFile)) {
      if (fileType == null) {
        return getOrDetectFromContent(file, content);
      }
      if (mightBeReplacedByDetectedFileType(fileType)) {
        FileType detectedFromContent = getOrDetectFromContent(file, content);
        if (detectedFromContent != UnknownFileType.INSTANCE && detectedFromContent != PlainTextFileType.INSTANCE) {
          return detectedFromContent;
        }
      }
    }
    return ObjectUtils.notNull(fileType, UnknownFileType.INSTANCE);
  }

  private static boolean mightBeReplacedByDetectedFileType(FileType fileType) {
    return fileType instanceof PlainTextLikeFileType && fileType.isReadOnly();
  }

  @Nullable // null means all conventional detect methods returned UnknownFileType.INSTANCE, have to detect from content
  public FileType getByFile(@Nonnull VirtualFile file) {
    consulo.util.lang.Pair<VirtualFile, FileType> fixedType = FILE_TYPE_FIXED_TEMPORARILY.get();
    if (fixedType != null && fixedType.getFirst().equals(file)) {
      FileType fileType = fixedType.getSecond();
      if (toLog()) {
        log("F: getByFile(" + file.getName() + ") was frozen to " + fileType.getName() + " in " + Thread.currentThread());
      }
      return fileType;
    }

    if (file instanceof LightVirtualFile) {
      FileType fileType = ((LightVirtualFile)file).getAssignedFileType();
      if (fileType != null) {
        return fileType;
      }
    }

    for (FileTypeIdentifiableByVirtualFile type : mySpecialFileTypes) {
      if (type.isMyFileType(file)) {
        if (toLog()) {
          log("F: getByFile(" + file.getName() + "): Special file type: " + type.getName());
        }
        return type;
      }
    }

    FileType fileType = getFileTypeByFileName(file.getNameSequence());
    if (fileType == UnknownFileType.INSTANCE) {
      fileType = null;
    }
    if (toLog()) {
      log("F: getByFile(" + file.getName() + ") By name file type: " + (fileType == null ? null : fileType.getName()));
    }
    return fileType;
  }

  @Nonnull
  private FileType getOrDetectFromContent(@Nonnull VirtualFile file, @Nullable byte[] content) {
    if (!isDetectable(file)) return UnknownFileType.INSTANCE;
    if (file instanceof VirtualFileWithId) {
      int id = ((VirtualFileWithId)file).getId();

      long flags = packedFlags.get(id);
      if (!consulo.util.lang.BitUtil.isSet(flags, ATTRIBUTES_WERE_LOADED_MASK)) {
        flags = readFlagsFromCache(file);
        flags = consulo.util.lang.BitUtil.set(flags, ATTRIBUTES_WERE_LOADED_MASK, true);

        packedFlags.set(id, flags);
        if (toLog()) {
          log("F: getOrDetectFromContent(" + file.getName() + "): readFlagsFromCache() = " + readableFlags(flags));
        }
      }
      boolean autoDetectWasRun = consulo.util.lang.BitUtil.isSet(flags, AUTO_DETECT_WAS_RUN_MASK);
      if (autoDetectWasRun) {
        FileType type = textOrBinaryFromCachedFlags(flags);
        if (toLog()) {
          log("F: getOrDetectFromContent(" +
              file.getName() +
              "):" +
              " cached type = " +
              (type == null ? null : type.getName()) +
              "; packedFlags.get(id):" +
              readableFlags(flags) +
              "; getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY): " +
              file.getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY));
        }
        if (type != null) {
          return type;
        }
      }
    }
    FileType fileType = file.getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY);
    if (toLog()) {
      log("F: getOrDetectFromContent(" + file.getName() + "): " + "getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY) = " + (fileType == null ? null : fileType.getName()));
    }
    if (fileType == null) {
      // run autodetection
      try {
        fileType = detectFromContentAndCache(file, content);
      }
      catch (IOException e) {
        fileType = UnknownFileType.INSTANCE;
      }
    }

    if (toLog()) {
      log("F: getOrDetectFromContent(" + file.getName() + "): getFileType after detect run = " + fileType.getName());
    }

    return fileType;
  }

  @Nonnull
  private static String readableFlags(long flags) {
    String result = "";
    if (consulo.util.lang.BitUtil.isSet(flags, ATTRIBUTES_WERE_LOADED_MASK)) result += (result.isEmpty() ? "" : " | ") + "ATTRIBUTES_WERE_LOADED_MASK";
    if (consulo.util.lang.BitUtil.isSet(flags, AUTO_DETECT_WAS_RUN_MASK)) result += (result.isEmpty() ? "" : " | ") + "AUTO_DETECT_WAS_RUN_MASK";
    if (consulo.util.lang.BitUtil.isSet(flags, AUTO_DETECTED_AS_BINARY_MASK)) result += (result.isEmpty() ? "" : " | ") + "AUTO_DETECTED_AS_BINARY_MASK";
    if (consulo.util.lang.BitUtil.isSet(flags, AUTO_DETECTED_AS_TEXT_MASK)) result += (result.isEmpty() ? "" : " | ") + "AUTO_DETECTED_AS_TEXT_MASK";
    return result;
  }

  private volatile FileAttribute autoDetectedAttribute;

  // read auto-detection flags from the persistent FS file attributes. If file attributes are absent, return 0 for flags
  // returns three bits value for AUTO_DETECTED_AS_TEXT_MASK, AUTO_DETECTED_AS_BINARY_MASK and AUTO_DETECT_WAS_RUN_MASK bits
  protected byte readFlagsFromCache(@Nonnull VirtualFile file) {
    boolean wasAutoDetectRun = false;
    byte status = 0;
    try (DataInputStream stream = autoDetectedAttribute.readAttribute(file)) {
      status = stream == null ? 0 : stream.readByte();
      wasAutoDetectRun = stream != null;
    }
    catch (IOException ignored) {

    }
    status = consulo.util.lang.BitUtil.set(status, AUTO_DETECT_WAS_RUN_MASK, wasAutoDetectRun);

    return (byte)(status & (AUTO_DETECTED_AS_TEXT_MASK | AUTO_DETECTED_AS_BINARY_MASK | AUTO_DETECT_WAS_RUN_MASK));
  }

  // store auto-detection flags to the persistent FS file attributes
  // writes AUTO_DETECTED_AS_TEXT_MASK, AUTO_DETECTED_AS_BINARY_MASK bits only
  protected void writeFlagsToCache(@Nonnull VirtualFile file, int flags) {
    try (DataOutputStream stream = autoDetectedAttribute.writeAttribute(file)) {
      stream.writeByte(flags & (AUTO_DETECTED_AS_TEXT_MASK | AUTO_DETECTED_AS_BINARY_MASK));
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  void clearCaches() {
    packedFlags.clear();
    if (toLog()) {
      log("F: clearCaches()");
    }
  }

  private void clearPersistentAttributes() {
    int count = fileTypeChangedCount.incrementAndGet();
    autoDetectedAttribute = autoDetectedAttribute.newVersion(count);
    PropertiesComponent.getInstance().setValue("fileTypeChangedCounter", Integer.toString(count));
    if (toLog()) {
      log("F: clearPersistentAttributes()");
    }
  }

  @Nullable //null means the file was not auto-detected as text/binary
  private static FileType textOrBinaryFromCachedFlags(long flags) {
    return consulo.util.lang.BitUtil.isSet(flags, AUTO_DETECTED_AS_TEXT_MASK) ? PlainTextFileType.INSTANCE : consulo.util.lang.BitUtil.isSet(flags, AUTO_DETECTED_AS_BINARY_MASK) ? UnknownFileType.INSTANCE : null;
  }

  private void cacheAutoDetectedFileType(@Nonnull VirtualFile file, @Nonnull FileType fileType) {
    boolean wasAutodetectedAsText = fileType == PlainTextFileType.INSTANCE;
    boolean wasAutodetectedAsBinary = fileType == UnknownFileType.INSTANCE;

    int flags = consulo.util.lang.BitUtil.set(0, AUTO_DETECTED_AS_TEXT_MASK, wasAutodetectedAsText);
    flags = consulo.util.lang.BitUtil.set(flags, AUTO_DETECTED_AS_BINARY_MASK, wasAutodetectedAsBinary);
    writeFlagsToCache(file, flags);
    if (file instanceof VirtualFileWithId) {
      int id = ((VirtualFileWithId)file).getId();
      flags = consulo.util.lang.BitUtil.set(flags, AUTO_DETECT_WAS_RUN_MASK, true);
      flags = consulo.util.lang.BitUtil.set(flags, ATTRIBUTES_WERE_LOADED_MASK, true);
      packedFlags.set(id, flags);

      if (wasAutodetectedAsText || wasAutodetectedAsBinary) {
        file.putUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY, null);
        if (toLog()) {
          log("F: cacheAutoDetectedFileType(" +
              file.getName() +
              ") " +
              "cached to " +
              fileType.getName() +
              " flags = " +
              readableFlags(flags) +
              "; getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY): " +
              file.getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY));
        }
        return;
      }
    }
    file.putUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY, fileType);
    if (toLog()) {
      log("F: cacheAutoDetectedFileType(" +
          file.getName() +
          ") " +
          "cached to " +
          fileType.getName() +
          " flags = " +
          readableFlags(flags) +
          "; getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY): " +
          file.getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY));
    }
  }

  @Override
  public FileType findFileTypeByName(@Nonnull String fileTypeName) {
    FileType type = getStdFileType(fileTypeName);
    // TODO: Abstract file types are not std one, so need to be restored specially,
    // currently there are 6 of them and restoration does not happen very often so just iteration is enough
    if (type == PlainTextFileType.INSTANCE && !fileTypeName.equals(type.getName())) {
      for (FileType fileType : mySchemeManager.getAllSchemes()) {
        if (fileTypeName.equals(fileType.getName())) {
          return fileType;
        }
      }
    }
    return type;
  }

  private static boolean isDetectable(@Nonnull final VirtualFile file) {
    if (file.isDirectory() || !file.isValid() || file.is(VFileProperty.SPECIAL) || file.getLength() == 0) {
      // for empty file there is still hope its type will change
      return false;
    }
    return file.getFileSystem() instanceof FileSystemInterface;
  }

  private int readSafely(@Nonnull InputStream stream, @Nonnull byte[] buffer, int offset, int length) throws IOException {
    int n = stream.read(buffer, offset, length);
    if (n <= 0) {
      // maybe locked because someone else is writing to it
      // repeat inside read action to guarantee all writes are finished
      if (toLog()) {
        log("F: processFirstBytes(): inputStream.read() returned " + n + "; retrying with read action. stream=" + streamInfo(stream));
      }
      n = ReadAction.compute(() -> stream.read(buffer, offset, length));
      if (toLog()) {
        log("F: processFirstBytes(): under read action inputStream.read() returned " + n + "; stream=" + streamInfo(stream));
      }
    }
    return n;
  }

  @Nonnull
  private FileType detectFromContentAndCache(@Nonnull final VirtualFile file, @Nullable byte[] content) throws IOException {
    long start = System.currentTimeMillis();
    FileType fileType = detectFromContent(file, content, FileTypeDetector.EP_NAME.getExtensionList());

    cacheAutoDetectedFileType(file, fileType);
    counterAutoDetect.incrementAndGet();
    long elapsed = System.currentTimeMillis() - start;
    elapsedAutoDetect.addAndGet(elapsed);

    return fileType;
  }

  @Nonnull
  private FileType detectFromContent(@Nonnull VirtualFile file, @Nullable byte[] content, @Nonnull Iterable<? extends FileTypeDetector> detectors) throws IOException {
    FileType fileType;
    if (content != null) {
      fileType = detect(file, content, content.length, detectors);
    }
    else {
      try (InputStream inputStream = ((FileSystemInterface)file.getFileSystem()).getInputStream(file)) {
        if (toLog()) {
          log("F: detectFromContentAndCache(" + file.getName() + "):" + " inputStream=" + streamInfo(inputStream));
        }

        int fileLength = (int)file.getLength();

        int bufferLength = StreamSupport.stream(detectors.spliterator(), false).map(FileTypeDetector::getDesiredContentPrefixLength).max(Comparator.naturalOrder())
                .orElse(RawFileLoaderImpl.getUserContentLoadLimit());
        byte[] buffer = fileLength <= FileUtilRt.THREAD_LOCAL_BUFFER_LENGTH ? FileUtilRt.getThreadLocalBuffer() : new byte[Math.min(fileLength, bufferLength)];

        int n = readSafely(inputStream, buffer, 0, buffer.length);
        fileType = detect(file, buffer, n, detectors);

        if (toLog()) {
          try (InputStream newStream = ((FileSystemInterface)file.getFileSystem()).getInputStream(file)) {
            byte[] buffer2 = new byte[50];
            int n2 = newStream.read(buffer2, 0, buffer2.length);
            log("F: detectFromContentAndCache(" +
                file.getName() +
                "): result: " +
                fileType.getName() +
                "; stream: " +
                streamInfo(inputStream) +
                "; newStream: " +
                streamInfo(newStream) +
                "; read: " +
                n2 +
                "; buffer: " +
                Arrays.toString(buffer2));
          }
        }
      }
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug(file + "; type=" + fileType.getDescription() + "; " + counterAutoDetect);
    }
    return fileType;
  }

  @Nonnull
  private FileType detect(@Nonnull VirtualFile file, @Nonnull byte[] bytes, int length, @Nonnull Iterable<? extends FileTypeDetector> detectors) {
    if (length <= 0) return UnknownFileType.INSTANCE;

    // use PlainTextFileType because it doesn't supply its own charset detector
    // help set charset in the process to avoid double charset detection from content
    return LoadTextUtil.processTextFromBinaryPresentationOrNull(bytes, length, file, true, true, PlainTextFileType.INSTANCE, (@Nullable CharSequence text) -> {
      if (toLog()) {
        log("F: detectFromContentAndCache.processFirstBytes(" +
            file.getName() +
            "): bytes length=" +
            length +
            "; isText=" +
            (text != null) +
            "; text='" +
            (text == null ? null : StringUtil.first(text, 100, true)) +
            "'" +
            ", detectors=" +
            detectors);
      }
      FileType detected = null;
      ByteSequence firstBytes = new ByteArraySequence(bytes, 0, length);
      for (FileTypeDetector detector : detectors) {
        try {
          detected = detector.detect(file, firstBytes, text);
        }
        catch (Exception e) {
          LOG.error("Detector " + detector + " (" + detector.getClass() + ") exception occurred:", e);
        }
        if (detected != null) {
          if (toLog()) {
            log("F: detectFromContentAndCache.processFirstBytes(" + file.getName() + "): detector " + detector + " type as " + detected.getName());
          }
          break;
        }
      }

      if (detected == null) {
        detected = text == null ? UnknownFileType.INSTANCE : PlainTextFileType.INSTANCE;
        if (toLog()) {
          log("F: detectFromContentAndCache.processFirstBytes(" + file.getName() + "): " + "no detector was able to detect. assigned " + detected.getName());
        }
      }
      return detected;
    });
  }

  // for diagnostics
  @SuppressWarnings("ConstantConditions")
  private static Object streamInfo(@Nonnull InputStream stream) throws IOException {
    if (stream instanceof BufferedInputStream) {
      InputStream in = ReflectionUtil.getField(stream.getClass(), stream, InputStream.class, "in");
      byte[] buf = ReflectionUtil.getField(stream.getClass(), stream, byte[].class, "buf");
      int count = ReflectionUtil.getField(stream.getClass(), stream, int.class, "count");
      int pos = ReflectionUtil.getField(stream.getClass(), stream, int.class, "pos");
      return "BufferedInputStream(buf=" + (buf == null ? null : Arrays.toString(Arrays.copyOf(buf, count))) + ", count=" + count + ", pos=" + pos + ", in=" + streamInfo(in) + ")";
    }
    if (stream instanceof FileInputStream) {
      String path = ReflectionUtil.getField(stream.getClass(), stream, String.class, "path");
      FileChannel channel = ReflectionUtil.getField(stream.getClass(), stream, FileChannel.class, "channel");
      boolean closed = ReflectionUtil.getField(stream.getClass(), stream, boolean.class, "closed");
      int available = stream.available();
      File file = new File(path);
      return "FileInputStream(path=" +
             path +
             ", available=" +
             available +
             ", closed=" +
             closed +
             ", channel=" +
             channel +
             ", channel.size=" +
             (channel == null ? null : channel.size()) +
             ", file.exists=" +
             file.exists() +
             ", file.content='" +
             FileUtil.loadFile(file) +
             "')";
    }
    return stream;
  }

  @Nullable
  private Collection<FileTypeDetector> getDetectorsForType(@Nonnull FileType fileType) {
    synchronized (FILE_TYPE_DETECTOR_MAP_LOCK) {
      if (myFileTypeDetectorMap == null) {
        myFileTypeDetectorMap = new MultiValuesMap<>();
        for (FileTypeDetector detector : FileTypeDetector.EP_NAME.getExtensionList()) {
          Collection<? extends FileType> detectedFileTypes = detector.getDetectedFileTypes();
          if (detectedFileTypes != null) {
            for (FileType type : detectedFileTypes) {
              myFileTypeDetectorMap.put(type, detector);
            }
          }
          else {
            myUntypedFileTypeDetectors.add(detector);
            if (ApplicationManager.getApplication().isInternal()) {
              LOG.error("File type detector " + detector + " does not implement getDetectedFileTypes(), leading to suboptimal performance. Please implement the method.");
            }
          }
        }
      }

      return myFileTypeDetectorMap.get(fileType);
    }
  }

  @Override
  public boolean isFileOfType(@Nonnull VirtualFile file, @Nonnull FileType type) {
    if (mightBeReplacedByDetectedFileType(type) || type.equals(UnknownFileType.INSTANCE)) {
      // a file has unknown file type if none of file type detectors matched it
      // for plain text file type, we run file type detection based on content

      return file.getFileType().equals(type);
    }

    if (file instanceof LightVirtualFile) {
      FileType assignedFileType = ((LightVirtualFile)file).getAssignedFileType();
      if (assignedFileType != null) {
        return type.equals(assignedFileType);
      }
    }

    FileType overriddenFileType = FileTypeOverrider.EP_NAME.computeSafeIfAny((overrider) -> overrider.getOverriddenFileType(file));
    if (overriddenFileType != null) {
      return overriddenFileType.equals(type);
    }

    if (type instanceof FileTypeIdentifiableByVirtualFile && ((FileTypeIdentifiableByVirtualFile)type).isMyFileType(file)) {
      return true;
    }

    FileType fileTypeByFileName = getFileTypeByFileName(file.getNameSequence());
    if (fileTypeByFileName == type) {
      return true;
    }
    if (fileTypeByFileName != UnknownFileType.INSTANCE) {
      return false;
    }
    if (file instanceof StubVirtualFile || !isDetectable(file)) {
      return false;
    }

    FileType detectedFromContentFileType = file.getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY);
    if (detectedFromContentFileType != null) {
      return detectedFromContentFileType.equals(type);
    }

    Collection<FileTypeDetector> detectors = getDetectorsForType(type);
    if (detectors != null || !myUntypedFileTypeDetectors.isEmpty()) {
      Iterable<FileTypeDetector> applicableDetectors = detectors != null ? ContainerUtil.concat(detectors, myUntypedFileTypeDetectors) : myUntypedFileTypeDetectors;

      try {
        FileType detectedType = detectFromContent(file, null, applicableDetectors);
        if (detectedType != UnknownFileType.INSTANCE && detectedType != PlainTextFileType.INSTANCE) {
          cacheAutoDetectedFileType(file, detectedType);
        }
        if (detectedType.equals(type)) {
          return true;
        }
      }
      catch (IOException ignored) {
      }
    }

    return false;
  }

  @Override
  public LanguageFileType findFileTypeByLanguage(@Nonnull Language language) {
    synchronized (PENDING_INIT_LOCK) {
      for (FileTypeBean bean : myPendingFileTypes.values()) {
        if (language.getID().equals(bean.language)) {
          return (LanguageFileType)instantiateFileTypeBean(bean);
        }
      }
    }
    // Do not use getRegisteredFileTypes() to avoid instantiating all pending file types
    return language.findMyFileType(mySchemeManager.getAllSchemes().toArray(FileType.EMPTY_ARRAY));
  }

  @Override
  @Nonnull
  public FileType getFileTypeByExtension(@Nonnull String extension) {
    synchronized (PENDING_INIT_LOCK) {
      final FileTypeBean pendingFileType = myPendingAssociations.findByExtension(extension);
      if (pendingFileType != null) {
        return ObjectUtils.notNull(instantiateFileTypeBean(pendingFileType), UnknownFileType.INSTANCE);
      }
      FileType type = myPatternsTable.findByExtension(extension);
      return ObjectUtils.notNull(type, UnknownFileType.INSTANCE);
    }
  }

  @Override
  @Deprecated
  public void registerFileType(@Nonnull FileType fileType) {
    registerFileType(fileType, ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  @Override
  public void registerFileType(@Nonnull final FileType type, @Nonnull final List<? extends FileNameMatcher> defaultAssociations) {
    DeprecatedMethodException.report("Use fileType extension instead.");
    ApplicationManager.getApplication().runWriteAction(() -> {
      fireBeforeFileTypesChanged();
      registerFileTypeWithoutNotification(type, defaultAssociations, true);
      fireFileTypesChanged(type, null);
    });
  }

  @Override
  public void unregisterFileType(@Nonnull final FileType fileType) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      fireBeforeFileTypesChanged();
      unregisterFileTypeWithoutNotification(fileType);
      myStandardFileTypes.remove(fileType.getName());
      fireFileTypesChanged(null, fileType);
    });
  }

  private void unregisterFileTypeWithoutNotification(@Nonnull FileType fileType) {
    myPatternsTable.removeAllAssociations(fileType);
    myInitialAssociations.removeAllAssociations(fileType);
    mySchemeManager.removeScheme(fileType);
    if (fileType instanceof FileTypeIdentifiableByVirtualFile) {
      final FileTypeIdentifiableByVirtualFile fakeFileType = (FileTypeIdentifiableByVirtualFile)fileType;
      mySpecialFileTypes = ArrayUtil.remove(mySpecialFileTypes, fakeFileType, FileTypeIdentifiableByVirtualFile.ARRAY_FACTORY);
    }
  }

  @Override
  @Nonnull
  public FileType[] getRegisteredFileTypes() {
    synchronized (PENDING_INIT_LOCK) {
      instantiatePendingFileTypes();
    }
    Collection<FileType> fileTypes = mySchemeManager.getAllSchemes();
    return fileTypes.toArray(FileType.EMPTY_ARRAY);
  }

  @Override
  @Nonnull
  public String getExtension(@Nonnull String fileName) {
    return FileUtilRt.getExtension(fileName);
  }

  @Nonnull
  @Override
  public Set<String> getIgnoredFiles() {
    return myIgnoredPatterns.getIgnoreMasks();
  }

  @Override
  public void setIgnoredFiles(@Nonnull Set<String> list) {
    fireBeforeFileTypesChanged();
    myIgnoredFileCache.clearCache();
    myIgnoredPatterns.setIgnoreMasks(list);
    fireFileTypesChanged();
  }

  @Override
  public boolean isFileIgnored(@Nonnull String name) {
    return myIgnoredPatterns.isIgnored(name);
  }

  @Override
  public boolean isFileIgnored(@Nonnull VirtualFile file) {
    return myIgnoredFileCache.isFileIgnored(file);
  }

  @Override
  @Nonnull
  public String[] getAssociatedExtensions(@Nonnull FileType type) {
    synchronized (PENDING_INIT_LOCK) {
      instantiatePendingFileTypeByName(type.getId());

      //noinspection deprecation
      return myPatternsTable.getAssociatedExtensions(type);
    }
  }

  @Override
  @Nonnull
  public List<FileNameMatcher> getAssociations(@Nonnull FileType type) {
    synchronized (PENDING_INIT_LOCK) {
      instantiatePendingFileTypeByName(type.getId());

      return myPatternsTable.getAssociations(type);
    }
  }

  @Override
  public void associate(@Nonnull FileType type, @Nonnull FileNameMatcher matcher) {
    associate(type, matcher, true);
  }

  @Override
  public void removeAssociation(@Nonnull FileType type, @Nonnull FileNameMatcher matcher) {
    removeAssociation(type, matcher, true);
  }

  @Override
  public void fireBeforeFileTypesChanged() {
    FileTypeEvent event = new FileTypeEvent(this, null, null);
    myMessageBus.syncPublisher(TOPIC).beforeFileTypesChanged(event);
  }

  private final AtomicInteger fileTypeChangedCount;

  @Override
  public void fireFileTypesChanged() {
    fireFileTypesChanged(null, null);
  }

  public void fireFileTypesChanged(@Nullable FileType addedFileType, @Nullable FileType removedFileType) {
    clearCaches();
    clearPersistentAttributes();
    myMessageBus.syncPublisher(TOPIC).fileTypesChanged(new FileTypeEvent(this, addedFileType, removedFileType));
  }

  private final Map<FileTypeListener, MessageBusConnection> myAdapters = new HashMap<>();

  @Override
  public void addFileTypeListener(@Nonnull FileTypeListener listener) {
    final MessageBusConnection connection = myMessageBus.connect();
    connection.subscribe(TOPIC, listener);
    myAdapters.put(listener, connection);
  }

  @Override
  public void removeFileTypeListener(@Nonnull FileTypeListener listener) {
    final MessageBusConnection connection = myAdapters.remove(listener);
    if (connection != null) {
      connection.disconnect();
    }
  }

  @Override
  public void loadState(@Nonnull Element state) {
    int savedVersion = StringUtilRt.parseInt(state.getAttributeValue(ATTRIBUTE_VERSION), 0);

    for (Element element : state.getChildren()) {
      if (element.getName().equals(ELEMENT_IGNORE_FILES)) {
        myIgnoredPatterns.setIgnoreMasks(element.getAttributeValue(ATTRIBUTE_LIST));
      }
      else if (AbstractFileType.ELEMENT_EXTENSION_MAP.equals(element.getName())) {
        readGlobalMappings(element, false);
      }
    }

    if (savedVersion < 4) {
      if (savedVersion == 0) {
        addIgnore(".svn");
      }

      if (savedVersion < 2) {
        restoreStandardFileExtensions();
      }

      addIgnore("*.pyc");
      addIgnore("*.pyo");
      addIgnore(".git");
    }

    if (savedVersion < 5) {
      addIgnore("*.hprof");
    }

    if (savedVersion < 6) {
      addIgnore("_svn");
    }

    if (savedVersion < 7) {
      addIgnore(".hg");
    }

    if (savedVersion < 8) {
      addIgnore("*~");
    }

    if (savedVersion < 9) {
      addIgnore("__pycache__");
    }

    if (savedVersion < 11) {
      addIgnore("*.rbc");
    }

    if (savedVersion < 13) {
      // we want *.lib back since it's an important user artifact for CLion, also for IDEA project itself, since we have some libs.
      unignoreMask("*.lib");
    }

    if (savedVersion < 15) {
      // we want .bundle back, bundler keeps useful data there
      unignoreMask(".bundle");
    }

    if (savedVersion < 16) {
      // we want .tox back to allow users selecting interpreters from it
      unignoreMask(".tox");
    }

    if (savedVersion < 17) {
      addIgnore("*.rbc");
    }

    myIgnoredFileCache.clearCache();

    String counter = JDOMExternalizer.readString(state, "fileTypeChangedCounter");
    if (counter != null) {
      fileTypeChangedCount.set(StringUtilRt.parseInt(counter, 0));
      autoDetectedAttribute = autoDetectedAttribute.newVersion(fileTypeChangedCount.get());
    }
  }

  private void unignoreMask(@Nonnull final String maskToRemove) {
    final Set<String> masks = new LinkedHashSet<>(myIgnoredPatterns.getIgnoreMasks());
    masks.remove(maskToRemove);

    myIgnoredPatterns.clearPatterns();
    for (final String each : masks) {
      myIgnoredPatterns.addIgnoreMask(each);
    }
  }

  private void readGlobalMappings(@Nonnull Element e, boolean isAddToInit) {
    for (consulo.util.lang.Pair<FileNameMatcher, String> association : AbstractFileType.readAssociations(e)) {
      FileType type = getFileTypeByName(association.getSecond());
      FileNameMatcher matcher = association.getFirst();
      final FileTypeBean pendingFileTypeBean = myPendingAssociations.findAssociatedFileType(matcher);
      if (pendingFileTypeBean != null) {
        instantiateFileTypeBean(pendingFileTypeBean);
      }

      if (type != null) {
        if (PlainTextFileType.INSTANCE == type) {
          FileType newFileType = myPatternsTable.findAssociatedFileType(matcher);
          if (newFileType != null && newFileType != PlainTextFileType.INSTANCE && newFileType != UnknownFileType.INSTANCE) {
            myRemovedMappingTracker.add(matcher, newFileType.getName(), false);
          }
        }
        associate(type, matcher, false);
        if (isAddToInit) {
          myInitialAssociations.addAssociation(matcher, type);
        }
      }
      else {
        myUnresolvedMappings.put(matcher, association.getSecond());
      }
    }

    myRemovedMappingTracker.load(e);
    for (RemovedMappingTracker.RemovedMapping mapping : myRemovedMappingTracker.getRemovedMappings()) {
      FileType fileType = getFileTypeByName(mapping.getFileTypeName());
      if (fileType != null) {
        removeAssociation(fileType, mapping.getFileNameMatcher(), false);
      }
    }
  }

  private void addIgnore(@NonNls @Nonnull String ignoreMask) {
    myIgnoredPatterns.addIgnoreMask(ignoreMask);
  }

  private void restoreStandardFileExtensions() {
    for (final String name : FILE_TYPES_WITH_PREDEFINED_EXTENSIONS) {
      final StandardFileType stdFileType = myStandardFileTypes.get(name);
      if (stdFileType != null) {
        FileType fileType = stdFileType.fileType;
        for (FileNameMatcher matcher : myPatternsTable.getAssociations(fileType)) {
          FileType defaultFileType = myInitialAssociations.findAssociatedFileType(matcher);
          if (defaultFileType != null && defaultFileType != fileType) {
            removeAssociation(fileType, matcher, false);
            associate(defaultFileType, matcher, false);
          }
        }

        for (FileNameMatcher matcher : myInitialAssociations.getAssociations(fileType)) {
          associate(fileType, matcher, false);
        }
      }
    }
  }

  @Nonnull
  @Override
  public Element getState() {
    Element state = new Element("state");

    Set<String> masks = myIgnoredPatterns.getIgnoreMasks();
    String ignoreFiles;
    if (masks.isEmpty()) {
      ignoreFiles = "";
    }
    else {
      String[] strings = ArrayUtilRt.toStringArray(masks);
      Arrays.sort(strings);
      ignoreFiles = StringUtil.join(strings, ";") + ";";
    }

    if (!ignoreFiles.equalsIgnoreCase(DEFAULT_IGNORED)) {
      // empty means empty list - we need to distinguish null and empty to apply or not to apply default value
      state.addContent(new Element(ELEMENT_IGNORE_FILES).setAttribute(ATTRIBUTE_LIST, ignoreFiles));
    }

    Element map = new Element(AbstractFileType.ELEMENT_EXTENSION_MAP);

    List<FileType> notExternalizableFileTypes = new ArrayList<>();
    for (FileType type : mySchemeManager.getAllSchemes()) {
      if (!(type instanceof AbstractFileType) || myDefaultTypes.contains(type)) {
        notExternalizableFileTypes.add(type);
      }
    }
    if (!notExternalizableFileTypes.isEmpty()) {
      Collections.sort(notExternalizableFileTypes, Comparator.comparing(FileType::getName));
      for (FileType type : notExternalizableFileTypes) {
        writeExtensionsMap(map, type, true);
      }
    }

    // https://youtrack.jetbrains.com/issue/IDEA-138366
    myRemovedMappingTracker.save(map);

    if (!myUnresolvedMappings.isEmpty()) {
      FileNameMatcher[] unresolvedMappingKeys = myUnresolvedMappings.keySet().toArray(new FileNameMatcher[0]);
      Arrays.sort(unresolvedMappingKeys, Comparator.comparing(FileNameMatcher::getPresentableString));

      for (FileNameMatcher fileNameMatcher : unresolvedMappingKeys) {
        Element content = AbstractFileType.writeMapping(myUnresolvedMappings.get(fileNameMatcher), fileNameMatcher, true);
        if (content != null) {
          map.addContent(content);
        }
      }
    }

    if (!map.getChildren().isEmpty()) {
      state.addContent(map);
    }

    if (!state.getChildren().isEmpty()) {
      state.setAttribute(ATTRIBUTE_VERSION, String.valueOf(VERSION));
    }
    return state;
  }

  private void writeExtensionsMap(@Nonnull Element map, @Nonnull FileType type, boolean specifyTypeName) {
    List<FileNameMatcher> associations = myPatternsTable.getAssociations(type);
    Set<FileNameMatcher> defaultAssociations = new HashSet<>(myInitialAssociations.getAssociations(type));

    for (FileNameMatcher matcher : associations) {
      boolean isDefaultAssociationContains = defaultAssociations.remove(matcher);
      if (!isDefaultAssociationContains && shouldSave(type)) {
        Element content = AbstractFileType.writeMapping(type.getName(), matcher, specifyTypeName);
        if (content != null) {
          map.addContent(content);
        }
      }
    }

    myRemovedMappingTracker.saveRemovedMappingsForFileType(map, type.getName(), defaultAssociations, specifyTypeName);
  }

  // -------------------------------------------------------------------------
  // Helper methods
  // -------------------------------------------------------------------------

  @Nullable
  private FileType getFileTypeByName(@Nonnull String name) {
    synchronized (PENDING_INIT_LOCK) {
      instantiatePendingFileTypeByName(name);
      return mySchemeManager.findSchemeByName(name);
    }
  }

  @Nonnull
  private static List<FileNameMatcher> parse(@Nullable String semicolonDelimited) {
    return parse(semicolonDelimited, token -> new ExtensionFileNameMatcher(token));
  }

  @Nonnull
  private static List<FileNameMatcher> parse(@Nullable String semicolonDelimited, Function<? super String, ? extends FileNameMatcher> matcherFactory) {
    if (semicolonDelimited == null) {
      return Collections.emptyList();
    }

    StringTokenizer tokenizer = new StringTokenizer(semicolonDelimited, FileTypeConsumer.EXTENSION_DELIMITER, false);
    ArrayList<FileNameMatcher> list = new ArrayList<>(semicolonDelimited.length() / "py;".length());
    while (tokenizer.hasMoreTokens()) {
      list.add(matcherFactory.fun(tokenizer.nextToken().trim()));
    }
    return list;
  }

  /**
   * Registers a standard file type. Doesn't notifyListeners any change events.
   */
  private void registerFileTypeWithoutNotification(@Nonnull FileType fileType, @Nonnull List<? extends FileNameMatcher> matchers, boolean addScheme) {
    if (addScheme) {
      mySchemeManager.addNewScheme(fileType, true);
    }
    for (FileNameMatcher matcher : matchers) {
      myPatternsTable.addAssociation(matcher, fileType);
      myInitialAssociations.addAssociation(matcher, fileType);
    }

    if (fileType instanceof FileTypeIdentifiableByVirtualFile) {
      mySpecialFileTypes = ArrayUtil.append(mySpecialFileTypes, (FileTypeIdentifiableByVirtualFile)fileType, FileTypeIdentifiableByVirtualFile.ARRAY_FACTORY);
    }
  }

  private void bindUnresolvedMappings(@Nonnull FileType fileType) {
    for (FileNameMatcher matcher : new HashSet<>(myUnresolvedMappings.keySet())) {
      String name = myUnresolvedMappings.get(matcher);
      if (Comparing.equal(name, fileType.getName())) {
        myPatternsTable.addAssociation(matcher, fileType);
        myUnresolvedMappings.remove(matcher);
      }
    }

    for (FileNameMatcher matcher : myRemovedMappingTracker.getMappingsForFileType(fileType.getName())) {
      removeAssociation(fileType, matcher, false);
    }
  }

  @Nonnull
  private FileType loadFileType(@Nonnull Element typeElement, boolean isDefault) {
    String fileTypeName = typeElement.getAttributeValue(ATTRIBUTE_NAME);
    String fileTypeDescr = typeElement.getAttributeValue(ATTRIBUTE_DESCRIPTION);
    String iconPath = typeElement.getAttributeValue("icon");

    String extensionsStr = StringUtil.nullize(typeElement.getAttributeValue("extensions"));
    if (isDefault && extensionsStr != null) {
      // todo support wildcards
      extensionsStr = filterAlreadyRegisteredExtensions(extensionsStr);
    }

    FileType type = isDefault ? getFileTypeByName(fileTypeName) : null;
    if (type != null) {
      return type;
    }

    Element element = typeElement.getChild(AbstractFileType.ELEMENT_HIGHLIGHTING);
    if (element == null) {
      type = new UserBinaryFileType();
    }
    else {
      SyntaxTable table = AbstractFileType.readSyntaxTable(element);
      type = new AbstractFileType(table);
      ((AbstractFileType)type).initSupport();
    }

    setFileTypeAttributes((UserFileType)type, fileTypeName, fileTypeDescr, iconPath);
    registerFileTypeWithoutNotification(type, parse(extensionsStr), isDefault);

    if (isDefault) {
      myDefaultTypes.add(type);
      if (type instanceof ExternalizableFileType) {
        ((ExternalizableFileType)type).markDefaultSettings();
      }
    }
    else {
      Element extensions = typeElement.getChild(AbstractFileType.ELEMENT_EXTENSION_MAP);
      if (extensions != null) {
        for (consulo.util.lang.Pair<FileNameMatcher, String> association : AbstractFileType.readAssociations(extensions)) {
          associate(type, association.getFirst(), false);
        }

        for (RemovedMappingTracker.RemovedMapping removedAssociation : RemovedMappingTracker.readRemovedMappings(extensions)) {
          removeAssociation(type, removedAssociation.getFileNameMatcher(), false);
        }
      }
    }
    return type;
  }

  @Nullable
  private String filterAlreadyRegisteredExtensions(@Nonnull String semicolonDelimited) {
    StringTokenizer tokenizer = new StringTokenizer(semicolonDelimited, FileTypeConsumer.EXTENSION_DELIMITER, false);
    StringBuilder builder = null;
    while (tokenizer.hasMoreTokens()) {
      String extension = tokenizer.nextToken().trim();
      if (myPendingAssociations.findByExtension(extension) == null && getFileTypeByExtension(extension) == UnknownFileType.INSTANCE) {
        if (builder == null) {
          builder = new StringBuilder();
        }
        else if (builder.length() > 0) {
          builder.append(FileTypeConsumer.EXTENSION_DELIMITER);
        }
        builder.append(extension);
      }
    }
    return builder == null ? null : builder.toString();
  }

  private static void setFileTypeAttributes(@Nonnull UserFileType fileType, @Nullable String name, @Nullable String description, @Nullable String iconPath) {
    if (!StringUtil.isEmptyOrSpaces(iconPath)) {
      fileType.setIconPath(iconPath);
    }
    if (description != null) {
      fileType.setDescription(description);
    }
    if (name != null) {
      fileType.setName(name);
    }
  }

  private static boolean shouldSave(@Nonnull FileType fileType) {
    return fileType != UnknownFileType.INSTANCE && !fileType.isReadOnly();
  }

  // -------------------------------------------------------------------------
  // Setup
  // -------------------------------------------------------------------------


  @Nonnull
  public FileTypeAssocTable<FileType> getExtensionMap() {
    synchronized (PENDING_INIT_LOCK) {
      instantiatePendingFileTypes();
    }
    return myPatternsTable;
  }

  public void setPatternsTable(@Nonnull Set<? extends FileType> fileTypes, @Nonnull FileTypeAssocTable<FileType> assocTable) {
    Map<FileNameMatcher, FileType> removedMappings = getExtensionMap().getRemovedMappings(assocTable, fileTypes);
    fireBeforeFileTypesChanged();
    for (FileType existing : getRegisteredFileTypes()) {
      if (!fileTypes.contains(existing)) {
        mySchemeManager.removeScheme(existing);
      }
    }
    for (FileType fileType : fileTypes) {
      mySchemeManager.addNewScheme(fileType, true);
      if (fileType instanceof AbstractFileType) {
        ((AbstractFileType)fileType).initSupport();
      }
    }
    myPatternsTable = assocTable.copy();
    fireFileTypesChanged();

    myRemovedMappingTracker.removeMatching((matcher, fileTypeName) -> {
      FileType fileType = getFileTypeByName(fileTypeName);
      return fileType != null && assocTable.isAssociatedWith(fileType, matcher);
    });
    for (Map.Entry<FileNameMatcher, FileType> entry : removedMappings.entrySet()) {
      myRemovedMappingTracker.add(entry.getKey(), entry.getValue().getName(), true);
    }
  }

  public void associate(@Nonnull FileType fileType, @Nonnull FileNameMatcher matcher, boolean fireChange) {
    if (!myPatternsTable.isAssociatedWith(fileType, matcher)) {
      if (fireChange) {
        fireBeforeFileTypesChanged();
      }
      myPatternsTable.addAssociation(matcher, fileType);
      if (fireChange) {
        fireFileTypesChanged();
      }
    }
  }

  public void removeAssociation(@Nonnull FileType fileType, @Nonnull FileNameMatcher matcher, boolean fireChange) {
    if (myPatternsTable.isAssociatedWith(fileType, matcher)) {
      if (fireChange) {
        fireBeforeFileTypesChanged();
      }
      myPatternsTable.removeAssociation(matcher, fileType);
      if (fireChange) {
        fireFileTypesChanged();
      }
    }
  }

  @Override
  @Nullable
  public FileType getKnownFileTypeOrAssociate(@Nonnull VirtualFile file) {
    FileType type = file.getFileType();
    if (type == UnknownFileType.INSTANCE) {
      type = FileTypeChooser.associateFileType(file.getName());
    }

    return type;
  }

  @Override
  public FileType getKnownFileTypeOrAssociate(@Nonnull VirtualFile file, @Nonnull Project project) {
    return FileTypeChooser.getKnownFileTypeOrAssociate(file, project);
  }

  private void registerReDetectedMappings(@Nonnull StandardFileType pair) {
    FileType fileType = pair.fileType;
    if (fileType == PlainTextFileType.INSTANCE) return;
    for (FileNameMatcher matcher : pair.matchers) {
      registerReDetectedMapping(fileType.getName(), matcher);
      if (matcher instanceof ExtensionFileNameMatcher) {
        // also check exact file name matcher
        ExtensionFileNameMatcher extMatcher = (ExtensionFileNameMatcher)matcher;
        registerReDetectedMapping(fileType.getName(), new ExactFileNameMatcher("." + extMatcher.getExtension()));
      }
    }
  }

  private void registerReDetectedMapping(@Nonnull String fileTypeName, @Nonnull FileNameMatcher matcher) {
    String typeName = myUnresolvedMappings.get(matcher);
    if (typeName != null && !typeName.equals(fileTypeName)) {
      if (!myRemovedMappingTracker.hasRemovedMapping(matcher)) {
        myRemovedMappingTracker.add(matcher, fileTypeName, false);
      }
      myUnresolvedMappings.remove(matcher);
    }
  }

  @Nonnull
  RemovedMappingTracker getRemovedMappingTracker() {
    return myRemovedMappingTracker;
  }

  @TestOnly
  void clearForTests() {
    for (StandardFileType fileType : myStandardFileTypes.values()) {
      myPatternsTable.removeAllAssociations(fileType.fileType);
    }
    for (FileType type : myDefaultTypes) {
      myPatternsTable.removeAllAssociations(type);
    }
    myStandardFileTypes.clear();
    myDefaultTypes.clear();
    myUnresolvedMappings.clear();
    myRemovedMappingTracker.clear();
    for (FileTypeBean bean : myPendingFileTypes.values()) {
      myPendingAssociations.removeAllAssociations(bean);
    }
    myPendingFileTypes.clear();
    mySchemeManager.clearAllSchemes();
  }

  @Override
  public void dispose() {
    LOG.info(String.format("%s auto-detected files. Detection took %s ms", counterAutoDetect, elapsedAutoDetect));
  }

  @VisibleForTesting
  public static int getVersionFromDetectors() {
    int version = 0;
    for (FileTypeDetector detector : FileTypeDetector.EP_NAME.getExtensions()) {
      version += detector.getVersion();
    }
    return version;
  }
}
