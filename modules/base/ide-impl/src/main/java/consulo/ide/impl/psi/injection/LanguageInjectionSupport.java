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
package consulo.ide.impl.psi.injection;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.ide.impl.idea.openapi.util.Factory;
import consulo.ui.ex.SimpleColoredText;
import consulo.ide.impl.idea.util.Consumer;
import consulo.component.extension.ExtensionPointName;
import consulo.configurable.Configurable;
import consulo.language.Language;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiLanguageInjectionHost;
import consulo.project.Project;
import consulo.ui.ex.action.AnAction;
import consulo.util.dataholder.Key;
import consulo.util.lang.ref.Ref;
import consulo.ide.impl.intelliLang.Configuration;
import consulo.ide.impl.intelliLang.inject.InjectedLanguage;
import consulo.ide.impl.intelliLang.inject.LanguageInjectionConfigBean;
import consulo.ide.impl.intelliLang.inject.config.BaseInjection;
import org.jdom.Element;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Gregory.Shrago
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class LanguageInjectionSupport {
  public static final ExtensionPointName<LanguageInjectionConfigBean> CONFIG_EP_NAME = ExtensionPointName.create("consulo.injectionConfig");


  public static Key<InjectedLanguage> TEMPORARY_INJECTED_LANGUAGE = Key.create("TEMPORARY_INJECTED_LANGUAGE");
  public static Key<LanguageInjectionSupport> INJECTOR_SUPPORT = Key.create("INJECTOR_SUPPORT");
  public static Key<LanguageInjectionSupport> SETTINGS_EDITOR = Key.create("SETTINGS_EDITOR");

  @Nonnull
  public abstract String getId();

  @Nonnull
  public abstract Class[] getPatternClasses();

  public abstract boolean isApplicableTo(PsiLanguageInjectionHost host);

  public abstract boolean useDefaultInjector(PsiLanguageInjectionHost host);

  @Nullable
  public abstract BaseInjection findCommentInjection(@Nonnull PsiElement host, @Nullable Ref<PsiElement> commentRef);

  public abstract boolean addInjectionInPlace(final Language language, final PsiLanguageInjectionHost psiElement);

  public abstract boolean removeInjectionInPlace(final PsiLanguageInjectionHost psiElement);

  public boolean removeInjection(final PsiElement psiElement) {
    return psiElement instanceof PsiLanguageInjectionHost && removeInjectionInPlace((PsiLanguageInjectionHost)psiElement);
  }

  public abstract boolean editInjectionInPlace(final PsiLanguageInjectionHost psiElement);

  public abstract BaseInjection createInjection(final Element element);

  public abstract void setupPresentation(final BaseInjection injection, final SimpleColoredText presentation, final boolean isSelected);

  public abstract Configurable[] createSettings(final Project project, final Configuration configuration);

  public abstract AnAction[] createAddActions(final Project project, final Consumer<BaseInjection> consumer);

  public abstract AnAction createEditAction(final Project project, final Factory<BaseInjection> producer);
}
