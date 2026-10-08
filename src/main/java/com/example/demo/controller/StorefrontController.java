package com.example.demo.controller;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.example.demo.service.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.time.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Controller @RequiredArgsConstructor
public class StorefrontController {
 private record CheckoutPreview(LocalDateTime start,LocalDateTime end,String fulfilment,String payment,String address,String code,boolean full,List<Long> productIds) {}
 @lombok.Getter @lombok.RequiredArgsConstructor
 private static class RentalPriceBreakdown {
  private final long hours;
  private final long hourlyRate;
  private final boolean exactHourlyRate;
 }
 private final ProductRepository products; private final CustomerRepository customers; private final StaffAccountRepository staff; private final PolicyRepository policies;
 private final RentalOrderRepository orders; private final SiteImageRepository images; private final OrderLineRepository lines;
 private final CartItemRepository cartItems;
 private final CheckinGallery checkinGallery;
 private final MoneyRepository money;
 private final SaleInvoiceRepository saleInvoices; private final Operations ops; private final PurchaseService purchases; private final ImageStorage storage; private final BCryptPasswordEncoder encoder;
 public static boolean employee(Authentication a){return a!=null&&a.getAuthorities().stream().anyMatch(r->Set.of("ROLE_STAFF","ROLE_ADMIN").contains(r.getAuthority()));}
 @GetMapping("/") String home(Model m){
  m.addAttribute("productImages",displayImages());
  var frames=checkinGallery.frames();
  m.addAttribute("checkinLeft",frames.subList(0,3));
  m.addAttribute("checkinRight",frames.subList(3,6));
  m.addAttribute("media",images.findAll().stream().filter(i->i.getImageUrl()!=null).collect(Collectors.toMap(SiteImage::getSlot,SiteImage::getImageUrl)));
  var settings=policies.findById(1L).orElseGet(ShopPolicy::new);
  var availableOutfits=products.findAll().stream().filter(p->!p.isAccessory()&&!"RETIRED".equals(p.getStockStatus())).toList();
  m.addAttribute("homeSettings",settings);
  m.addAttribute("homeVideoEmbed",YouTubeVideo.embed(settings.getHomeVideoUrl()));
  m.addAttribute("dayProducts",homeCollection(settings.getDayCollectionProductIds(),availableOutfits,0));
  m.addAttribute("nightProducts",homeCollection(settings.getNightCollectionProductIds(),availableOutfits,3));
  return "home";
 }
 private List<Product> homeCollection(String configuredIds,List<Product> availableOutfits,int fallbackOffset){
  var byId=availableOutfits.stream().collect(Collectors.toMap(Product::getId,p->p));
  var selected=Arrays.stream(Objects.toString(configuredIds,"").split(",")).filter(id->id.matches("\\d+"))
   .map(Long::valueOf).map(byId::get).filter(Objects::nonNull).distinct().toList();
  if(!selected.isEmpty())return new ArrayList<>(selected.stream().collect(Collectors.toMap(
   this::suggestionName,p->p,(first,duplicate)->first,LinkedHashMap::new)).values()).stream().limit(3).toList();
  var unique=uniqueCollectionProducts(availableOutfits.stream());
  var fallback=unique.stream().skip(fallbackOffset).limit(3).toList();
  return fallback.isEmpty()?unique.stream().limit(3).toList():fallback;
 }
 @GetMapping("/collection") String collection(@RequestParam(defaultValue="") String q,Model m){
  m.addAttribute("productImages",displayImages());
  var search=normalizedProductName(q);
  m.addAttribute("q",q.trim());
  m.addAttribute("products",uniqueCollectionProducts(products.findAll().stream()
   .filter(p->!"RETIRED".equals(p.getStockStatus()))
   .filter(p->suggestionName(p).contains(search))));
  return "collection";
 }
 private List<Product> uniqueCollectionProducts(java.util.stream.Stream<Product> candidates){
  // Một thẻ công khai cho mỗi tên mẫu, không dùng giá/size/màu làm khóa gây lặp.
  // Dữ liệu kho, biến thể và lịch sử đơn vẫn giữ nguyên; toàn bộ thẻ lấy từ cùng một mã.
  return new ArrayList<>(candidates.sorted(Comparator
   .comparing((Product p)->!"AVAILABLE".equals(p.getStockStatus())).thenComparing(Product::getId))
   .collect(Collectors.toMap(this::suggestionName,p->p,(first,duplicate)->first,LinkedHashMap::new)).values());
 }
 private Map<Long,String> displayImages(){
  var all=products.findAll().stream().sorted(Comparator.comparing(Product::getId)).toList();
  var uploaded=new LinkedHashMap<String,String>();
  // Giữ ảnh của mẫu kể cả khi một mã vật lý nghỉ cho thuê; không đổi dữ liệu JPA.
  for(var product:all)if(storage.hasUploadedImage(product.getImageUrl()))
   uploaded.putIfAbsent(suggestionName(product),product.getImageUrl());
  var result=new HashMap<Long,String>();
  for(var product:all)result.put(product.getId(),storage.hasUploadedImage(product.getImageUrl())?product.getImageUrl():
   uploaded.getOrDefault(suggestionName(product),Objects.toString(product.getImageUrl(),"")));
  return result;
 }
 private String suggestionName(Product product){
  return normalizedProductName(product.getName());
 }
 private String normalizedProductName(String name){
  return java.text.Normalizer.normalize(Objects.toString(name,""),java.text.Normalizer.Form.NFC)
   .strip().replaceAll("(?U)\\s+"," ").toLowerCase(Locale.ROOT);
 }
 @GetMapping("/product/{id}") String product(@PathVariable Long id,Model m){
  m.addAttribute("productImages",displayImages());
  var p=products.findById(id).orElseThrow();m.addAttribute("product",p);
  // Mỗi tên mẫu chỉ có một thẻ gợi ý; không xóa hay gộp các mã hàng vật lý trong kho.
  // Ưu tiên mã đang sẵn sàng, rồi mã có ID nhỏ nhất để lựa chọn luôn ổn định.
  m.addAttribute("suggestions",uniqueCollectionProducts(products.findAll().stream().filter(Product::isAccessory)
   .filter(candidate->!suggestionName(candidate).equals(suggestionName(p)))
   .filter(candidate->!"RETIRED".equals(candidate.getStockStatus()))));
  return "product";
 }
 @GetMapping("/product/{id}/availability") String availability(@PathVariable Long id,Model m){
  var product=products.findById(id).orElseThrow();
  var days=new ArrayList<LocalDate>();
  for(int offset=1;offset<=14;offset++){
   var date=LocalDate.now().plusDays(offset);
   if(ops.available(product,date.atStartOfDay(),date.plusDays(1).atStartOfDay()))days.add(date);
  }
  m.addAttribute("product",product);m.addAttribute("availabilityDays",days);return "product-availability";
 }
 private Optional<Customer> cartCustomer(Authentication authentication){
  return authentication==null?Optional.empty():customers.findByEmail(authentication.getName());
 }
 @SuppressWarnings("unchecked") private List<Long> cart(HttpSession s,Authentication authentication){
  var customer=cartCustomer(authentication);
  if(customer.isPresent())return cartItems.findByCustomerIdOrderByIdAsc(customer.get().getId()).stream().map(item->item.getProduct().getId()).toList();
  if(s.getAttribute("cart")==null)s.setAttribute("cart",new ArrayList<Long>());
  return (List<Long>)s.getAttribute("cart");
 }
 private void clearCart(HttpSession s,Authentication authentication){
  var customer=cartCustomer(authentication);
  if(customer.isPresent())cartItems.deleteByCustomerId(customer.get().getId());else cart(s,authentication).clear();
 }
 private List<Product> selectedCartProducts(List<Long> productIds,HttpSession s,Authentication authentication){
  var customer=cartCustomer(authentication).orElseThrow(()->new IllegalArgumentException("Đăng nhập tài khoản khách hàng để mua sản phẩm."));
  if(productIds==null||productIds.isEmpty()||productIds.stream().anyMatch(Objects::isNull)||new HashSet<>(productIds).size()!=productIds.size())throw new IllegalArgumentException("Chọn ít nhất một sản phẩm khác nhau để mua.");
  var cartIds=new HashSet<>(cart(s,authentication));
  if(!cartIds.containsAll(productIds))throw new IllegalArgumentException("Sản phẩm mua phải có trong giỏ của bạn.");
  return productIds.stream().map(id->products.findById(id).orElseThrow()).peek(product->{
   if(product.getSalePrice()==null||product.getSalePrice()<=0||!"AVAILABLE".equals(product.getStockStatus()))throw new IllegalArgumentException("Sản phẩm "+product.getName()+" hiện không mở bán.");
  }).toList();
 }
 private void removeFromCart(List<Long> productIds,HttpSession s,Authentication authentication){
  var customer=cartCustomer(authentication).orElseThrow();
  for(var productId:productIds)cartItems.deleteByCustomerIdAndProductId(customer.getId(),productId);
 }
 @PostMapping("/cart/add/{id}") @Transactional String add(@PathVariable Long id,HttpSession s,Authentication authentication){
  if(!products.existsById(id))throw new IllegalArgumentException("Không tìm thấy sản phẩm.");
  var customer=cartCustomer(authentication);
  if(customer.isPresent()){
   if(!cartItems.existsByCustomerIdAndProductId(customer.get().getId(),id))cartItems.save(CartItem.builder().customer(customer.get()).product(products.getReferenceById(id)).build());
  }else if(!cart(s,authentication).contains(id))cart(s,authentication).add(id);
  return "redirect:/cart";
 }
 @PostMapping("/cart/remove/{id}") @Transactional String remove(@PathVariable Long id,HttpSession s,Authentication authentication){
  var customer=cartCustomer(authentication);if(customer.isPresent())cartItems.deleteByCustomerIdAndProductId(customer.get().getId(),id);else cart(s,authentication).remove(id);return "redirect:/cart";
 }
 @PostMapping("/cart/replace/{id}") @Transactional String replace(@PathVariable Long id,@RequestParam Long replacement,HttpSession s,Authentication authentication){
  var replacementProduct=products.findById(replacement).orElseThrow(()->new IllegalArgumentException("Không tìm thấy sản phẩm thay thế."));
  if(!"AVAILABLE".equals(replacementProduct.getStockStatus()))throw new IllegalArgumentException("Sản phẩm thay thế hiện không sẵn sàng cho thuê.");
  var customer=cartCustomer(authentication);
  if(customer.isPresent()){
   cartItems.deleteByCustomerIdAndProductId(customer.get().getId(),id);
   if(!cartItems.existsByCustomerIdAndProductId(customer.get().getId(),replacement))cartItems.save(CartItem.builder().customer(customer.get()).product(replacementProduct).build());
  }else {var items=cart(s,authentication);items.remove(id);if(!items.contains(replacement))items.add(replacement);}
  return "redirect:/cart";
 }
 private List<Long> rentalSelection(List<Long> selected,HttpSession s,Authentication authentication){
  var current=cart(s,authentication);
  var ids=selected==null?new ArrayList<>(current):new ArrayList<>(selected);
  if(ids.isEmpty()||ids.contains(null)||new HashSet<>(ids).size()!=ids.size()||!current.containsAll(ids))
   throw new IllegalArgumentException("Sản phẩm thuê phải còn trong giỏ của bạn.");
  return List.copyOf(ids);
 }
 @GetMapping("/cart/rent") String rentCart(@RequestParam List<Long> productIds,HttpSession s,Authentication authentication,Model m){
  var ids=rentalSelection(productIds,s,authentication);
  m.addAttribute("items",products.findAllById(ids));return "cart-rent";
 }
 @GetMapping({"/cart","/checkout"}) String cartPage(@RequestParam(defaultValue="false") boolean rent,HttpSession s,Authentication authentication,Model m){
  var items=products.findAllById(cart(s,authentication));
  m.addAttribute("items",items);m.addAttribute("productImages",displayImages());m.addAttribute("rentMode",rent);
  m.addAttribute("products",products.findAll().stream().filter(p->"AVAILABLE".equals(p.getStockStatus())).toList());return "cart";
 }
 @GetMapping("/cart/purchase") String cartPurchasePage(@RequestParam List<Long> productIds,HttpSession s,Authentication authentication,Model m){
  var selected=selectedCartProducts(productIds,s,authentication);
  m.addAttribute("items",selected);m.addAttribute("total",selected.stream().mapToLong(Product::getSalePrice).sum());m.addAttribute("policy",ops.policy());return "cart-purchase";
 }
 @PostMapping("/cart/purchase") @Transactional String purchaseCart(@RequestParam List<Long> productIds,@RequestParam String paymentMethod,
   @RequestParam(defaultValue="") String paymentReference,HttpSession s,Authentication authentication){
  selectedCartProducts(productIds,s,authentication);
  purchases.buyAll(productIds,authentication.getName(),paymentMethod,paymentReference);
  removeFromCart(productIds,s,authentication);return "redirect:/account";
 }
 @PostMapping("/checkout/preview") String preview(@RequestParam LocalDateTime start,@RequestParam LocalDateTime end,@RequestParam String fulfilment,
  @RequestParam String payment,@RequestParam(defaultValue="") String address,@RequestParam(defaultValue="") String code,
  @RequestParam(defaultValue="false") boolean full,@RequestParam(required=false) List<Long> productIds,HttpSession s,Authentication authentication){
  var ids=rentalSelection(productIds,s,authentication);
  s.setAttribute("checkoutPreview",new CheckoutPreview(start,end,fulfilment,payment,address,code,full,ids));return "redirect:/checkout/preview";
 }
 @GetMapping("/checkout/preview") String previewPage(HttpSession s,Authentication authentication,Model m){
  var preview=(CheckoutPreview)s.getAttribute("checkoutPreview");
  if(preview==null)return "redirect:/cart";
  if(!cart(s,authentication).containsAll(preview.productIds())){s.removeAttribute("checkoutPreview");return "redirect:/cart";}
  m.addAttribute("productIds",preview.productIds());
  m.addAttribute("quote",ops.preview(preview.productIds(),preview.start(),preview.end(),preview.code(),preview.fulfilment(),preview.full()));
  m.addAttribute("start",preview.start());m.addAttribute("end",preview.end());m.addAttribute("fulfilment",preview.fulfilment());m.addAttribute("payment",preview.payment());
  m.addAttribute("address",preview.address());m.addAttribute("code",preview.code());m.addAttribute("full",preview.full());return "quote";
 }
 @PostMapping("/checkout") @Transactional String checkout(@RequestParam LocalDateTime start,@RequestParam LocalDateTime end,@RequestParam String fulfilment,
  @RequestParam String payment,@RequestParam(defaultValue="") String address,@RequestParam(defaultValue="") String code,
  @RequestParam(defaultValue="false") boolean full,@RequestParam(required=false) List<Long> productIds,HttpSession s,Authentication a){
  var ids=rentalSelection(productIds,s,a);
  var o=ops.book(a.getName(),a.getName(),ids,start,end,fulfilment,payment,address,code,full,a.getName());
  removeFromCart(ids,s,a);s.removeAttribute("checkoutPreview");return "redirect:/orders/"+o.getId();
 }
 @GetMapping("/account") String account(Authentication a,Model m){
  m.addAttribute("orders",orders.findByCustomerEmailOrderByIdDesc(a.getName()).stream().filter(o->!Boolean.TRUE.equals(o.getCustomerDeleted())).toList());
  m.addAttribute("saleInvoices",saleInvoices.findByCustomerEmailOrderByIssuedAtDesc(a.getName()));
  return "account";
 }
 @GetMapping("/purchase/{id}") String purchasePage(@PathVariable Long id,Model m){
  var product=products.findById(id).orElseThrow();
  if(product.getSalePrice()==null||product.getSalePrice()<=0||!"AVAILABLE".equals(product.getStockStatus()))throw new IllegalArgumentException("Sản phẩm không còn sẵn sàng để bán.");
  m.addAttribute("product",product);m.addAttribute("policy",ops.policy());return "purchase";
 }
 @PostMapping("/purchase/{id}") String purchase(@PathVariable Long id,@RequestParam String paymentMethod,
   @RequestParam(defaultValue="") String paymentReference,Authentication a){
  purchases.buy(id,a.getName(),paymentMethod,paymentReference);return "redirect:/account";
 }
 @GetMapping({"/orders/{id}","/staff/document/{id}"}) String detail(@PathVariable Long id,@RequestParam(defaultValue="false") boolean paymentReported,Authentication a,Model m){
  var o=orders.findById(id).orElseThrow();if(!employee(a)&&!a.getName().equals(o.getCustomerEmail()))throw new IllegalArgumentException("Bạn không có quyền xem đơn này.");
  var orderLines=lines.findByRentalOrderId(id);
  m.addAttribute("order",o);m.addAttribute("items",orderLines);
  m.addAttribute("customerOwner",a.getName().equals(o.getCustomerEmail()));
  m.addAttribute("canManageRental",employee(a)||a.getName().equals(o.getCustomerEmail()));
  String editBase=employee(a)?(a.getAuthorities().stream().anyMatch(role->role.getAuthority().equals("ROLE_ADMIN"))?"/admin/orders/":"/staff/orders/"):"/orders/";
  m.addAttribute("rentalEditUrl",editBase+id+"/edit");
  m.addAttribute("rentalDeleteUrl",editBase+id+"/delete");
  m.addAttribute("orderProductIds",orderLines.stream().map(line->line.getProduct().getId()).toList());
  m.addAttribute("rentalPriceBreakdowns",orderLines.stream().collect(Collectors.toMap(OrderLine::getId,this::rentalPriceBreakdown)));
  m.addAttribute("entries",money.findByRentalOrderId(id));
  var policy=ops.policy();m.addAttribute("policy",policy);m.addAttribute("bankQrUrl",bankQrUrl(policy,o));m.addAttribute("paymentReported",paymentReported);
  m.addAttribute("allProducts",products.findAll().stream().filter(p->!"RETIRED".equals(p.getStockStatus())).toList());
  return "order";
 }
 private String bankQrUrl(ShopPolicy policy,RentalOrder order){
  var bankBin=Objects.toString(policy.getBankBin(),"").trim();
  var account=Objects.toString(policy.getBankAccount(),"").replaceAll("\\s+","");
  if(!bankBin.matches("\\d{6}")||account.isBlank()||order.getBookingDeposit()<=0)return null;
  var note=URLEncoder.encode(order.getPaymentCode(),StandardCharsets.UTF_8);
  var owner=URLEncoder.encode(Objects.toString(policy.getBankOwner(),""),StandardCharsets.UTF_8);
  return "https://img.vietqr.io/image/"+bankBin+"-"+account+"-compact2.png?amount="+order.getBookingDeposit()+"&addInfo="+note+"&accountName="+owner;
 }
 private RentalPriceBreakdown rentalPriceBreakdown(OrderLine line){
  var order=line.getRentalOrder();
  long hours=order.getPickupAt()!=null&&order.getReturnAt()!=null
   ?Math.max(1,(java.time.temporal.ChronoUnit.MINUTES.between(order.getPickupAt(),order.getReturnAt())+59)/60)
   :Math.max(1,java.time.temporal.ChronoUnit.DAYS.between(order.getPickupDate(),order.getReturnDate())+1)*24;
  boolean exact=line.getRent()%hours==0;
  return new RentalPriceBreakdown(hours,exact?line.getRent()/hours:line.getProduct().getDailyPrice(),exact);
 }
 @PostMapping("/orders/{id}/bank-transfer-notice") String bankTransferNotice(@PathVariable Long id,@RequestParam String reference,Authentication a){
  var order=orders.findById(id).orElseThrow();
  if(!a.getName().equals(order.getCustomerEmail())||!Operations.PENDING.equals(order.getStatus())||!"BANK".equals(order.getPaymentMethod()))throw new IllegalArgumentException("Không thể gửi thông báo chuyển khoản cho đơn này.");
  if(reference.isBlank()||reference.trim().length()>120)throw new IllegalArgumentException("Nhập mã giao dịch chuyển khoản hợp lệ.");
  order.setBookingPaymentReference(reference.trim());orders.save(order);return "redirect:/orders/"+id+"?paymentReported";
 }
 @PostMapping("/orders/{id}/cancel") String cancel(@PathVariable Long id,Authentication a){ops.cancel(id,a.getName(),employee(a));return "redirect:/orders/"+id;}
 @PostMapping("/orders/{id}/edit") String editOrder(@PathVariable Long id,@RequestParam List<Long> productIds,@RequestParam String customerName,
   @RequestParam LocalDateTime start,@RequestParam LocalDateTime end,@RequestParam String fulfilment,
   @RequestParam String payment,@RequestParam(defaultValue="") String address,@RequestParam(defaultValue="") String code,Authentication a){
  ops.updateCustomerOrder(id,a.getName(),productIds,customerName,start,end,fulfilment,payment,address,code);return "redirect:/orders/"+id;
 }
 @PostMapping("/orders/{id}/delete") String deleteOrder(@PathVariable Long id,Authentication a){ops.deleteCustomerOrder(id,a.getName());return "redirect:/account";}
 @PostMapping("/account/purchases/{id}/edit") String editPurchase(@PathVariable Long id,@RequestParam Long productId,
   @RequestParam String customerName,@RequestParam String paymentMethod,@RequestParam(defaultValue="") String paymentReference,Authentication a){
  purchases.update(id,a.getName(),productId,customerName,paymentMethod,paymentReference);return "redirect:/account/purchases/"+id;
 }
 @PostMapping("/account/purchases/{id}/delete") String deletePurchase(@PathVariable Long id,Authentication a){purchases.deleteUnpaid(id,a.getName());return "redirect:/account";}
 @GetMapping({"/login","/staff/login"}) String login(){return "login";}
 @GetMapping({"/register","/staff/register"}) String register(HttpServletRequest r,Model m){m.addAttribute("staffSignup",r.getRequestURI().startsWith("/staff"));return "register";}
 @PostMapping({"/register","/staff/register"}) String signup(@RequestParam String email,@RequestParam String password,@RequestParam String fullName,
  @RequestParam(defaultValue="") String phone,HttpServletRequest r){
  email=email.trim().toLowerCase();
  if(!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")||password.length()<8||fullName.isBlank())throw new IllegalArgumentException("Nhập tên, email hợp lệ và mật khẩu ít nhất 8 ký tự.");
  if(customers.findByEmail(email).isPresent()||staff.findByEmail(email).isPresent())throw new IllegalArgumentException("Email đã tồn tại.");
  if(r.getRequestURI().startsWith("/staff")){
   staff.save(StaffAccount.builder().email(email).fullName(fullName).password(encoder.encode(password)).role("STAFF").enabled(false).build());
   return "redirect:/login?registered=staff";
  }
  // Khách hàng được tạo là tài khoản sử dụng ngay; không có bước quản trị viên phê duyệt.
  customers.save(Customer.builder().email(email).fullName(fullName).phone(phone).password(encoder.encode(password)).build());
  return "redirect:/login?registered=customer";
 }
}
