package tr.edu.ytu.matching.core.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders") // SQL'de "order" özel bir komut (ORDER BY) olduğu için tablo adını "orders" yapıyoruz.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId; // Emri giren kullanıcının ID'si

    private String symbol; // İşlem çifti (Örn: BTC_USDT, THYAO)

    @Enumerated(EnumType.STRING)
    private OrderSide side; // BUY veya SELL
    
    @Enumerated(EnumType.STRING)
    private OrderType type = OrderType.LIMIT; // Varsayılan olarak Limit kabul edelim

    // Finansal işlemler için BigDecimal KESİNLİKLE şarttır.
    @Column(precision = 19, scale = 4)
    private BigDecimal price; 

    @Column(precision = 19, scale = 4)
    private BigDecimal quantity; 

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    // Emrin sisteme giriş zamanı (Eşleştirme önceliği için kritik)
    private Instant createdAt; 
}