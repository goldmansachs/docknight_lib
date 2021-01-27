/*
 *   Copyright 2021 Goldman Sachs.
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

package com.gs.ep.docknight.util;

import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.set.MutableSet;

/**
 * Wrapper over StatsDClient which is used to store and send metrics to statsD daemon.
 */
public class StatsDClientWrapper {

  private static StatsDClient client;
  private static MutableSet<String> storedMetrics = Sets.mutable.empty();

  /**
   * Initiate the statsD connection with host machine if it is not exists already.
   * @param workspaceName DEP workspace name. It is used as prefix in metric name
   * @param serviceName DEP service name. It is used as prefix in metric name
   * @param host hostname for initiating the connection
   * @param port port number for initiating the connection
   */
  public static void initializeClient(String workspaceName, String serviceName, String host,
      String port) {
    if (client == null) {
      client = new NonBlockingStatsDClient(String.format("%s.%s", workspaceName, serviceName), host,
          Integer.valueOf(port));
    } else {
      throw new RuntimeException("StatDClient is a singleton and can not be initialized twice");
    }
  }

  /**
   * Increment the count of {@code metric}
   * @param metric metric name whose count will be incremented
   */
  public static void increment(String metric) {
    if (client != null) {
      client.incrementCounter(metric);
      storedMetrics.add(metric);
    }
  }

  /**
   * Assign the {@code value} to the {@code metric}
   * @param metric metric name
   * @param value value assigned to the metric
   */
  public static void setValue(String metric, int value) {
    if (client != null) {
      client.recordGaugeValue(metric, value);
      storedMetrics.add(metric);
    }
  }

  /**
   * Send all the metrics collected to the statsD daemon
   */
  public static void sendAllMetrics() {
    for (String metric : storedMetrics) {
      sendMetric(metric);
    }
  }

  /**
   * Send the {@code metric} to the statsD daemon
   * @param metric metric which will be sent
   */
  public static void sendMetric(String metric) {
    if (client != null) {
      client.count(metric, 0, 1);
    }
  }

  public static void closeClient() {
    if(client != null){
      client.stop();
    }
  }
}
