/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package consulo.ide.impl.idea.openapi.vcs.changes.shelf;

import consulo.component.persist.scheme.ExternalInfo;
import consulo.component.persist.scheme.ExternalizableScheme;
import consulo.versionControlSystem.change.patch.FilePatch;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import consulo.util.xml.serializer.DefaultJDOMExternalizer;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.JDOMExternalizable;
import consulo.util.xml.serializer.WriteExternalException;
import consulo.virtualFileSystem.status.FileStatus;
import jakarta.annotation.Nonnull;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author yole
 * @since 2006-11-24
 */
public class ShelvedChangeList implements JDOMExternalizable, ExternalizableScheme {
  private static final Logger LOG = Logger.getInstance(ShelvedChangeList.class);

  @NonNls private static final String NAME_ATTRIBUTE = "name";
  @NonNls private static final String ATTRIBUTE_DATE = "date";
  @NonNls private static final String ATTRIBUTE_RECYCLED_CHANGELIST = "recycled";
  @NonNls private static final String ATTRIBUTE_TOBE_DELETED_CHANGELIST = "toDelete";
  @NonNls private static final String ELEMENT_BINARY = "binary";

  private final ExternalInfo myExternalInfo = new ExternalInfo();

  public String PATH;
  public String DESCRIPTION;
  public Date DATE;
  private List<ShelvedChange> myChanges;
  private List<ShelvedBinaryFile> myBinaryFiles;
  private boolean myRecycled;
  private boolean myToDelete;
  private String mySchemeName;

  public ShelvedChangeList() {
  }

  public ShelvedChangeList(final String path, final String description, final List<ShelvedBinaryFile> binaryFiles) {
    this(path, description, binaryFiles, System.currentTimeMillis());
  }

  public ShelvedChangeList(final String path, final String description, final List<ShelvedBinaryFile> binaryFiles, final long time) {
    PATH = FileUtil.toSystemIndependentName(path);
    DESCRIPTION = description;
    DATE = new Date(time);
    myBinaryFiles = binaryFiles;
    mySchemeName = DESCRIPTION;
  }

  public boolean isRecycled() {
    return myRecycled;
  }

  public void setRecycled(final boolean recycled) {
    myRecycled = recycled;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    PATH = FileUtil.toSystemIndependentName(PATH);
    mySchemeName = element.getAttributeValue(NAME_ATTRIBUTE);
    DATE = new Date(Long.parseLong(element.getAttributeValue(ATTRIBUTE_DATE)));
    myRecycled = Boolean.parseBoolean(element.getAttributeValue(ATTRIBUTE_RECYCLED_CHANGELIST));
    myToDelete = Boolean.parseBoolean(element.getAttributeValue(ATTRIBUTE_TOBE_DELETED_CHANGELIST));
    //noinspection unchecked
    final List<Element> children = element.getChildren(ELEMENT_BINARY);
    myBinaryFiles = new ArrayList<>(children.size());
    for (Element child : children) {
      ShelvedBinaryFile binaryFile = new ShelvedBinaryFile();
      binaryFile.readExternal(child);
      myBinaryFiles.add(binaryFile);
    }
  }

  @Override
  public void writeExternal(@Nonnull Element element) throws WriteExternalException {
    writeExternal(element, this);
  }

  private static void writeExternal(@Nonnull Element element, @Nonnull ShelvedChangeList shelvedChangeList) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(shelvedChangeList, element);
    element.setAttribute(NAME_ATTRIBUTE, shelvedChangeList.getName());
    element.setAttribute(ATTRIBUTE_DATE, Long.toString(shelvedChangeList.DATE.getTime()));
    element.setAttribute(ATTRIBUTE_RECYCLED_CHANGELIST, Boolean.toString(shelvedChangeList.isRecycled()));
    if (shelvedChangeList.isMarkedToDelete()) {
      element.setAttribute(ATTRIBUTE_TOBE_DELETED_CHANGELIST, Boolean.toString(shelvedChangeList.isMarkedToDelete()));
    }
    for (ShelvedBinaryFile file : shelvedChangeList.getBinaryFiles()) {
      Element child = new Element(ELEMENT_BINARY);
      file.writeExternal(child);
      element.addContent(child);
    }
  }

  @Override
  public String toString() {
    return DESCRIPTION;
  }

  public List<ShelvedChange> getChanges(Project project) {
    if (myChanges == null) {
      try {
        myChanges = new ArrayList<>();
        final List<? extends FilePatch> list = ShelveChangesManager.loadPatchesWithoutContent(project, PATH, null);
        for (FilePatch patch : list) {
          FileStatus status;
          if (patch.isNewFile()) {
            status = FileStatus.ADDED;
          }
          else if (patch.isDeletedFile()) {
            status = FileStatus.DELETED;
          }
          else {
            status = FileStatus.MODIFIED;
          }
          myChanges.add(new ShelvedChange(PATH, patch.getBeforeName(), patch.getAfterName(), status));
        }
      }
      catch (Exception e) {
        LOG.error("Failed to parse the file patch: [" + PATH + "]", e);
      }
    }
    return myChanges;
  }

  public void clearLoadedChanges() {
    myChanges = null;
  }

  public List<ShelvedBinaryFile> getBinaryFiles() {
    return myBinaryFiles;
  }

  @Nonnull
  @Override
  public String getName() {
    return mySchemeName;
  }

  @Nonnull
  @Override
  public ExternalInfo getExternalInfo() {
    return myExternalInfo;
  }

  @Override
  public void setName(@Nonnull String newName) {
    mySchemeName = newName;
  }

  public boolean isValid() {
    return new File(PATH).exists();
  }

  public void markToDelete(boolean toDeleted) {
    myToDelete = toDeleted;
  }

  public boolean isMarkedToDelete() {
    return myToDelete;
  }

  /**
   * Update Date while recycle or restore shelvedChangelist
   */
  public void updateDate() {
    DATE = new Date(System.currentTimeMillis());
  }
}
