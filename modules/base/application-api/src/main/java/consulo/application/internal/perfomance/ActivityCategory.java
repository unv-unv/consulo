// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.application.internal.perfomance;

import jakarta.annotation.Nonnull;

// non-sequential and repeated items
public enum ActivityCategory {
  MAIN("item"),
  APP_INIT("prepareAppInitActivity"),
  PRELOAD_ACTIVITY("preloadActivity"),
  APP_OPTIONS_TOP_HIT_PROVIDER("appOptionsTopHitProvider"),
  PROJECT_OPTIONS_TOP_HIT_PROVIDER("projectOptionsTopHitProvider"),

  APP_COMPONENT("appComponents"),
  PROJECT_COMPONENT("projectComponents"),
  MODULE_COMPONENT("moduleComponents"),

  APP_SERVICE("appServices"),
  PROJECT_SERVICE("projectServices"),
  MODULE_SERVICE("moduleServices"),

  APP_EXTENSION("appExtensions"),
  PROJECT_EXTENSION("projectExtensions"),
  MODULE_EXTENSION("moduleExtensions"),

  PROJECT_OPEN_HANDLER("openHandler"),

  POST_STARTUP_ACTIVITY("projectPostStartupActivity"),
  GC("GC"),
  REOPENING_EDITOR("reopeningEditor"),

  SERVICE_WAITING("serviceWaiting"),
  ;

  private final String jsonName;

  ActivityCategory(@Nonnull String jsonName) {
    this.jsonName = jsonName;
  }

  @Nonnull
  public String getJsonName() {
    return jsonName;
  }
}
