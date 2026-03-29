package tr.edu.ytu.matching.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tr.edu.ytu.matching.core.model.Trade;

import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {
    
    // Spring Data JPA bu isimlendirmeyi okuyup SQL sorgusunu otomatik yazar:
    // SELECT * FROM trades ORDER BY executed_at DESC LIMIT 50;
    List<Trade> findTop50ByOrderByExecutedAtDesc();
}