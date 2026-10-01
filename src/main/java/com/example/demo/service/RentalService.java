package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import org.springframework.stereotype.Service;
import java.time.LocalDate;

@Service
public class RentalService {
    private final ProductRepository products;
    private final Operations operations;
    public RentalService(ProductRepository products, Operations operations) { this.products = products; this.operations = operations; }

    public boolean available(Long productId, LocalDate start, LocalDate end) {
        return start != null && end != null && !end.isBefore(start)
            && products.findById(productId).map(p->operations.available(p,start,end)).orElse(false);
    }
    public RentalOrder book(String email, Long productId, LocalDate start, LocalDate end, String fulfilment, String payment) {
        return operations.book(email,email,java.util.List.of(productId),start,end,fulfilment,payment,"","",false,email);
    }
}
