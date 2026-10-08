package com.example.demo.config;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.http.HttpMethod;
import com.example.demo.repository.*;

@Configuration
public class SecurityConfig {
    @Bean BCryptPasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService userDetails(CustomerRepository customers, StaffAccountRepository staff) {
        return email -> {
            var employee = staff.findByEmail(email.toLowerCase().trim());
            if (employee.isPresent()) {
                var a = employee.get();
                return User.withUsername(a.getEmail()).password(a.getPassword())
                    .roles("ADMIN".equals(a.getRole()) ? new String[]{"ADMIN", "STAFF"} : new String[]{"STAFF"})
                    .disabled(!a.isEnabled()).build();
            }
            var a = customers.findByEmail(email.toLowerCase().trim()).orElseThrow(() -> new UsernameNotFoundException("Tài khoản không tồn tại"));
            return User.withUsername(a.getEmail()).password(a.getPassword()).roles("CUSTOMER").build();
        };
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.ignoringRequestMatchers("/api/payments/webhook"))
            .authorizeHttpRequests(a -> a
            .requestMatchers(HttpMethod.POST, "/api/payments/webhook").permitAll()
            .requestMatchers("/staff/login", "/staff/register", "/login", "/register", "/", "/collection", "/product/**", "/css/**", "/js/**", "/images/**", "/uploads/**", "/error").permitAll()
            // Mọi thao tác với giỏ yêu cầu đăng nhập. Khi khách truy cập một trang GET
            // (giỏ, thuê hoặc mua), Spring Security lưu URL để trả họ về đúng trang sau đăng nhập.
            .requestMatchers("/cart/**").authenticated()
            .requestMatchers("/admin/**").hasRole("ADMIN")
            .requestMatchers("/staff/**").hasRole("STAFF")
            .anyRequest().authenticated())
            // Không ép về trang chủ để giữ lại trang khách/nhân viên/quản trị mà người dùng vừa yêu cầu.
            .formLogin(f -> f.loginPage("/login").usernameParameter("email").defaultSuccessUrl("/").permitAll())
            .logout(l -> l.logoutSuccessUrl("/")).build();
    }
}
