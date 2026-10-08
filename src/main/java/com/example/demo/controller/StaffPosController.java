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
 private final PenaltyRepository penalties;private final Operations ops;private final PurchaseService purchases;
 private final SaleInvoiceRepository saleInvoices;
 @GetMapping String dashboard(@RequestParam(defaultValue="") String scan,Model m){
  var all=orders.findAll().stream().sorted(Comparator.comparing(RentalOrder::getId).reversed()).toList();
  if(!scan.isBlank())all=all.stream().filter(o->lines.findByRentalOrderId(o.getId()).stream().anyMatch(l->scan.trim().equalsIgnoreCase(l.getBarcode()))).toList();
  m.addAttribute("orders",all);m.addAttribute("products",products.findAll());
  m.addAttribute("saleOrders",saleInvoices.findAll().stream().sorted(Comparator.comparing(SaleInvoice::getId).reversed()).toList());
  m.addAttribute("saleProducts",products.findAll().stream().filter(p->"AVAILABLE".equals(p.getStockStatus())&&p.getSalePrice()!=null&&p.getSalePrice()>0).toList());
  m.addAttribute("penalties",penalties.findAll());m.addAttribute("policy",ops.policy());m.addAttribute("lineMap",orders.findAll().stream().collect(java.util.stream.Collectors.toMap(RentalOrder::getId,o->lines.findByRentalOrderId(o.getId()))));return "staff-pos";
 }
 @PostMapping("/quick-rental") String create(@RequestParam String customerName,@RequestParam String email,@RequestParam String customerPhone,@RequestParam List<Long> productIds,
 @RequestParam LocalDate pickup,@RequestParam LocalDate returnDate,@RequestParam(defaultValue="") String code,Authentication a){
  var o=ops.bookAtCounter(email,customerName,customerPhone,productIds,pickup,returnDate,code,a.getName());return "redirect:/orders/"+o.getId();
 }
 @PostMapping("/sales") String createSaleOrder(@RequestParam Long productId,@RequestParam String customerName,@RequestParam String customerEmail,
  @RequestParam String paymentMethod,@RequestParam(defaultValue="") String paymentReference){
  purchases.createByStaff(productId,customerEmail,customerName,paymentMethod,paymentReference);return "redirect:/staff";
 }
 @PostMapping("/confirm/{id}") String confirm(@PathVariable Long id,@RequestParam String method,@RequestParam String reference,Authentication a){ops.confirmAtCounter(id,method,reference,a.getName());return "redirect:/staff";}
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
 @PostMapping("/orders/{id}/edit") String editRentalOrder(@PathVariable Long id,@RequestParam List<Long> productIds,@RequestParam String customerName,
  @RequestParam LocalDateTime start,@RequestParam LocalDateTime end,@RequestParam String fulfilment,
  @RequestParam String payment,@RequestParam(defaultValue="") String address,@RequestParam(defaultValue="") String code){
  ops.updateStaffOrder(id,productIds,customerName,start,end,fulfilment,payment,address,code);return "redirect:/staff";
 }
 @PostMapping("/orders/{id}/delete") String deleteRentalOrder(@PathVariable Long id){ops.deleteStaffOrder(id);return "redirect:/staff";}
 @PostMapping("/sales/{id}/edit") String editSaleOrder(@PathVariable Long id,@RequestParam Long productId,@RequestParam String customerEmail,
  @RequestParam String customerName,@RequestParam String paymentMethod,@RequestParam(defaultValue="") String paymentReference){
  purchases.updateByStaff(id,productId,customerEmail,customerName,paymentMethod,paymentReference);return "redirect:/staff";
 }
 @PostMapping("/sales/{id}/delete") String deleteSaleOrder(@PathVariable Long id){purchases.deleteByStaff(id);return "redirect:/staff";}
 @PostMapping("/sales/{id}/confirm") String confirmSaleOrder(@PathVariable Long id,@RequestParam String paymentMethod,
  @RequestParam(defaultValue="") String paymentReference,Authentication a){
  purchases.confirm(id,paymentMethod,paymentReference,a.getName());return "redirect:/staff";
 }
}
