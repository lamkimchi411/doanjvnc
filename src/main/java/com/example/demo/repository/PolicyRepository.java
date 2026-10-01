package com.example.demo.repository;
import com.example.demo.entity.ShopPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PolicyRepository extends JpaRepository<ShopPolicy,Long>{}
