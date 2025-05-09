/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package consulo.language.copyright.impl.internal;

import consulo.language.copyright.config.CopyrightProfile;
import consulo.logging.Logger;
import consulo.ui.ex.awt.Messages;
import consulo.util.jdom.JDOMUtil;
import jakarta.annotation.Nullable;
import org.jdom.Document;
import org.jdom.Element;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ExternalOptionHelper {


  @Nullable
  public static List<CopyrightProfile> loadOptions(File file) {
    try {
      List<CopyrightProfile> profiles = new ArrayList<CopyrightProfile>();
      Document doc = JDOMUtil.loadDocument(file);
      Element root = doc.getRootElement();
      if (root.getName().equals("component")) {
        final Element copyrightElement = root.getChild("copyright");
        if (copyrightElement != null) extractNewNoticeAndKeyword(copyrightElement, profiles);
      }
      else {
        List list = root.getChildren("component");
        for (Object element : list) {
          Element component = (Element)element;
          String name = component.getAttributeValue("name");
          if (name.equals("CopyrightManager")) {
            for (Object o : component.getChildren("copyright")) {
              extractNewNoticeAndKeyword((Element)o, profiles);
            }
          }
          else if (name.equals("copyright")) {
            extractNoticeAndKeyword(component, profiles);
          }
        }
      }
      return profiles;
    }
    catch (Exception e) {
      logger.info(e);
      Messages.showErrorDialog(e.getMessage(), "Import Failure");
      return null;
    }
  }

  public static void extractNoticeAndKeyword(Element valueElement, List<CopyrightProfile> profiles) {
    CopyrightProfile profile = new CopyrightProfile();
    boolean extract = false;
    for (Object l : valueElement.getChildren("LanguageOptions")) {
      if (((Element)l).getAttributeValue("name").equals("__TEMPLATE__")) {
        for (Object o1 : ((Element)l).getChildren("option")) {
          extract |= extract(profile, (Element)o1);
        }
        break;
      }
    }
    if (extract) profiles.add(profile);
  }

  public static void extractNewNoticeAndKeyword(Element valueElement, List<CopyrightProfile> profiles) {
    CopyrightProfile profile = new CopyrightProfile();
    boolean extract = false;
    for (Object l : valueElement.getChildren("option")) {
      extract |= extract(profile, (Element)l);
    }
    if (extract) profiles.add(profile);
  }

  private static boolean extract(final CopyrightProfile profile, final Element el) {
    if (el.getAttributeValue("name").equals("notice")) {
      profile.setNotice(el.getAttributeValue("value"));
      return true;
    }
    else if (el.getAttributeValue("name").equals("keyword")) {
      profile.setKeyword(el.getAttributeValue("value"));
    }
    else if (el.getAttributeValue("name").equals("myName")) {
      profile.setName(el.getAttributeValue("value"));
    }
    return false;
  }


  private ExternalOptionHelper() {
  }

  private static final Logger logger = Logger.getInstance(ExternalOptionHelper.class);
}
