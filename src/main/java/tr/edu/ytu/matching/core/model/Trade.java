package tr.edu.ytu.matching.core.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trades")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol; // Örn: BTC_USDT

    // Eşleşen Emirlerin Veritabanı ID'leri
    private Long makerOrderId; // Tahtada bekleyen emrin ID'si
    private Long takerOrderId; // Tahtaya sonradan gelip eşleşmeyi tetikleyen emrin ID'si

    @Column(precision = 19, scale = 4)
    private BigDecimal price; // İşlemin gerçekleştiği nihai fiyat

    @Column(precision = 19, scale = 4)
    private BigDecimal quantity; // İşlem miktarı (Kaç BTC el değiştirdi?)

    private Instant executedAt; // İşlemin gerçekleşme zamanı
}