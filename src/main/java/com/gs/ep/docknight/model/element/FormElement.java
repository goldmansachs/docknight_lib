/*
 *   Copyright 2020 Goldman Sachs.
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 */

package com.gs.ep.docknight.model.element;

import org.eclipse.collections.impl.factory.Lists;
import com.gs.ep.docknight.model.ElementAttribute;
import com.gs.ep.docknight.model.Form.FormType;
import com.gs.ep.docknight.model.attribute.FormData;
import java.util.List;

/**
 * Element containing form data
 */
public class FormElement extends InlineElement<FormElement> implements
    FormData.Holder<FormElement> {

  public static final List<Class<? extends ElementAttribute>> LAYOUT = Lists.mutable.of();
  private static final long serialVersionUID = 5487571011700166293L;

  @Override
  public List<Class<? extends ElementAttribute>> getDefaultLayout() {
    return LAYOUT;
  }

  @Override
  public String getTextStr() {
    FormData formData = this.getFormData();
    if (formData == null) {
      // formData will be null only in cases of non-supported FormTypes
      return "";
    }
    FormType formType = formData.getValue().getFormType();
    if (formType.equals(FormType.CheckBox) || formType.equals(FormType.RadioButton)) {
      return String.valueOf(formData.getValue().isChecked());
    }
    return formData.getValue().getValuesString();
  }
}
