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

package com.gs.ep.docknight.model;

import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Sets;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class is used to store form information like checkbox, signature field, radio button etc.
 */
public class Form implements Serializable {

  public static final String IS_MULTILINE = "isMultiLine";
  public static final String IS_MULTSELECT = "isMultiSelect";
  private static final long serialVersionUID = -3855036022583861836L;
  private final FormType formType;
  private final List<String> options; //Possible values that the form field could have
  private final List<String> displayOptions; //Display text for each of the possible values
  private final Set<String> onValues; //Combination of values, which when selected, will the form field be considered "on"
  private final List<String> values; //The selected values among "options"
  private final Map<String, Boolean> flags;
  public Form(FormType formType, Iterable<String> options, Iterable<String> displayOptions,
      Iterable<String> onValues, Iterable<String> values, Map<String, Boolean> flags) {
    this.formType = formType;
    this.options = options == null ? null : Lists.mutable.ofAll(options);
    this.displayOptions = displayOptions == null ? null : Lists.mutable.ofAll(displayOptions);
    this.onValues = onValues == null ? null : Sets.mutable.ofAll(onValues);
    this.values = values == null ? null : Lists.mutable.ofAll(values);
    this.flags = flags;
  }

  public FormType getFormType() {
    return this.formType;
  }

  public List<String> getOptions() {
    return this.options;
  }

  public List<String> getDisplayOptions() {
    return this.displayOptions;
  }

  public Set<String> getOnValues() {
    return this.onValues;
  }

  public boolean isChecked() {
    return this.onValues == null || this.onValues.contains(this.getValue());
  }

  public boolean isEmpty() {
    return this.values.isEmpty() || (this.values.size() == 1 && this.values.get(0).isEmpty());
  }

  public String getValue() {
    return this.values.isEmpty() ? null : this.values.get(0);
  }

  public boolean isButton() {
    return this.formType.equals(FormType.CheckBox) || this.formType.equals(FormType.RadioButton);
  }

  public boolean isChoice() {
    return this.formType.equals(FormType.ListBox) || this.formType.equals(FormType.ComboBox);
  }

  public List<String> getValues() {
    return this.values;
  }

  public Map<String, Boolean> getFlags() {
    return this.flags;
  }

  public boolean getFlag(String flagName, boolean defaultValue) {
    return this.flags == null ? defaultValue
        : this.flags.containsKey(flagName) ? this.flags.get(flagName) : defaultValue;
  }

  public String getValuesString() {
    return String.join(",", this.values);
  }

  @Override
  public String toString() {
    return this.getValuesString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || this.getClass() != o.getClass()) {
      return false;
    }

    Form form = (Form) o;

    if (this.formType != form.formType) {
      return false;
    }
    if (this.options == null ? form.options != null : !this.options.equals(form.options)) {
      return false;
    }
    if (this.displayOptions == null ? form.displayOptions != null
        : !this.displayOptions.equals(form.displayOptions)) {
      return false;
    }
    if (this.onValues == null ? form.onValues != null : !this.onValues.equals(form.onValues)) {
      return false;
    }
    if (!this.values.equals(form.values)) {
      return false;
    }
    return this.flags == null ? form.flags == null : this.flags.equals(form.flags);
  }

  @Override
  public int hashCode() {
    int result = this.formType.hashCode();
    result = 31 * result + (this.options == null ? 0 : this.options.hashCode());
    result = 31 * result + (this.displayOptions == null ? 0 : this.displayOptions.hashCode());
    result = 31 * result + (this.onValues == null ? 0 : this.onValues.hashCode());
    result = 31 * result + this.values.hashCode();
    result = 31 * result + (this.flags == null ? 0 : this.flags.hashCode());
    return result;
  }

  public enum FormType {
    CheckBox,
    ComboBox,
    ListBox,
    RadioButton,
    SignatureField,
    TextField
  }
}
