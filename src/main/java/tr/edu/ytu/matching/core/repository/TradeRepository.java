package tr.edu.ytu.matching.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tr.edu.ytu.matching.core.model.Trade;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {
    // Şimdilik standart JPA metotları (save, findAll) işimizi fazlasıyla görecek.
}