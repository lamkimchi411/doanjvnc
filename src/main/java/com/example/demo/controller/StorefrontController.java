package com.example.demo.controller;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.example.demo.service.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import lombok.RequiredArgsConstructor;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Controller @RequiredArgsConstructor
public class StorefrontController {
 private final ProductRepository products; private final CustomerRepository customers; private final StaffAccountRepository staff; private final PolicyRepository policies;
 private final RentalOrderRepository orders; private final SiteImageRepository images; private final OrderLineRepository lines;
 private final ReviewRepository reviews; private final MoneyRepository money; private final TryOnAppointmentRepository appointments;
 private final Operations ops; private final ImageStorage storage; private final BCryptPasswordEncoder encoder;
 public static boolean employee(Authentication a){return a!=null&&a.getAuthorities().stream().anyMatch(r->Set.of("ROLE_STAFF","ROLE_ADMIN").contains(r.getAuthority()));}
 @GetMapping("/") String home(Model m){
  m.addAttribute("media",images.findAll().stream().filter(i->i.getImageUrl()!=null).collect(Collectors.toMap(SiteImage::getSlot,SiteImage::getImageUrl)));
  m.addAttribute("homeSettings",policies.findById(1L).orElseGet(ShopPolicy::new));
  m.addAttribute("products",products.findAll().stream().filter(p->!p.isAccessory()).limit(8).toList());return "home";
 }
 @GetMapping("/collection") String collection(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String category,
  @RequestParam(defaultValue="") String size,@RequestParam(defaultValue="") String color,@RequestParam(defaultValue="") String style,
  @RequestParam(required=false) LocalDate start,@RequestParam(required=false) LocalDate end,Model m){
  if(start!=null||end!=null)ops.dates(start,end);
  var all=products.findAll();m.addAttribute("categories",all.stream().map(Product::getCategory).distinct().sorted().toList());
  m.addAttribute("colors",all.stream().map(Product::getColor).distinct().sorted().toList());
  m.addAttribute("styles",all.stream().map(Product::getStyle).distinct().sorted().toList());
  m.addAttribute("products",all.stream().filter(p->p.getName().toLowerCase().contains(q.toLowerCase()))
   .filter(p->category.isBlank()||category.equals(p.getCategory())).filter(p->size.isBlank()||size.equals(p.getSize()))
   .filter(p->color.isBlank()||color.equals(p.getColor())).filter(p->style.isBlank()||style.equals(p.getStyle()))
   .filter(p->start==null||ops.available(p,start,end)).toList());return "collection";
 }
 @GetMapping("/product/{id}") String product(@PathVariable Long id,Model m){
  var p=products.findById(id).orElseThrow();m.addAttribute("product",p);
  m.addAttribute("suggestions",products.findAll().stream().filter(Product::isAccessory).toList());
  m.addAttribute("reviews",reviews.findByProductId(id));return "product";
 }
 @SuppressWarnings("unchecked") private List<Long> cart(HttpSession s){
  if(s.getAttribute("cart")==null)s.setAttribute("cart",new ArrayList<Long>());
  return (List<Long>)s.getAttribute("cart");
 }
 @PostMapping("/cart/add/{id}") String add(@PathVariable Long id,HttpSession s){
  if(!products.existsById(id))throw new IllegalArgumentException("Không tìm thấy sản phẩm.");
  if(!cart(s).contains(id))cart(s).add(id);return "redirect:/cart";
 }
 @PostMapping("/cart/remove/{id}") String remove(@PathVariable Long id,HttpSession s){cart(s).remove(id);return "redirect:/cart";}
 @PostMapping("/cart/replace/{id}") String replace(@PathVariable Long id,@RequestParam Long replacement,HttpSession s){
  if(!cart(s).contains(id)||!products.existsById(replacement))throw new IllegalArgumentException("Sản phẩm không hợp lệ.");
  cart(s).remove(id);if(!cart(s).contains(replacement))cart(s).add(replacement);return "redirect:/cart";
 }
 @GetMapping({"/cart","/checkout"}) String cartPage(HttpSession s,Model m){
  m.addAttribute("items",products.findAllById(cart(s)));m.addAttribute("products",products.findAll());return "cart";
 }
 @PostMapping("/checkout/preview") String preview(@RequestParam LocalDate start,@RequestParam LocalDate end,@RequestParam String fulfilment,
  @RequestParam String payment,@RequestParam(defaultValue="") String address,@RequestParam(defaultValue="") String code,
  @RequestParam(defaultValue="false") boolean full,HttpSession s,Model m){
  m.addAttribute("quote",ops.preview(new ArrayList<>(cart(s)),start,end,code,fulfilment,full));
  m.addAttribute("start",start);m.addAttribute("end",end);m.addAttribute("fulfilment",fulfilment);m.addAttribute("payment",payment);
  m.addAttribute("address",address);m.addAttribute("code",code);m.addAttribute("full",full);return "quote";
 }
 @PostMapping("/checkout") String checkout(@RequestParam LocalDate start,@RequestParam LocalDate end,@RequestParam String fulfilment,
  @RequestParam String payment,@RequestParam(defaultValue="") String address,@RequestParam(defaultValue="") String code,
  @RequestParam(defaultValue="false") boolean full,HttpSession s,Authentication a){
  var o=ops.book(a.getName(),a.getName(),new ArrayList<>(cart(s)),start,end,fulfilment,payment,address,code,full,a.getName());
  cart(s).clear();return "redirect:/orders/"+o.getId();
 }
 @GetMapping("/account") String account(Authentication a,Model m){
  m.addAttribute("orders",orders.findByCustomerEmailOrderByIdDesc(a.getName()));
  m.addAttribute("appointments",appointments.findAll().stream().filter(x->a.getName().equals(x.getCustomerEmail())).toList());return "account";
 }
 @GetMapping({"/orders/{id}","/staff/document/{id}"}) String detail(@PathVariable Long id,Authentication a,Model m){
  var o=orders.findById(id).orElseThrow();if(!employee(a)&&!a.getName().equals(o.getCustomerEmail()))throw new IllegalArgumentException("Bạn không có quyền xem đơn này.");
  m.addAttribute("order",o);m.addAttribute("items",lines.findByRentalOrderId(id));m.addAttribute("entries",money.findByRentalOrderId(id));
  m.addAttribute("policy",ops.policy());return "order";
 }
 @PostMapping("/orders/{id}/cancel") String cancel(@PathVariable Long id,Authentication a){ops.cancel(id,a.getName(),employee(a));return "redirect:/orders/"+id;}
 @PostMapping("/orders/{id}/review") String review(@PathVariable Long id,@RequestParam Long productId,@RequestParam int rating,
   @RequestParam String comment,@RequestParam(required=false) MultipartFile image,Authentication a){
  var o=orders.findById(id).orElseThrow();
  if(!a.getName().equals(o.getCustomerEmail())||!Set.of(Operations.RETURNED,Operations.REFUNDED).contains(o.getStatus()))throw new IllegalArgumentException("Đơn chưa đủ điều kiện đánh giá.");
  ops.review(id,productId,a.getName(),rating,comment,storage.save(image));return "redirect:/orders/"+id;
 }
 @GetMapping("/appointments") String appointmentPage(){return "appointment";}
 @PostMapping("/appointments") String appointment(@RequestParam String customerName,@RequestParam String phone,@RequestParam LocalDateTime appointmentAt,
  @RequestParam(defaultValue="") String note,Authentication a){ops.appointment(a.getName(),customerName,phone,appointmentAt,note);return "redirect:/account";}
 @PostMapping("/appointments/{id}/cancel") String cancelAppointment(@PathVariable Long id,Authentication a){
  var ap=appointments.findById(id).orElseThrow();if(!ap.getCustomerEmail().equals(a.getName()))throw new IllegalArgumentException("Không có quyền.");
  ap.setStatus("Đã hủy");appointments.save(ap);return "redirect:/account";
 }
 @GetMapping({"/login","/staff/login"}) String login(){return "login";}
 @GetMapping({"/register","/staff/register"}) String register(HttpServletRequest r,Model m){m.addAttribute("staffSignup",r.getRequestURI().startsWith("/staff"));return "register";}
 @PostMapping({"/register","/staff/register"}) String signup(@RequestParam String email,@RequestParam String password,@RequestParam String fullName,
  @RequestParam(defaultValue="") String phone,HttpServletRequest r){
  email=email.trim().toLowerCase();
  if(!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")||password.length()<8||fullName.isBlank())throw new IllegalArgumentException("Nhập tên, email hợp lệ và mật khẩu ít nhất 8 ký tự.");
  if(customers.findByEmail(email).isPresent()||staff.findByEmail(email).isPresent())throw new IllegalArgumentException("Email đã tồn tại.");
  if(r.getRequestURI().startsWith("/staff"))staff.save(StaffAccount.builder().email(email).fullName(fullName).password(encoder.encode(password)).role("STAFF").enabled(false).build());
  else customers.save(Customer.builder().email(email).fullName(fullName).phone(phone).password(encoder.encode(password)).build());
  return "redirect:/login?registered";
 }
}
