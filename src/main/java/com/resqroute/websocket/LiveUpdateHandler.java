package com.resqroute.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Minimal broadcast WebSocket endpoint at /ws. Dashboards subscribe here and
 * refresh live when incidents, traffic or units change.
 */
@Component
public class LiveUpdateHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(LiveUpdateHandler.class);

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.debug("WebSocket connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        sessions.remove(session);
    }

    public void broadcast(String payload) {
        TextMessage message = new TextMessage(payload);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(message);
                    }
                } catch (IOException e) {
                    log.warn("WebSocket send failed for {}: {}", session.getId(), e.getMessage());
                }
            }
        }
    }

    public int connectedClients() {
        return sessions.size();
    }
}
