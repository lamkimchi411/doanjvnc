package com.example.demo.repository;
import com.example.demo.entity.SiteImage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface SiteImageRepository extends JpaRepository<SiteImage, Long> { Optional<SiteImage> findBySlot(String slot); }
