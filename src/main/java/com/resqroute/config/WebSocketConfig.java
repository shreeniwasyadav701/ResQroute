package com.resqroute.config;

import com.resqroute.websocket.LiveUpdateHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final LiveUpdateHandler liveUpdateHandler;

    public WebSocketConfig(LiveUpdateHandler liveUpdateHandler) {
        this.liveUpdateHandler = liveUpdateHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(liveUpdateHandler, "/ws").setAllowedOrigins("*");
    }
}
