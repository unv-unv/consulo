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
package consulo.language.ast;

import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

public class TreeBackedLighterAST extends LighterAST {
  private final FileASTNode myRoot;

  public TreeBackedLighterAST(@Nonnull FileASTNode root) {
    super(root.getCharTable());
    myRoot = root;
  }

  @Nonnull
  @Override
  public LighterASTNode getRoot() {
    return wrap(myRoot);
  }

  @Override
  public LighterASTNode getParent(@Nonnull final LighterASTNode node) {
    ASTNode parent = ((NodeWrapper)node).myNode.getTreeParent();
    return parent == null ? null : wrap(parent);
  }

  @Nonnull
  @Override
  public List<LighterASTNode> getChildren(@Nonnull final LighterASTNode parent) {
    final ASTNode[] children = ((NodeWrapper)parent).myNode.getChildren(null);
    if (children.length == 0) return List.of();

    List<LighterASTNode> result = new ArrayList<>(children.length);
    for (final ASTNode child : children) {
      result.add(wrap(child));
    }
    return result;
  }

  @Nonnull
  public static LighterASTNode wrap(@Nonnull ASTNode node) {
    return node.getFirstChildNode() == null && node.getTextLength() > 0 ? new TokenNodeWrapper(node) : new NodeWrapper(node);
  }

  @Nonnull
  public ASTNode unwrap(@Nonnull LighterASTNode node) {
    return ((NodeWrapper)node).myNode;
  }

  private static class NodeWrapper implements LighterASTNode {
    protected final ASTNode myNode;

    public NodeWrapper(ASTNode node) {
      myNode = node;
    }

    @Nonnull
    @Override
    public IElementType getTokenType() {
      return myNode.getElementType();
    }

    @Override
    public int getStartOffset() {
      return myNode.getStartOffset();
    }

    @Override
    public int getEndOffset() {
      return myNode.getStartOffset() + myNode.getTextLength();
    }

    @Override
    public int getTextLength() {
      return myNode.getTextLength();
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof NodeWrapper)) return false;
      final NodeWrapper that = (NodeWrapper)o;
      if (myNode != null ? !myNode.equals(that.myNode) : that.myNode != null) return false;
      return true;
    }

    @Override
    public int hashCode() {
      return myNode.hashCode();
    }

    @Override
    public String toString() {
      return "node wrapper[" + myNode + "]";
    }
  }

  private static class TokenNodeWrapper extends NodeWrapper implements LighterASTTokenNode {
    public TokenNodeWrapper(final ASTNode node) {
      super(node);
    }

    @Override
    public CharSequence getText() {
      return myNode.getText();
    }

    @Override
    public String toString() {
      return "token wrapper[" + myNode + "]";
    }
  }
}
