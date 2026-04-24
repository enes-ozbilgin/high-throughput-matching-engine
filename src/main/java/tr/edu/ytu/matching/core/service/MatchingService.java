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
import java.util.ArrayList;
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

    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final java.util.concurrent.ExecutorService executorService;
    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public static final List<String> SYMBOLS = List.of(
            "BTC_USDT", "ETH_USDT", "BNB_USDT", "SOL_USDT", "XRP_USDT",
            "ADA_USDT", "AVAX_USDT", "DOGE_USDT", "DOT_USDT", "LINK_USDT"
    );

    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();

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

    // YENİ: Eşleşen işlemleri kilit dışına taşımak için veri tutucu (Record)
    private record TradeResult(Trade trade, Order maker, Order taker) {}

    @PostConstruct
    public void init() {
        for (String symbol : SYMBOLS) {
            orderBooks.put(symbol, new OrderBook());
        }
        log.debug("🚀 10 Farklı pazar için Lock Striping altyapısı başarıyla kuruldu!");
        log.debug("🎧 Redis Emir Dinleyicileri (20 Virtual Thread) başlatılıyor...");
        
        for (int i = 0; i < 20; i++) {
            executorService.submit(() -> {
                while (true) {
                    try {
                        String orderJson = redisTemplate.opsForList().rightPop("engine:order_queue", java.time.Duration.ofSeconds(1));
                        if (orderJson != null) {
                            Order newOrder = objectMapper.readValue(orderJson, Order.class);
                            processOrder(newOrder);
                        }
                    } catch (Exception e) {
                        // Zaman aşımı sessiz geçiş
                    }
                }
            });
        }
    }

    public void processOrder(Order order) {
        OrderBook ob = orderBooks.get(order.getSymbol());
        if (ob == null) {
            log.error("Geçersiz Sembol: {}", order.getSymbol());
            return;
        }

        // OPTİMİZASYON 1: İşlemleri kilit içindeyken yayınlamak yerine bu listede biriktiriyoruz
        List<TradeResult> pendingTrades = new ArrayList<>();

        ob.getLock().lock();
        try {
            if (order.getSide() == OrderSide.BUY) {
                matchBuyOrder(order, ob, pendingTrades);
                
                if (order.getType() == OrderType.LIMIT && order.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                    ob.getBuyOrders().add(order);
                }
            } else if (order.getSide() == OrderSide.SELL) {
                matchSellOrder(order, ob, pendingTrades);
                
                if (order.getType() == OrderType.LIMIT && order.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                    ob.getSellOrders().add(order);
                }
            }
        } finally {
            // Kilidi MÜMKÜN OLAN EN KISA SÜREDE aç! Ağ işlemlerini bekleme.
            ob.getLock().unlock();
        }

        // OPTİMİZASYON 2: Ağ (WebSocket) ve Veritabanı işlemleri kilit AÇILDIKTAN SONRA yapılır.
        // Artık bu işlemler diğer thread'lerin BTC pazarında işlem yapmasını engellemiyor!
        for (TradeResult result : pendingTrades) {
            processExecutedTrade(result);
        }
    }

    private void matchBuyOrder(Order buyOrder, OrderBook ob, List<TradeResult> pendingTrades) {
        boolean isMarket = buyOrder.getType() == OrderType.MARKET;
        
        while (buyOrder.getQuantity().compareTo(BigDecimal.ZERO) > 0 && !ob.getSellOrders().isEmpty()) {
            Order bestSellOrder = ob.getSellOrders().peek();
            
            if (isMarket || buyOrder.getPrice().compareTo(bestSellOrder.getPrice()) >= 0) {
                BigDecimal tradeQuantity = buyOrder.getQuantity().min(bestSellOrder.getQuantity());
                
                // İşlemi hemen fırlatmak yerine listeye ekliyoruz
                Trade trade = buildTradeObj(bestSellOrder, buyOrder, tradeQuantity, bestSellOrder.getPrice());
                pendingTrades.add(new TradeResult(trade, bestSellOrder, buyOrder));

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

    private void matchSellOrder(Order sellOrder, OrderBook ob, List<TradeResult> pendingTrades) {
        boolean isMarket = sellOrder.getType() == OrderType.MARKET;
        
        while (sellOrder.getQuantity().compareTo(BigDecimal.ZERO) > 0 && !ob.getBuyOrders().isEmpty()) {
            Order bestBuyOrder = ob.getBuyOrders().peek();
            
            if (isMarket || sellOrder.getPrice().compareTo(bestBuyOrder.getPrice()) <= 0) {
                BigDecimal tradeQuantity = sellOrder.getQuantity().min(bestBuyOrder.getQuantity());

                Trade trade = buildTradeObj(bestBuyOrder, sellOrder, tradeQuantity, bestBuyOrder.getPrice());
                pendingTrades.add(new TradeResult(trade, bestBuyOrder, sellOrder));

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

    private Trade buildTradeObj(Order makerOrder, Order takerOrder, BigDecimal tradeQuantity, BigDecimal tradePrice) {
        return Trade.builder()
                .symbol(makerOrder.getSymbol())
                .makerOrderId(makerOrder.getId())
                .takerOrderId(takerOrder.getId())
                .price(tradePrice)
                .quantity(tradeQuantity)
                .executedAt(Instant.now())
                .build();
    }

    private void processExecutedTrade(TradeResult result) {
        // Bu metot artık KİLİT DIŞINDA çalıştığı için istediği kadar yavaş olabilir.
        messagingTemplate.convertAndSend("/topic/trades/" + result.trade().getSymbol(), result.trade());

        executorService.submit(() -> {
            try {
                // Eşleşme (Trade) işlemi benzersiz olduğu için klasik save ile kaydedilebilir
                tradeRepository.save(result.trade());

                // 🚀 Order'lar için RACE-CONDITION önleyici UPSERT kullanıyoruz:
                Order maker = result.maker();
                orderRepository.upsertOrder(
                        maker.getId(), maker.getUserId(), maker.getSymbol(), 
                        maker.getSide().name(), maker.getType().name(), maker.getPrice(), 
                        maker.getQuantity(), maker.getStatus().name(), maker.getCreatedAt()
                );

                Order taker = result.taker();
                orderRepository.upsertOrder(
                        taker.getId(), taker.getUserId(), taker.getSymbol(), 
                        taker.getSide().name(), taker.getType().name(), taker.getPrice(), 
                        taker.getQuantity(), taker.getStatus().name(), taker.getCreatedAt()
                );
            } catch (Exception e) {
                // Upsert sayesinde artık buraya hata düşmeyecek, ama yine de sigorta olarak kalsın.
                log.warn("Beklenmeyen DB Kayıt Hatası: {}", e.getMessage());
            }
        });
    }

    public Map<String, Object> getOrderBookSnapshot(String symbol) {
        OrderBook ob = orderBooks.get(symbol);
        if (ob == null) return Map.of("bids", List.of(), "asks", List.of());

        Map<String, Object> snapshot = new java.util.HashMap<>();
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
    
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 500)
    public void broadcastQueueDepth() {
        try {
            Long queueSize = redisTemplate.opsForList().size("engine:order_queue");
            if (queueSize == null) queueSize = 0L;
            messagingTemplate.convertAndSend("/topic/metrics/queue", queueSize);
        } catch (Exception e) {
        }
    }
}