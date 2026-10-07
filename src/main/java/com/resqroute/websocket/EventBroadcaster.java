package com.resqroute.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns domain events into JSON frames for the live dashboards.
 */
@Service
public class EventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(EventBroadcaster.class);

    private final LiveUpdateHandler handler;
    private final ObjectMapper objectMapper;

    public EventBroadcaster(LiveUpdateHandler handler, ObjectMapper objectMapper) {
        this.handler = handler;
        this.objectMapper = objectMapper;
    }

    public void incident(Object payload) {
        send("INCIDENT", payload);
    }

    public void traffic(Object payload) {
        send("TRAFFIC", payload);
    }

    public void unit(Object payload) {
        send("UNIT", payload);
    }

    public void routeRefresh(Object payload) {
        send("ROUTE_REFRESH", payload);
    }

    private void send(String type, Object payload) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", type);
        frame.put("payload", payload);
        frame.put("timestamp", Instant.now().toString());
        try {
            handler.broadcast(objectMapper.writeValueAsString(frame));
        } catch (JsonProcessingException e) {
            log.warn("Could not serialise broadcast frame: {}", e.getMessage());
        }
    }
}
