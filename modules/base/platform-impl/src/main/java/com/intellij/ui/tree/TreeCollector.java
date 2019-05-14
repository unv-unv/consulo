// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.Invoker;
import javax.annotation.Nonnull;

import javax.swing.tree.TreePath;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;

public final class TreeCollector<T> {
  private final AtomicReference<List<T>> reference = new AtomicReference<>();
  private final AtomicLong counter = new AtomicLong();
  private final BiPredicate<? super T, ? super T> predicate;

  private TreeCollector(@Nonnull BiPredicate<? super T, ? super T> predicate) {
    this.predicate = predicate;
  }

  @Nonnull
  public List<T> get() {
    synchronized (reference) {
      List<T> list = reference.getAndSet(null);
      return list != null ? list : Collections.emptyList();
    }
  }

  public boolean add(@Nonnull T object) {
    synchronized (reference) {
      List<T> list = reference.get();
      if (list == null) {
        reference.set(new SmartList<>(object));
      }
      else {
        for (T parent : list) {
          if (predicate.test(parent, object)) {
            return false;
          }
        }
        list.removeIf(t -> predicate.test(object, t));
        list.add(object);
      }
      return true;
    }
  }

  public void processLater(@Nonnull Invoker invoker, @Nonnull Consumer<? super List<T>> consumer) {
    long count = counter.incrementAndGet();
    invoker.invokeLater(() -> {
      // is this request still actual after 10 ms?
      if (count == counter.get()) consumer.accept(get());
    }, 10);
  }


  public static TreeCollector<VirtualFile> createFileLeafsCollector() {
    return new TreeCollector<>((child, parent) -> isAncestor(parent, child, false));
  }

  public static TreeCollector<VirtualFile> createFileRootsCollector() {
    return new TreeCollector<>((parent, child) -> isAncestor(parent, child, false));
  }


  public static TreeCollector<TreePath> createPathLeafsCollector() {
    return new TreeCollector<>((child, parent) -> parent.isDescendant(child));
  }

  public static TreeCollector<TreePath> createPathRootsCollector() {
    return new TreeCollector<>((parent, child) -> parent.isDescendant(child));
  }
}
