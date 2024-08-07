/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package consulo.ide.impl.idea.vcs.log.graph.utils;

import consulo.ide.impl.idea.util.containers.ContainerUtil;
import consulo.ide.impl.idea.vcs.log.graph.api.EdgeFilter;
import consulo.ide.impl.idea.vcs.log.graph.api.LinearGraph;
import consulo.ide.impl.idea.vcs.log.graph.api.LiteLinearGraph;
import consulo.ide.impl.idea.vcs.log.graph.api.elements.GraphEdge;
import consulo.ide.impl.idea.vcs.log.graph.api.elements.GraphEdgeType;
import consulo.ide.impl.idea.vcs.log.graph.impl.facade.LinearGraphController;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.awt.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LinearGraphUtils {
  public static final LinearGraphController.LinearGraphAnswer DEFAULT_GRAPH_ANSWER =
    new LinearGraphController.LinearGraphAnswer(Cursor.getDefaultCursor(), null);

  public static boolean intEqual(@Nullable Integer value, int number) {
    return value != null && value == number;
  }

  public static boolean isEdgeUp(@Nonnull GraphEdge edge, int nodeIndex) {
    return intEqual(edge.getDownNodeIndex(), nodeIndex);
  }

  public static boolean isEdgeDown(@Nonnull GraphEdge edge, int nodeIndex) {
    return intEqual(edge.getUpNodeIndex(), nodeIndex);
  }

  public static boolean isNormalEdge(@Nullable GraphEdge edge) {
    if (edge != null && edge.getType().isNormalEdge()) {
      assert edge.getUpNodeIndex() != null && edge.getDownNodeIndex() != null;
      return true;
    }
    return false;
  }

  @Nullable
  public static NormalEdge asNormalEdge(@Nullable GraphEdge edge) {
    if (isNormalEdge(edge)) {
      assert edge.getUpNodeIndex() != null && edge.getDownNodeIndex() != null;
      return NormalEdge.create(edge.getUpNodeIndex(), edge.getDownNodeIndex());
    }
    return null;
  }

  public static int getNotNullNodeIndex(@Nonnull GraphEdge edge) {
    if (edge.getUpNodeIndex() != null) return edge.getUpNodeIndex();
    assert edge.getDownNodeIndex() != null;
    return edge.getDownNodeIndex();
  }

  @Nonnull
  public static List<Integer> getUpNodes(@Nonnull LinearGraph graph, final int nodeIndex) {
    return ContainerUtil.mapNotNull(graph.getAdjacentEdges(nodeIndex, EdgeFilter.NORMAL_UP), GraphEdge::getUpNodeIndex);
  }

  @Nonnull
  public static List<Integer> getDownNodes(@Nonnull LinearGraph graph, final int nodeIndex) {
    return ContainerUtil.mapNotNull(graph.getAdjacentEdges(nodeIndex, EdgeFilter.NORMAL_DOWN), GraphEdge::getDownNodeIndex);
  }

  @Nonnull
  public static List<Integer> getDownNodesIncludeNotLoad(@Nonnull final LinearGraph graph, final int nodeIndex) {
    return ContainerUtil.mapNotNull(graph.getAdjacentEdges(nodeIndex, EdgeFilter.ALL), graphEdge -> {
      if (isEdgeDown(graphEdge, nodeIndex)) {
        if (graphEdge.getType() == GraphEdgeType.NOT_LOAD_COMMIT) return graphEdge.getTargetId();
        return graphEdge.getDownNodeIndex();
      }
      return null;
    });
  }

  @Nonnull
  public static LiteLinearGraph asLiteLinearGraph(@Nonnull final LinearGraph graph) {
    return new LiteLinearGraph() {
      @Override
      public int nodesCount() {
        return graph.nodesCount();
      }

      @Nonnull
      @Override
      public List<Integer> getNodes(final int nodeIndex, @Nonnull final NodeFilter filter) {
        return ContainerUtil.mapNotNull(graph.getAdjacentEdges(nodeIndex, filter.edgeFilter), edge -> {
          if (isEdgeUp(edge, nodeIndex)) return edge.getUpNodeIndex();
          if (isEdgeDown(edge, nodeIndex)) return edge.getDownNodeIndex();

          return null;
        });
      }
    };
  }

  @Nonnull
  public static Cursor getCursor(boolean hand) {
    if (hand) {
      return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    }
    else {
      return Cursor.getDefaultCursor();
    }
  }

  public static LinearGraphController.LinearGraphAnswer createSelectedAnswer(
    @Nonnull LinearGraph linearGraph,
    @Nonnull Collection<Integer> selectedNodeIndexes
  ) {
    Set<Integer> selectedIds = new HashSet<>();
    for (Integer nodeIndex : selectedNodeIndexes) {
      if (nodeIndex == null) continue;
      selectedIds.add(linearGraph.getNodeId(nodeIndex));
    }
    return new LinearGraphController.LinearGraphAnswer(getCursor(true), selectedIds);
  }

  @Nullable
  public static GraphEdge getEdge(@Nonnull LinearGraph graph, int up, int down) {
    List<GraphEdge> edges = graph.getAdjacentEdges(up, EdgeFilter.NORMAL_DOWN);
    for (GraphEdge edge : edges) {
      if (intEqual(edge.getDownNodeIndex(), down)) {
        return edge;
      }
    }
    return null;
  }

  @Nonnull
  public static Set<Integer> convertNodeIndexesToIds(@Nonnull final LinearGraph graph, @Nonnull Collection<Integer> nodeIndexes) {
    return ContainerUtil.map2Set(nodeIndexes, graph::getNodeId);
  }

  @Nonnull
  public static Set<Integer> convertIdsToNodeIndexes(@Nonnull final LinearGraph graph, @Nonnull Collection<Integer> ids) {
    List<Integer> result = ContainerUtil.mapNotNull(ids, graph::getNodeIndex);
    return ContainerUtil.newHashSet(result);
  }
}
