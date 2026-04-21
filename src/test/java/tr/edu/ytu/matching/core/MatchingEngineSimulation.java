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

public class MatchingEngineSimulation extends Simulation {

    // 1. HTTP Protokol Ayarları (Motorumuzun adresi)
    HttpProtocolBuilder httpProtocol = http
        .baseUrl("http://localhost:8080")
        .acceptHeader("application/json")
        .contentTypeHeader("application/json");

    // Sistemdeki 10 Pazar (Lock Striping'i test etmek için yükü dağıtıyoruz)
    List<String> symbols = Arrays.asList(
        "BTC_USDT", "ETH_USDT", "BNB_USDT", "SOL_USDT", "XRP_USDT", 
        "ADA_USDT", "AVAX_USDT", "DOGE_USDT", "DOT_USDT", "LINK_USDT"
    );
    Random random = new Random();

    // 2. Rastgele Emir Üretici (Feeder)
    // Her saniye binlerce farklı kullanıcıdan, farklı pazarlara rastgele emirler üretir.
    Iterator<Map<String, Object>> customFeeder = Stream.generate((Supplier<Map<String, Object>>) () -> {
        String symbol = symbols.get(random.nextInt(symbols.size()));
        String side = random.nextBoolean() ? "BUY" : "SELL";
        
        // %80 Limit Emri, %20 Piyasa Emri göndererek gerçekçi bir senaryo yaratıyoruz
        String type = random.nextInt(10) > 7 ? "MARKET" : "LIMIT"; 
        
        double price = 50000 + (random.nextDouble() * 10000); // 50K - 60K arası rastgele fiyat
        double quantity = 0.1 + (random.nextDouble() * 5);    // 0.1 - 5 arası miktar
        
        return Map.of(
            "userId", "load_tester_" + random.nextInt(10000),
            "symbol", symbol,
            "side", side,
            "type", type,
            // JSON formatı hatası almamak için sayıları Amerikan formatında (Noktalı) üretiyoruz
            "price", String.format(java.util.Locale.US, "%.2f", type.equals("MARKET") ? 0.0 : price),
            "quantity", String.format(java.util.Locale.US, "%.2f", quantity)
        );
    }).iterator();

    // 3. Saldırı Senaryosu (Scenario)
    ScenarioBuilder scn = scenario("High-Throughput Market Maker")
        .feed(customFeeder)
        .exec(http("Post Order")
            .post("/api/orders")
            // Feeder'dan gelen verileri JSON şablonuna yerleştirip API'ye fırlatıyoruz
            .body(StringBody("{ \"userId\": \"#{userId}\", \"symbol\": \"#{symbol}\", \"side\": \"#{side}\", \"type\": \"#{type}\", \"price\": #{price}, \"quantity\": #{quantity} }"))
            .check(status().is(200)) // Başarılı (200 OK) döndüğünü doğrula
        );

    // 4. Testin Yükleme Profili (Saniyede kaç emir?)
    {
        setUp(
            scn.injectOpen(
                // Isınma turu: 10 saniye boyunca yükü yavaş yavaş saniyede 10.000 kullanıcıya çıkar
                rampUsersPerSec(10).to(10000).during(Duration.ofSeconds(10)),
                // Zirve noktası: Tam 30 saniye boyunca saniyede 10.000 emir fırlatmaya devam et!
                constantUsersPerSec(10000).during(Duration.ofSeconds(30))
            )
        ).protocols(httpProtocol);
    }
}