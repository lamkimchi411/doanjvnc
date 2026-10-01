package com.example.demo.controller;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.example.demo.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.time.*;
import java.util.*;

@Controller @RequestMapping("/admin") @RequiredArgsConstructor
public class AdminController {
 private final ProductRepository products; private final PolicyRepository policies; private final PromotionRepository promotions;
 private final PenaltyRepository penalties;private final StaffAccountRepository staff;private final SiteImageRepository images;
 private final CustomerRepository customers; private final TryOnAppointmentRepository appointments; private final CategoryRepository categories;
 private final MoneyRepository money;private final EventRepository events;
 private final ImageStorage storage;
 private final InventoryReportService reports;
 private final ProductService productService;
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
  m.addAttribute("penalties",penalties.findAll());m.addAttribute("staffAccounts",staff.findAll());return "admin";
 }
 private long sum(List<MoneyEntry> list,String kind){return list.stream().filter(e->kind.equals(e.getKind())).mapToLong(MoneyEntry::getAmount).sum();}
 @PostMapping("/staff/{id}") String approve(@PathVariable Long id,@RequestParam boolean enabled,Authentication a){
  var user=staff.findById(id).orElseThrow();if(user.getEmail().equals(a.getName()))throw new IllegalArgumentException("Không thể tự vô hiệu hóa tài khoản.");
  user.setEnabled(enabled);if(user.getRole()==null)user.setRole("STAFF");staff.save(user);return "redirect:/admin";
 }
 @PostMapping("/policy") @Transactional String policy(@RequestParam int weekendPercent,@RequestParam int holidayPercent,@RequestParam int bookingPercent,
 @RequestParam int appointmentCapacity,@RequestParam int holdMinutes,@RequestParam long shippingEachWay,@RequestParam long lateHourly,
 @RequestParam long lateDaily,@RequestParam int accessoryLossAlertPercent,@RequestParam int accessoryRestockTarget,@RequestParam int retirementRentalThreshold,@RequestParam int retirementWashThreshold,
 @RequestParam String holidays,@RequestParam String bankName,@RequestParam String bankAccount,@RequestParam String bankOwner){
  if(weekendPercent<1||holidayPercent<1||weekendPercent>1000||holidayPercent>1000||bookingPercent<20||bookingPercent>50||appointmentCapacity<1||holdMinutes<1||shippingEachWay<0||lateHourly<0||lateDaily<0||accessoryLossAlertPercent<0||accessoryLossAlertPercent>100||accessoryRestockTarget<1||retirementRentalThreshold<1||retirementWashThreshold<1)
   throw new IllegalArgumentException("Giá phải không âm; cọc giữ lịch 20–50%; sức chứa và thời gian giữ lịch phải dương.");
  for(var day:holidays.split("[,\\s]+"))if(!day.isBlank())LocalDate.parse(day);
  var p=policies.findById(1L).orElseThrow();p.setWeekendPercent(weekendPercent);p.setHolidayPercent(holidayPercent);p.setBookingPercent(bookingPercent);
  p.setAppointmentCapacity(appointmentCapacity);p.setHoldMinutes(holdMinutes);p.setShippingEachWay(shippingEachWay);p.setLateHourly(lateHourly);p.setLateDaily(lateDaily);p.setAccessoryLossAlertPercent(accessoryLossAlertPercent);p.setAccessoryRestockTarget(accessoryRestockTarget);p.setRetirementRentalThreshold(retirementRentalThreshold);p.setRetirementWashThreshold(retirementWashThreshold);
  p.setHolidays(holidays);p.setBankName(bankName);p.setBankAccount(bankAccount);p.setBankOwner(bankOwner);return "redirect:/admin";
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
 @RequestParam String components,@RequestParam String description,@RequestParam(defaultValue="false") boolean accessory,@RequestParam(required=false) MultipartFile image){
  productService.save(id,quantity,name,category,size,color,style,dailyPrice,depositAmount,components,description,accessory,image);return "redirect:/admin/qr";
 }
 @GetMapping("/product/{id}") String edit(@PathVariable Long id,Model m){m.addAttribute("product",products.findById(id).orElseThrow());m.addAttribute("events",events.findByProductIdOrderByRecordedAtDesc(id));m.addAttribute("policy",policies.findById(1L).orElseThrow());m.addAttribute("categories",categories.findAll());return "admin-product";}
 @GetMapping("/product/new") String create(Model m){var p=new Product();p.setSize("M");m.addAttribute("product",p);m.addAttribute("events",List.of());m.addAttribute("policy",policies.findById(1L).orElseThrow());m.addAttribute("categories",categories.findAll());return "admin-product";}
 @PostMapping("/product/{id}/delete") String deleteProduct(@PathVariable Long id){
  if(events.findByProductIdOrderByRecordedAtDesc(id).stream().anyMatch(e->!"AVAILABLE".equals(e.getNextStatus())))throw new IllegalArgumentException("Không thể xóa mã đồ đã phát sinh vận hành; hãy chuyển trạng thái ngừng cho thuê.");
  products.deleteById(id);return "redirect:/admin/qr";
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
 @PostMapping("/customers/{id}") String updateCustomer(@PathVariable Long id,@RequestParam String fullName,@RequestParam String phone){var c=customers.findById(id).orElseThrow();if(fullName.isBlank())throw new IllegalArgumentException("Tên khách hàng không được để trống.");c.setFullName(fullName.trim());c.setPhone(phone.trim());customers.save(c);return "redirect:/admin/users";}
 @PostMapping("/customers/{id}/delete") String deleteCustomer(@PathVariable Long id){customers.deleteById(id);return "redirect:/admin/users";}
 @PostMapping("/staff/{id}/update") String updateStaff(@PathVariable Long id,@RequestParam String fullName,@RequestParam String role,@RequestParam(defaultValue="false") boolean enabled,Authentication a){var s=staff.findById(id).orElseThrow();if(s.getEmail().equals(a.getName())&&(!enabled||!"ADMIN".equals(role)))throw new IllegalArgumentException("Không thể tự tước quyền hoặc vô hiệu hóa tài khoản đang đăng nhập.");s.setFullName(fullName.trim());s.setRole("ADMIN".equals(role)?"ADMIN":"STAFF");s.setEnabled(enabled);staff.save(s);return "redirect:/admin/users";}
 @GetMapping("/appointments") String appointmentList(Model m){m.addAttribute("appointments",appointments.findAll());return "admin-appointments";}
 @PostMapping("/appointments/{id}") String updateAppointment(@PathVariable Long id,@RequestParam String customerName,@RequestParam String phone,@RequestParam LocalDateTime appointmentAt,@RequestParam String status,@RequestParam(defaultValue="") String note){var ap=appointments.findById(id).orElseThrow();ap.setCustomerName(customerName.trim());ap.setPhone(phone.trim());ap.setAppointmentAt(appointmentAt);ap.setStatus(status);ap.setNote(note.trim());appointments.save(ap);return "redirect:/admin/appointments";}
 @PostMapping("/appointments/{id}/delete") String deleteAppointment(@PathVariable Long id){appointments.deleteById(id);return "redirect:/admin/appointments";}
 @GetMapping("/qr") String qrInventory(Model m){m.addAttribute("products",products.findAll());m.addAttribute("policy",policies.findById(1L).orElseThrow());return "admin-qr";}
 @GetMapping({"/home-settings","/media","/home-content"}) String homeSettings(Model m){
  m.addAttribute("policy",policies.findById(1L).orElseThrow());m.addAttribute("images",images.findAll());return "admin-home-settings";
 }
 @PostMapping("/home-settings") @Transactional String saveHomeSettings(@RequestParam String homeTitle,@RequestParam String homeSubtitle,
  @RequestParam String storeAddress,@RequestParam String storePhone,@RequestParam String storeMapUrl,@RequestParam String storeSocialUrl,
  @RequestParam(required=false) List<String> slots,@RequestParam(required=false) List<String> imageTitles,
  @RequestParam(required=false) List<MultipartFile> imageFiles){
  var p=policies.findById(1L).orElseThrow();p.setHomeTitle(homeTitle.trim());p.setHomeSubtitle(homeSubtitle.trim());p.setStoreAddress(storeAddress.trim());p.setStorePhone(storePhone.trim());p.setStoreMapUrl(storeMapUrl.trim());p.setStoreSocialUrl(storeSocialUrl.trim());policies.save(p);
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
}
