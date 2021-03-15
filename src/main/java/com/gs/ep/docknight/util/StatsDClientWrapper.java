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

import com.timgroup.statsd.NonBlockingStatsDClientBuilder;
import com.timgroup.statsd.StatsDClient;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper over StatsDClient which is used to store and send metrics to statsD daemon.
 */
public class StatsDClientWrapper {

  private static final Logger LOGGER = LoggerFactory.getLogger(StatsDClientWrapper.class);
  private static StatsDClient client;
  private static MutableList<String> tags = Lists.mutable.empty();

  /**
   * Initiate the statsD connection with host machine if it is not exists already.
   * @param prefix name which will be used as prefix in all metric names
   * @param tags labels to associate with the metrics sent by this client
   * @param host hostname for initiating the connection
   * @param port port number for initiating the connection
   */
  public static void initializeClient(String prefix, MutableList<String> tags, String host,
      int port) {
    if (client == null) {
      StatsDClientWrapper.tags = tags;
      client = new NonBlockingStatsDClientBuilder()
              .prefix(prefix)
              .hostname(host)
              .port(port)
              .build();
      LOGGER.info(String.format("StatsDClient initialized successfully on host: %s, port: %d with prefix: %s", host, port, prefix));
    } else {
      throw new RuntimeException("StatDClient is a singleton and can not be initialized twice");
    }
  }

  /**
   * Increment the count of {@code metric}
   * @param metric metric name whose count will be incremented
   * @param delta amount by which increment is performed
   */
  public static void increment(String metric, int delta) {
    if (client != null) {
      client.count(metric, delta, tags.toArray(new String[tags.size()]));
      LOGGER.info(String.format("Incrementing the counter for metric %s by delta %d", metric, delta));
    }
  }

  /**
   * Setter for tags
   */
  public static void setTags(MutableList<String> tags){
    StatsDClientWrapper.tags = tags;
  }

  /**
   * Cleanly shut down this StatsD client.
   */
  public static void closeClient() {
    if(client != null){
      client.stop();
    }
  }
}
