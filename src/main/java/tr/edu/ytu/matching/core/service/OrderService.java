package tr.edu.ytu.matching.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tr.edu.ytu.matching.core.model.Order;
import tr.edu.ytu.matching.core.model.OrderStatus;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    
    // MİKRO-OPTİMİZASYON: Tüm thread'lerin kilitlenmeden, ışık hızında benzersiz ID 
    // alabilmesi için donanım seviyesinde (CAS) çalışan atomik bir sayaç.
    private final AtomicLong orderIdCounter = new AtomicLong(System.currentTimeMillis());

    public Order createOrder(Order incomingOrder) {
        incomingOrder.setStatus(OrderStatus.PENDING);
        incomingOrder.setCreatedAt(Instant.now());
        
        // Math.random() yerine Atomic sayacımızı kullanıyoruz. Asla çakışma ve bekleme olmaz!
        if (incomingOrder.getId() == null) {
            incomingOrder.setId(orderIdCounter.incrementAndGet());
        }

        try {
            // DB'yi HİÇ BEKLEMEDEN, emri doğrudan Redis otobanına fırlatıyoruz!
            redisTemplate.opsForList().leftPush("engine:order_queue", objectMapper.writeValueAsString(incomingOrder));
            
            // Eğer saniyede 10.000 işlem yapıyorsan, bu debug logunu bile kapatmak
            // performansı ekstra %2-3 artıracaktır. Şimdilik kalabilir.
            log.debug("Emir ışık hızında Redis kuyruğuna fırlatıldı! 🚀");
        } catch (JsonProcessingException e) {
            log.error("JSON Hatası", e);
        }

        return incomingOrder;
    }
}