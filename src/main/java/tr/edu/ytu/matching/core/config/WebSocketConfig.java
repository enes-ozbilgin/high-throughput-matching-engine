package tr.edu.ytu.matching.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Dış dünyanın (Frontend'in) dinleyeceği canlı anons kanalları (Örn: /topic/trades)
        config.enableSimpleBroker("/topic");
        
        // Dış dünyadan motora gelecek mesajların (eğer olursa) ön eki
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // SockJS yedeğini kaldırdık, saf ve ışık hızında WebSocket bağlantısı:
        registry.addEndpoint("/ws-engine")
                .setAllowedOriginPatterns("*"); 
    }
}