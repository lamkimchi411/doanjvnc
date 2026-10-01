package com.example.demo.repository;
import com.example.demo.entity.PenaltyRule;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PenaltyRepository extends JpaRepository<PenaltyRule,String>{}
