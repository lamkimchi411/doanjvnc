package com.example.demo.repository;
import com.example.demo.entity.StaffAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface StaffAccountRepository extends JpaRepository<StaffAccount,Long>{ Optional<StaffAccount> findByEmail(String email); }
