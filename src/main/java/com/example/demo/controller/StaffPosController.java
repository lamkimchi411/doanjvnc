package com.example.demo.controller;
import com.example.demo.repository.*;
import com.example.demo.service.*;
import com.example.demo.entity.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import lombok.RequiredArgsConstructor;
import java.time.*;
import java.util.*;
@Controller @RequestMapping("/staff") @RequiredArgsConstructor
public class StaffPosController {
 private final ProductRepository products;private final RentalOrderRepository orders;private final OrderLineRepository lines;
 private final TryOnAppointmentRepository appointments;private final PenaltyRepository penalties;private final Operations ops;
 @GetMapping String dashboard(@RequestParam(defaultValue="") String scan,Model m){
  var all=orders.findAll().stream().sorted(Comparator.comparing(RentalOrder::getId).reversed()).toList();
  if(!scan.isBlank())all=all.stream().filter(o->lines.findByRentalOrderId(o.getId()).stream().anyMatch(l->scan.trim().equalsIgnoreCase(l.getBarcode()))).toList();
  m.addAttribute("orders",all);m.addAttribute("products",products.findAll());m.addAttribute("appointments",appointments.findAll());
  m.addAttribute("penalties",penalties.findAll());m.addAttribute("policy",ops.policy());m.addAttribute("lineMap",orders.findAll().stream().collect(java.util.stream.Collectors.toMap(RentalOrder::getId,o->lines.findByRentalOrderId(o.getId()))));return "staff-pos";
 }
 @PostMapping("/quick-rental") String create(@RequestParam String customerName,@RequestParam String email,@RequestParam List<Long> productIds,
 @RequestParam LocalDate pickup,@RequestParam LocalDate returnDate,@RequestParam(defaultValue="") String code,Authentication a){
  var o=ops.book(email,customerName,productIds,pickup,returnDate,"PICKUP","CASH","",code,false,a.getName());return "redirect:/orders/"+o.getId();
 }
 @PostMapping("/confirm/{id}") String confirm(@PathVariable Long id,@RequestParam String method,@RequestParam String reference,Authentication a){ops.confirm(id,method,reference,a.getName());return "redirect:/staff";}
 @PostMapping("/prepare/{id}") String prepare(@PathVariable Long id){ops.prepare(id);return "redirect:/staff";}
 @PostMapping("/checkout/{id}") String checkout(@PathVariable Long id,@RequestParam String scanned,@RequestParam String condition,@RequestParam String collateral,
  @RequestParam String reference,@RequestParam String method,Authentication a){ops.checkout(id,scanned,condition,collateral,reference,method,a.getName());return "redirect:/orders/"+id;}
 @PostMapping("/checkin/{id}") String checkin(@PathVariable Long id,@RequestParam String scanned,@RequestParam String condition,@RequestParam Map<String,String> params,Authentication a){
  Map<Long,String> codes=new HashMap<>(),notes=new HashMap<>();params.forEach((k,v)->{
   if(k.startsWith("damage_"))codes.put(Long.parseLong(k.substring(7)),v);
   if(k.startsWith("returnNote_"))notes.put(Long.parseLong(k.substring(11)),v);
  });
  ops.checkin(id,scanned,codes,notes,condition,a.getName());return "redirect:/orders/"+id;
 }
 @PostMapping("/refund/{id}") String refund(@PathVariable Long id,@RequestParam String method,@RequestParam String reference,Authentication a){ops.refund(id,method,reference,a.getName());return "redirect:/orders/"+id;}
 @PostMapping("/stock/{id}") String stock(@PathVariable Long id,@RequestParam String stockStatus,Authentication a){ops.stock(id,stockStatus,a.getName());return "redirect:/staff";}
 @PostMapping("/appointment") String appointment(@RequestParam String email,@RequestParam String customerName,@RequestParam String phone,
  @RequestParam LocalDateTime appointmentAt,@RequestParam(defaultValue="") String note){ops.appointment(email,customerName,phone,appointmentAt,note);return "redirect:/staff";}
 @PostMapping("/appointment/{id}") String appointmentStatus(@PathVariable Long id,@RequestParam String status){
  if(!Set.of("Đã đến","Đã hủy").contains(status))throw new IllegalArgumentException("Trạng thái không hợp lệ.");
  var ap=appointments.findById(id).orElseThrow();ap.setStatus(status);appointments.save(ap);return "redirect:/staff";
 }
}
