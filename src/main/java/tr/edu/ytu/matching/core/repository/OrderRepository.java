package tr.edu.ytu.matching.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tr.edu.ytu.matching.core.model.Order;
import tr.edu.ytu.matching.core.model.OrderSide;
import tr.edu.ytu.matching.core.model.OrderStatus;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    // 1. Belirli bir kullanıcının emirlerini getirmek için
    List<Order> findByUserId(String userId);

    // 2. Fiyat-Zaman Önceliği (Price-Time Priority) - SATIŞ emirleri için (En ucuz ve en eski emir önce gelir)
    List<Order> findBySymbolAndSideAndStatusOrderByPriceAscCreatedAtAsc(
            String symbol, OrderSide side, OrderStatus status);

    // 3. Fiyat-Zaman Önceliği - ALIŞ emirleri için (En pahalı ve en eski emir önce gelir)
    List<Order> findBySymbolAndSideAndStatusOrderByPriceDescCreatedAtAsc(
            String symbol, OrderSide side, OrderStatus status);
}