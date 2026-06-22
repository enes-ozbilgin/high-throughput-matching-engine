package tr.edu.ytu.matching.core.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "trades", indexes = {
	    // findTop50BySymbolOrderByExecutedAtDesc sorgusunun Full-Table-Scan yapmasını engeller!
	    @Index(name = "idx_trade_symbol_time", columnList = "symbol, executedAt DESC")
	})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Trade için DB'nin ID vermesi uygundur (Arka planda kaydediliyor)
    private Long id;

    private String symbol;
    private Long makerOrderId;
    private Long takerOrderId;

    @Column(columnDefinition = "DOUBLE PRECISION")
    private BigDecimal price;

    @Column(columnDefinition = "DOUBLE PRECISION")
    private BigDecimal quantity;

    private Instant executedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Trade trade = (Trade) o;
        return id != null && id.equals(trade.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}