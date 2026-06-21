package tr.edu.ytu.matching.core;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.concurrent.atomic.AtomicLong;

public class MatchingEngineSimulation extends Simulation {

    // 1. HTTP Protokol Ayarları
    HttpProtocolBuilder httpProtocol = http
        .baseUrl("http://localhost:8080")
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")
        .shareConnections(); // Bağlantı havuzunu optimize eder

    // Sistemdeki 10 Pazar
    List<String> symbols = Arrays.asList(
        "BTC_USDT", "ETH_USDT", "BNB_USDT", "SOL_USDT", "XRP_USDT", 
        "ADA_USDT", "AVAX_USDT", "DOGE_USDT", "DOT_USDT", "LINK_USDT"
    );
    Random random = new Random();
    
    // Donanım seviyesinde atomik ID sayacı (Çakışmaları %100 önler)
    AtomicLong globalOrderId = new AtomicLong(System.nanoTime());

    // 2. Rastgele Emir Üretici (Feeder)
    Iterator<Map<String, Object>> customFeeder = Stream.generate((Supplier<Map<String, Object>>) () -> {
        String symbol = symbols.get(random.nextInt(symbols.size()));
        String side = random.nextBoolean() ? "BUY" : "SELL";
        
        // %80 Limit Emri, %20 Piyasa Emri
        String type = random.nextInt(10) > 7 ? "MARKET" : "LIMIT"; 
        
        double price = 50000 + (random.nextDouble() * 10000); 
        double quantity = 0.1 + (random.nextDouble() * 5);    
        
        return Map.of(
            "id", globalOrderId.getAndIncrement(), 
            "userId", "load_tester_" + random.nextInt(10000),
            "symbol", symbol,
            "side", side,
            "type", type,
            "price", String.format(java.util.Locale.US, "%.2f", type.equals("MARKET") ? 0.0 : price),
            "quantity", String.format(java.util.Locale.US, "%.2f", quantity)
        );
    }).iterator();

    // 3. Saldırı Senaryosu (Scenario)
    ScenarioBuilder scn = scenario("High-Throughput Market Maker")
        .feed(customFeeder)
        // İstek adını dinamik "#{type}" ile belirledik (Raporlarda Limit ve Market olarak ikiye ayrılacak)
        .exec(http("#{type} Emri Gonder") 
            .post("/api/orders")
            .body(StringBody("{ \"id\": #{id}, \"userId\": \"#{userId}\", \"symbol\": \"#{symbol}\", \"side\": \"#{side}\", \"type\": \"#{type}\", \"price\": #{price}, \"quantity\": #{quantity} }"))
            .check(status().is(200)) 
        );

    // 4. Testin Yükleme Profili (Saniyede kaç emir?)
    {
        setUp(
            scn.injectOpen(
                // 1. ISINMA TURU: JIT Compiler ve Veritabanı havuzlarının ısınması için 15 saniye hafif yük
                constantUsersPerSec(1000).during(Duration.ofSeconds(15)),
                
                // 2. TIRMANIŞ: Yükü 10 saniyede saniyede 10.000 kullanıcıya çıkar
                rampUsersPerSec(1000).to(10000).during(Duration.ofSeconds(10)),
                
                // 3. ZİRVE (STRESS): Tam 30 saniye boyunca saniyede 10.000 emir fırlat!
                constantUsersPerSec(10000).during(Duration.ofSeconds(30))
            )
        ).protocols(httpProtocol);
    }
}