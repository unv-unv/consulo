// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.ui.ex.awt.speedSearch;

import consulo.application.AllIcons;
import consulo.application.ApplicationManager;
import consulo.application.ui.UISettings;
import consulo.application.util.matcher.MatcherTextRange;
import consulo.application.util.matcher.NameUtilCore;
import consulo.dataContext.DataManager;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.externalService.statistic.FeatureUsageTracker;
import consulo.logging.Logger;
import consulo.platform.Platform;
import consulo.project.Project;
import consulo.project.ui.wm.ToolWindowManager;
import consulo.project.ui.wm.ToolWindowManagerListener;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.Gray;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.CustomShortcutSet;
import consulo.ui.ex.awt.CustomLineBorder;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import kava.beans.PropertyChangeListener;
import kava.beans.PropertyChangeSupport;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.*;
import java.util.ListIterator;
import java.util.NoSuchElementException;

public abstract class SpeedSearchBase<Comp extends JComponent> extends SpeedSearchSupply {
    private static final Logger LOG = Logger.getInstance(SpeedSearchBase.class);

    private static final Border BORDER =
        new CustomLineBorder(JBColor.namedColor("SpeedSearch.borderColor", JBColor.LIGHT_GRAY), JBUI.insets(1));
    private static final Color FOREGROUND_COLOR = JBColor.namedColor("SpeedSearch.foreground", UIUtil.getToolTipForeground());
    private static final Color BACKGROUND_COLOR = JBColor.namedColor("SpeedSearch.background", new JBColor(Gray.xFF, Gray._111));
    private static final Color ERROR_FOREGROUND_COLOR = JBColor.namedColor("SpeedSearch.errorForeground", JBColor.RED);

    private SearchPopup mySearchPopup;
    private JLayeredPane myPopupLayeredPane;
    protected final Comp myComponent;
    private final ToolWindowManagerListener myWindowManagerListener = new MyToolWindowManagerListener();
    private final PropertyChangeSupport myChangeSupport = new PropertyChangeSupport(this);
    private String myRecentEnteredPrefix;
    private SpeedSearchComparator myComparator = new SpeedSearchComparator(false);
    private boolean myClearSearchOnNavigateNoMatch;

    private Disposable myListenerDisposable;

    public SpeedSearchBase(Comp component) {
        myComponent = component;

        myComponent.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentHidden(ComponentEvent event) {
                manageSearchPopup(null);
            }

            @Override
            public void componentMoved(ComponentEvent event) {
                moveSearchPopup();
            }

            @Override
            public void componentResized(ComponentEvent event) {
                moveSearchPopup();
            }
        });
        myComponent.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                manageSearchPopup(null);
            }
        });
        myComponent.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                processKeyEvent(e);
            }

            @Override
            public void keyPressed(KeyEvent e) {
                processKeyEvent(e);
            }
        });

        new AnAction() {
            @Override
            @RequiredUIAccess
            public void actionPerformed(@Nonnull AnActionEvent e) {
                final String prefix = getEnteredPrefix();
                assert prefix != null;
                final String[] strings = NameUtilCore.splitNameIntoWords(prefix);
                final String last = strings[strings.length - 1];
                final int i = prefix.lastIndexOf(last);
                mySearchPopup.mySearchField.setText(prefix.substring(0, i).trim());
            }

            @Override
            public void update(@Nonnull AnActionEvent e) {
                e.getPresentation().setEnabled(isPopupActive() && !StringUtil.isEmpty(getEnteredPrefix()));
            }
        }.registerCustomShortcutSet(
            CustomShortcutSet.fromString(Platform.current().os().isMac() ? "meta BACK_SPACE" : "control BACK_SPACE"),
            myComponent
        );

        installSupplyTo(component);
    }

    @Nullable
    public JTextField getSearchField() {
        if (mySearchPopup != null) {
            return mySearchPopup.mySearchField;
        }
        return null;
    }

    public static boolean hasActiveSpeedSearch(JComponent component) {
        return getSupply(component) != null;
    }

    public void setClearSearchOnNavigateNoMatch(boolean clearSearchOnNavigateNoMatch) {
        myClearSearchOnNavigateNoMatch = clearSearchOnNavigateNoMatch;
    }

    @Override
    public boolean isPopupActive() {
        return mySearchPopup != null && mySearchPopup.isVisible();
    }

    @Override
    public Iterable<MatcherTextRange> matchingFragments(@Nonnull String text) {
        if (!isPopupActive()) {
            return null;
        }
        final SpeedSearchComparator comparator = getComparator();
        final String recentSearchText = comparator.getRecentSearchText();
        if (StringUtil.isNotEmpty(recentSearchText)) {
            return comparator.matchingFragments(recentSearchText, text);
        }
        else {
            return null;
        }
    }

    /**
     * Returns visual (view) selection index.
     */
    protected abstract int getSelectedIndex();

    @Nonnull
    protected abstract Object[] getAllElements();

    @Nullable
    protected abstract String getElementText(Object element);

    protected int getElementCount() {
        return getAllElements().length;
    }

    /**
     * Should convert given view index to model index
     */
    protected int convertIndexToModel(final int viewIndex) {
        return viewIndex;
    }

    /**
     * @param element      Element to select. Don't forget to convert model index to view index if needed (i.e. table.convertRowIndexToView(modelIndex), etc).
     * @param selectedText search text
     */
    protected abstract void selectElement(Object element, String selectedText);

    @Nonnull
    protected ListIterator<Object> getElementIterator(int startingIndex) {
        return new ViewIterator(this, startingIndex < 0 ? getElementCount() : startingIndex);
    }

    @Override
    public void addChangeListener(@Nonnull PropertyChangeListener listener) {
        myChangeSupport.addPropertyChangeListener(listener);
    }

    @Override
    public void removeChangeListener(@Nonnull PropertyChangeListener listener) {
        myChangeSupport.removePropertyChangeListener(listener);
    }

    private void fireStateChanged() {
        String enteredPrefix = getEnteredPrefix();
        myChangeSupport.firePropertyChange(ENTERED_PREFIX_PROPERTY_NAME, myRecentEnteredPrefix, enteredPrefix);
        myRecentEnteredPrefix = enteredPrefix;
    }

    protected boolean isMatchingElement(Object element, String pattern) {
        String str = getElementText(element);
        return str != null && compare(str, pattern);
    }

    protected boolean compare(@Nonnull String text, @Nullable String pattern) {
        return pattern != null && myComparator.matchingFragments(pattern, text) != null;
    }

    public SpeedSearchComparator getComparator() {
        return myComparator;
    }

    public void setComparator(final SpeedSearchComparator comparator) {
        myComparator = comparator;
    }

    @Nullable
    private Object findNextElement(String s) {
        final int selectedIndex = getSelectedIndex();
        final ListIterator<?> it = getElementIterator(selectedIndex + 1);
        final Object current;
        if (it.hasPrevious()) {
            current = it.previous();
            it.next();
        }
        else {
            current = null;
        }
        final String _s = s.trim();
        while (it.hasNext()) {
            final Object element = it.next();
            if (isMatchingElement(element, _s)) {
                return element;
            }
        }

        if (UISettings.getInstance().getCycleScrolling()) {
            final ListIterator<Object> i = getElementIterator(0);
            while (i.hasNext()) {
                final Object element = i.next();
                if (isMatchingElement(element, _s)) {
                    return element;
                }
            }
        }

        return current != null && isMatchingElement(current, _s) ? current : null;
    }

    @Nullable
    private Object findPreviousElement(String s) {
        final int selectedIndex = getSelectedIndex();
        if (selectedIndex < 0) {
            return null;
        }
        final ListIterator<?> it = getElementIterator(selectedIndex);
        final Object current;
        if (it.hasNext()) {
            current = it.next();
            it.previous();
        }
        else {
            current = null;
        }
        final String _s = s.trim();
        while (it.hasPrevious()) {
            final Object element = it.previous();
            if (isMatchingElement(element, _s)) {
                return element;
            }
        }

        if (UISettings.getInstance().getCycleScrolling()) {
            final ListIterator<Object> i = getElementIterator(getElementCount());
            while (i.hasPrevious()) {
                final Object element = i.previous();
                if (isMatchingElement(element, _s)) {
                    return element;
                }
            }
        }

        return isMatchingElement(current, _s) ? current : null;
    }

    @Nullable
    protected Object findElement(String s) {
        int selectedIndex = getSelectedIndex();
        if (selectedIndex < 0) {
            selectedIndex = 0;
        }
        final ListIterator<Object> it = getElementIterator(selectedIndex);
        final String _s = s.trim();
        while (it.hasNext()) {
            final Object element = it.next();
            if (isMatchingElement(element, _s)) {
                return element;
            }
        }
        if (selectedIndex > 0) {
            while (it.hasPrevious()) it.previous();
            while (it.hasNext() && it.nextIndex() != selectedIndex) {
                final Object element = it.next();
                if (isMatchingElement(element, _s)) {
                    return element;
                }
            }
        }
        return null;
    }

    @Nullable
    private Object findFirstElement(String s) {
        final String _s = s.trim();
        for (ListIterator<?> it = getElementIterator(0); it.hasNext(); ) {
            final Object element = it.next();
            if (isMatchingElement(element, _s)) {
                return element;
            }
        }
        return null;
    }

    @Nullable
    private Object findLastElement(String s) {
        final String _s = s.trim();
        for (ListIterator<?> it = getElementIterator(-1); it.hasPrevious(); ) {
            final Object element = it.previous();
            if (isMatchingElement(element, _s)) {
                return element;
            }
        }
        return null;
    }

    public void showPopup(String searchText) {
        manageSearchPopup(new SearchPopup(searchText));
    }

    public void showPopup() {
        showPopup("");
    }

    public void hidePopup() {
        manageSearchPopup(null);
    }

    protected void processKeyEvent(KeyEvent e) {
        if (e.isAltDown()) {
            return;
        }
        if (e.isShiftDown() && isNavigationKey(e.getKeyCode())) {
            return;
        }
        if (mySearchPopup != null) {
            mySearchPopup.processKeyEvent(e);
            return;
        }
        if (!isSpeedSearchEnabled()) {
            return;
        }
        if (e.getID() == KeyEvent.KEY_TYPED) {
            if (!UIUtil.isReallyTypedEvent(e)) {
                return;
            }

            char c = e.getKeyChar();
            if (Character.isLetterOrDigit(c) || !Character.isWhitespace(c) && SpeedSearch.PUNCTUATION_MARKS.indexOf(c) != -1) {
                manageSearchPopup(new SearchPopup(String.valueOf(c)));
                e.consume();
            }
        }
    }

    public Comp getComponent() {
        return myComponent;
    }

    protected boolean isSpeedSearchEnabled() {
        return true;
    }

    @Override
    @Nullable
    public String getEnteredPrefix() {
        return mySearchPopup != null ? mySearchPopup.mySearchField.getText() : null;
    }

    @Override
    public void refreshSelection() {
        if (mySearchPopup != null) {
            mySearchPopup.refreshSelection();
        }
    }

    @Override
    public void findAndSelectElement(@Nonnull String searchQuery) {
        selectElement(findElement(searchQuery), searchQuery);
    }

    public boolean adjustSelection(int keyCode, @Nonnull String searchQuery) {
        if (isUpDownHomeEnd(keyCode)) {
            Object element = findTargetElement(keyCode, searchQuery);
            if (element != null) {
                selectElement(element, searchQuery);
                return true;
            }
        }
        return false;
    }

    @Nullable
    private Object findTargetElement(int keyCode, String searchPrefix) {
        if (keyCode == KeyEvent.VK_UP) {
            return findPreviousElement(searchPrefix);
        }
        else if (keyCode == KeyEvent.VK_DOWN) {
            return findNextElement(searchPrefix);
        }
        else if (keyCode == KeyEvent.VK_HOME) {
            return findFirstElement(searchPrefix);
        }
        else {
            assert keyCode == KeyEvent.VK_END;
            return findLastElement(searchPrefix);
        }
    }

    private class SearchPopup extends JPanel {
        private final SearchField mySearchField;

        SearchPopup(String initialString) {
            mySearchField = new SearchField();
            final JLabel searchLabel = new JBLabel(AllIcons.Actions.Search);
            searchLabel.setBorder(JBUI.Borders.empty(0, 2));
            searchLabel.setFont(searchLabel.getFont().deriveFont(Font.BOLD));
            searchLabel.setForeground(FOREGROUND_COLOR);
            mySearchField.setBorder(null);
            mySearchField.setBackground(BACKGROUND_COLOR);
            mySearchField.setForeground(FOREGROUND_COLOR);

            mySearchField.setDocument(new PlainDocument() {
                @Override
                public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
                    String oldText;
                    try {
                        oldText = getText(0, getLength());
                    }
                    catch (BadLocationException e1) {
                        oldText = "";
                    }

                    String newText = oldText.substring(0, offs) + str + oldText.substring(offs);
                    super.insertString(offs, str, a);
                    if (findElement(newText) == null) {
                        mySearchField.setForeground(ERROR_FOREGROUND_COLOR);
                    }
                    else {
                        mySearchField.setForeground(FOREGROUND_COLOR);
                    }
                }
            });
            mySearchField.setText(initialString);

            setBorder(BORDER);
            setBackground(BACKGROUND_COLOR);
            setLayout(new BorderLayout());
            add(searchLabel, BorderLayout.WEST);
            add(mySearchField, BorderLayout.EAST);
            Object element = findElement(mySearchField.getText());
            onSearchFieldUpdated(initialString);
            updateSelection(element);
        }

        @Override
        public void processKeyEvent(KeyEvent e) {
            mySearchField.processKeyEvent(e);
            if (e.isConsumed()) {
                String s = mySearchField.getText();
                onSearchFieldUpdated(s);
                int keyCode = e.getKeyCode();
                Object element;
                if (isUpDownHomeEnd(keyCode)) {
                    element = findTargetElement(keyCode, s);
                    if (myClearSearchOnNavigateNoMatch && element == null) {
                        manageSearchPopup(null);
                        element = findTargetElement(keyCode, "");
                    }
                }
                else {
                    element = findElement(s);
                }
                updateSelection(element);
            }
        }

        void refreshSelection() {
            findAndSelectElement(mySearchField.getText());
        }

        private void updateSelection(Object element) {
            if (element != null) {
                selectElement(element, mySearchField.getText());
                mySearchField.setForeground(FOREGROUND_COLOR);
            }
            else {
                mySearchField.setForeground(ERROR_FOREGROUND_COLOR);
            }
            if (mySearchPopup != null) {
                mySearchPopup.setSize(mySearchPopup.getPreferredSize());
                mySearchPopup.validate();
            }

            fireStateChanged();
        }
    }

    protected void onSearchFieldUpdated(String pattern) {
    }

    private class SearchField extends JTextField {
        SearchField() {
            setFocusable(false);
        }

        @Override
        public void setForeground(Color color) {
            super.setForeground(color);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension dim = super.getPreferredSize();
            Insets m = getMargin();
            dim.width = getFontMetrics(getFont()).stringWidth(getText()) + 10 + m.left + m.right;
            return dim;
        }

        /**
         * I made this method public in order to be able to call it from the outside.
         * This is needed for delegating calls.
         */
        @Override
        public void processKeyEvent(KeyEvent e) {
            int i = e.getKeyCode();
            if (i == KeyEvent.VK_BACK_SPACE && getDocument().getLength() == 0) {
                e.consume();
                return;
            }
            if (i == KeyEvent.VK_ENTER || i == KeyEvent.VK_ESCAPE || i == KeyEvent.VK_PAGE_UP || i == KeyEvent.VK_PAGE_DOWN || i == KeyEvent.VK_LEFT || i == KeyEvent.VK_RIGHT) {
                manageSearchPopup(null);
                if (i == KeyEvent.VK_ESCAPE) {
                    e.consume();
                }
                return;
            }

            if (isUpDownHomeEnd(i)) {
                e.consume();
                return;
            }

            super.processKeyEvent(e);
            if (i == KeyEvent.VK_BACK_SPACE) {
                e.consume();
            }
        }
    }

    private static boolean isUpDownHomeEnd(int keyCode) {
        return keyCode == KeyEvent.VK_HOME || keyCode == KeyEvent.VK_END || keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_DOWN;
    }

    private static boolean isPgUpPgDown(int keyCode) {
        return keyCode == KeyEvent.VK_PAGE_UP || keyCode == KeyEvent.VK_PAGE_DOWN;
    }

    private static boolean isNavigationKey(int keyCode) {
        return isPgUpPgDown(keyCode) || isUpDownHomeEnd(keyCode);
    }

    private void manageSearchPopup(@Nullable SearchPopup searchPopup) {
        Project project = null;
        if (ApplicationManager.getApplication() != null && !ApplicationManager.getApplication().isDisposed()) {
            project = DataManager.getInstance().getDataContext(myComponent).getData(Project.KEY);
        }
        if (project != null && project.isDefault()) {
            project = null;
        }
        if (mySearchPopup != null) {
            if (myPopupLayeredPane != null) {
                myPopupLayeredPane.remove(mySearchPopup);
                myPopupLayeredPane.validate();
                myPopupLayeredPane.repaint();
                myPopupLayeredPane = null;
            }

            if (myListenerDisposable != null) {
                Disposer.dispose(myListenerDisposable);
                myListenerDisposable = null;
            }
        }
        else if (searchPopup != null) {
            FeatureUsageTracker.getInstance().triggerFeatureUsed("ui.tree.speedsearch");
        }

        mySearchPopup = myComponent.isShowing() ? searchPopup : null;

        fireStateChanged();

        //select here!

        if (mySearchPopup == null || !myComponent.isDisplayable()) {
            return;
        }

        if (project != null) {
            myListenerDisposable = Disposable.newDisposable();
            project.getMessageBus().connect(myListenerDisposable).subscribe(ToolWindowManagerListener.class, myWindowManagerListener);
        }
        JRootPane rootPane = myComponent.getRootPane();
        myPopupLayeredPane = rootPane == null ? null : rootPane.getLayeredPane();
        if (myPopupLayeredPane == null) {
            LOG.error(this + " in " + myComponent);
            return;
        }
        myPopupLayeredPane.add(mySearchPopup, JLayeredPane.POPUP_LAYER);
        moveSearchPopup();

        mySearchPopup.refreshSelection();
    }

    private void moveSearchPopup() {
        if (myComponent == null || mySearchPopup == null || myPopupLayeredPane == null) {
            return;
        }
        Point lPaneP = myPopupLayeredPane.getLocationOnScreen();
        Point componentP = getComponentLocationOnScreen();
        Rectangle r = getComponentVisibleRect();
        Dimension prefSize = mySearchPopup.getPreferredSize();
        Window window = (Window) SwingUtilities.getAncestorOfClass(Window.class, myComponent);
        Point windowP;
        if (window instanceof JDialog) {
            windowP = ((JDialog) window).getContentPane().getLocationOnScreen();
        }
        else if (window instanceof JFrame) {
            windowP = ((JFrame) window).getContentPane().getLocationOnScreen();
        }
        else {
            windowP = window.getLocationOnScreen();
        }
        int y = r.y + componentP.y - lPaneP.y - prefSize.height;
        y = Math.max(y, windowP.y - lPaneP.y);
        mySearchPopup.setLocation(componentP.x - lPaneP.x + r.x, y);
        mySearchPopup.setSize(prefSize);
        mySearchPopup.setVisible(true);
        mySearchPopup.validate();
    }

    protected Rectangle getComponentVisibleRect() {
        return myComponent.getVisibleRect();
    }

    protected Point getComponentLocationOnScreen() {
        return myComponent.getLocationOnScreen();
    }

    private class MyToolWindowManagerListener implements ToolWindowManagerListener {
        @Override
        public void stateChanged(ToolWindowManager toolWindowManager) {
            manageSearchPopup(null);
        }
    }

    protected class ViewIterator implements ListIterator<Object> {
        private final SpeedSearchBase mySpeedSearch;
        private int myCurrentIndex;
        private final Object[] myElements;

        ViewIterator(@Nonnull final SpeedSearchBase speedSearch, final int startIndex) {
            mySpeedSearch = speedSearch;
            myCurrentIndex = startIndex;
            myElements = speedSearch.getAllElements();

            if (startIndex < 0 || startIndex > myElements.length) {
                throw new IndexOutOfBoundsException("Index: " + startIndex + " in: " + SpeedSearchBase.this.getClass());
            }
        }

        @Override
        public boolean hasPrevious() {
            return myCurrentIndex != 0;
        }

        @Override
        public Object previous() {
            final int i = myCurrentIndex - 1;
            if (i < 0) {
                throw new NoSuchElementException();
            }
            final Object previous = myElements[mySpeedSearch.convertIndexToModel(i)];
            myCurrentIndex = i;
            return previous;
        }

        @Override
        public int nextIndex() {
            return myCurrentIndex;
        }

        @Override
        public int previousIndex() {
            return myCurrentIndex - 1;
        }

        @Override
        public boolean hasNext() {
            return myCurrentIndex != myElements.length;
        }

        @Override
        public Object next() {
            if (myCurrentIndex + 1 > myElements.length) {
                throw new NoSuchElementException();
            }
            return myElements[mySpeedSearch.convertIndexToModel(myCurrentIndex++)];
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Not implemented in: " + getClass().getCanonicalName());
        }

        @Override
        public void set(Object o) {
            throw new UnsupportedOperationException("Not implemented in: " + getClass().getCanonicalName());
        }

        @Override
        public void add(Object o) {
            throw new UnsupportedOperationException("Not implemented in: " + getClass().getCanonicalName());
        }
    }
}
