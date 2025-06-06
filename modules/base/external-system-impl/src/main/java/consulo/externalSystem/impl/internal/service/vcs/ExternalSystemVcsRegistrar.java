/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package consulo.externalSystem.impl.internal.service.vcs;

import consulo.externalSystem.ExternalSystemManager;
import consulo.externalSystem.setting.AbstractExternalSystemSettings;
import consulo.externalSystem.setting.ExternalProjectSettings;
import consulo.externalSystem.setting.ExternalSystemSettingsListenerAdapter;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.ProjectLevelVcsManager;
import consulo.versionControlSystem.VcsDirectoryMapping;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.versionControlSystem.util.VcsUtil;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * It's rather often when open-source project is backed by an external system and stored at public repo hosting
 * like github, bitbucket etc. Standard actions sequence looks as below then:
 * <pre>
 * <ol>
 *   <li>Clone target repo to local machine;</li>
 *   <li>Execute 'import from external system' wizard;</li>
 * </ol>
 * </pre>
 * The problem is that the ide detects unregistered vcs roots at the newly imported project and shows corresponding notification
 * with suggestion to configure vcs roots for the current project. We want to simplify that in a way to not showing the notification
 * but register them automatically. This class manages that.
 *
 * @author Denis Zhdanov
 * @since 2013-07-15
 */
public class ExternalSystemVcsRegistrar {
    @SuppressWarnings("unchecked")
    public static void handle(@Nonnull Project project) {
        project.getApplication().getExtensionPoint(ExternalSystemManager.class).forEach(manager -> {
            AbstractExternalSystemSettings<?, ?, ?> settings =
                ((ExternalSystemManager<?, ?, ?, ?, ?>)manager).getSettingsProvider().apply(project);
            settings.subscribe(new ExternalSystemSettingsListenerAdapter() {
                @Override
                public void onProjectsLinked(@Nonnull Collection linked) {
                    List<VcsDirectoryMapping> newMappings = new ArrayList<>();
                    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
                    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
                    for (Object o : linked) {
                        ExternalProjectSettings settings = (ExternalProjectSettings)o;
                        VirtualFile dir = fileSystem.refreshAndFindFileByPath(settings.getExternalProjectPath());
                        if (dir == null) {
                            continue;
                        }
                        if (!dir.isDirectory()) {
                            dir = dir.getParent();
                        }
                        newMappings.addAll(VcsUtil.findRoots(dir, project));
                    }

                    // There is a possible case that no VCS mappings are configured for the current project. There is a single
                    // mapping like <Project> - <No VCS> then. We want to replace it if only one mapping to the project root dir
                    // has been detected then.
                    List<VcsDirectoryMapping> oldMappings = vcsManager.getDirectoryMappings();
                    if (oldMappings.size() == 1 && newMappings.size() == 1 && StringUtil.isEmpty(oldMappings.get(0).getVcs())) {
                        VcsDirectoryMapping newMapping = newMappings.iterator().next();
                        String detectedDirPath = newMapping.getDirectory();
                        VirtualFile detectedDir = fileSystem.findFileByPath(detectedDirPath);
                        if (detectedDir != null && detectedDir.equals(project.getBaseDir())) {
                            newMappings.clear();
                            newMappings.add(new VcsDirectoryMapping("", newMapping.getVcs()));
                            vcsManager.setDirectoryMappings(newMappings);
                            return;
                        }
                    }

                    newMappings.addAll(oldMappings);
                    vcsManager.setDirectoryMappings(newMappings);
                }
            });
        });
    }
}
