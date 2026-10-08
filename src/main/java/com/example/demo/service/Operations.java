package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service @RequiredArgsConstructor
public class Operations {
 private final ProductRepository products;
 private final RentalOrderRepository orders;
 private final OrderLineRepository lines;
 private final PolicyRepository policies;
 private final PromotionRepository promotions;
 private final PenaltyRepository penalties;
 private final MoneyRepository money;
 private final EventRepository events;
 @PersistenceContext private EntityManager em;
 public static final String PENDING="Chờ thanh toán", RESERVED="Đã cọc giữ lịch", PREPARING="Đang chuẩn bị đồ",
 RENTED="Đã lấy đồ / Đang mặc", RETURNED="Đã trả đồ", REFUNDED="Đã hoàn cọc", CANCELLED="Đã hủy";
 public ShopPolicy policy(){return policies.findById(1L).orElseThrow();}
 private ShopPolicy lockPolicy(){return em.find(ShopPolicy.class,1L,LockModeType.PESSIMISTIC_WRITE);}
 private void require(boolean ok,String message){if(!ok)throw new IllegalArgumentException(message);}
 public void dates(LocalDate start,LocalDate end){
  require(start!=null&&end!=null&&!start.isBefore(LocalDate.now())&&!end.isBefore(start),"Ngày nhận/trả không hợp lệ.");
  require(ChronoUnit.DAYS.between(start,end)<=365,"Thời hạn thuê tối đa 366 ngày.");
 }
 public void dates(LocalDateTime start,LocalDateTime end){
  require(start!=null&&end!=null&&start.isAfter(LocalDateTime.now())&&end.isAfter(start),"Thời điểm nhận/trả không hợp lệ.");
  require(ChronoUnit.HOURS.between(start,end)<=366L*24,"Thời hạn thuê tối đa 366 ngày.");
 }
 public boolean active(RentalOrder o) {
  return Set.of(RESERVED,PREPARING,RENTED).contains(o.getStatus()) ||
    (PENDING.equals(o.getStatus()) && o.getHoldUntil()!=null && o.getHoldUntil().isAfter(LocalDateTime.now()));
 }
 public boolean available(Product p,LocalDate start,LocalDate end){
  if(Set.of("RENTED","WASHING","REPAIRING","RETIRED").contains(Objects.toString(p.getStockStatus(),"AVAILABLE")))return false;
  return lines.findByProductId(p.getId()).stream().noneMatch(l->{
   var o=l.getRentalOrder();
   return active(o)&&!o.getPickupDate().isAfter(end)&&!o.getReturnDate().isBefore(start);
  });
 }
 public boolean available(Product p,LocalDateTime start,LocalDateTime end){
  return availableExcept(p,start,end,null);
 }
 private boolean availableExcept(Product p,LocalDateTime start,LocalDateTime end,Long excludedOrder){
  if(Set.of("RENTED","WASHING","REPAIRING","RETIRED").contains(Objects.toString(p.getStockStatus(),"AVAILABLE")))return false;
  return lines.findByProductId(p.getId()).stream().noneMatch(l->{
   var o=l.getRentalOrder();
   LocalDateTime bookedStart=o.getPickupAt()!=null?o.getPickupAt():o.getPickupDate().atStartOfDay();
   LocalDateTime bookedEnd=o.getReturnAt()!=null?o.getReturnAt():o.getReturnDate().plusDays(1).atStartOfDay();
   return !Objects.equals(o.getId(),excludedOrder)&&active(o)&&bookedStart.isBefore(end)&&bookedEnd.isAfter(start);
  });
 }
 public long price(Product p,LocalDate start,LocalDate end,ShopPolicy policy) {
  long total=0;
  Set<String> holidays=new HashSet<>(Arrays.asList(Objects.toString(policy.getHolidays(),"").split("[,\\s]+")));
  for(LocalDate d=start;!d.isAfter(end);d=d.plusDays(1)){
   int percent=holidays.contains(d.toString())?policy.getHolidayPercent():
     (d.getDayOfWeek()==DayOfWeek.SATURDAY||d.getDayOfWeek()==DayOfWeek.SUNDAY)?policy.getWeekendPercent():100;
   total=Math.addExact(total,Math.multiplyExact(p.getDailyPrice(),percent)/100);
  }
  return total;
 }
 public long price(Product p,LocalDateTime start,LocalDateTime end,ShopPolicy policy) {
  long total=0;Set<String> holidays=new HashSet<>(Arrays.asList(Objects.toString(policy.getHolidays(),"").split("[,\\s]+")));
  long hours=Math.max(1,(ChronoUnit.MINUTES.between(start,end)+59)/60);
  for(long hour=0;hour<hours;hour++){
   LocalDate day=start.plusHours(hour).toLocalDate();
   int percent=holidays.contains(day.toString())?policy.getHolidayPercent():(day.getDayOfWeek()==DayOfWeek.SATURDAY||day.getDayOfWeek()==DayOfWeek.SUNDAY)?policy.getWeekendPercent():100;
   total=Math.addExact(total,Math.multiplyExact(p.getDailyPrice(),percent)/100);
  }
  return total;
 }
 public record Quote(long rent,long discount,long shipping,long security,long booking,long total){}
 private Quote quote(List<Product> selected,LocalDate start,LocalDate end,String code,String fulfilment,boolean full,boolean consume) {
  var policy=policy(); long rent=selected.stream().mapToLong(p->price(p,start,end,policy)).sum();
  long discount=0;
  if(code!=null&&!code.isBlank()){
   var promotion=em.find(Promotion.class,code.trim().toUpperCase(),consume?LockModeType.PESSIMISTIC_WRITE:LockModeType.NONE);
   require(promotion!=null,"Mã giảm giá không tồn tại.");
   require(promotion.isActive()&&!LocalDate.now().isBefore(promotion.getStartDate())&&!LocalDate.now().isAfter(promotion.getEndDate()),"Mã giảm giá ngoài thời hạn.");
   require(promotion.getUsageLimit()==0||promotion.getUsedCount()<promotion.getUsageLimit(),"Mã đã hết lượt.");
   require(rent>=promotion.getMinimumRent(),"Đơn chưa đạt mức tối thiểu của mã giảm giá.");
   List<String> categories=new ArrayList<>(selected.stream().map(p->p.isAccessory()?"Phụ kiện":p.getCategory()).toList());
   for(String category:Objects.toString(promotion.getRequiredCategories(),"").split(",")){
    if(!category.isBlank())require(categories.remove(category.trim()),"Chưa đủ thành phần combo: "+category);
   }
   discount=rent*promotion.getPercent()/100;
  }
  long shipping="DELIVERY".equals(fulfilment)?policy.getShippingEachWay()*2:0;
  long security=selected.stream().mapToLong(Product::getDepositAmount).sum();
  long booking=full?security:(rent-discount)*policy.getBookingPercent()/100;
  return new Quote(rent,discount,shipping,security,booking,rent-discount+shipping+security);
 }
 @Transactional public Quote preview(List<Long> ids,LocalDate start,LocalDate end,String code,String fulfilment,boolean full){
  dates(start,end);return quote(select(ids,false),start,end,code,fulfilment,full,false);
 }
 @Transactional public Quote preview(List<Long> ids,LocalDateTime start,LocalDateTime end,String code,String fulfilment,boolean full){
  return previewExcept(ids,start,end,code,fulfilment,full,null);
 }
 private Quote previewExcept(List<Long> ids,LocalDateTime start,LocalDateTime end,String code,String fulfilment,boolean full,Long excludedOrder){
  dates(start,end);var selected=select(ids,false);selected.forEach(p->require(availableExcept(p,start,end,excludedOrder),"Mã "+p.getBarcode()+" không còn trống trong thời gian đã chọn."));
  var policy=policy();long rent=selected.stream().mapToLong(p->price(p,start,end,policy)).sum();long discount=0;
  if(code!=null&&!code.isBlank()){
   var promotion=em.find(Promotion.class,code.trim().toUpperCase());require(promotion!=null,"Mã giảm giá không tồn tại.");
   require(promotion.isActive()&&!LocalDate.now().isBefore(promotion.getStartDate())&&!LocalDate.now().isAfter(promotion.getEndDate()),"Mã giảm giá ngoài thời hạn.");
   require(promotion.getUsageLimit()==0||promotion.getUsedCount()<promotion.getUsageLimit(),"Mã đã hết lượt.");require(rent>=promotion.getMinimumRent(),"Đơn chưa đạt mức tối thiểu của mã giảm giá.");
   List<String> categories=new ArrayList<>(selected.stream().map(p->p.isAccessory()?"Phụ kiện":p.getCategory()).toList());for(String category:Objects.toString(promotion.getRequiredCategories(),"").split(","))if(!category.isBlank())require(categories.remove(category.trim()),"Chưa đủ thành phần combo: "+category);
   discount=rent*promotion.getPercent()/100;
  }
  long shipping="DELIVERY".equals(fulfilment)?policy.getShippingEachWay()*2:0;long security=selected.stream().mapToLong(Product::getDepositAmount).sum();long booking=full?security:(rent-discount)*policy.getBookingPercent()/100;
  return new Quote(rent,discount,shipping,security,booking,rent-discount+shipping+security);
 }
 private List<Product> select(List<Long> ids,boolean lock){
  require(ids!=null&&!ids.isEmpty(),"Giỏ hàng đang trống.");
  require(new HashSet<>(ids).size()==ids.size(),"Mỗi mã tem là một bộ đồ riêng; hãy chọn mã khác cho bộ thứ hai.");
  return ids.stream().sorted().map(id->{
   Product p=em.find(Product.class,id,lock?LockModeType.PESSIMISTIC_WRITE:LockModeType.NONE);
   require(p!=null,"Sản phẩm không tồn tại.");return p;
  }).toList();
 }
 /** Lưu số liên hệ riêng của đơn tại quầy trong cùng giao dịch tạo đơn. */
 @Transactional public RentalOrder bookAtCounter(String email,String name,String phone,List<Long> ids,
   LocalDate start,LocalDate end,String code,String actor){
  String normalized=phone==null?"":phone.trim().replaceAll("[\\s().-]", "");
  require(normalized.matches("\\+?[0-9]{9,15}"),"Nhập số điện thoại hợp lệ gồm 9–15 chữ số.");
  var order=book(email,name,ids,start,end,"PICKUP","CASH","",code,false,actor);
  order.setCustomerPhone(normalized);
  return order;
 }
 @Transactional public RentalOrder book(String email,String name,List<Long> ids,LocalDate start,LocalDate end,
    String fulfilment,String payment,String address,String code,boolean full,String actor){
  dates(start,end);
  require(Set.of("PICKUP","DELIVERY").contains(fulfilment),"Hình thức nhận không hợp lệ.");
  require(Set.of("CASH","BANK").contains(payment),"Chọn tiền mặt hoặc chuyển khoản.");
  require(!"DELIVERY".equals(fulfilment)||(address!=null&&address.trim().length()>=10),"Nhập địa chỉ giao hàng đầy đủ.");
  var selected=select(ids,true);
  selected.forEach(p->require(available(p,start,end),"Mã "+p.getBarcode()+" không còn trống trong thời gian đã chọn."));
  var q=quote(selected,start,end,code,fulfilment,full,true);
  RentalOrder o=RentalOrder.builder().customerEmail(email).customerName(name).product(selected.get(0))
    .pickupDate(start).returnDate(end).fulfilment(fulfilment).paymentMethod(payment).address(address)
    .voucherCode(code).rentalTotal(q.rent()).depositTotal(q.security()).discountTotal(q.discount()).shippingTotal(q.shipping())
    .bookingDeposit(q.booking()).status(PENDING).holdUntil(LocalDateTime.now().plusMinutes(policy().getHoldMinutes()))
    .createdBy(actor).build();
  o.setCollateralType(full?"PREPAY_SECURITY":"PREPAY_RENT");orders.save(o);o.setPaymentCode(paymentCode(o.getId()));
  for(var p:selected){
   OrderLine l=new OrderLine();l.setRentalOrder(o);l.setProduct(p);l.setName(p.getName());l.setBarcode(p.getBarcode());
   l.setRent(price(p,start,end,policy()));l.setDeposit(p.getDepositAmount());l.setComponents(p.getComponents());lines.save(l);
  }
  return o;
 }
 @Transactional public RentalOrder book(String email,String name,List<Long> ids,LocalDateTime start,LocalDateTime end,
   String fulfilment,String payment,String address,String code,boolean full,String actor){
  dates(start,end);require(Set.of("PICKUP","DELIVERY").contains(fulfilment),"Hình thức nhận không hợp lệ.");require(Set.of("CASH","BANK").contains(payment),"Chọn tiền mặt hoặc chuyển khoản.");
  require(!"DELIVERY".equals(fulfilment)||(address!=null&&address.trim().length()>=10),"Nhập địa chỉ giao hàng đầy đủ.");
  var selected=select(ids,true);selected.forEach(p->require(available(p,start,end),"Mã "+p.getBarcode()+" không còn trống trong thời gian đã chọn."));
  var quote=preview(ids,start,end,code,fulfilment,full);RentalOrder order=RentalOrder.builder().customerEmail(email).customerName(name).product(selected.get(0)).pickupDate(start.toLocalDate()).returnDate(end.toLocalDate()).pickupAt(start).returnAt(end).fulfilment(fulfilment).paymentMethod(payment).address(address).voucherCode(code).rentalTotal(quote.rent()).depositTotal(quote.security()).discountTotal(quote.discount()).shippingTotal(quote.shipping()).bookingDeposit(quote.booking()).status(PENDING).holdUntil(LocalDateTime.now().plusMinutes(policy().getHoldMinutes())).createdBy(actor).build();
  order.setCollateralType(full?"PREPAY_SECURITY":"PREPAY_RENT");orders.save(order);order.setPaymentCode(paymentCode(order.getId()));
  for(var product:selected){OrderLine line=new OrderLine();line.setRentalOrder(order);line.setProduct(product);line.setName(product.getName());line.setBarcode(product.getBarcode());line.setRent(price(product,start,end,policy()));line.setDeposit(product.getDepositAmount());line.setComponents(product.getComponents());lines.save(line);}
  return order;
 }
 private RentalOrder locked(Long id) {
  var o=em.find(RentalOrder.class,id,LockModeType.PESSIMISTIC_WRITE);require(o!=null,"Không tìm thấy đơn.");return o;
 }
 private String paymentCode(Long id){return String.format(Locale.ROOT,"DH%08d",id);}
 private void entry(RentalOrder o,String kind,long amount,String method,String reference,String actor){
  require(amount>=0,"Số tiền không được âm.");
  require(Set.of("CASH","BANK").contains(method),"Phương thức không hợp lệ.");
  require(!"BANK".equals(method)||(reference!=null&&!reference.isBlank()),"Cần mã tham chiếu chuyển khoản.");
  if(amount==0)return;
  MoneyEntry e=new MoneyEntry();e.setRentalOrder(o);e.setKind(kind);e.setAmount(amount);e.setMethod(method);
  e.setReference(reference);e.setActor(actor);e.setRecordedAt(LocalDateTime.now());money.save(e);
 }
 @Transactional public void confirm(Long id,String method,String reference,String actor){
  var o=locked(id);require(PENDING.equals(o.getStatus())&&active(o),"Đơn đã xử lý hoặc hết thời gian giữ lịch.");
  confirmPending(o,method,reference,actor);
 }
 /** Nhân viên đã thực nhận tiền có thể giữ lại đơn quá hạn nếu các bộ đồ vẫn còn trống. */
 @Transactional public void confirmAtCounter(Long id,String method,String reference,String actor){
  var o=locked(id);require(PENDING.equals(o.getStatus()),"Đơn đã được xử lý.");
  LocalDateTime start=o.getPickupAt()!=null?o.getPickupAt():o.getPickupDate().atStartOfDay();
  LocalDateTime end=o.getReturnAt()!=null?o.getReturnAt():o.getReturnDate().plusDays(1).atStartOfDay();
  for(var line:lines.findByRentalOrderId(id)){
   var product=em.find(Product.class,line.getProduct().getId(),LockModeType.PESSIMISTIC_WRITE);
   require(availableExcept(product,start,end,id),"Mã "+product.getBarcode()+" không còn trống trong thời gian đã chọn.");
  }
  confirmPending(o,method,reference,actor);
 }
 private void confirmPending(RentalOrder o,String method,String reference,String actor){
  if(o.getVoucherCode()!=null&&!o.getVoucherCode().isBlank()){
   var promo=em.find(Promotion.class,o.getVoucherCode().trim().toUpperCase(),LockModeType.PESSIMISTIC_WRITE);
   require(promo!=null&&(promo.getUsageLimit()==0||promo.getUsedCount()<promo.getUsageLimit()),"Combo đã hết lượt; tạo lại đơn với mã khác.");
   promo.setUsedCount(promo.getUsedCount()+1);
  }
  boolean security="PREPAY_SECURITY".equals(o.getCollateralType());
  entry(o,security?"SECURITY_IN":"RENT_IN",o.getBookingDeposit(),method,reference,actor);
  if(security)o.setSecurityPaid(o.getBookingDeposit());else o.setRentalPaid(o.getBookingDeposit());
  o.setStatus(RESERVED);
  // Giữ lịch theo đơn, không ghi đè trạng thái vật lý của bộ đang thuê/giặt.
  for(var l:lines.findByRentalOrderId(o.getId())){
   var p=em.find(Product.class,l.getProduct().getId(),LockModeType.PESSIMISTIC_WRITE);
   if("AVAILABLE".equals(p.getStockStatus()))change(p,"RESERVED",actor,"Cọc giữ lịch đơn "+o.getId());
  }
 }
 @Transactional public boolean confirmBankWebhook(String paymentCode,long amount,String transactionId){
  require(paymentCode!=null&&paymentCode.matches("DH\\d{8}"),"Mã thanh toán không hợp lệ.");
  require(amount>0,"Số tiền giao dịch không hợp lệ.");
  var reference=Objects.toString(transactionId,"").trim();
  require(!reference.isBlank()&&reference.length()<=120,"Mã giao dịch không hợp lệ.");
  var o=orders.findByPaymentCodeForUpdate(paymentCode).orElseThrow(()->new IllegalArgumentException("Không tìm thấy đơn thanh toán."));
  if(RESERVED.equals(o.getStatus())&&reference.equals(o.getBookingPaymentReference()))return true;
  require(PENDING.equals(o.getStatus())&&active(o)&&"BANK".equals(o.getPaymentMethod()),"Đơn không còn chờ thanh toán chuyển khoản.");
  require(amount==o.getBookingDeposit(),"Số tiền chuyển khoản không khớp.");
  confirm(o.getId(),"BANK",reference,"webhook");
  o.setBookingPaymentReference(reference);
  return true;
 }
 @Transactional public void prepare(Long id){var o=locked(id);require(RESERVED.equals(o.getStatus()),"Đơn chưa cọc.");o.setStatus(PREPARING);}
 @Transactional public void updateStatusByAdmin(Long id,String nextStatus,String method,String reference,String actor){
  require(nextStatus!=null,"Trạng thái không hợp lệ.");
  switch(nextStatus){
   case RESERVED -> confirm(id,method,reference,actor);
   case PREPARING -> prepare(id);
   case CANCELLED -> cancel(id,actor,true);
   default -> throw new IllegalArgumentException("Trạng thái này phải được xử lý theo quy trình bàn giao, nhận trả hoặc hoàn cọc tại POS.");
  }
 }
 @Transactional public void cancel(Long id,String email,boolean staff){
  var o=locked(id);require(staff||o.getCustomerEmail().equals(email),"Không có quyền.");
  require(o.isEditable(),"Chỉ hủy đơn chưa thanh toán và chưa thu cọc.");o.setStatus(CANCELLED);
 }
 @Transactional public void updateCustomerOrder(Long id,String email,List<Long> productIds,String name,
   LocalDateTime start,LocalDateTime end,String fulfilment,String payment,String address,String code){
  var o=locked(id);require(o.getCustomerEmail().equals(email),"Không có quyền sửa đơn này.");
  require(o.isEditable(),"Chỉ được sửa đơn chưa thanh toán và chưa thu cọc.");
  require(name!=null&&!name.isBlank()&&name.trim().length()<=150,"Tên khách hàng không hợp lệ.");
  require(Set.of("PICKUP","DELIVERY").contains(fulfilment)&&Set.of("CASH","BANK").contains(payment),"Hình thức nhận/thanh toán không hợp lệ.");
  require(address!=null&&address.length()<=300&&(!"DELIVERY".equals(fulfilment)||address.trim().length()>=10),"Nhập địa chỉ giao hàng đầy đủ, tối đa 300 ký tự.");
  require(code!=null&&code.length()<=50,"Mã ưu đãi tối đa 50 ký tự.");
  var selected=select(productIds,true);
  var q=previewExcept(productIds,start,end,code,fulfilment,"PREPAY_SECURITY".equals(o.getCollateralType()),id);
  // Tính lại bằng nghiệp vụ dự toán chung; bỏ qua chính đơn đang sửa khi kiểm tra trùng lịch.
  lines.deleteAll(lines.findByRentalOrderId(id));lines.flush();
  o.setProduct(selected.get(0));o.setCustomerName(name.trim());o.setPickupAt(start);o.setReturnAt(end);
  o.setPickupDate(start.toLocalDate());o.setReturnDate(end.toLocalDate());o.setFulfilment(fulfilment);
  o.setPaymentMethod(payment);o.setAddress(address.trim());o.setVoucherCode(code.trim());
  o.setRentalTotal(q.rent());o.setDiscountTotal(q.discount());o.setDepositTotal(q.security());
  o.setShippingTotal(q.shipping());o.setBookingDeposit(q.booking());
  for(var p:selected){
   var l=new OrderLine();l.setRentalOrder(o);l.setProduct(p);l.setName(p.getName());l.setBarcode(p.getBarcode());
   l.setRent(price(p,start,end,policy()));l.setDeposit(p.getDepositAmount());l.setComponents(p.getComponents());lines.save(l);
  }
 }
 @Transactional public void deleteCustomerOrder(Long id,String email){
  var o=locked(id);require(o.getCustomerEmail().equals(email),"Không có quyền xóa đơn này.");
  require(o.isEditable(),"Chỉ được xóa đơn chưa thanh toán và chưa thu cọc.");
  // Giữ lịch sử phục vụ đối soát; trạng thái hủy giải phóng lịch thuê.
  o.setStatus(CANCELLED);o.setCustomerDeleted(true);
 }
 /** Nhân viên / admin sửa đơn thuê chưa thanh toán – không kiểm tra quyền email. */
 @Transactional public void updateStaffOrder(Long id,List<Long> productIds,String name,
   LocalDateTime start,LocalDateTime end,String fulfilment,String payment,String address,String code){
  var o=locked(id);
  require(o.isEditable(),"Chỉ được sửa đơn chưa thanh toán và chưa thu cọc.");
  require(name!=null&&!name.isBlank()&&name.trim().length()<=150,"Tên khách hàng không hợp lệ.");
  require(Set.of("PICKUP","DELIVERY").contains(fulfilment)&&Set.of("CASH","BANK").contains(payment),"Hình thức nhận/thanh toán không hợp lệ.");
  require(address!=null&&address.length()<=300&&(!"DELIVERY".equals(fulfilment)||address.trim().length()>=10),"Nhập địa chỉ giao hàng đầy đủ, tối đa 300 ký tự.");
  require(code!=null&&code.length()<=50,"Mã ưu đãi tối đa 50 ký tự.");
  var selected=select(productIds,true);
  var q=previewExcept(productIds,start,end,code,fulfilment,"PREPAY_SECURITY".equals(o.getCollateralType()),id);
  lines.deleteAll(lines.findByRentalOrderId(id));lines.flush();
  o.setProduct(selected.get(0));o.setCustomerName(name.trim());o.setPickupAt(start);o.setReturnAt(end);
  o.setPickupDate(start.toLocalDate());o.setReturnDate(end.toLocalDate());o.setFulfilment(fulfilment);
  o.setPaymentMethod(payment);o.setAddress(address.trim());o.setVoucherCode(code.trim());
  o.setRentalTotal(q.rent());o.setDiscountTotal(q.discount());o.setDepositTotal(q.security());
  o.setShippingTotal(q.shipping());o.setBookingDeposit(q.booking());
  for(var p:selected){
   var l=new OrderLine();l.setRentalOrder(o);l.setProduct(p);l.setName(p.getName());l.setBarcode(p.getBarcode());
   l.setRent(price(p,start,end,policy()));l.setDeposit(p.getDepositAmount());l.setComponents(p.getComponents());lines.save(l);
  }
 }
 /** Nhân viên / admin xóa đơn thuê chưa thanh toán – không kiểm tra quyền email. */
 @Transactional public void deleteStaffOrder(Long id){
  var o=locked(id);
  require(o.isEditable(),"Chỉ được xóa đơn chưa thanh toán và chưa thu cọc.");
  o.setStatus(CANCELLED);o.setCustomerDeleted(true);
 }
 @Transactional public void checkout(Long id,String scanned,String condition,String collateral,String reference,String method,String actor){
  var o=locked(id);require(Set.of(RESERVED,PREPARING).contains(o.getStatus()),"Đơn chưa sẵn sàng bàn giao.");
  LocalDateTime pickup=o.getPickupAt()!=null?o.getPickupAt():o.getPickupDate().atStartOfDay();
  LocalDateTime returnAt=o.getReturnAt()!=null?o.getReturnAt():o.getReturnDate().plusDays(1).atStartOfDay();
  require(!LocalDateTime.now().isBefore(pickup)&&!LocalDateTime.now().isAfter(returnAt),"Chỉ bàn giao trong thời gian thuê.");
  var items=lines.findByRentalOrderId(id); verifyScans(items,scanned);
  require(Set.of("CASH_DEPOSIT","CCCD","PASSPORT").contains(collateral),"Loại đảm bảo không hợp lệ.");
  require(condition!=null&&!condition.isBlank(),"Nhập tình trạng bàn giao.");
  require("CASH_DEPOSIT".equals(collateral)||(reference!=null&&!reference.isBlank()),"Nhập mã tham chiếu giấy tờ (không lưu ảnh giấy tờ).");
  for(var l:items){
   var p=em.find(Product.class,l.getProduct().getId(),LockModeType.PESSIMISTIC_WRITE);
   require(Set.of("AVAILABLE","RESERVED").contains(p.getStockStatus()),"Mã "+p.getBarcode()+" chưa sẵn sàng.");
   l.setConditionOut(condition);p.setRentalCount(p.getRentalCount()+1);change(p,"RENTED",actor,"Bàn giao "+id);
  }
  long rentDue=o.getRentalTotal()-o.getDiscountTotal()-o.getRentalPaid();
  entry(o,"RENT_IN",rentDue,method,reference,actor);o.setRentalPaid(o.getRentalPaid()+rentDue);
  entry(o,"SHIPPING_IN",o.getShippingTotal(),method,reference,actor);
  {
   long securityDue=o.getDepositTotal()-o.getSecurityPaid();
   entry(o,"SECURITY_IN",securityDue,method,reference,actor);o.setSecurityPaid(o.getSecurityPaid()+securityDue);
  }
  o.setCollateralType(collateral);o.setCollateralReference(reference);o.setConditionOut(condition);o.setCheckedOutAt(LocalDateTime.now());o.setStatus(RENTED);
 }
 private void verifyScans(List<OrderLine> items,String scanned) {
  Set<String> codes=scanCodes(scanned);
  require(codes.equals(new HashSet<>(items.stream().map(OrderLine::getBarcode).toList())),"Quét/nhập đầy đủ mã tem trong đơn, cách nhau bằng dấu phẩy.");
 }
 private Set<String> scanCodes(String scanned){
  Set<String> codes=new HashSet<>();
  for(String code:Objects.toString(scanned,"").trim().split("[,\\s]+"))if(!code.isBlank())codes.add(code);
  return codes;
 }
 public long lateFee(LocalDateTime due,LocalDateTime returned,ShopPolicy p){
  long seconds=Math.max(0,ChronoUnit.SECONDS.between(due,returned));
  long hours=(seconds+3599)/3600;
  return (hours/24)*p.getLateDaily()+Math.min((hours%24)*p.getLateHourly(),p.getLateDaily());
 }
 @Transactional public void checkin(Long id,String scanned,Map<Long,String> damageCodes,String condition,String actor){
  checkin(id,scanned,damageCodes,Map.of(),condition,actor);
 }
 @Transactional public void checkin(Long id,String scanned,Map<Long,String> damageCodes,Map<Long,String> returnNotes,String condition,String actor){
  var o=locked(id);require(RENTED.equals(o.getStatus()),"Đơn không ở trạng thái đang thuê.");
  var items=lines.findByRentalOrderId(id);
  require(condition!=null&&!condition.isBlank(),"Nhập tình trạng nhận trả.");
  Set<Long> itemIds=new HashSet<>(items.stream().map(OrderLine::getId).toList());
  require(itemIds.containsAll(damageCodes.keySet())&&itemIds.containsAll(returnNotes.keySet()),"Mã kiểm tra không thuộc đơn.");
  Set<String> expected=new HashSet<>(),missing=new HashSet<>();
  for(var l:items){
   var rule=penalties.findById(damageCodes.getOrDefault(l.getId(),"NORMAL")).orElseThrow(()->new IllegalArgumentException("Loại lỗi không hợp lệ."));
   if("LOSS".equals(rule.getIncidentType()))missing.add(l.getBarcode());else expected.add(l.getBarcode());
  }
  var actual=scanCodes(scanned);
  require(actual.containsAll(expected)&&items.stream().map(OrderLine::getBarcode).toList().containsAll(actual),"Quét đủ món đã trả; món bị mất phải chọn lỗi Mất đồ.");
  require(Collections.disjoint(actual,missing),"Mã đã quét nhận trả không thể đồng thời ghi mất đồ.");
  long damages=0;
  for(var l:items){
   var rule=penalties.findById(damageCodes.getOrDefault(l.getId(),"NORMAL")).orElseThrow(()->new IllegalArgumentException("Loại lỗi không hợp lệ."));
   l.setDamageCode(rule.getCode());l.setIncidentType(rule.getIncidentType());l.setDamageLabel(rule.getLabel());l.setDamageFee(rule.getAmount());l.setConditionReturn(condition);
   require(!"LOSS".equals(rule.getIncidentType())||!Objects.toString(returnNotes.get(l.getId()),"").isBlank(),"Ghi lý do mất cho mã "+l.getBarcode());
   l.setReturnNote(Objects.toString(returnNotes.get(l.getId()),""));damages+=rule.getAmount();
   var p=em.find(Product.class,l.getProduct().getId(),LockModeType.PESSIMISTIC_WRITE);
   change(p,"LOSS".equals(rule.getIncidentType())?"RETIRED":rule.getNextStockStatus(),actor,"Kiểm tra đơn "+id+": "+rule.getLabel()+"; "+l.getReturnNote());
  }
  long total=damages+lateFee(o.getReturnAt()!=null?o.getReturnAt():o.getReturnDate().plusDays(1).atStartOfDay(),LocalDateTime.now(),policy());
  o.setPenaltyTotal(total);o.setExtraDue(Math.max(0,total-o.getSecurityPaid()));
  o.setConditionReturn(condition);o.setReturnedAt(LocalDateTime.now());o.setStatus(RETURNED);
 }
 @Transactional public void refund(Long id,String method,String reference,String actor){
  var o=locked(id);require(RETURNED.equals(o.getStatus()),"Đơn chưa kiểm tra hoặc đã hoàn cọc.");
  long retained=Math.min(o.getSecurityPaid(),o.getPenaltyTotal());
  entry(o,"SECURITY_APPLIED",retained,method,reference,actor);
  entry(o,"PENALTY_IN",o.getExtraDue(),method,reference,actor);
  long refund=Math.max(0,o.getSecurityPaid()-o.getPenaltyTotal());
  entry(o,"SECURITY_REFUND",refund,method,reference,actor);
  o.setRefundedDeposit(refund);o.setRefundReference(reference);o.setRefundedAt(LocalDateTime.now());o.setStatus(REFUNDED);
 }
 private void change(Product p,String next,String actor,String note){
  InventoryEvent event=new InventoryEvent();event.setProduct(p);event.setPreviousStatus(p.getStockStatus());event.setNextStatus(next);
  event.setActor(actor);event.setNote(note);event.setRecordedAt(LocalDateTime.now());events.save(event);p.setStockStatus(next);products.save(p);
 }
 @Transactional public void stock(Long id,String next,String actor){
  var p=em.find(Product.class,id,LockModeType.PESSIMISTIC_WRITE);require(p!=null,"Không tìm thấy mã đồ.");
  String current=p.getStockStatus();
  Map<String,Set<String>> transitions=Map.of("AVAILABLE",Set.of("WASHING","REPAIRING","RETIRED"),"RESERVED",Set.of("WASHING","REPAIRING"),
    "WASHING",Set.of("AVAILABLE","REPAIRING","RETIRED"),"REPAIRING",Set.of("WASHING","AVAILABLE","RETIRED"));
  require(transitions.getOrDefault(current,Set.of()).contains(next),"Chuyển trạng thái không hợp lệ; bàn giao/nhận trả phải qua đơn.");
  if("WASHING".equals(current)&&"AVAILABLE".equals(next))p.setWashCount(p.getWashCount()+1);
  change(p,next,actor,"Bảo dưỡng kho");
 }
}
