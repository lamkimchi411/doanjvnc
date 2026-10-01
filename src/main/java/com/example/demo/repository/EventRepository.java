package com.example.demo.repository;
import com.example.demo.entity.InventoryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface EventRepository extends JpaRepository<InventoryEvent,Long>{ List<InventoryEvent> findByProductIdOrderByRecordedAtDesc(Long id); }
