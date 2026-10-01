package com.example.demo.config;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
        return http.authorizeHttpRequests(a -> a
            .requestMatchers("/staff/login", "/staff/register", "/login", "/register", "/", "/collection", "/product/**", "/css/**", "/js/**", "/images/**", "/uploads/**", "/error").permitAll()
            .requestMatchers("/admin/**").hasRole("ADMIN")
            .requestMatchers("/staff/**").hasRole("STAFF")
            .requestMatchers("/cart/**").permitAll()
            .anyRequest().authenticated())
            .formLogin(f -> f.loginPage("/login").usernameParameter("email").defaultSuccessUrl("/", true).permitAll())
            .logout(l -> l.logoutSuccessUrl("/")).build();
    }
}
