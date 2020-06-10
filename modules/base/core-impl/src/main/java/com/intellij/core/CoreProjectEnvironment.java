/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.core;

import com.intellij.mock.MockDumbService;
import com.intellij.mock.MockFileIndexFacade;
import com.intellij.mock.MockProject;
import com.intellij.mock.MockResolveScopeManager;
import consulo.disposer.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.ProjectScopeBuilder;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.CachedValuesManagerImpl;
import com.intellij.util.messages.impl.MessageBusImpl;

@Deprecated
public class CoreProjectEnvironment {
  private final Disposable myParentDisposable;
  private final CoreApplicationEnvironment myEnvironment;

  protected final FileIndexFacade myFileIndexFacade;
  protected final PsiManagerImpl myPsiManager;
  protected final MockProject myProject;
  protected final MessageBusImpl myMessageBus;

  public CoreProjectEnvironment(Disposable parentDisposable, CoreApplicationEnvironment applicationEnvironment) {
    myParentDisposable = parentDisposable;
    myEnvironment = applicationEnvironment;
    myProject = new MockProject(myEnvironment.getApplication(), myParentDisposable);

    preregisterServices();

    myFileIndexFacade = createFileIndexFacade();
    myMessageBus = (MessageBusImpl)myProject.getMessageBus();

    PsiModificationTrackerImpl modificationTracker = new PsiModificationTrackerImpl(applicationEnvironment.getApplication(), myProject);
    myProject.registerService(PsiModificationTracker.class, modificationTracker);
    myProject.registerService(FileIndexFacade.class, myFileIndexFacade);
    myProject.registerService(ResolveCache.class, new ResolveCache(myProject));

    registerProjectExtensionPoint(PsiTreeChangePreprocessor.EP_NAME, PsiTreeChangePreprocessor.class);
    myPsiManager = new PsiManagerImpl(myProject, () -> myFileIndexFacade, modificationTracker);
    ((FileManagerImpl)myPsiManager.getFileManager()).markInitialized();
    registerProjectComponent(PsiManager.class, myPsiManager);

    registerProjectComponent(PsiDocumentManager.class, new CorePsiDocumentManager(myProject, new MockDocumentCommitProcessor()));

    myProject.registerService(ResolveScopeManager.class, createResolveScopeManager(myPsiManager));

    myProject.registerService(PsiFileFactory.class, new PsiFileFactoryImpl(myPsiManager));
    myProject.registerService(CachedValuesManager.class, new CachedValuesManagerImpl(myProject, new PsiCachedValuesFactory(myPsiManager)));
    myProject.registerService(ProjectScopeBuilder.class, createProjectScopeBuilder());
    myProject.registerService(DumbService.class, new MockDumbService(myProject));
  }

  protected ProjectScopeBuilder createProjectScopeBuilder() {
    return new CoreProjectScopeBuilder(myProject, myFileIndexFacade);
  }

  protected void preregisterServices() {

  }

  protected FileIndexFacade createFileIndexFacade() {
    return new MockFileIndexFacade(myProject);
  }

  protected ResolveScopeManager createResolveScopeManager(PsiManager psiManager) {
    return new MockResolveScopeManager(myProject);
  }

  public <T> void registerProjectExtensionPoint(final ExtensionPointName<T> extensionPointName, final Class<? extends T> aClass) {
  }

  public <T> void addProjectExtension(final ExtensionPointName<T> name, final T extension) {

  }


  public <T> void registerProjectComponent(final Class<T> interfaceClass, final T implementation) {
    CoreApplicationEnvironment.registerComponentInstance(myProject.getInjectingContainer(), interfaceClass, implementation);
  }

  public Disposable getParentDisposable() {
    return myParentDisposable;
  }

  public CoreApplicationEnvironment getEnvironment() {
    return myEnvironment;
  }

  public MockProject getProject() {
    return myProject;
  }
}
