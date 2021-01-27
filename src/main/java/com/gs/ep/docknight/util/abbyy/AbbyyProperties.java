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

package com.gs.ep.docknight.util.abbyy;

import org.eclipse.collections.impl.factory.Maps;
import java.util.Map;
import java.util.Properties;

/**
 * Class to define configuration for abbyy
 */
public final class AbbyyProperties {

  private static AbbyyProperties abbyyProperties;
  private static Properties properties;

  static {
    Properties properties = new Properties();
    Map<String, String> propertiesToEnvVarMap = Maps.mutable.of(
        "abbyy.customerProjectId", "ABBYY_CUSTOMER_PROJECT_ID",
        "abbyy.abbyyurl", "ABBYY_SERVER_URL",
        "abbyy.unixFolder", "ABBYY_BIN_DIR",
        "abbyy.predefinedProfile", "ABBYY_PREDEFINED_PROFILE");
    for (String property : propertiesToEnvVarMap.keySet()) {
      String envVarValue = System.getenv(propertiesToEnvVarMap.get(property));
      if (envVarValue != null) {
        properties.setProperty(property, envVarValue);
      }
    }
    AbbyyProperties.properties = properties;
  }

  private AbbyyProperties() {
  }

  /**
   * Setter for abbyy properties
   */
  public static synchronized void setAbbyyProperties(Properties properties) {
    AbbyyProperties.properties = properties;
  }

  /**
   * Returns the instance of abbyy properites
   */
  public static synchronized AbbyyProperties getInstance() {
    if (abbyyProperties == null) {
      abbyyProperties = new AbbyyProperties();
    }
    return abbyyProperties;
  }

  /**
   * Returns the abbyy server endpoint
   */
  public String getServerUrl() {
    return this.getProperties().getProperty("abbyy.abbyyurl");
  }

  public Properties getProperties() {
    return properties;
  }
}
