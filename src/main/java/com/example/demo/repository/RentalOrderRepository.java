package com.example.demo.repository;
import com.example.demo.entity.RentalOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.*;
public interface RentalOrderRepository extends JpaRepository<RentalOrder, Long> {
    List<RentalOrder> findByCustomerEmailOrderByIdDesc(String customerEmail);
    boolean existsByProductIdAndStatusNotAndPickupDateLessThanEqualAndReturnDateGreaterThanEqual(Long productId, String status, LocalDate end, LocalDate start);
}
