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

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.factory.Maps;
import java.util.Set;

/**
 * Class to represent customizations that can change behaviour of {@see Parser} or {@see
 * Transformer}
 */
public class ModelCustomizations {

  private final MutableMap<ModelCustomizationKey, Object> customizationsMap = Maps.mutable.empty();

  /**
   * Add the customization
   *
   * @param key key of the customization
   * @param value value of the customization
   * @return this object
   */
  public <V> ModelCustomizations add(ModelCustomizationKey<V> key, V value) {
    this.customizationsMap.put(key, value);
    return this;
  }

  /**
   * Parse the {@code rawData} and add it as value in customizationsMap
   *
   * @param key key of the customization
   * @param rawData raw data of the customization whose parsed data will be saved as value of the
   * customization
   * @return this object
   */
  public <V> ModelCustomizations parseAndAdd(ModelCustomizationKey<V> key,
      MutableList<String> rawData) {
    return this.add(key, key.parse(rawData));
  }

  /**
   * Remove the {@code key} from this object
   *
   * @param key key of the customization that will be removed
   */
  public <V> void remove(ModelCustomizationKey<V> key) {
    this.customizationsMap.remove(key);
  }

  /**
   * Retrieve the value corresponding to customization {@code key} if it exists, otherwise, retrieve
   * the {@code defaultValue}
   *
   * @param key - key of the customization
   * @param defaultValue value to return if no value found for the {@code key}
   * @return value corresponding to {@code key} if it exists, else {@code defaultValue}
   */
  public <V> V retrieveOrDefault(ModelCustomizationKey<V> key, V defaultValue) {
    return (V) this.customizationsMap.getOrDefault(key, defaultValue);
  }

  /**
   * Return all the keys stored in this object
   *
   * @return keys stored in this object
   */
  public Set<ModelCustomizationKey> getStoredKeys() {
    return this.customizationsMap.keySet();
  }

  /**
   * Add other customization object to this object and override the value if common key exists
   *
   * @param modelCustomizations customization object that will be added to this object
   * @return modified customization object
   */
  public ModelCustomizations combineAndOverrideWith(ModelCustomizations modelCustomizations) {
    ModelCustomizations combinedModelCustomizations = new ModelCustomizations();
    combinedModelCustomizations.customizationsMap.putAll(this.customizationsMap);
    combinedModelCustomizations.customizationsMap.putAll(modelCustomizations.customizationsMap);
    return combinedModelCustomizations;
  }

  /**
   * @return string representation of customization object
   */
  @Override
  public String toString() {
    return this.customizationsMap.isEmpty() ? "NONE"
        : this.customizationsMap.keyValuesView()
            .collect(keyValuePair -> keyValuePair.getOne().getName() + "=" + keyValuePair.getTwo())
            .makeString();
  }
}
