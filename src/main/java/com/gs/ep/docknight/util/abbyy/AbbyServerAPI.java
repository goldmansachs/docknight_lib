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

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abbyy api uses abbyy engine using an http end point
 */
public class AbbyServerAPI implements AbbyyAPI {

  protected static final Logger LOGGER = LoggerFactory.getLogger(AbbyServerAPI.class);

  public AbbyServerAPI() throws Exception {
    String serverUrl = AbbyyProperties.getInstance().getServerUrl();
    URL siteURL = new URL(serverUrl);
    HttpURLConnection connection = (HttpURLConnection) siteURL.openConnection();
    connection.setRequestMethod("GET");
    connection.setConnectTimeout(3000);
    try {
      connection.connect();
    } catch (Exception e) {
      throw new RuntimeException("Couldn't connect to Abbyy server. Server url: " + serverUrl, e);
    }
    if (connection.getResponseCode() != 200) {
      throw new RuntimeException(
          "Abbyy service didn't respond with OK status. Service url: " + serverUrl);
    }
  }

  @Override
  public InputStream convertPdf(InputStream input, AbbyyParams abbyyParams) {
    String serverUrl = AbbyyProperties.getInstance().getServerUrl();
    Client client = ClientBuilder.newClient();
    WebTarget webTarget = client.target(serverUrl);
    FormDataMultiPart multiPart = new FormDataMultiPart();
    multiPart.field("params", abbyyParams, MediaType.APPLICATION_JSON_TYPE);
    multiPart.bodyPart(new StreamDataBodyPart("file", input, "file.pdf"));
    Response response = webTarget.request(MediaType.APPLICATION_JSON_TYPE)
        .post(Entity.entity(multiPart, multiPart.getMediaType()));
    if (Status.OK.getStatusCode() != response.getStatus()) {
      throw new RuntimeException(response.getStatusInfo().toString());
    }
    return response.readEntity(InputStream.class);
  }
}
