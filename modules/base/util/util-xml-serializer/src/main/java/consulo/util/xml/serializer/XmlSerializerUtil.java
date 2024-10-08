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
package consulo.util.xml.serializer;

import consulo.util.xml.serializer.internal.BeanBinding;
import consulo.util.xml.serializer.internal.InternalReflectionUtil;
import consulo.util.xml.serializer.internal.MutableAccessor;
import jakarta.annotation.Nonnull;
import java.util.List;

public class XmlSerializerUtil {
  private XmlSerializerUtil() {
  }

  public static <T> void copyBean(@Nonnull T from, @Nonnull T to) {
    assert from.getClass().isAssignableFrom(to.getClass()) : "Beans of different classes specified: Cannot assign " +
                                                             from.getClass() + " to " + to.getClass();
    for (MutableAccessor accessor : BeanBinding.getAccessors(from.getClass())) {
      accessor.set(to, accessor.read(from));
    }
  }

  public static <T> T createCopy(@Nonnull T from) {
    try {
      @SuppressWarnings("unchecked")
      T to = (T)InternalReflectionUtil.newInstance(from.getClass());
      copyBean(from, to);
      return to;
    }
    catch (Exception ignored) {
      return null;
    }
  }

  @Nonnull
  public static List<MutableAccessor> getAccessors(@Nonnull Class<?> aClass) {
    return BeanBinding.getAccessors(aClass);
  }
}
