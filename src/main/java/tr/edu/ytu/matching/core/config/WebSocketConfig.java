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
        // Dış dünyanın (Örn: React/Vue uygulamasının) bizim motora bağlanmak için gireceği ana kapı (Handshake adresi)
        registry.addEndpoint("/ws-engine")
                .setAllowedOriginPatterns("*") // Geliştirme aşamasında her yerden bağlantıya izin ver
                .withSockJS(); // Bağlantı koparsa veya tarayıcı desteklemezse otomatik yedekleme sağlar
    }
}