/*
 * Copyright 2013-2021 consulo.io
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
package consulo.container.internal.plugin.classloader;

import consulo.container.classloader.PluginClassLoader;
import consulo.container.internal.plugin.classloader.ProxyDescription;
import consulo.container.internal.plugin.classloader.ProxyFactory;

import java.util.function.Function;

/**
 * @author VISTALL
 * @since 11/03/2021
 */
public interface ProxyHolderClassLoader extends PluginClassLoader {
  ProxyFactory registerOrGetProxy(ProxyDescription description, Function<ProxyDescription, ProxyFactory> proxyFactoryFunction);
}
