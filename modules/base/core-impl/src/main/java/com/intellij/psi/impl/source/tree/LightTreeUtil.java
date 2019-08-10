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
package com.intellij.psi.impl.source.tree;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.LighterASTTokenNode;
import com.intellij.lang.LighterLazyParseableNode;
import consulo.logging.Logger;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Collections;
import java.util.List;

@SuppressWarnings("ForLoopReplaceableByForEach")
public class LightTreeUtil {

  @Nullable
  public static LighterASTNode firstChildOfType(@Nonnull LighterAST tree, @Nullable LighterASTNode node, @Nonnull IElementType type) {
    if (node == null) return null;

    List<LighterASTNode> children = tree.getChildren(node);
    for (int i = 0; i < children.size(); ++i) {
      LighterASTNode child = children.get(i);
      if (child.getTokenType() == type) return child;
    }
    return null;
  }

  @Nullable
  public static LighterASTNode firstChildOfType(@Nonnull LighterAST tree, @Nullable LighterASTNode node, @Nonnull TokenSet types) {
    if (node == null) return null;

    List<LighterASTNode> children = tree.getChildren(node);
    for (int i = 0; i < children.size(); ++i) {
      LighterASTNode child = children.get(i);
      if (types.contains(child.getTokenType())) return child;
    }

    return null;
  }

  @Nonnull
  public static LighterASTNode requiredChildOfType(@Nonnull LighterAST tree, @Nonnull LighterASTNode node, @Nonnull IElementType type) {
    LighterASTNode child = firstChildOfType(tree, node, type);
    assert child != null : "Required child " + type + " not found in " + node.getTokenType() + ": " + tree.getChildren(node);
    return child;
  }

  @Nonnull
  public static LighterASTNode requiredChildOfType(@Nonnull LighterAST tree, @Nonnull LighterASTNode node, @Nonnull TokenSet types) {
    LighterASTNode child = firstChildOfType(tree, node, types);
    assert child != null : "Required child " + types + " not found in " + node.getTokenType() + ": " + tree.getChildren(node);
    return child;
  }

  @Nonnull
  public static List<LighterASTNode> getChildrenOfType(@Nonnull LighterAST tree, @Nonnull LighterASTNode node, @Nonnull IElementType type) {
    List<LighterASTNode> result = null;

    List<LighterASTNode> children = tree.getChildren(node);
    for (int i = 0, size = children.size(); i < size; ++i) {
      LighterASTNode child = children.get(i);
      if (child.getTokenType() == type) {
        if (result == null) result = new SmartList<>();
        result.add(child);
      }
    }

    return result != null ? result: Collections.emptyList();
  }

  @Nonnull
  public static List<LighterASTNode> getChildrenOfType(@Nonnull LighterAST tree, @Nonnull LighterASTNode node, @Nonnull TokenSet types) {
    List<LighterASTNode> children = tree.getChildren(node);
    List<LighterASTNode> result = null;

    for (int i = 0, size = children.size(); i < size; ++i) {
      LighterASTNode child = children.get(i);
      if (types.contains(child.getTokenType())) {
        if (result == null) result = new SmartList<>();
        result.add(child);
      }
    }

    return result != null ? result: Collections.emptyList();
  }

  @Nonnull
  public static String toFilteredString(@Nonnull LighterAST tree, @Nonnull LighterASTNode node, @Nullable TokenSet skipTypes) {
    int length = node.getEndOffset() - node.getStartOffset();
    if (length < 0) {
      length = 0;
      Logger.getInstance(LightTreeUtil.class).error("tree=" + tree + " node=" + node);
    }
    StringBuilder buffer = new StringBuilder(length);
    toBuffer(tree, node, buffer, skipTypes);
    return buffer.toString();
  }

  public static void toBuffer(@Nonnull LighterAST tree, @Nonnull LighterASTNode node, @Nonnull StringBuilder buffer, @Nullable TokenSet skipTypes) {
    if (skipTypes != null && skipTypes.contains(node.getTokenType())) {
      return;
    }

    if (node instanceof LighterASTTokenNode) {
      buffer.append(((LighterASTTokenNode)node).getText());
      return;
    }

    if (node instanceof LighterLazyParseableNode) {
      buffer.append(((LighterLazyParseableNode)node).getText());
      return;
    }

    List<LighterASTNode> children = tree.getChildren(node);
    for (int i = 0, size = children.size(); i < size; ++i) {
      toBuffer(tree, children.get(i), buffer, skipTypes);
    }
  }

  @Nullable
  public static LighterASTNode getParentOfType(@Nonnull LighterAST tree, @Nullable LighterASTNode node,
                                               @Nonnull TokenSet types, @Nonnull TokenSet stopAt) {
    if (node == null) return null;
    node = tree.getParent(node);
    while (node != null) {
      final IElementType type = node.getTokenType();
      if (types.contains(type)) return node;
      if (stopAt.contains(type)) return null;
      node = tree.getParent(node);
    }
    return null;
  }

  @Nullable
  public static LighterASTNode findLeafElementAt(@Nonnull LighterAST tree, final int offset) {
    LighterASTNode eachNode = tree.getRoot();
    if (!containsOffset(eachNode, offset)) return null;

    while (eachNode != null) {
      List<LighterASTNode> children = tree.getChildren(eachNode);
      if (children.isEmpty()) return eachNode;

      eachNode = findChildAtOffset(offset, children);
    }
    return null;
  }

  private static LighterASTNode findChildAtOffset(final int offset, List<LighterASTNode> children) {
    return ContainerUtil.find(children, node -> containsOffset(node, offset));
  }

  private static boolean containsOffset(LighterASTNode node, int offset) {
    return node.getStartOffset() <= offset && node.getEndOffset() > offset;
  }
}
