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

package consulo.compiler.setting;

import consulo.component.persist.PersistentStateComponent;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jdom.Element;

import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * @author nik
 */
public class ExcludedEntriesConfiguration implements PersistentStateComponent<ExcludedEntriesConfiguration>, Disposable {
    private static final String FILE = "file";
    private static final String DIRECTORY = "directory";
    private static final String URL = "url";
    private static final String INCLUDE_SUBDIRECTORIES = "includeSubdirectories";
    private final Collection<ExcludeEntryDescription> myExcludeEntryDescriptions = new LinkedHashSet<>();
    private ExcludeEntryDescription[] myCachedDescriptions = null;

    public synchronized boolean isEmpty() {
        return myExcludeEntryDescriptions.isEmpty();
    }

    public synchronized ExcludeEntryDescription[] getExcludeEntryDescriptions() {
        if (myCachedDescriptions == null) {
            myCachedDescriptions = myExcludeEntryDescriptions.toArray(new ExcludeEntryDescription[myExcludeEntryDescriptions.size()]);
        }
        return myCachedDescriptions;
    }

    public synchronized void addExcludeEntryDescription(ExcludeEntryDescription description) {
        myExcludeEntryDescriptions.add(description);
        myCachedDescriptions = null;
    }

    public synchronized void removeExcludeEntryDescription(ExcludeEntryDescription description) {
        myExcludeEntryDescriptions.remove(description);
        myCachedDescriptions = null;
    }

    public synchronized void removeAllExcludeEntryDescriptions() {
        myExcludeEntryDescriptions.clear();
        myCachedDescriptions = null;
    }

    public synchronized boolean containsExcludeEntryDescription(ExcludeEntryDescription description) {
        return myExcludeEntryDescriptions.contains(description);
    }

    public void readExternal(Element node) {
        for (Element element : node.getChildren()) {
            String url = element.getAttributeValue(URL);
            if (url == null) {
                continue;
            }
            if (FILE.equals(element.getName())) {
                ExcludeEntryDescription excludeEntryDescription = new ExcludeEntryDescription(url, false, true, this);
                addExcludeEntryDescription(excludeEntryDescription);
            }
            if (DIRECTORY.equals(element.getName())) {
                boolean includeSubdirectories = Boolean.parseBoolean(element.getAttributeValue(INCLUDE_SUBDIRECTORIES));
                ExcludeEntryDescription excludeEntryDescription = new ExcludeEntryDescription(url, includeSubdirectories, false, this);
                addExcludeEntryDescription(excludeEntryDescription);
            }
        }
    }

    public void writeExternal(Element element) {
        for (ExcludeEntryDescription description : getExcludeEntryDescriptions()) {
            if (description.isFile()) {
                Element entry = new Element(FILE);
                entry.setAttribute(URL, description.getUrl());
                element.addContent(entry);
            }
            else {
                Element entry = new Element(DIRECTORY);
                entry.setAttribute(URL, description.getUrl());
                entry.setAttribute(INCLUDE_SUBDIRECTORIES, Boolean.toString(description.isIncludeSubdirectories()));
                element.addContent(entry);
            }
        }
    }

    public boolean isExcluded(VirtualFile virtualFile) {
        for (ExcludeEntryDescription entryDescription : getExcludeEntryDescriptions()) {
            VirtualFile descriptionFile = entryDescription.getVirtualFile();
            if (descriptionFile == null) {
                continue;
            }
            if (entryDescription.isFile()) {
                if (descriptionFile.equals(virtualFile)) {
                    return true;
                }
            }
            else if (entryDescription.isIncludeSubdirectories()) {
                if (VirtualFileUtil.isAncestor(descriptionFile, virtualFile, false)) {
                    return true;
                }
            }
            else {
                if (virtualFile.isDirectory()) {
                    continue;
                }
                if (descriptionFile.equals(virtualFile.getParent())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void dispose() {
        for (ExcludeEntryDescription description : myExcludeEntryDescriptions) {
            Disposer.dispose(description);
        }
    }

    @Override
    public ExcludedEntriesConfiguration getState() {
        return this;
    }

    @Override
    public void loadState(ExcludedEntriesConfiguration state) {
        for (ExcludeEntryDescription description : state.getExcludeEntryDescriptions()) {
            addExcludeEntryDescription(description.copy(this));
        }
        Disposer.dispose(state);
    }
}
