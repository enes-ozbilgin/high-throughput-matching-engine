package tr.edu.ytu.matching.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MatchingEngineCoreApplication {
	
    public static void main(String[] args) {
        SpringApplication.run(MatchingEngineCoreApplication.class, args);
    }
}
