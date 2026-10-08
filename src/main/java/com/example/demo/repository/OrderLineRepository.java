package com.example.demo.repository;
import com.example.demo.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface OrderLineRepository extends JpaRepository<OrderLine,Long> {
 List<OrderLine> findByRentalOrderId(Long id);
 List<OrderLine> findByProductId(Long id);
 boolean existsByProductId(Long productId);
}
