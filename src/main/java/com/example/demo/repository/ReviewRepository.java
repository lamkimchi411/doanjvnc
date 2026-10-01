package com.example.demo.repository;
import com.example.demo.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ReviewRepository extends JpaRepository<Review,Long>{ List<Review> findByProductId(Long id); }
