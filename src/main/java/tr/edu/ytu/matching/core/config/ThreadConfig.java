package tr.edu.ytu.matching.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ThreadConfig {

    // Eğer 'virtual' profili aktifse bu fasulyeyi (bean) yarat:
    @Bean
    @Profile("virtual")
    public ExecutorService virtualThreadExecutor() {
        // Milyonlarca hafif thread açabilen Java 21 harikası
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    // Eğer 'classic' profili aktifse (veya hiçbir profil belirtilmemişse) bunu yarat:
    @Bean
    @Profile({"classic", "default"})
    public ExecutorService classicThreadExecutor() {
        // Geleneksel, ağır işletim sistemi thread havuzu (Örn: 200 adet)
        return Executors.newFixedThreadPool(200);
    }
}