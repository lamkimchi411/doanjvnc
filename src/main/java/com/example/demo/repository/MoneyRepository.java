package com.example.demo.repository;
import com.example.demo.entity.MoneyEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface MoneyRepository extends JpaRepository<MoneyEntry,Long>{ List<MoneyEntry> findByRentalOrderId(Long id); }
