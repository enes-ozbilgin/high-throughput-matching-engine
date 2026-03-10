package tr.edu.ytu.matching.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.edu.ytu.matching.core.model.Order;
import tr.edu.ytu.matching.core.model.OrderStatus;
import tr.edu.ytu.matching.core.repository.OrderRepository;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    
    // Redis ile konuşmamızı sağlayacak araç
    private final StringRedisTemplate redisTemplate;
    
    // Java objemizi (Order) JSON metnine çevirecek araç
    private final ObjectMapper objectMapper;

    // Redis'teki kuyruğumuzun adı
    private static final String ORDER_QUEUE = "engine:order_queue";

    @Transactional
    public Order createOrder(Order incomingOrder) {
        
        incomingOrder.setStatus(OrderStatus.PENDING);
        incomingOrder.setCreatedAt(Instant.now());

        // 1. PostgreSQL'e Kaydet (Kalıcı Yedek)
        Order savedOrder = orderRepository.save(incomingOrder);
        log.info("Yeni emir DB'ye kaydedildi -> ID: {}", savedOrder.getId());

        // 2. Redis'e Fırlat (Hızlı İşlem İçin)
        try {
            // Emri JSON formatına çeviriyoruz
            String orderJson = objectMapper.writeValueAsString(savedOrder);
            
            // Redis'teki "engine:order_queue" listesinin en soluna (başına) ekliyoruz
            redisTemplate.opsForList().leftPush(ORDER_QUEUE, orderJson);
            
            log.info("Emir ışık hızında Redis kuyruğuna fırlatıldı! 🚀");
            
        } catch (JsonProcessingException e) {
            log.error("Emir Redis'e gönderilirken JSON hatası oluştu!", e);
        }

        return savedOrder;
    }
}