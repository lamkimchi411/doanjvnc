package com.example.demo.repository;

import com.example.demo.entity.SaleInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SaleInvoiceRepository extends JpaRepository<SaleInvoice, Long> {
    List<SaleInvoice> findByCustomerEmailOrderByIssuedAtDesc(String customerEmail);
    boolean existsByProductId(Long productId);
    boolean existsByProductIdAndStatusIsNull(Long productId);
    boolean existsByProductIdAndStatus(Long productId, String status);
}
