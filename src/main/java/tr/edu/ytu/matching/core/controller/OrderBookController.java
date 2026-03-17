package tr.edu.ytu.matching.core.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tr.edu.ytu.matching.core.service.MatchingService;

import java.util.Map;

@RestController
@RequestMapping("/api/book")
@CrossOrigin(origins = "*") // React'in erişimine izin ver
@RequiredArgsConstructor
public class OrderBookController {

    private final MatchingService matchingService;

    @GetMapping
    public Map<String, Object> getOrderBook() {
        // Arayüz her /api/book adresine geldiğinde tahtanın son halini ver
        return matchingService.getOrderBookSnapshot();
    }
}