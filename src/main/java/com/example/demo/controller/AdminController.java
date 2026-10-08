package com.example.demo.controller;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.example.demo.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.time.*;
import java.util.*;

@Controller @RequestMapping("/admin") @RequiredArgsConstructor
public class AdminController {
 private final ProductRepository products; private final PolicyRepository policies; private final PromotionRepository promotions;
 private final PenaltyRepository penalties;private final StaffAccountRepository staff;private final SiteImageRepository images;
 private final CustomerRepository customers; private final CategoryRepository categories;
 private final SaleInvoiceRepository saleInvoices;
 private final RentalOrderRepository rentalOrders;
 private final OrderLineRepository rentalLines;
 private final ReviewRepository reviews;
 private final TryOnAppointmentRepository appointments;
 private final MoneyRepository money;private final EventRepository events;
 private final ImageStorage storage;
 private final CheckinGallery checkinGallery;
 private final InventoryReportService reports;
 private final ProductService productService; private final Operations ops; private final PurchaseService purchases;
 private final BCryptPasswordEncoder passwordEncoder;
 @GetMapping String dashboard(@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,Model m){
  if(from==null)from=LocalDate.now().withDayOfMonth(1);if(to==null)to=LocalDate.now();
  if(to.isBefore(from))throw new IllegalArgumentException("Khoảng báo cáo không hợp lệ.");
  final LocalDate begin=from,finish=to;
  var ledger=money.findAll();
  var period=ledger.stream().filter(e->!e.getRecordedAt().toLocalDate().isBefore(begin)&&!e.getRecordedAt().toLocalDate().isAfter(finish)).toList();
  m.addAttribute("rentRevenue",sum(period,"RENT_IN"));
  m.addAttribute("damageRevenue",sum(period,"PENALTY_IN")+sum(period,"SECURITY_APPLIED"));
  m.addAttribute("held",sum(ledger,"SECURITY_IN")-sum(ledger,"SECURITY_REFUND")-sum(ledger,"SECURITY_APPLIED"));
  m.addAttribute("refunded",sum(period,"SECURITY_REFUND"));m.addAttribute("entries",period);
  var policy=policies.findById(1L).orElseThrow();var allProducts=products.findAll();
  var stats=reports.report(from,to);
  var accessoryStats=reports.accessoryReport(stats);
  m.addAttribute("stats",stats);m.addAttribute("accessoryStats",accessoryStats);
  m.addAttribute("from",from);m.addAttribute("to",to);m.addAttribute("products",allProducts);
  m.addAttribute("policy",policy);m.addAttribute("promotions",promotions.findAll());
  m.addAttribute("penalties",penalties.findAll());return "admin";
 }
 private long sum(List<MoneyEntry> list,String kind){return list.stream().filter(e->kind.equals(e.getKind())).mapToLong(MoneyEntry::getAmount).sum();}
 @PostMapping("/policy") @Transactional String policy(@RequestParam int weekendPercent,@RequestParam int holidayPercent,@RequestParam int bookingPercent,
 @RequestParam int holdMinutes,@RequestParam long shippingEachWay,@RequestParam long lateHourly,
 @RequestParam long lateDaily,@RequestParam int accessoryLossAlertPercent,@RequestParam(required=false) Integer accessoryRestockTarget,@RequestParam(required=false) Integer retirementRentalThreshold,@RequestParam(required=false) Integer retirementWashThreshold,
 @RequestParam String holidays,@RequestParam String bankName,@RequestParam(defaultValue="") String bankBin,@RequestParam String bankAccount,@RequestParam String bankOwner){
  if(weekendPercent<1||holidayPercent<1||weekendPercent>1000||holidayPercent>1000||bookingPercent<20||bookingPercent>50||holdMinutes<1||shippingEachWay<0||lateHourly<0||lateDaily<0||accessoryLossAlertPercent<0||accessoryLossAlertPercent>100||(accessoryRestockTarget!=null&&accessoryRestockTarget<1)||(retirementRentalThreshold!=null&&retirementRentalThreshold<1)||(retirementWashThreshold!=null&&retirementWashThreshold<1))
   throw new IllegalArgumentException("Giá phải không âm; cọc giữ lịch 20–50%; thời gian giữ đơn phải dương.");
  for(var day:holidays.split("[,\\s]+"))if(!day.isBlank())LocalDate.parse(day);
  if(!bankBin.isBlank()&&!bankBin.trim().matches("\\d{6}"))throw new IllegalArgumentException("Mã BIN VietQR phải gồm đúng 6 chữ số.");
  var p=policies.findById(1L).orElseThrow();p.setWeekendPercent(weekendPercent);p.setHolidayPercent(holidayPercent);p.setBookingPercent(bookingPercent);
  p.setHoldMinutes(holdMinutes);p.setShippingEachWay(shippingEachWay);p.setLateHourly(lateHourly);p.setLateDaily(lateDaily);p.setAccessoryLossAlertPercent(accessoryLossAlertPercent);
  if(accessoryRestockTarget!=null)p.setAccessoryRestockTarget(accessoryRestockTarget);
  if(retirementRentalThreshold!=null)p.setRetirementRentalThreshold(retirementRentalThreshold);
  if(retirementWashThreshold!=null)p.setRetirementWashThreshold(retirementWashThreshold);
  p.setHolidays(holidays);p.setBankName(bankName);p.setBankBin(bankBin.trim());p.setBankAccount(bankAccount);p.setBankOwner(bankOwner);return "redirect:/admin";
 }
 @PostMapping("/promotion") String promotion(@RequestParam String code,@RequestParam(defaultValue="") String name,@RequestParam int percent,@RequestParam long minimumRent,
 @RequestParam LocalDate startDate,@RequestParam LocalDate endDate,@RequestParam int usageLimit,
 @RequestParam(defaultValue="") String requiredCategories,@RequestParam(defaultValue="") String description,@RequestParam(defaultValue="false") boolean active){
  code=code.trim().toUpperCase();if(!code.matches("[A-Z0-9_-]{2,40}")||percent<0||percent>100||minimumRent<0||usageLimit<0||endDate.isBefore(startDate))
   throw new IllegalArgumentException("Thông tin mã giảm giá không hợp lệ.");
  var p=promotions.findById(code).orElse(new Promotion());p.setCode(code);p.setName(name.isBlank()?null:name.trim());p.setPercent(percent);p.setMinimumRent(minimumRent);
  p.setStartDate(startDate);p.setEndDate(endDate);p.setUsageLimit(usageLimit);p.setRequiredCategories(requiredCategories);p.setDescription(description.isBlank()?null:description.trim());p.setActive(active);promotions.save(p);return "redirect:/admin";
 }
 @PostMapping("/penalty") String penalty(@RequestParam String code,@RequestParam String label,@RequestParam long amount,@RequestParam String incidentType,@RequestParam String nextStockStatus){
  if(!code.matches("[A-Z_]{2,40}")||amount<0||!Set.of("DAMAGE","LOSS").contains(incidentType)||!Set.of("WASHING","REPAIRING","RETIRED").contains(nextStockStatus))throw new IllegalArgumentException("Bảng phí không hợp lệ.");
  if("NORMAL".equals(code)&&(amount!=0||!"DAMAGE".equals(incidentType)||!"WASHING".equals(nextStockStatus)))throw new IllegalArgumentException("Bình thường phải có phí 0 và chuyển đi giặt.");
  if("LOSS".equals(incidentType)&&!"RETIRED".equals(nextStockStatus))throw new IllegalArgumentException("Mất đồ phải ngừng cho thuê (RETIRED).");
  if("LOST".equals(code)&&!"LOSS".equals(incidentType))throw new IllegalArgumentException("Mã LOST luôn dùng để ghi nhận mất đồ.");
  var p=penalties.findById(code).orElseGet(PenaltyRule::new);p.setCode(code);p.setLabel(label);p.setAmount(amount);p.setIncidentType(incidentType);p.setNextStockStatus(nextStockStatus);penalties.save(p);return "redirect:/admin";
 }
 @PostMapping("/product") String product(@RequestParam(required=false) Long id,@RequestParam(defaultValue="1") int quantity,@RequestParam String name,@RequestParam String category,@RequestParam String size,
 @RequestParam String color,@RequestParam String style,@RequestParam long dailyPrice,@RequestParam long depositAmount,
 @RequestParam String components,@RequestParam String description,@RequestParam(defaultValue="false") boolean accessory,@RequestParam(required=false) MultipartFile image,@RequestParam(defaultValue="") String barcode,@RequestParam(required=false) Long salePrice){
  productService.save(id,quantity,name,category,size,color,style,dailyPrice,depositAmount,components,description,accessory,image,barcode,salePrice);return "redirect:/admin/products";
 }
 @GetMapping("/product/{id}") String edit(@PathVariable Long id,Model m){m.addAttribute("product",products.findById(id).orElseThrow());m.addAttribute("events",events.findByProductIdOrderByRecordedAtDesc(id));m.addAttribute("policy",policies.findById(1L).orElseThrow());m.addAttribute("categories",categories.findAll());return "admin-product";}
 @GetMapping("/product/new") String create(Model m){var p=new Product();p.setSize("M");m.addAttribute("product",p);m.addAttribute("events",List.of());m.addAttribute("policy",policies.findById(1L).orElseThrow());m.addAttribute("categories",categories.findAll());return "admin-product";}
 @PostMapping("/product/{id}/delete") String deleteProduct(@PathVariable Long id){
  productService.delete(id);return "redirect:/admin/products";
 }
 @GetMapping("/categories") String categoryList(Model m){m.addAttribute("categories",categories.findAll());return "admin-categories";}
 @PostMapping("/categories") String saveCategory(@RequestParam(required=false) Long id,@RequestParam String name,@RequestParam(defaultValue="") String description){
  name=name.trim();if(name.isBlank())throw new IllegalArgumentException("Tên danh mục không được để trống.");
  var c=id==null?new Category():categories.findById(id).orElseThrow();
  categories.findByNameIgnoreCase(name).filter(x->!x.getId().equals(c.getId())).ifPresent(x->{throw new IllegalArgumentException("Danh mục đã tồn tại.");});
  String old=c.getName();c.setName(name);c.setDescription(description.trim());categories.save(c);
  if(old!=null&&!old.equals(name)){final String updatedName=name;products.findAll().stream().filter(p->old.equals(p.getCategory())).forEach(p->{p.setCategory(updatedName);products.save(p);});}
  return "redirect:/admin/categories";
 }
 @PostMapping("/categories/{id}/delete") String deleteCategory(@PathVariable Long id){var c=categories.findById(id).orElseThrow();if(products.findAll().stream().anyMatch(p->c.getName().equals(p.getCategory())))throw new IllegalArgumentException("Không thể xóa danh mục còn sản phẩm.");categories.delete(c);return "redirect:/admin/categories";}
 @GetMapping("/users") String users(Model m){m.addAttribute("customers",customers.findAll());m.addAttribute("staffAccounts",staff.findAll());return "admin-users";}
 @PostMapping("/customers/{id}") @Transactional String updateCustomer(@PathVariable Long id,@RequestParam String email,@RequestParam String fullName,@RequestParam String phone,@RequestParam(defaultValue="") String password){
  var customer=customers.findById(id).orElseThrow();
  var updatedEmail=email.trim().toLowerCase(Locale.ROOT);
  if(!updatedEmail.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")||fullName.isBlank()||phone.trim().length()>25)throw new IllegalArgumentException("Thông tin khách hàng không hợp lệ.");
  if(customers.findByEmail(updatedEmail).filter(existing->!existing.getId().equals(id)).isPresent()||staff.findByEmail(updatedEmail).isPresent())throw new IllegalArgumentException("Email đã tồn tại.");
  var previousEmail=customer.getEmail();
  if(!password.isBlank()&&password.length()<8)throw new IllegalArgumentException("Mật khẩu mới phải có ít nhất 8 ký tự.");
  customer.setEmail(updatedEmail);customer.setFullName(fullName.trim());customer.setPhone(phone.trim());
  if(!password.isBlank())customer.setPassword(passwordEncoder.encode(password));
  if(!previousEmail.equals(updatedEmail)){
   rentalOrders.findByCustomerEmailOrderByIdDesc(previousEmail).forEach(order->order.setCustomerEmail(updatedEmail));
   saleInvoices.findByCustomerEmailOrderByIssuedAtDesc(previousEmail).forEach(invoice->invoice.setCustomerEmail(updatedEmail));
   reviews.findAll().stream().filter(review->previousEmail.equals(review.getCustomerEmail())).forEach(review->review.setCustomerEmail(updatedEmail));
   appointments.findAll().stream().filter(appointment->previousEmail.equals(appointment.getCustomerEmail())).forEach(appointment->appointment.setCustomerEmail(updatedEmail));
  }
  customers.save(customer);return "redirect:/admin/users?customerUpdated";
 }
 @PostMapping("/customers/{id}/delete") String deleteCustomer(@PathVariable Long id){customers.deleteById(id);return "redirect:/admin/users?customerDeleted";}
 @PostMapping("/staff/{id}/update") String updateStaff(@PathVariable Long id,@RequestParam String fullName,@RequestParam String role,@RequestParam(defaultValue="false") boolean enabled,Authentication a){var s=staff.findById(id).orElseThrow();if(s.getEmail().equals(a.getName())&&(!enabled||!"ADMIN".equals(role)))throw new IllegalArgumentException("Không thể tự tước quyền hoặc vô hiệu hóa tài khoản đang đăng nhập.");s.setFullName(fullName.trim());s.setRole("ADMIN".equals(role)?"ADMIN":"STAFF");s.setEnabled(enabled);staff.save(s);return "redirect:/admin/users?staffUpdated";}
 @GetMapping({"/products","/qr"}) String qrInventory(Model m){
  var allProducts=products.findAll().stream().sorted(
   Comparator.comparing(Product::getCategory, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
    .thenComparing(Product::getId)).toList();
  var listedProducts=uniqueInventoryProducts(allProducts);
  m.addAttribute("products",allProducts);
  m.addAttribute("policy",policies.findById(1L).orElseThrow());
  m.addAttribute("outfitProducts",listedProducts.stream().filter(p->!p.isAccessory()).toList());
  m.addAttribute("accessoryProducts",listedProducts.stream().filter(Product::isAccessory).toList());
  return "admin-qr";
 }
 private List<Product> uniqueInventoryProducts(List<Product> sortedProducts){
  return new ArrayList<>(sortedProducts.stream().collect(java.util.stream.Collectors.toMap(
   product->normalizedInventoryName(product.getCategory())+"\u0000"+normalizedInventoryName(product.getName()),
   product->product,(first,duplicate)->!"AVAILABLE".equals(first.getStockStatus())&&"AVAILABLE".equals(duplicate.getStockStatus())?duplicate:first,
   LinkedHashMap::new)).values());
 }
 private String normalizedInventoryName(String value){return Objects.toString(value,"").trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);}
 @GetMapping("/orders") String orders(Model m){
  var rentals=rentalOrders.findAll();
  m.addAttribute("rentalOrders",rentals);m.addAttribute("saleOrders",saleInvoices.findAll());
  // Giữ lựa chọn hiện tại để lưu thông tin không vô tình đổi sản phẩm của đơn thuê.
  m.addAttribute("rentalProductIds",rentals.stream().collect(java.util.stream.Collectors.toMap(RentalOrder::getId,
   order->rentalLines.findByRentalOrderId(order.getId()).stream().map(line->line.getProduct().getId()).toList())));
  m.addAttribute("products",products.findAll().stream().filter(p->!"RETIRED".equals(p.getStockStatus())).toList());
  return "admin-orders";
 }
 @PostMapping("/orders/{id}/status") String updateOrderStatus(@PathVariable Long id,@RequestParam String status,
  @RequestParam(defaultValue="CASH") String paymentMethod,@RequestParam(defaultValue="") String paymentReference,Authentication a){
  ops.updateStatusByAdmin(id,status,paymentMethod,paymentReference,a.getName());return "redirect:/admin/orders";
 }
 @PostMapping("/orders/{id}/edit") String editRentalOrder(@PathVariable Long id,@RequestParam List<Long> productIds,@RequestParam String customerName,
  @RequestParam java.time.LocalDateTime start,@RequestParam java.time.LocalDateTime end,@RequestParam String fulfilment,
  @RequestParam String payment,@RequestParam(defaultValue="") String address,@RequestParam(defaultValue="") String code){
  ops.updateStaffOrder(id,productIds,customerName,start,end,fulfilment,payment,address,code);return "redirect:/admin/orders";
 }
 @PostMapping("/orders/{id}/delete") String deleteRentalOrder(@PathVariable Long id){ops.deleteStaffOrder(id);return "redirect:/admin/orders";}
 @PostMapping("/sales/{id}/edit") String editSaleOrder(@PathVariable Long id,@RequestParam Long productId,
  @RequestParam String customerName,@RequestParam String paymentMethod,@RequestParam(defaultValue="") String paymentReference){
  purchases.updateByStaff(id,productId,customerName,paymentMethod,paymentReference);return "redirect:/admin/orders";
 }
 @PostMapping("/sales/{id}/delete") String deleteSaleOrder(@PathVariable Long id){purchases.deleteByStaff(id);return "redirect:/admin/orders";}
 @PostMapping("/sales/{id}/confirm") String confirmSaleOrder(@PathVariable Long id,@RequestParam String paymentMethod,
  @RequestParam(defaultValue="") String paymentReference,Authentication a){
  purchases.confirm(id,paymentMethod,paymentReference,a.getName());return "redirect:/admin/orders";
 }
 @GetMapping({"/home-settings","/media","/home-content"}) String homeSettings(Model m){
  var policy=policies.findById(1L).orElseThrow();
  m.addAttribute("policy",policy);m.addAttribute("images",images.findAll().stream().filter(i->!i.getSlot().startsWith("checkin-")).toList());
  m.addAttribute("checkinFrames",checkinGallery.frames());
  m.addAttribute("collectionProducts",products.findAll().stream().filter(p->!p.isAccessory()&&!"RETIRED".equals(p.getStockStatus())
   ).sorted(Comparator.comparing(Product::getName,Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))).toList());
  m.addAttribute("daySlots",collectionSlots(policy.getDayCollectionProductIds()));
  m.addAttribute("nightSlots",collectionSlots(policy.getNightCollectionProductIds()));
  return "admin-home-settings";
 }
 @PostMapping("/home-settings") @Transactional String saveHomeSettings(@RequestParam String homeTitle,@RequestParam String homeSubtitle,
  @RequestParam String storeAddress,@RequestParam String storePhone,@RequestParam String storeMapUrl,@RequestParam String storeSocialUrl,
  @RequestParam(required=false) List<String> dayProductIds,@RequestParam(required=false) List<String> nightProductIds,
  @RequestParam(required=false) List<String> slots,@RequestParam(required=false) List<String> imageTitles,
  @RequestParam(required=false) List<MultipartFile> imageFiles,
  @RequestParam(required=false) List<String> checkinTitles,@RequestParam(required=false) List<MultipartFile> checkinFiles,
  @RequestParam(required=false) List<String> removeCheckin,@RequestParam(required=false) String homeVideoUrl){
  checkinGallery.save(checkinTitles,checkinFiles,removeCheckin);
  var p=policies.findById(1L).orElseThrow();p.setHomeTitle(homeTitle.trim());p.setHomeSubtitle(homeSubtitle.trim());p.setStoreAddress(storeAddress.trim());p.setStorePhone(storePhone.trim());p.setStoreMapUrl(storeMapUrl.trim());p.setStoreSocialUrl(storeSocialUrl.trim());
  if(dayProductIds!=null)p.setDayCollectionProductIds(collectionIds(dayProductIds,"ban ngày"));
  if(homeVideoUrl!=null)p.setHomeVideoUrl(YouTubeVideo.normalize(homeVideoUrl));
  if(nightProductIds!=null)p.setNightCollectionProductIds(collectionIds(nightProductIds,"ban đêm"));
  policies.save(p);
  if(slots!=null||imageTitles!=null||imageFiles!=null){
   if(slots==null||imageTitles==null||imageFiles==null||slots.size()!=imageTitles.size()||slots.size()!=imageFiles.size())throw new IllegalArgumentException("Dữ liệu ảnh trang chủ không hợp lệ.");
   for(int i=0;i<slots.size();i++){
    var row=images.findBySlot(slots.get(i)).orElseThrow(()->new IllegalArgumentException("Vị trí ảnh không hợp lệ."));
    if(imageTitles.get(i).isBlank())throw new IllegalArgumentException("Tiêu đề ảnh không được để trống.");
    row.setTitle(imageTitles.get(i).trim());String url=storage.save(imageFiles.get(i));if(!url.isBlank())row.setImageUrl(url);images.save(row);
   }
  }
  return "redirect:/admin/home-settings?success";
 }
 private String collectionIds(List<String> rawIds,String period){
  var ids=new ArrayList<Long>();
  try { for(var rawId:rawIds)if(rawId!=null&&!rawId.isBlank())ids.add(Long.valueOf(rawId)); }
  catch(NumberFormatException exception){throw new IllegalArgumentException("Sản phẩm trong khung "+period+" không hợp lệ.");}
  if(ids.size()>3||new HashSet<>(ids).size()!=ids.size())throw new IllegalArgumentException("Mỗi khung "+period+" chỉ nhận tối đa 3 sản phẩm khác nhau.");
  var allowed=products.findAllById(ids).stream().filter(p->!p.isAccessory()&&!"RETIRED".equals(p.getStockStatus())).map(Product::getId).collect(java.util.stream.Collectors.toSet());
  if(allowed.size()!=ids.size())throw new IllegalArgumentException("Sản phẩm trong khung "+period+" không hợp lệ.");
  return ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
 }
 private List<String> collectionSlots(String configuredIds){
  var slots=new ArrayList<>(Arrays.asList(Objects.toString(configuredIds,"").split(",",-1)));
  while(slots.size()<3)slots.add("");
  return slots.subList(0,3);
 }
}
