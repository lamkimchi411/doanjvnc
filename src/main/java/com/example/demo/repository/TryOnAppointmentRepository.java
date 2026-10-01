package com.example.demo.repository;
import com.example.demo.entity.TryOnAppointment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface TryOnAppointmentRepository extends JpaRepository<TryOnAppointment,Long> { List<TryOnAppointment> findTop10ByOrderByAppointmentAtAsc(); }
