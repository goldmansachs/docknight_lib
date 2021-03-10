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

package com.gs.ep.docknight.util;

public final class BetaMode {

  private static boolean enabled = false;
  private static long isEnabledCounter = 0;

  private BetaMode() {
  }

  public static boolean isEnabled() {
    isEnabledCounter++;
    return enabled;
  }

  public static long getIsEnabledCounter() {
    return isEnabledCounter;
  }

  public static synchronized void run(ThrowsRunnable runnable) {
    enabled = true;
    try {
      runnable.run();
    } catch (Exception e) {
      throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
    } finally {
      enabled = false;
    }
  }

  public interface ThrowsRunnable {

    void run() throws Exception;
  }
}
