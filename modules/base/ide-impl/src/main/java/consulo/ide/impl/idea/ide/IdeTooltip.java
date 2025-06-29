/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package consulo.ide.impl.idea.ide;

import consulo.application.util.registry.Registry;
import consulo.component.util.ComparableObject;
import consulo.ui.ex.RelativePoint;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.hint.TooltipEvent;
import consulo.ui.ex.popup.Balloon;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public class IdeTooltip extends ComparableObject.Impl {
    public static final Object TOOLTIP_DISMISS_DELAY_KEY = "TOOLTIP_DISMISS_DELAY_KEY";
    private Component myComponent;
    private Point myPoint;

    private Balloon.Position myPreferredPosition;
    private Balloon.Layer myLayer;

    private JComponent myTipComponent;

    private boolean myToCenter = false;
    private boolean myToCenterIfSmall = true;
    private boolean myHighlighter;
    private boolean myRequestFocus;

    private Color myTextBackground;
    private Color myTextForeground;
    private Color myBorderColor;
    private Insets myBorderInsets;
    private Font myFont;

    private int myCalloutShift = 4;
    private boolean myExplicitClose;

    private int myPositionChangeX;
    private int myPositionChangeY;

    private Ui myUi;

    private boolean myHint = false;
    private Border myComponentBorder = JBUI.Borders.empty(1, 3, 2, 3);

    private Dimension myPointerSize;
    private boolean myPointerShiftedToStart;
    
    public IdeTooltip(Component component, Point point, JComponent tipComponent, Object... identity) {
        super(identity);
        myComponent = component;
        myPoint = point;
        myTipComponent = tipComponent;
        setPreferredPosition(Balloon.Position.above);
    }

    public IdeTooltip setPreferredPosition(Balloon.Position position) {
        myPreferredPosition = position;
        return this;
    }

    public Component getComponent() {
        return myComponent;
    }

    public Point getPoint() {
        return myPoint;
    }

    public RelativePoint getShowingPoint() {
        return myUi != null ? myUi.getShowingPoint() : new RelativePoint(getComponent(), getPoint());
    }

    public Balloon.Position getPreferredPosition() {
        return myPreferredPosition;
    }

    public JComponent getTipComponent() {
        return myTipComponent;
    }

    public IdeTooltip setToCenter(boolean toCenter) {
        myToCenter = toCenter;
        return this;
    }

    public boolean isToCenter() {
        return myToCenter;
    }

    public boolean isToCenterIfSmall() {
        return myToCenterIfSmall;
    }

    public IdeTooltip setToCenterIfSmall(boolean mayCenter) {
        myToCenterIfSmall = mayCenter;
        return this;
    }

    public IdeTooltip setPointerSize(Dimension pointerSize) {
        myPointerSize = pointerSize;
        return this;
    }

    public Dimension getPointerSize() {
        return myPointerSize;
    }

    public boolean isPointerShiftedToStart() {
        return myPointerShiftedToStart;
    }

    public IdeTooltip setPointerShiftedToStart(boolean pointerShiftedToStart) {
        myPointerShiftedToStart = pointerShiftedToStart;
        return this;
    }

    protected boolean canAutohideOn(TooltipEvent event) {
        return true;
    }

    protected void onHidden() {

    }

    protected boolean beforeShow() {
        return true;
    }

    public void hide() {
        IdeTooltipManagerImpl.getInstanceImpl().hide(this);
    }

    public boolean canBeDismissedOnTimeout() {
        return true;
    }

    public int getShowDelay() {
        return myHighlighter ? Registry.intValue("ide.tooltip.initialDelay.highlighter") : Registry.intValue("ide.tooltip.initialDelay");
    }

    public int getInitialReshowDelay() {
        return Registry.intValue("ide.tooltip.initialReshowDelay");
    }

    public int getDismissDelay() {
        if (myComponent instanceof JComponent) {
            final Object value = ((JComponent) myComponent).getClientProperty(TOOLTIP_DISMISS_DELAY_KEY);
            if (value instanceof Integer) {
                return ((Integer) value).intValue();
            }
        }
        return Registry.intValue("ide.tooltip.dismissDelay");
    }

    public IdeTooltip setHighlighterType(boolean isHighlighter) {
        myHighlighter = isHighlighter;
        return this;
    }

    void setTipComponent(JComponent tipComponent) {
        myTipComponent = tipComponent;
    }

    public IdeTooltip setTextBackground(Color textBackground) {
        myTextBackground = textBackground;
        return this;
    }

    public IdeTooltip setTextForeground(Color textForeground) {
        myTextForeground = textForeground;
        return this;
    }

    public IdeTooltip setBorderColor(Color borderColor) {
        myBorderColor = borderColor;
        return this;
    }

    public IdeTooltip setBorderInsets(Insets insets) {
        myBorderInsets = insets;
        return this;
    }

    public Color getTextBackground() {
        return myTextBackground;
    }

    public Font getFont() {
        return myFont;
    }

    public Color getTextForeground() {
        return myTextForeground;
    }

    public Color getBorderColor() {
        return myBorderColor;
    }

    public Insets getBorderInsets() {
        return myBorderInsets;
    }

    public Border getComponentBorder() {
        return myComponentBorder;
    }

    public IdeTooltip setComponentBorder(@Nullable Border value) {
        myComponentBorder = value;
        return this;
    }

    public IdeTooltip setFont(Font font) {
        myFont = font;
        return this;
    }

    public int getCalloutShift() {
        return myCalloutShift;
    }

    public IdeTooltip setCalloutShift(int calloutShift) {
        myCalloutShift = calloutShift;
        return this;
    }

    public void setComponent(Component component) {
        myComponent = component;
    }

    public void setPoint(Point point) {
        myPoint = point;
    }

    public IdeTooltip setExplicitClose(boolean explicitClose) {
        myExplicitClose = explicitClose;
        return this;
    }

    public boolean isExplicitClose() {
        return myExplicitClose;
    }

    public IdeTooltip setPositionChangeShift(int positionChangeX, int positionChangeY) {
        myPositionChangeX = positionChangeX;
        myPositionChangeY = positionChangeY;
        return this;
    }

    public int getPositionChangeX() {
        return myPositionChangeX;
    }

    public int getPositionChangeY() {
        return myPositionChangeY;
    }

    public void setUi(Ui ui) {
        myUi = ui;
    }

    public IdeTooltip setLayer(Balloon.Layer layer) {
        myLayer = layer;
        return this;
    }

    public Balloon.Layer getLayer() {
        return myLayer;
    }

    public IdeTooltip setHint(boolean hint) {
        this.myHint = hint;
        return this;
    }

    public boolean isHint() {
        return myHint;
    }

    public boolean isInside(RelativePoint target) {
        return myUi != null && myUi.isInside(target);
    }

    public boolean isRequestFocus() {
        return myRequestFocus;
    }

    public IdeTooltip setRequestFocus(boolean requestFocus) {
        myRequestFocus = requestFocus;
        return this;
    }

    public interface Ui {
        boolean isInside(RelativePoint target);

        RelativePoint getShowingPoint();
    }
}

