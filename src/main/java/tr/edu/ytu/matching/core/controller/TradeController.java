package tr.edu.ytu.matching.core.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/{symbol}")
    public List<Trade> getRecentTrades(@PathVariable String symbol) {
        return tradeRepository.findTop50BySymbolOrderByExecutedAtDesc(symbol);
    }
}