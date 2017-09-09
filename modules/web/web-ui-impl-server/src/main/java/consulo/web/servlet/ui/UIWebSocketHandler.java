/*
 * Copyright 2013-2016 consulo.io
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
package consulo.web.servlet.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import consulo.web.gwt.shared.UIClientEvent;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;

/**
 * @author VISTALL
 * @since 09-Jun-16
 */
@ServerEndpoint(value = "/ws")
public class UIWebSocketHandler {
  public static final ObjectMapper ourObjectMapper = new ObjectMapper();

  @OnOpen
  public void onOpen(Session session) {
    System.out.println("Connected ... " + session.getId());
  }

  @OnMessage
  public void onMessage(String message, Session session) {
    UIClientEvent clientEvent = decode(message);

    switch (clientEvent.getType()) {
      case sessionOpen:
        UISessionManager.ourInstance.onSessionOpen(session, clientEvent);
        break;
      case invokeEvent:
        UISessionManager.ourInstance.onInvokeEvent(session, clientEvent);
        break;
    }
  }

  private static UIClientEvent decode(String data) {
    try {
      return ourObjectMapper.readValue(data, UIClientEvent.class);
    }
    catch (Exception e) {
      e.printStackTrace();
      throw new Error(e);
    }
  }

  @OnClose
  public void onClose(Session session, CloseReason closeReason) {
    UISessionManager.ourInstance.onClose(session);
  }
}
