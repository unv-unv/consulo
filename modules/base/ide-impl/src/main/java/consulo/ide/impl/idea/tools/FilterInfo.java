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

package consulo.ide.impl.idea.tools;

import consulo.util.lang.Comparing;
import consulo.util.xml.serializer.JDOMExternalizable;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.Iterator;

/**
 * @author dyoma
 */
public class FilterInfo implements JDOMExternalizable {
  @NonNls private static final String FILTER_NAME = "NAME";
  @NonNls private static final String FILTER_DESCRIPTION = "DESCRIPTION";
  @NonNls private static final String FILTER_REGEXP = "REGEXP";

  private String myName = ToolsBundle.message("tools.filters.name.default");
  private String myDescription;
  private String myRegExp;
  @NonNls private static final String ELEMENT_OPTION = "option";
  @NonNls private static final String ATTRIBUTE_VALUE = "value";
  @NonNls private static final String ATTRIBUTE_NAME = "name";

  public FilterInfo() {
  }

  public FilterInfo(String regExp, String name, String description) {
    myRegExp = regExp;
    myName = name;
    myDescription = description;
  }

  public String getDescription() {
    return myDescription;
  }

  public void setDescription(String description) {
    myDescription = description;
  }

  public String getName() {
    return myName;
  }

  public void setName(String name) {
    myName = name;
  }

  public String getRegExp() {
    return myRegExp;
  }

  public void setRegExp(String regExp) {
    myRegExp = regExp;
  }

  public int hashCode() {
    return Comparing.hashcode(myName) +
           Comparing.hashcode(myDescription) +
           Comparing.hashcode(myRegExp);
  }

  public boolean equals(Object object) {
    if (!(object instanceof FilterInfo)) return false;
    FilterInfo other = (FilterInfo)object;
    return Comparing.equal(myName, other.myName) &&
           Comparing.equal(myDescription, other.myDescription) &&
           Comparing.equal(myRegExp, other.myRegExp);
  }

  public FilterInfo createCopy() {
    return new FilterInfo(myRegExp, myName, myDescription);
  }

  @Override
  public void readExternal(Element element) {
    for (Iterator i2 = element.getChildren(ELEMENT_OPTION).iterator(); i2.hasNext(); ) {
      Element optionElement = (Element)i2.next();
      String value = optionElement.getAttributeValue(ATTRIBUTE_VALUE);
      String name = optionElement.getAttributeValue(ATTRIBUTE_NAME);

      if (FILTER_NAME.equals(name)) {
        if (value != null) {
          myName = convertString(value);
        }
      }
      if (FILTER_DESCRIPTION.equals(name)) {
        myDescription = convertString(value);
      }
      if (FILTER_REGEXP.equals(name)) {
        myRegExp = convertString(value);
      }
    }
  }

  @Override
  public void writeExternal(Element filterElement) {
    Element option = new Element(ELEMENT_OPTION);
    filterElement.addContent(option);
    option.setAttribute(ATTRIBUTE_NAME, FILTER_NAME);
    if (myName != null) {
      option.setAttribute(ATTRIBUTE_VALUE, myName);
    }

    option = new Element(ELEMENT_OPTION);
    filterElement.addContent(option);
    option.setAttribute(ATTRIBUTE_NAME, FILTER_DESCRIPTION);
    if (myDescription != null) {
      option.setAttribute(ATTRIBUTE_VALUE, myDescription);
    }

    option = new Element(ELEMENT_OPTION);
    filterElement.addContent(option);
    option.setAttribute(ATTRIBUTE_NAME, FILTER_REGEXP);
    if (myRegExp != null) {
      option.setAttribute(ATTRIBUTE_VALUE, myRegExp);
    }
  }

  public static String convertString(String s) {
    return ToolManager.convertString(s);
  }
}
