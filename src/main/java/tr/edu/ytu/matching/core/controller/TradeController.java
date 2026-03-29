package tr.edu.ytu.matching.core.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tr.edu.ytu.matching.core.model.Trade;
import tr.edu.ytu.matching.core.repository.TradeRepository;

import java.util.List;

@RestController
@RequestMapping("/api/trades")
@CrossOrigin(origins = "*") // React'in bağlanmasına izin ver
@RequiredArgsConstructor
public class TradeController {

    private final TradeRepository tradeRepository;

    @GetMapping
    public List<Trade> getRecentTrades() {
        // En son gerçekleşen 50 işlemi tarihe göre azalan sırayla (en yeni en üstte) gönder
        return tradeRepository.findTop50ByOrderByExecutedAtDesc();
    }
}