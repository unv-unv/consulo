package com.intellij.psi.targets;

import consulo.component.extension.ExtensionPointName;
import com.intellij.pom.PomTarget;
import javax.annotation.Nonnull;

import java.util.Set;

public interface AliasingPsiTargetMapper {
  ExtensionPointName<AliasingPsiTargetMapper> EP_NAME = ExtensionPointName.create("consulo.base.aliasingPsiTargetMapper");

  @Nonnull
  Set<AliasingPsiTarget> getTargets(@Nonnull PomTarget target);
}
