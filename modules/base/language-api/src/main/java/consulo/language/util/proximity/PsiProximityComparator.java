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

package consulo.language.util.proximity;

import consulo.application.util.function.Computable;
import consulo.language.Weigher;
import consulo.language.WeighingComparable;
import consulo.language.WeighingService;
import consulo.language.psi.PsiElement;
import consulo.language.statistician.StatisticsInfo;
import consulo.language.statistician.StatisticsManager;
import consulo.language.util.ModuleUtilCore;
import consulo.language.util.ProcessingContext;
import consulo.module.Module;
import consulo.util.collection.FactoryMap;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nullable;

import java.util.Comparator;
import java.util.Map;

public class PsiProximityComparator implements Comparator<Object> {
  public static final Key<ProximityStatistician> STATISTICS_KEY = Key.create("proximity");
  public static final Key<ProximityWeigher> WEIGHER_KEY = Key.create("proximity");
  private static final Key<Module> MODULE_BY_LOCATION = Key.create("ModuleByLocation");
  private final PsiElement myContext;

  private final Map<PsiElement, WeighingComparable<PsiElement, ProximityLocation>> myProximities;

  private final Module myContextModule;

  public PsiProximityComparator(@Nullable PsiElement context) {
    myContext = context;
    myContextModule = context == null ? null : context.getModule();
    myProximities = FactoryMap.create(key -> getProximity(key, myContext));
  }

  @Override
  public int compare(final Object o1, final Object o2) {
    PsiElement element1 = o1 instanceof PsiElement ? (PsiElement)o1 : null;
    PsiElement element2 = o2 instanceof PsiElement ? (PsiElement)o2 : null;
    if (element1 == null) return element2 == null ? 0 : 1;
    if (element2 == null) return -1;

    if (myContext != null && myContextModule != null) {
      final ProximityLocation location = new ProximityLocation(myContext, myContextModule);
      StatisticsInfo info1 = StatisticsManager.serialize(STATISTICS_KEY, element1, location);
      StatisticsInfo info2 = StatisticsManager.serialize(STATISTICS_KEY, element2, location);
      if (info1 != null && info2 != null) {
        StatisticsManager statisticsManager = StatisticsManager.getInstance();
        int count1 = statisticsManager.getLastUseRecency(info1);
        int count2 = statisticsManager.getLastUseRecency(info2);
        if (count1 != count2) {
          return count1 < count2 ? -1 : 1;
        }
      }
    }

    final WeighingComparable<PsiElement, ProximityLocation> proximity1 = myProximities.get(element1);
    final WeighingComparable<PsiElement, ProximityLocation> proximity2 = myProximities.get(element2);
    if (proximity1 == null || proximity2 == null) {
      return 0;
    }
    return -proximity1.compareTo(proximity2);
  }


  @Nullable
  public static WeighingComparable<PsiElement, ProximityLocation> getProximity(final PsiElement element, final PsiElement context) {
    if (element == null) return null;
    final Module contextModule = context != null ? ModuleUtilCore.findModuleForPsiElement(context) : null;
    return WeighingService.weigh(WEIGHER_KEY, element, new ProximityLocation(context, contextModule));
  }

  @Nullable
  public static WeighingComparable<PsiElement, ProximityLocation> getProximity(final Computable<? extends PsiElement> elementComputable, final PsiElement context, ProcessingContext processingContext) {
    PsiElement element = elementComputable.compute();
    if (element == null || context == null) return null;
    Module contextModule = processingContext.get(MODULE_BY_LOCATION);
    if (contextModule == null) {
      contextModule = context.getModule();
      processingContext.put(MODULE_BY_LOCATION, contextModule);
    }

    return new WeighingComparable<>(elementComputable, new ProximityLocation(context, contextModule, processingContext), getProximityWeighers());
  }

  private static Weigher<PsiElement, ProximityLocation>[] getProximityWeighers() {
    return WeighingService.getWeighers(WEIGHER_KEY).toArray(new Weigher[0]);
  }
}