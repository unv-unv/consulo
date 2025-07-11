// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.virtualFileSystem.impl.internal.local;

import consulo.logging.Logger;
import consulo.util.collection.Maps;
import consulo.util.collection.MultiMap;
import consulo.util.collection.SmartList;
import consulo.util.io.FileUtil;
import consulo.util.lang.Couple;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.internal.FileSystemUtil;
import jakarta.annotation.Nonnull;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

class CanonicalPathMap {
  private static final Logger LOG = Logger.getInstance(FileWatcher.class);

  private final List<String> myRecursiveWatchRoots;
  private final List<String> myFlatWatchRoots;
  private final List<String> myCanonicalRecursiveWatchRoots;
  private final List<String> myCanonicalFlatWatchRoots;
  private final MultiMap<String, String> myPathMapping;

  CanonicalPathMap() {
    myRecursiveWatchRoots = myCanonicalRecursiveWatchRoots = myFlatWatchRoots = myCanonicalFlatWatchRoots = Collections.emptyList();
    myPathMapping = MultiMap.empty();
  }

  CanonicalPathMap(@Nonnull List<String> recursive, @Nonnull List<String> flat) {
    myRecursiveWatchRoots = new ArrayList<>(recursive);
    myFlatWatchRoots = new ArrayList<>(flat);

    List<Pair<String, String>> mapping = new SmartList<>();
    Map<String, String> resolvedPaths = resolvePaths(recursive, flat);
    myCanonicalRecursiveWatchRoots = mapPaths(resolvedPaths, recursive, mapping);
    myCanonicalFlatWatchRoots = mapPaths(resolvedPaths, flat, mapping);

    myPathMapping = MultiMap.createConcurrentSet();
    addMapping(mapping);
  }

  @Nonnull
  private static Map<String, String> resolvePaths(@Nonnull Collection<String> recursiveRoots, @Nonnull Collection<String> flatRoots) {
    Map<String, String> result = new ConcurrentHashMap<>();
    Stream.concat(recursiveRoots.stream(), flatRoots.stream()).parallel().forEach(root -> Maps.putIfNotNull(root, FileSystemUtil.resolveSymLink(root), result));
    return result;
  }

  @Nonnull
  private static List<String> mapPaths(
    @Nonnull Map<String, String> resolvedPaths,
    @Nonnull List<String> paths,
    @Nonnull Collection<? super Pair<String, String>> mapping
  ) {
    List<String> canonicalPaths = new ArrayList<>(paths);
    for (int i = 0; i < paths.size(); i++) {
      String path = paths.get(i);
      String canonicalPath = resolvedPaths.get(path);
      if (canonicalPath != null && !path.equals(canonicalPath)) {
        canonicalPaths.set(i, canonicalPath);
        mapping.add(Couple.of(canonicalPath, path));
      }
    }
    return canonicalPaths;
  }

  @Nonnull
  List<String> getCanonicalRecursiveWatchRoots() {
    return myCanonicalRecursiveWatchRoots;
  }

  @Nonnull
  List<String> getCanonicalFlatWatchRoots() {
    return myCanonicalFlatWatchRoots;
  }

  public void addMapping(@Nonnull Collection<? extends Pair<String, String>> mapping) {
    for (Pair<String, String> pair : mapping) {
      // See if we are adding a mapping that itself should be mapped to a different path
      // Example: /foo/real_path -> /foo/symlink, /foo/remapped_path -> /foo/real_path
      // In this case, if the file watcher returns /foo/remapped_path/file.txt, we want to report /foo/symlink/file.txt back to IntelliJ.
      Collection<String> preRemapPathToWatchedPaths = myPathMapping.get(pair.second);
      for (String realWatchedPath : preRemapPathToWatchedPaths) {
        Collection<String> remappedPathMappings = myPathMapping.getModifiable(pair.first);
        remappedPathMappings.add(realWatchedPath);
      }

      // Since there can be more than one file watcher and REMAPPING is an implementation detail of the native file watcher,
      // add the mapping as usual even if we added data above.
      Collection<String> symLinksToCanonicalPath = myPathMapping.getModifiable(pair.first);
      symLinksToCanonicalPath.add(pair.second);
    }
  }

  /**
   * Maps reported paths from canonical representation to requested paths, then filters out those which do not fall under watched roots.
   *
   * <h3>Exactness</h3>
   * Some watchers (notable the native one on OS X) report a parent directory as dirty instead of the "exact" file path.
   * <p>
   * For flat roots, it means that if and only if the exact dirty file path is returned, we should compare the parent to the flat roots,
   * otherwise we should compare to path given to us because it is already the parent of the actual dirty path.
   * <p>
   * For recursive roots, if the path given to us is already the parent of the actual dirty path, we need to compare the path to the parent
   * of the recursive root because if the root itself was changed, we need to know about it.
   */
  @Nonnull
  Collection<String> getWatchedPaths(@Nonnull String reportedPath, boolean isExact) {
    if (myFlatWatchRoots.isEmpty() && myRecursiveWatchRoots.isEmpty()) return Collections.emptyList();

    Collection<String> affectedPaths = applyMapping(reportedPath);
    Collection<String> changedPaths = new SmartList<>();

    ext:
    for (String path : affectedPaths) {
      for (String root : myFlatWatchRoots) {
        if (FileUtil.namesEqual(path, root)) {
          changedPaths.add(path);
          continue ext;
        }
        if (isExact) {
          if (isApproxParent(path, root)) {
            changedPaths.add(path);
            continue ext;
          }
        }
        else if (isApproxParent(root, path)) {
          changedPaths.add(root);
        }
      }

      for (String root : myRecursiveWatchRoots) {
        if (FileUtil.startsWith(path, root)) {
          changedPaths.add(path);
          continue ext;
        }
        if (!isExact && isApproxParent(root, path)) {
          changedPaths.add(root);
        }
      }
    }

    if (changedPaths.isEmpty() && LOG.isDebugEnabled()) {
      LOG.debug("Not watchable, filtered: " + reportedPath);
    }

    return changedPaths;
  }

  // doesn't care about drive or UNC
  private static boolean isApproxParent(@Nonnull String path, @Nonnull String parent) {
    return path.lastIndexOf(File.separatorChar) == parent.length() && FileUtil.startsWith(path, parent);
  }

  @Nonnull
  private Collection<String> applyMapping(@Nonnull String reportedPath) {
    if (myPathMapping.isEmpty()) {
      return Collections.singletonList(reportedPath);
    }

    List<String> results = new SmartList<>(reportedPath);
    List<String> pathComponents = FileUtil.splitPath(reportedPath);

    File runningPath = null;
    for (int i = 0; i < pathComponents.size(); ++i) {
      String currentPathComponent = pathComponents.get(i);
      if (runningPath == null) {
        runningPath = new File(currentPathComponent.isEmpty() ? "/" : currentPathComponent);
      }
      else {
        runningPath = new File(runningPath, currentPathComponent);
      }
      Collection<String> mappedPaths = myPathMapping.get(runningPath.getPath());
      for (String mappedPath : mappedPaths) {
        // Append the specific file suffix to the mapped watch root.
        String fileSuffix = StringUtil.join(pathComponents.subList(i + 1, pathComponents.size()), File.separator);
        results.add(new File(mappedPath, fileSuffix).getPath());
      }
    }

    return results;
  }
}