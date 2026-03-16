package tr.edu.ytu.matching.core.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tr.edu.ytu.matching.core.model.Order;
import tr.edu.ytu.matching.core.service.OrderService;
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody Order order) {
        
        // Gelen JSON verisini Order nesnesi olarak alıp Service'e gönderiyoruz
        Order savedOrder = orderService.createOrder(order);
        
        // İşlem başarılıysa kaydedilen emri 200 OK koduyla geri döndürüyoruz
        return ResponseEntity.ok(savedOrder);
    }
}