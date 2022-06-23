// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.ide.impl.psi.impl.cache.impl.id;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.Extension;
import consulo.application.Application;
import consulo.component.extension.ExtensionPoint;
import consulo.component.extension.ExtensionPointCacheKey;
import consulo.index.io.DataIndexer;
import consulo.language.psi.stub.FileContent;
import consulo.virtualFileSystem.fileType.FileType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

/**
 * @author traff
 */
@Extension(ComponentScope.APPLICATION)
public interface IdIndexer extends DataIndexer<IdIndexEntry, Integer, FileContent> {
  ExtensionPointCacheKey<IdIndexer, Map<FileType, IdIndexer>> KEY = ExtensionPointCacheKey.groupBy("IdIndexer", IdIndexer::getFileType);

  @Nullable
  static IdIndexer forFileType(FileType fileType) {
    ExtensionPoint<IdIndexer> extensionPoint = Application.get().getExtensionPoint(IdIndexer.class);
    Map<FileType, IdIndexer> map = extensionPoint.getOrBuildCache(KEY);
    return map.get(fileType);
  }

  @Nonnull
  FileType getFileType();

  default int getVersion() {
    return 1;
  }
}
