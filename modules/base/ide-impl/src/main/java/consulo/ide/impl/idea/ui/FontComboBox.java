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
package consulo.ide.impl.idea.ui;

import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.localize.ApplicationLocalize;
import consulo.ui.ex.awt.ComboBox;
import consulo.ui.ex.awt.FontInfo;
import consulo.util.lang.Comparing;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Sergey.Malenkov
 */
public final class FontComboBox extends ComboBox {
  private static final FontInfoRenderer RENDERER = new FontInfoRenderer();

  private Model myModel;

  public FontComboBox() {
    this(false, true, false);
  }

  public FontComboBox(boolean withAllStyles, boolean filterNonLatin, boolean noFontItem) {
    super(new Model(withAllStyles, filterNonLatin, noFontItem));
    setRenderer(RENDERER);
  }

  public boolean isMonospacedOnly() {
    return myModel.myMonospacedOnly;
  }

  public void setMonospacedOnly(boolean monospaced) {
    if (myModel.myMonospacedOnly != monospaced) {
      myModel.myMonospacedOnly = monospaced;
      Object item = myModel.getSelectedItem();
      myModel.setSelectedItem(null);
      myModel.setSelectedItem(item);
    }
  }

  public String getFontName() {
    Object item = myModel.getSelectedItem();
    return item == null ? null : item.toString();
  }

  public void setFontName(String item) {
    myModel.setSelectedItem(item);
  }

  @Override
  public void setModel(ComboBoxModel model) {
    if (model instanceof Model) {
      myModel = (Model)model;
      super.setModel(model);
    }
    else {
      throw new UnsupportedOperationException();
    }
  }

  private static final class Model extends AbstractListModel implements ComboBoxModel {
    private final NoFontItem myNoFontItem;
    private volatile List<FontInfo> myAllFonts = Collections.emptyList();
    private volatile List<FontInfo> myMonoFonts = Collections.emptyList();
    private boolean myMonospacedOnly;
    private Object mySelectedItem;

    private Model(boolean withAllStyles, boolean filterNonLatin, boolean noFontItem) {
      myNoFontItem = noFontItem ? new NoFontItem() : null;
      Application application = ApplicationManager.getApplication();
      if (application == null || application.isUnitTestMode()) {
        setFonts(FontInfo.getAll(withAllStyles), filterNonLatin);
      }
      else {
        application.executeOnPooledThread(() -> {
          List<FontInfo> all = FontInfo.getAll(withAllStyles);
          application.invokeLater(() -> {
            setFonts(all, filterNonLatin);
            updateSelectedItem();
          }, application.getAnyModalityState());
        });
      }
    }

    private void setFonts(List<FontInfo> all, boolean filterNonLatin) {
      List<FontInfo> allFonts = new ArrayList<>(all.size());
      List<FontInfo> monoFonts = new ArrayList<>();
      for (FontInfo info : all) {
        if (!filterNonLatin || info.getFont().canDisplayUpTo(info.toString()) == -1) {
          allFonts.add(info);
          if (info.isMonospaced()) {
            monoFonts.add(info);
          }
        }
      }
      myAllFonts = allFonts;
      myMonoFonts = monoFonts;
    }

    private void updateSelectedItem() {
      Object item = getSelectedItem();
      setSelectedItem(null);
      setSelectedItem(item);
    }

    @Override
    public Object getSelectedItem() {
      return mySelectedItem;
    }

    @Override
    public void setSelectedItem(Object item) {
      if (item == null && myNoFontItem != null) {
        item = myNoFontItem;
      }
      else {
        if (item instanceof FontInfo) {
          FontInfo info = getInfo(item);
          if (info == null) {
            List<FontInfo> list = myMonospacedOnly ? myMonoFonts : myAllFonts;
            item = list.isEmpty() ? null : list.get(0);
          }
        }
        if (item instanceof String) {
          FontInfo info = getInfo(item);
          if (info != null) item = info;
        }
      }
      if (!Comparing.equal(mySelectedItem, item) || item == myNoFontItem) {
        mySelectedItem = item;
        fireContentsChanged(this, -1, -1);
      }
    }

    @Override
    public int getSize() {
      List<FontInfo> list = myMonospacedOnly ? myMonoFonts : myAllFonts;
      return mySelectedItem instanceof String ? 1 + list.size() : list.size();
    }

    @Override
    public Object getElementAt(int index) {
      List<FontInfo> list = myMonospacedOnly ? myMonoFonts : myAllFonts;
      return 0 <= index && index < list.size() ? list.get(index) : mySelectedItem;
    }

    private FontInfo getInfo(Object item) {
      for (FontInfo info : myMonospacedOnly ? myMonoFonts : myAllFonts) {
        if (item instanceof String ? info.toString().equalsIgnoreCase((String)item) : info.equals(item)) {
          return info;
        }
      }
      return null;
    }

    private final static class NoFontItem {
      @Override
      public String toString() {
        return ApplicationLocalize.settingsEditorFontNone().get();
      }
    }
  }
}
