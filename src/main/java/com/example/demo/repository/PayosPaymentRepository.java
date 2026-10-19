package com.example.demo.repository;

import com.example.demo.entity.PayosPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface PayosPaymentRepository extends JpaRepository<PayosPayment, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PayosPayment> findByOrderCode(long orderCode);
}
