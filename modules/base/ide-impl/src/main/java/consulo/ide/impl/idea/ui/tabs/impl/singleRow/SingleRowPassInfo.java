/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package consulo.ide.impl.idea.ui.tabs.impl.singleRow;

import consulo.ui.ex.awt.tab.TabInfo;
import consulo.ide.impl.idea.ui.tabs.impl.JBTabsImpl;
import consulo.ide.impl.idea.ui.tabs.impl.LayoutPassInfo;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class SingleRowPassInfo extends LayoutPassInfo {
  final Dimension layoutSize;
  final int contentCount;
  int position;
  int requiredLength;
  int toFitLength;
  public final List<TabInfo> toLayout;
  public final List<TabInfo> toDrop;
  final int moreRectAxisSize;
  public Rectangle moreRect;

  public JComponent hToolbar;
  public JComponent vToolbar;

  public Insets insets;

  public JComponent comp;
  public Rectangle tabRectangle;
  final int scrollOffset;


  public SingleRowPassInfo(SingleRowLayout layout, List<TabInfo> visibleInfos) {
    super(visibleInfos);
    JBTabsImpl tabs = layout.myTabs;
    layoutSize = tabs.getSize();
    contentCount = tabs.getTabCount();
    toLayout = new ArrayList<>();
    toDrop = new ArrayList<>();
    moreRectAxisSize = layout.getStrategy().getMoreRectAxisSize();
    scrollOffset = layout.getScrollOffset();
  }

  @Override
  public TabInfo getPreviousFor(final TabInfo info) {
    return getPrevious(myVisibleInfos, myVisibleInfos.indexOf(info));
  }

  @Override
  public TabInfo getNextFor(final TabInfo info) {
    return getNext(myVisibleInfos, myVisibleInfos.indexOf(info));
  }

  @Override
  public int getRowCount() {
    return 1;
  }

  @Override
  public int getColumnCount(final int row) {
    return myVisibleInfos.size();
  }

  @Override
  public TabInfo getTabAt(final int row, final int column) {
    return myVisibleInfos.get(column);
  }

  @Override
  public Rectangle getHeaderRectangle() {
    return (Rectangle)tabRectangle.clone();
  }

  @Override
  public boolean hasCurveSpaceFor(final TabInfo tabInfo) {
    return true;
  }
}
