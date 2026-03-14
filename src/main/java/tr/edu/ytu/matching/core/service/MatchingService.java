package tr.edu.ytu.matching.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tr.edu.ytu.matching.core.model.Order;
import tr.edu.ytu.matching.core.model.OrderSide;
import tr.edu.ytu.matching.core.model.OrderStatus;
import tr.edu.ytu.matching.core.model.Trade;
import tr.edu.ytu.matching.core.repository.OrderRepository;
import tr.edu.ytu.matching.core.repository.TradeRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private static final String ORDER_QUEUE = "engine:order_queue";
    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;

    // 1. ALIM EMİRLERİ DEFTERİ (Önce en yüksek fiyat, eşitse en eski zaman)
    private final PriorityQueue<Order> buyOrders = new PriorityQueue<>(
            Comparator.comparing(Order::getPrice).reversed()
                      .thenComparing(Order::getCreatedAt)
    );

    // 2. SATIM EMİRLERİ DEFTERİ (Önce en düşük fiyat, eşitse en eski zaman)
    private final PriorityQueue<Order> sellOrders = new PriorityQueue<>(
            Comparator.comparing(Order::getPrice)
                      .thenComparing(Order::getCreatedAt)
    );

    // Spring Boot tamamen ayağa kalktığında bu metot otomatik tetiklenir
    @EventListener(ApplicationReadyEvent.class)
    public void startMatchingEngine() {
        log.info("Eşleştirme Motoru (Matching Engine) uykudan uyandı ve Redis kuyruğunu dinlemeye başladı...");

        // Gelen API isteklerini (8080 portunu) kilitlememek için dinleme işlemini arka plan iş parçacığına (Thread) atıyoruz
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            
            // Motor çalıştığı sürece dönecek olan sonsuz döngü (Kuyruk Dinleyici)
            while (true) {
                try {
                    // Kuyruğun sağından (en eskisinden) emri çek. Eğer kuyruk boşsa 1 saniye bekle, sonra tekrar bak (BPOP mantığı).
                    String orderJson = redisTemplate.opsForList().rightPop(ORDER_QUEUE, Duration.ofSeconds(1));

                    if (orderJson != null) {
                        // Gelen JSON metnini tekrar bizim Java'daki Order objemize çeviriyoruz
                        Order incomingOrder = objectMapper.readValue(orderJson, Order.class);
                        
                        log.info("🔥 Kuyruktan yeni emir kapıldı! İşleniyor -> ID: {}, {} {} {}", 
                            incomingOrder.getId(), incomingOrder.getSide(), incomingOrder.getQuantity(), incomingOrder.getSymbol());

                        // Asıl eşleştirme algoritmasını başlat
                        processOrder(incomingOrder);
                    }
                } catch (Exception e) {
                    log.error("Kuyruktan emir çekilirken kritik bir hata oluştu: ", e);
                }
            }
        });
    }

    private void processOrder(Order order) {
        if (order.getSide() == OrderSide.BUY) {
            buyOrders.add(order);
            log.info("🟢 Alım emri tahtaya yazıldı. Tahtadaki toplam alıcı sayısı: {}", buyOrders.size());
            
            matchBuyOrder(order); 
            
        } else if (order.getSide() == OrderSide.SELL) {
            sellOrders.add(order);
            log.info("🔴 Satım emri tahtaya yazıldı. Tahtadaki toplam satıcı sayısı: {}", sellOrders.size());
            
            // TODO: Eşleşme var mı diye Alış tahtasını kontrol et
            matchSellOrder(order);
        }
    }
    
    private void matchBuyOrder(Order buyOrder) {
        
        // Alıcının almak istediği miktar sıfırdan büyük olduğu sürece VE satıcı tahtası boş olmadığı sürece dön
        while (buyOrder.getQuantity().compareTo(BigDecimal.ZERO) > 0 && !sellOrders.isEmpty()) {
            
            // Satıcı tahtasındaki EN UCUZ teklife bak (peek metodu kuyruktan silmeden sadece bakar)
            Order bestSellOrder = sellOrders.peek();
            
            // Alıcının teklif ettiği fiyat, satıcının istediği fiyata eşit veya ondan büyük mü?
            if (buyOrder.getPrice().compareTo(bestSellOrder.getPrice()) >= 0) {
                
                // EŞLEŞME YAKALANDI! 🚀
                // Ne kadar işlem yapılacak? (Alıcının istediği ile satıcının elindeki miktardan hangisi küçükse o kadar)
                BigDecimal tradeQuantity = buyOrder.getQuantity().min(bestSellOrder.getQuantity());
                
                log.info("💥 EŞLEŞME YAKALANDI! Alıcı ID: {}, Satıcı ID: {}, Fiyat: {}, Miktar: {}", 
                        buyOrder.getUserId(), bestSellOrder.getUserId(), bestSellOrder.getPrice(), tradeQuantity);
                
                // 1. Miktarları hesaplayıp düşüyoruz
                buyOrder.setQuantity(buyOrder.getQuantity().subtract(tradeQuantity));
                bestSellOrder.setQuantity(bestSellOrder.getQuantity().subtract(tradeQuantity));
                
                // 2. Satıcının elindeki miktar tamamen bittiyse onu tahtadan (kuyruktan) atıyoruz
                if (bestSellOrder.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                    bestSellOrder.setStatus(OrderStatus.FILLED);
                    sellOrders.poll(); // poll() metodu emri kuyruktan tamamen siler
                } else {
                    bestSellOrder.setStatus(OrderStatus.PARTIALLY_FILLED);
                }
                
                // 3. Alıcının durumu güncelleniyor
                if (buyOrder.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                    buyOrder.setStatus(OrderStatus.FILLED);
                } else {
                    buyOrder.setStatus(OrderStatus.PARTIALLY_FILLED);
                }
                // Maker: Tahtadaki satıcı (bestSellOrder), Taker: Gelen alıcı (buyOrder)
                saveTradeAndUpdateOrders(bestSellOrder, buyOrder, tradeQuantity, bestSellOrder.getPrice());
                
            } else {
                // Alıcının parası artık sıradaki en ucuz satıcıya bile yetmiyorsa döngüyü kırıp çıkıyoruz
                break;
            }
        }
    }
    private void matchSellOrder(Order sellOrder) {
        
        // Satıcının satmak istediği miktar sıfırdan büyük olduğu sürece VE alıcı tahtası boş olmadığı sürece dön
        while (sellOrder.getQuantity().compareTo(BigDecimal.ZERO) > 0 && !buyOrders.isEmpty()) {
            
            // Alıcı tahtasındaki EN PAHALI teklife bak (peek)
            Order bestBuyOrder = buyOrders.peek();
            
            // Satıcının istediği fiyat, alıcının teklif ettiği fiyata eşit veya ondan küçük mü? (Yani alıcı satıcının istediği parayı veriyor mu?)
            if (sellOrder.getPrice().compareTo(bestBuyOrder.getPrice()) <= 0) {
                
                // EŞLEŞME YAKALANDI! 🚀
                BigDecimal tradeQuantity = sellOrder.getQuantity().min(bestBuyOrder.getQuantity());
                
                log.info("💥 EŞLEŞME YAKALANDI! Satıcı ID: {}, Alıcı ID: {}, Fiyat: {}, Miktar: {}", 
                        sellOrder.getUserId(), bestBuyOrder.getUserId(), bestBuyOrder.getPrice(), tradeQuantity);
                
                // 1. Miktarları düşüyoruz
                sellOrder.setQuantity(sellOrder.getQuantity().subtract(tradeQuantity));
                bestBuyOrder.setQuantity(bestBuyOrder.getQuantity().subtract(tradeQuantity));
                
                // 2. Alıcının elindeki miktar tamamen bittiyse onu tahtadan (kuyruktan) atıyoruz
                if (bestBuyOrder.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                    bestBuyOrder.setStatus(OrderStatus.FILLED);
                    buyOrders.poll(); 
                } else {
                    bestBuyOrder.setStatus(OrderStatus.PARTIALLY_FILLED);
                }
                
                // 3. Satıcının durumu güncelleniyor
                if (sellOrder.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                    sellOrder.setStatus(OrderStatus.FILLED);
                } else {
                    sellOrder.setStatus(OrderStatus.PARTIALLY_FILLED);
                }
                // Maker: Tahtadaki alıcı (bestBuyOrder), Taker: Gelen satıcı (sellOrder)
                saveTradeAndUpdateOrders(bestBuyOrder, sellOrder, tradeQuantity, bestBuyOrder.getPrice());
                
            } else {
                // Tahtadaki en yüksek teklif bile satıcının istediği fiyata ulaşmıyorsa döngüyü kır
                break;
            }
        }
    }
    
    private void saveTradeAndUpdateOrders(Order makerOrder, Order takerOrder, BigDecimal tradeQuantity, BigDecimal tradePrice) {
        
        // 1. Dekontu (Trade) Oluştur ve Kaydet
        Trade trade = Trade.builder()
                .symbol(makerOrder.getSymbol())
                .makerOrderId(makerOrder.getId()) // Tahtada bekleyen
                .takerOrderId(takerOrder.getId()) // Gelen
                .price(tradePrice) // İşlem fiyatı her zaman Maker'ın (tahtada bekleyenin) fiyatından gerçekleşir
                .quantity(tradeQuantity)
                .executedAt(Instant.now())
                .build();
                
        tradeRepository.save(trade);
        log.info("💾 İşlem (Trade) başarıyla DB'ye kaydedildi! Dekont ID: {}", trade.getId());

        // 2. Emirlerin yeni miktarlarını ve durumlarını (PARTIALLY_FILLED veya FILLED) DB'de güncelle
        orderRepository.save(makerOrder);
        orderRepository.save(takerOrder);
    }
}