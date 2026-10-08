package com.example.demo.repository;
import com.example.demo.entity.RentalOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.*;
public interface RentalOrderRepository extends JpaRepository<RentalOrder, Long> {
    List<RentalOrder> findByCustomerEmailOrderByIdDesc(String customerEmail);
    boolean existsByProductIdAndStatusNotAndPickupDateLessThanEqualAndReturnDateGreaterThanEqual(Long productId, String status, LocalDate end, LocalDate start);
    boolean existsByProductId(Long productId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from RentalOrder o where o.paymentCode = :paymentCode")
    Optional<RentalOrder> findByPaymentCodeForUpdate(String paymentCode);
}
