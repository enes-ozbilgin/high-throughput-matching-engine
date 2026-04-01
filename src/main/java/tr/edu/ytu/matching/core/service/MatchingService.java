package tr.edu.ytu.matching.core.service;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import tr.edu.ytu.matching.core.model.Order;
import tr.edu.ytu.matching.core.model.OrderSide;
import tr.edu.ytu.matching.core.model.OrderStatus;
import tr.edu.ytu.matching.core.model.OrderType;
import tr.edu.ytu.matching.core.model.Trade;
import tr.edu.ytu.matching.core.repository.OrderRepository;
import tr.edu.ytu.matching.core.repository.TradeRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchingService {

	// Redis'ten veri okumak için
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    
    // Virtual Thread veya Classic Thread kullanacak olan havuzumuz!
    private final java.util.concurrent.ExecutorService executorService;
    
    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // Sistemdeki 10 Farklı İşlem Çifti
    public static final List<String> SYMBOLS = List.of(
            "BTC_USDT", "ETH_USDT", "BNB_USDT", "SOL_USDT", "XRP_USDT",
            "ADA_USDT", "AVAX_USDT", "DOGE_USDT", "DOT_USDT", "LINK_USDT"
    );

    // Her sembolün kendi Emir Defterini ve Kilidini (Lock Striping) tutan harita
    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();

    // İç Sınıf: Bağımsız Emir Defteri
    @Data
    public static class OrderBook {
        private final ReentrantLock lock = new ReentrantLock();
        private final PriorityQueue<Order> buyOrders = new PriorityQueue<>(
                Comparator.comparing(Order::getPrice).reversed().thenComparing(Order::getCreatedAt)
        );
        private final PriorityQueue<Order> sellOrders = new PriorityQueue<>(
                Comparator.comparing(Order::getPrice).thenComparing(Order::getCreatedAt)
        );
    }

    @PostConstruct
    public void init() {
        // 1. Sistem kalkarken 10 pazarın tahtasını ve kilitlerini hazırla
        for (String symbol : SYMBOLS) {
            orderBooks.put(symbol, new OrderBook());
        }
        log.info("🚀 10 Farklı pazar için Lock Striping altyapısı başarıyla kuruldu!");

        // 2. Redis'i dinleyen ve Virtual Thread kullanan işçi (Consumer)
        executorService.submit(() -> {
            log.info("🎧 Redis Emir Dinleyicisi (Consumer) başlatıldı! Kuyruk dinleniyor...");
            
            // JSON metnini Order objesine çevirecek araç (Tarih formatları için modül ekliyoruz)
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

            while (true) {
                try {
                    // DÜZELTME 1: Kuyruk adını tam olarak OrderService'teki gibi yazdık.
                    // DÜZELTME 2: Gelen veriyi bir JSON metni (String) olarak karşıladık.
                	String orderJson = redisTemplate.opsForList().rightPop("engine:order_queue", java.time.Duration.ofSeconds(1));
                    
                    if (orderJson != null) {
                        // DÜZELTME 3: Gelen JSON metnini tekrar Order objesine dönüştürüyoruz
                        Order newOrder = mapper.readValue(orderJson, Order.class);

                        log.info("🔥 Kuyruktan yeni emir kapıldı! İşleniyor -> ID: {}, {} {} {}", 
                                newOrder.getId(), newOrder.getSide(), newOrder.getQuantity(), newOrder.getSymbol());
                        
                        // Emri alıp Lock Striping'li eşleştirme metoduna gönderiyoruz
                        processOrder(newOrder);
                    }
                } catch (Exception e) {
                    // Zaman aşımı durumunda (1 saniyede bir) buraya düşer, döngü devam eder.
                    // log.error("Kuyruk okuma hatası: {}", e.getMessage()); 
                }
            }
        });
    }

    public void processOrder(Order order) {
        OrderBook ob = orderBooks.get(order.getSymbol());
        if (ob == null) {
            log.error("Geçersiz Sembol: {}", order.getSymbol());
            return;
        }

        // 🔒 LOCK STRIPING: Sadece bu sembolün kilidini al! 
        // BTC eşleşirken ETH tahtası hiçbir engelleme olmadan çalışmaya devam eder.
        ob.getLock().lock();
        try {
            if (order.getSide() == OrderSide.BUY) {
                matchBuyOrder(order, ob);
                
                // SADECE LİMİT EMİRLERİ TAHTAYA YAZILABİLİR
                if (order.getType() == OrderType.LIMIT && order.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                    ob.getBuyOrders().add(order);
                }
            } else if (order.getSide() == OrderSide.SELL) {
                matchSellOrder(order, ob);
                
                // SADECE LİMİT EMİRLERİ TAHTAYA YAZILABİLİR
                if (order.getType() == OrderType.LIMIT && order.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                    ob.getSellOrders().add(order);
                }
            }
        } finally {
            // Kilidi ne olursa olsun serbest bırak
            ob.getLock().unlock();
        }
    }

    private void matchBuyOrder(Order buyOrder, OrderBook ob) {
        // Emrin piyasa emri olup olmadığını kontrol et
        boolean isMarket = buyOrder.getType() == OrderType.MARKET;
        
        while (buyOrder.getQuantity().compareTo(BigDecimal.ZERO) > 0 && !ob.getSellOrders().isEmpty()) {
            Order bestSellOrder = ob.getSellOrders().peek();
            
            // PİYASA emriyse fiyata bakmadan eşleşir, LİMİT emriyse fiyatın uygun olması gerekir
            if (isMarket || buyOrder.getPrice().compareTo(bestSellOrder.getPrice()) >= 0) {
                BigDecimal tradeQuantity = buyOrder.getQuantity().min(bestSellOrder.getQuantity());
                
                // İşlem fiyatı her zaman tahtada bekleyen (maker) emrin fiyatı olur
                executeTrade(bestSellOrder, buyOrder, tradeQuantity, bestSellOrder.getPrice());

                buyOrder.setQuantity(buyOrder.getQuantity().subtract(tradeQuantity));
                bestSellOrder.setQuantity(bestSellOrder.getQuantity().subtract(tradeQuantity));

                if (bestSellOrder.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                    bestSellOrder.setStatus(OrderStatus.FILLED);
                    ob.getSellOrders().poll();
                } else {
                    bestSellOrder.setStatus(OrderStatus.PARTIALLY_FILLED);
                }

                if (buyOrder.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                    buyOrder.setStatus(OrderStatus.FILLED);
                } else {
                    buyOrder.setStatus(OrderStatus.PARTIALLY_FILLED);
                }
            } else {
                break;
            }
        }
    }

    private void matchSellOrder(Order sellOrder, OrderBook ob) {
        // Emrin piyasa emri olup olmadığını kontrol et
        boolean isMarket = sellOrder.getType() == OrderType.MARKET;
        
        while (sellOrder.getQuantity().compareTo(BigDecimal.ZERO) > 0 && !ob.getBuyOrders().isEmpty()) {
            Order bestBuyOrder = ob.getBuyOrders().peek();
            
            // PİYASA emriyse fiyata bakmadan eşleşir, LİMİT emriyse fiyatın uygun olması gerekir
            if (isMarket || sellOrder.getPrice().compareTo(bestBuyOrder.getPrice()) <= 0) {
                BigDecimal tradeQuantity = sellOrder.getQuantity().min(bestBuyOrder.getQuantity());

                // İşlem fiyatı her zaman tahtada bekleyen (maker) emrin fiyatı olur
                executeTrade(bestBuyOrder, sellOrder, tradeQuantity, bestBuyOrder.getPrice());

                sellOrder.setQuantity(sellOrder.getQuantity().subtract(tradeQuantity));
                bestBuyOrder.setQuantity(bestBuyOrder.getQuantity().subtract(tradeQuantity));

                if (bestBuyOrder.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                    bestBuyOrder.setStatus(OrderStatus.FILLED);
                    ob.getBuyOrders().poll();
                } else {
                    bestBuyOrder.setStatus(OrderStatus.PARTIALLY_FILLED);
                }

                if (sellOrder.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                    sellOrder.setStatus(OrderStatus.FILLED);
                } else {
                    sellOrder.setStatus(OrderStatus.PARTIALLY_FILLED);
                }
            } else {
                break;
            }
        }
    }

    private void executeTrade(Order makerOrder, Order takerOrder, BigDecimal tradeQuantity, BigDecimal tradePrice) {
        Trade trade = Trade.builder()
                .symbol(makerOrder.getSymbol())
                .makerOrderId(makerOrder.getId())
                .takerOrderId(takerOrder.getId())
                .price(tradePrice)
                .quantity(tradeQuantity)
                .executedAt(Instant.now())
                .build();

        tradeRepository.save(trade);
        orderRepository.save(makerOrder);
        orderRepository.save(takerOrder);

        // Anonsu yaparken sembolü de kanala ekliyoruz ki frontend doğru kanalı dinlesin
        messagingTemplate.convertAndSend("/topic/trades/" + makerOrder.getSymbol(), trade);
    }

    // Arayüz için spesifik bir sembolün tahta fotoğrafını çeken metot
    public Map<String, Object> getOrderBookSnapshot(String symbol) {
        OrderBook ob = orderBooks.get(symbol);
        if (ob == null) return Map.of("bids", List.of(), "asks", List.of());

        Map<String, Object> snapshot = new java.util.HashMap<>();
        
        // Okuma yaparken anlık kilit almak verinin tutarlılığını garanti eder
        ob.getLock().lock();
        try {
            snapshot.put("bids", ob.getBuyOrders().stream()
                    .sorted(Comparator.comparing(Order::getPrice).reversed())
                    .toList());
            snapshot.put("asks", ob.getSellOrders().stream()
                    .sorted(Comparator.comparing(Order::getPrice))
                    .toList());
        } finally {
            ob.getLock().unlock();
        }
        return snapshot;
    }
}