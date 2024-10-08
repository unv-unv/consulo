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
package consulo.util.xml.serializer.internal;

import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.Converter;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public abstract class BasePrimitiveBinding extends Binding {
    protected final String myName;

    @Nullable
    protected final Converter<Object> myConverter;

    @Nullable
    protected Binding myBinding;

    protected BasePrimitiveBinding(@Nonnull MutableAccessor accessor, @Nullable String suggestedName, @Nullable Class<? extends Converter> converterClass) {
        super(accessor);

        myName = StringUtil.isEmpty(suggestedName) ? myAccessor.getName() : suggestedName;
        if (converterClass == null || converterClass == Converter.class) {
            myConverter = null;
            if (!(this instanceof AttributeBinding)) {
                myBinding = XmlSerializerImpl.getBinding(myAccessor);
            }
        }
        else {
            //noinspection unchecked
            myConverter = InternalReflectionUtil.newInstance(converterClass);
        }
    }

    @Nullable
    public Binding getBinding() {
        return myBinding;
    }
}