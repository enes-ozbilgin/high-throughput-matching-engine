package tr.edu.ytu.matching.core.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "orders", indexes = {
	    // 2. ve 3. sorguların (Fiyat-Zaman önceliği) ışık hızında çalışması için kompozit indeks:
	    @Index(name = "idx_order_matching", columnList = "symbol, side, status, price, createdAt"),
	    // Kullanıcının kendi emirlerini hızlıca görebilmesi için:
	    @Index(name = "idx_order_userid", columnList = "userId")
	})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    // DİKKAT: @GeneratedValue KALDIRILDI! ID'yi artık OrderService (AtomicLong) veriyor.
    private Long id;

    private String userId;
    private String symbol;

    @Enumerated(EnumType.STRING)
    private OrderSide side;
    
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private OrderType type = OrderType.LIMIT;

    @Column(precision = 19, scale = 4)
    private BigDecimal price; 

    @Column(precision = 19, scale = 4)
    private BigDecimal quantity; 

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private Instant createdAt; 

    // Eşleşme kuyruklarında (PriorityQueue) objelerin kaybolmaması için sadece ID'ye göre çalışan Hash/Equals
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return id != null && id.equals(order.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}