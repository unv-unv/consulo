/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.roots.libraries.ui.impl;

import com.intellij.ide.util.ChooseElementsDialog;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.LibraryRootType;
import com.intellij.openapi.roots.libraries.ui.DetectedLibraryRoot;
import com.intellij.openapi.roots.libraries.ui.LibraryRootsComponentDescriptor;
import com.intellij.openapi.roots.libraries.ui.LibraryRootsDetector;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xml.util.XmlStringUtil;
import javax.annotation.Nonnull;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author nik
 */
public class RootDetectionUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.libraryEditor.RootDetectionUtil");

  private RootDetectionUtil() {
  }

  @Nonnull
  public static List<OrderRoot> detectRoots(@Nonnull final Collection<VirtualFile> rootCandidates,
                                            @Nullable Component parentComponent,
                                            @Nullable Project project,
                                            @Nonnull final LibraryRootsComponentDescriptor rootsComponentDescriptor) {
    return detectRoots(rootCandidates, parentComponent, project, rootsComponentDescriptor.getRootsDetector(),
                       rootsComponentDescriptor.getRootTypes());
  }

  @Nonnull
  public static List<OrderRoot> detectRoots(@Nonnull final Collection<VirtualFile> rootCandidates, @Nullable Component parentComponent,
                                            @Nullable Project project, @Nonnull final LibraryRootsDetector detector,
                                            @Nonnull List<OrderRootType> rootTypesAllowedToBeSelectedByUserIfNothingIsDetected) {
    final List<OrderRoot> result = new ArrayList<>();
    final List<SuggestedChildRootInfo> suggestedRoots = new ArrayList<>();
    new Task.Modal(project, "Scanning for Roots", true) {
      @Override
      public void run(@Nonnull ProgressIndicator indicator) {
        try {
          for (VirtualFile rootCandidate : rootCandidates) {
            final Collection<DetectedLibraryRoot> roots = detector.detectRoots(rootCandidate, indicator);
            if (!roots.isEmpty() && allRootsHaveOneTypeAndEqualTo(roots, rootCandidate)) {
              for (DetectedLibraryRoot root : roots) {
                final LibraryRootType libraryRootType = root.getTypes().get(0);
                result.add(new OrderRoot(root.getFile(), libraryRootType.getType(), libraryRootType.isJarDirectory()));
              }
            }
            else {
              for (DetectedLibraryRoot root : roots) {
                final HashMap<LibraryRootType, String> names = new HashMap<>();
                for (LibraryRootType type : root.getTypes()) {
                  final String typeName = detector.getRootTypeName(type);
                  LOG.assertTrue(typeName != null, "Unexpected root type " + type.getType().getName() + (type.isJarDirectory() ? " (jar directory)" : "") + ", " +
                                                   "" +
                                                   "detectors: " + detector);
                  names.put(type, typeName);
                }
                suggestedRoots.add(new SuggestedChildRootInfo(rootCandidate, root, names));
              }
            }
          }
        }
        catch (ProcessCanceledException ignored) {
        }
      }
    }.queue();

    if (!suggestedRoots.isEmpty()) {
      final DetectedRootsChooserDialog dialog = parentComponent != null
                                                ? new DetectedRootsChooserDialog(parentComponent, suggestedRoots)
                                                : new DetectedRootsChooserDialog(project, suggestedRoots);
      dialog.show();
      if (!dialog.isOK()) {
        return Collections.emptyList();
      }
      for (SuggestedChildRootInfo rootInfo : dialog.getChosenRoots()) {
        final LibraryRootType selectedRootType = rootInfo.getSelectedRootType();
        result.add(new OrderRoot(rootInfo.getDetectedRoot().getFile(), selectedRootType.getType(), selectedRootType.isJarDirectory()));
      }
    }

    if (result.isEmpty() && !rootTypesAllowedToBeSelectedByUserIfNothingIsDetected.isEmpty()) {
      Map<String, Pair<OrderRootType, Boolean>> types = new HashMap<>();
      for (OrderRootType type : rootTypesAllowedToBeSelectedByUserIfNothingIsDetected) {
        for (boolean isJarDirectory : new boolean[]{false, true}) {
          final String typeName = detector.getRootTypeName(new LibraryRootType(type, isJarDirectory));
          if (typeName != null) {
            types.put(typeName, Pair.create(type, isJarDirectory));
          }
        }
      }
      if(types.isEmpty()) {
        return Collections.emptyList();
      }
      List<String> names = new ArrayList<>(types.keySet());
      String title = "Choose Categories of Selected Files";
      String description = XmlStringUtil.wrapInHtml(
              ApplicationNamesInfo.getInstance().getProductName() + " cannot determine what kind of files the chosen items contain.<br>" +
              "Choose the appropriate categories from the list.");
      ChooseElementsDialog<String> dialog;
      if (parentComponent != null) {
        dialog = new ChooseRootTypeElementsDialog(parentComponent, names, title, description);
      }
      else {
        dialog = new ChooseRootTypeElementsDialog(project, names, title, description);
      }
      for (String rootType : dialog.showAndGetResult()) {
        final Pair<OrderRootType, Boolean> pair = types.get(rootType);
        for (VirtualFile candidate : rootCandidates) {
          result.add(new OrderRoot(candidate, pair.getFirst(), pair.getSecond()));
        }
      }
    }

    return result;
  }

  private static boolean allRootsHaveOneTypeAndEqualTo(Collection<DetectedLibraryRoot> roots, VirtualFile candidate) {
    for (DetectedLibraryRoot root : roots) {
      if (root.getTypes().size() > 1 || !root.getFile().equals(candidate)) {
        return false;
      }
    }
    return true;
  }

  private static class ChooseRootTypeElementsDialog extends ChooseElementsDialog<String> {
    public ChooseRootTypeElementsDialog(Project project, List<String> names, String title, String description) {
      super(project, names, title, description, true);
    }

    private ChooseRootTypeElementsDialog(Component parent, List<String> names, String title, String description) {
      super(parent, names, title, description, true);
    }

    @Override
    protected String getItemText(String item) {
      return item;
    }

    @Nullable
    @Override
    protected Icon getItemIcon(String item) {
      return null;
    }
  }
}
