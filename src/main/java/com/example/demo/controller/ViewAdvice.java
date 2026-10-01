package com.example.demo.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import jakarta.servlet.http.HttpServletResponse;
@ControllerAdvice public class ViewAdvice {
 @ModelAttribute public void user(Authentication a,Model m){
  m.addAttribute("signedIn",a!=null);m.addAttribute("isStaff",StorefrontController.employee(a));
  m.addAttribute("isAdmin",a!=null&&a.getAuthorities().stream().anyMatch(r->r.getAuthority().equals("ROLE_ADMIN")));
 }
 @ExceptionHandler({IllegalArgumentException.class,java.util.NoSuchElementException.class,org.springframework.dao.DataIntegrityViolationException.class,
 org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,MaxUploadSizeExceededException.class})
 public String invalid(Exception e,Model m,HttpServletResponse response){
  response.setStatus(400);
  m.addAttribute("message",e instanceof IllegalArgumentException?e.getMessage():"Dữ liệu không hợp lệ, trùng mã hoặc tệp quá lớn. Vui lòng kiểm tra lại.");return "message";
 }
}
