package tr.edu.ytu.matching.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tr.edu.ytu.matching.core.model.Order;
import tr.edu.ytu.matching.core.model.OrderSide;
import tr.edu.ytu.matching.core.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserId(String userId);

    List<Order> findBySymbolAndSideAndStatusOrderByPriceAscCreatedAtAsc(
            String symbol, OrderSide side, OrderStatus status);

    List<Order> findBySymbolAndSideAndStatusOrderByPriceDescCreatedAtAsc(
            String symbol, OrderSide side, OrderStatus status);

    // 🚀 HFT UPSERT: Aynı anda gelen emirleri çakıştırmadan kaydeder veya günceller
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO orders (id, user_id, symbol, side, type, price, quantity, status, created_at) " +
                   "VALUES (:id, :userId, :symbol, :side, :type, :price, :quantity, :status, :createdAt) " +
                   "ON CONFLICT (id) DO UPDATE SET quantity = EXCLUDED.quantity, status = EXCLUDED.status",
           nativeQuery = true)
    void upsertOrder(Long id, String userId, String symbol, String side, String type, BigDecimal price, BigDecimal quantity, String status, Instant createdAt);
}