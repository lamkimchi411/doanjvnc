package com.example.demo;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.example.demo.service.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockHttpSession;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @Transactional
class BusinessFlowTests {
 @Autowired Operations ops; @Autowired ProductRepository products; @Autowired RentalOrderRepository orders;
 @Autowired OrderLineRepository lines;@Autowired PolicyRepository policies;@Autowired MoneyRepository money;
 @Autowired PromotionRepository promotions;@Autowired WebApplicationContext context;
 @Autowired PenaltyRepository penalties;@Autowired EventRepository events;
 @Autowired InventoryReportService reports;@Autowired ProductService productService;
 MockMvc mvc; Product p;
 @Autowired CustomerRepository customers;
 @Autowired CartItemRepository cartItems;
 @Autowired SaleInvoiceRepository saleInvoices;
 @Test void cartActionsKeepRentalSelectionAndRemainingProducts() throws Exception {
  var customer=customers.save(Customer.builder().email("cart-flow@test.vn").fullName("Khách thử giỏ").build());
  p.setSalePrice(300000L);products.save(p);
  var other=products.save(Product.builder().name("Sản phẩm còn lại").barcode("CART-"+UUID.randomUUID()).size("M").dailyPrice(50000).depositAmount(100000).stockStatus("AVAILABLE").build());
  for(var item:List.of(p,other))cartItems.save(CartItem.builder().customer(customer).product(item).build());
  var session=new MockHttpSession();
  var auth=user(customer.getEmail()).roles("CUSTOMER");
  var html=mvc.perform(get("/cart").session(session).with(auth)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertFalse(html.contains("Lịch thuê &amp; dự toán"));assertFalse(html.contains("name=\"start\""));
  assertTrue(html.contains("/cart/rent"));assertTrue(html.contains("/cart/purchase"));
  mvc.perform(get("/cart/rent").session(session).with(auth).param("productIds",p.getId().toString())).andExpect(status().isOk()).andExpect(model().attribute("items",List.of(p)));
  mvc.perform(get("/cart/purchase").session(session).with(auth).param("productIds",p.getId().toString())).andExpect(status().isOk());
  mvc.perform(get("/cart/rent").session(session).with(user("stranger@test.vn")).param("productIds",p.getId().toString())).andExpect(status().isBadRequest());
  String start=LocalDate.now().plusDays(2).atTime(9,0).toString(),end=LocalDate.now().plusDays(2).atTime(17,0).toString();
  mvc.perform(post("/checkout/preview").session(session).with(auth).with(csrf()).param("productIds",p.getId().toString()).param("start",start).param("end",end).param("fulfilment","PICKUP").param("payment","CASH")).andExpect(redirectedUrl("/checkout/preview"));
  mvc.perform(get("/checkout/preview").session(session).with(auth)).andExpect(status().isOk()).andExpect(model().attribute("productIds",List.of(p.getId())));
  mvc.perform(post("/checkout").session(session).with(auth).with(csrf()).param("productIds",p.getId().toString()).param("start",start).param("end",end).param("fulfilment","PICKUP").param("payment","CASH")).andExpect(status().is3xxRedirection());
  assertFalse(cartItems.existsByCustomerIdAndProductId(customer.getId(),p.getId()));
  assertTrue(cartItems.existsByCustomerIdAndProductId(customer.getId(),other.getId()));
  mvc.perform(get("/checkout/preview").session(session).with(auth)).andExpect(redirectedUrl("/cart"));
 }
 @Test void adminCanUpdateCustomerInformation() throws Exception {
  var customer=customers.save(Customer.builder().email("customer-edit-"+UUID.randomUUID()+"@test.vn").fullName("Tên cũ").phone("0900000000").build());
  String updatedEmail="customer-updated-"+UUID.randomUUID()+"@test.vn";
  mvc.perform(post("/admin/customers/{id}",customer.getId()).with(user("admin@test.vn").roles("ADMIN")).with(csrf())
   .param("email",updatedEmail).param("fullName","Tên mới").param("phone","0912345678").param("password","matkhau-moi"))
   .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/users?customerUpdated"));
  var updated=customers.findById(customer.getId()).orElseThrow();
  assertEquals(updatedEmail,updated.getEmail());assertEquals("Tên mới",updated.getFullName());assertEquals("0912345678",updated.getPhone());
  assertTrue(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches("matkhau-moi",updated.getPassword()));
  mvc.perform(get("/admin/users").with(user("admin@test.vn").roles("ADMIN"))).andExpect(status().isOk());
  mvc.perform(get("/admin/users").param("customerUpdated","").with(user("admin@test.vn").roles("ADMIN")))
   .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Đã cập nhật thông tin khách hàng.")));
 }
 @BeforeEach void setup(){
  mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  p=products.save(Product.builder().name("Nhật Bình kiểm thử").category("Nhật Bình").size("M").color("Đỏ").style("Lễ phục")
   .dailyPrice(100000).depositAmount(500000).barcode("TEST-"+UUID.randomUUID()).stockStatus("AVAILABLE").components("Áo ngoài, áo lót").build());
 }
 RentalOrder book(Product product,boolean security){
  return ops.book("customer@test.vn","Khách",List.of(product.getId()),LocalDate.now(),LocalDate.now(),"PICKUP","CASH","","",security,"customer@test.vn");
 }
 @Test void counterRentalSavesPhoneAndRejectsInvalidPhone() throws Exception {
  var auth=user("staff@test.vn").roles("STAFF");
  String date=LocalDate.now().plusDays(3).toString();
  long before=orders.count();
  for(String phone:List.of("", "abc", "123")){
   mvc.perform(post("/staff/quick-rental").with(auth).with(csrf())
    .param("customerName","Khách tại quầy").param("email","counter@test.vn")
    .param("customerPhone",phone).param("productIds",p.getId().toString())
    .param("pickup",date).param("returnDate",date)).andExpect(status().isBadRequest());
  }
  assertEquals(before,orders.count());
  var result=mvc.perform(post("/staff/quick-rental").with(auth).with(csrf())
   .param("customerName","Khách tại quầy").param("email","counter@test.vn")
   .param("customerPhone","090 123 4567").param("productIds",p.getId().toString())
   .param("pickup",date).param("returnDate",date)).andExpect(status().is3xxRedirection()).andReturn();
  var order=orders.findByCustomerEmailOrderByIdDesc("counter@test.vn").get(0);
  orders.flush();
  assertEquals("0901234567",order.getCustomerPhone());
  assertEquals("/orders/"+order.getId(),result.getResponse().getRedirectedUrl());
  var html=mvc.perform(get(result.getResponse().getRedirectedUrl()).with(auth))
   .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertTrue(html.contains("0901234567"));
 }
 @Test void staffCanCreateEditAndDeleteUnpaidSaleOrder() throws Exception {
  p.setSalePrice(250000L);products.saveAndFlush(p);
  var replacement=products.saveAndFlush(Product.builder().name("Sản phẩm thay thế").barcode("SALE-"+UUID.randomUUID())
   .size("L").dailyPrice(100000).depositAmount(300000).salePrice(320000L).stockStatus("AVAILABLE").build());
  var staff=user("employee@test.vn").roles("STAFF");
  mvc.perform(post("/staff/sales").with(staff).with(csrf()).param("productId",p.getId().toString())
   .param("customerName","Khách mua").param("customerEmail","buyer@example.vn").param("paymentMethod","CASH"))
   .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/staff"));
  var sale=saleInvoices.findAll().stream().filter(invoice->"buyer@example.vn".equals(invoice.getCustomerEmail())).findFirst().orElseThrow();
  assertTrue(sale.isEditable());
  mvc.perform(post("/staff/sales/{id}/edit",sale.getId()).with(staff).with(csrf()).param("productId",replacement.getId().toString())
   .param("customerName","Khách mua đã sửa").param("customerEmail","buyer-new@example.vn").param("paymentMethod","BANK").param("paymentReference","GD-01"))
   .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/staff"));
  sale=saleInvoices.findById(sale.getId()).orElseThrow();
  assertEquals(replacement.getId(),sale.getProduct().getId());
  assertEquals("buyer-new@example.vn",sale.getCustomerEmail());
  mvc.perform(post("/staff/sales/{id}/delete",sale.getId()).with(staff).with(csrf()))
   .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/staff"));
  sale=saleInvoices.findById(sale.getId()).orElseThrow();
  assertEquals(Operations.CANCELLED,sale.getStatus());assertTrue(sale.getCustomerDeleted());
  mvc.perform(post("/staff/sales").with(user("customer@test.vn").roles("CUSTOMER")).with(csrf()).param("productId",p.getId().toString())
   .param("customerName","Không có quyền").param("customerEmail","blocked@example.vn").param("paymentMethod","CASH"))
   .andExpect(status().isForbidden());
 }
 @Test void bookingConflictAndExpiredHold(){
  var o=book(p,false);
  assertThrows(IllegalArgumentException.class,()->book(p,false));
  o.setHoldUntil(LocalDateTime.now().minusMinutes(1));orders.saveAndFlush(o);
  assertTrue(o.isHoldExpired());
  assertNotNull(book(p,false).getId());
 }
 @Test void staffCanConfirmAnExpiredCounterHoldWhenTheProductIsStillAvailable(){
  var order=book(p,false);
  order.setHoldUntil(LocalDateTime.now().minusMinutes(1));orders.saveAndFlush(order);
  assertThrows(IllegalArgumentException.class,()->ops.confirm(order.getId(),"CASH","PHIEU-01","customer"));
  ops.confirmAtCounter(order.getId(),"CASH","PHIEU-01","staff");
  assertEquals(Operations.RESERVED,order.getStatus());
 }
 @Test void rentalCrudForThreeRolesRequiresUnpaidOrder() throws Exception {
  String[] roles={"CUSTOMER","STAFF","ADMIN"};
  String[] prefixes={"/orders/","/staff/orders/","/admin/orders/"};
  for(int i=0;i<roles.length;i++){
   var order=book(p,false);
   var actor=user(i==0?"customer@test.vn":"employee@test.vn").roles(roles[i]);
   String route=prefixes[i]+order.getId();
   var page=mvc.perform(get("/orders/"+order.getId()).with(actor)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
   assertTrue(page.contains("action=\""+route+"/edit\""));
   assertTrue(page.contains("name=\"_csrf\""));
   if("ADMIN".equals(roles[i])){
    var adminPage=mvc.perform(get("/admin/orders").with(actor)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    var editForm=adminPage.substring(adminPage.indexOf("action=\"/admin/orders/"+order.getId()+"/edit\""));
    editForm=editForm.substring(0,editForm.indexOf("</form>"));
    assertTrue(java.util.regex.Pattern.compile("<option[^>]*value=\""+p.getId()+"\"[^>]*selected").matcher(editForm).find());
   }
   mvc.perform(post(route+"/edit").with(actor).with(csrf())
    .param("productIds",p.getId().toString()).param("customerName","Khách đã sửa")
    .param("start",LocalDate.now().plusDays(2).atTime(9,0).toString())
    .param("end",LocalDate.now().plusDays(2).atTime(17,0).toString())
    .param("fulfilment","PICKUP").param("payment","CASH"))
    .andExpect(status().is3xxRedirection());
   assertEquals("Khách đã sửa",order.getCustomerName());
   order.setSecurityPaid(1);orders.saveAndFlush(order);
   mvc.perform(post(route+"/delete").with(actor).with(csrf())).andExpect(status().isBadRequest());
   mvc.perform(post(route+"/edit").with(actor).with(csrf())
    .param("productIds",p.getId().toString()).param("customerName","Không được sửa")
    .param("start",LocalDate.now().plusDays(2).atTime(9,0).toString())
    .param("end",LocalDate.now().plusDays(2).atTime(17,0).toString())
    .param("fulfilment","PICKUP").param("payment","CASH"))
    .andExpect(status().isBadRequest());
   assertEquals("Khách đã sửa",order.getCustomerName());
   order.setSecurityPaid(0);orders.saveAndFlush(order);
   mvc.perform(post("/orders/"+order.getId()+"/delete").with(user("other@test.vn")).with(csrf())).andExpect(status().isBadRequest());
   mvc.perform(post("/staff/orders/"+order.getId()+"/delete").with(user("customer@test.vn").roles("CUSTOMER")).with(csrf())).andExpect(status().isForbidden());
   mvc.perform(post(route+"/delete").with(actor).with(csrf())).andExpect(status().is3xxRedirection());
   assertTrue(order.getCustomerDeleted());
   assertEquals(Operations.CANCELLED,order.getStatus());
  }
 }
 @Test void refundUsesMoneyActuallyReceivedAndCannotRepeat(){
  var o=book(p,false);assertEquals(0,o.getSecurityPaid());assertEquals(Operations.PENDING,o.getStatus());
  ops.confirm(o.getId(),"CASH","receipt1","staff");ops.prepare(o.getId());
  ops.checkout(o.getId(),p.getBarcode(),"Đủ áo, không rách","CASH_DEPOSIT","receipt2","CASH","staff");
  assertEquals(500000,o.getSecurityPaid());assertEquals("RENTED",p.getStockStatus());
  var l=lines.findByRentalOrderId(o.getId()).get(0);
  ops.checkin(o.getId(),p.getBarcode(),Map.of(l.getId(),"STAIN"),Map.of(l.getId(),"Vết ố ở tay áo phải"),"Bẩn nhẹ","staff");
  assertEquals(100000,o.getPenaltyTotal());assertEquals("WASHING",p.getStockStatus());
  assertEquals("Vết ố ở tay áo phải",l.getReturnNote());
  ops.refund(o.getId(),"CASH","refund1","staff");
  assertEquals(400000,o.getRefundedDeposit());
  assertEquals(Operations.REFUNDED,o.getStatus());
  assertThrows(IllegalArgumentException.class,()->ops.refund(o.getId(),"CASH","again","staff"));
  ops.stock(p.getId(),"AVAILABLE","staff");assertEquals(1,p.getWashCount());
 }
 @Test void documentsStillRequireFullSecurityDeposit(){
  var o=book(p,false);ops.confirm(o.getId(),"CASH","1","staff");
  ops.checkout(o.getId(),p.getBarcode(),"Mới","CCCD","receipt-reference","CASH","staff");
  ops.checkin(o.getId(),p.getBarcode(),Map.of(),"Tốt","staff");ops.refund(o.getId(),"CASH","done","staff");
  assertEquals(500000,o.getRefundedDeposit());assertEquals(500000,o.getSecurityPaid());
 }
 @Test void lostItemCanBeCheckedInWithoutScanningAndKeepsHistoricalClassification(){
  p.setAccessory(true);
  var o=book(p,false);ops.confirm(o.getId(),"CASH","1","staff");
  ops.checkout(o.getId(),p.getBarcode(),"Đủ đồ","CASH_DEPOSIT","2","CASH","staff");
  var line=lines.findByRentalOrderId(o.getId()).get(0);
  ops.checkin(o.getId(),"",Map.of(line.getId(),"LOST"),Map.of(line.getId(),"Khách xác nhận làm mất trâm"),"Thiếu trâm","staff");
  assertEquals("RETIRED",p.getStockStatus());assertEquals("LOSS",line.getIncidentType());
  var rule=penalties.findById("LOST").orElseThrow();rule.setIncidentType("DAMAGE");penalties.saveAndFlush(rule);
  var row=reports.report(LocalDate.now(),LocalDate.now()).stream().filter(r->((Product)r.get("product")).getId().equals(p.getId())).findFirst().orElseThrow();
  assertEquals(1L,row.get("lost"));assertEquals(100L,row.get("lossRate"));
  ops.refund(o.getId(),"CASH","3","staff");assertEquals(0,o.getRefundedDeposit());
 }
 @Test void unscannedReturnedItemCannotBeSilentlyCheckedIn(){
  var o=book(p,false);ops.confirm(o.getId(),"CASH","1","staff");
  ops.checkout(o.getId(),p.getBarcode(),"Đủ","CASH_DEPOSIT","2","CASH","staff");
  assertThrows(IllegalArgumentException.class,()->ops.checkin(o.getId(),"",Map.of(),"Tốt","staff"));
 }
 @Test void reportingSeparatesShelfRentalAndMaintenanceTime(){
  var day=LocalDate.now().minusDays(2);p.setCreatedAt(day.atStartOfDay());products.saveAndFlush(p);
  String[] states={"RENTED","WASHING","AVAILABLE"};int[] hours={6,12,18};String previous="AVAILABLE";
  for(int i=0;i<states.length;i++){
   var event=new InventoryEvent();event.setProduct(p);event.setPreviousStatus(previous);event.setNextStatus(states[i]);event.setRecordedAt(day.atTime(hours[i],0));events.save(event);previous=states[i];
  }
  var row=reports.report(day,day).stream().filter(r->((Product)r.get("product")).getId().equals(p.getId())).findFirst().orElseThrow();
  assertEquals(0.25,row.get("wornDays"));assertEquals(0.5,row.get("shelfDays"));assertEquals(0.25,row.get("maintenanceDays"));
  assertEquals(25L,row.get("utilization"));assertEquals(50L,row.get("shelfRate"));
 }
 @Test void pendingAndCancelledOrdersDoNotConsumePromotionQuota(){
  var promo=new Promotion();promo.setCode("ONCE15");promo.setActive(true);promo.setPercent(15);promo.setUsageLimit(1);promo.setStartDate(LocalDate.now());promo.setEndDate(LocalDate.now().plusDays(1));promotions.save(promo);
  var o=ops.book("customer@test.vn","Khách",List.of(p.getId()),LocalDate.now(),LocalDate.now(),"PICKUP","CASH","","ONCE15",false,"customer");
  assertEquals(0,promo.getUsedCount());ops.cancel(o.getId(),"customer@test.vn",false);
  var next=ops.book("customer@test.vn","Khách",List.of(p.getId()),LocalDate.now(),LocalDate.now(),"PICKUP","CASH","","ONCE15",false,"customer");
  ops.confirm(next.getId(),"CASH","1","staff");assertEquals(1,promotions.findById("ONCE15").orElseThrow().getUsedCount());
 }
 @Test void batchInventoryCreatesFiveDistinctSerials(){
  productService.save(null,5,"Nhật Bình cùng mẫu","Nhật Bình","M","Đỏ","Lễ phục",100000,500000,"Áo","",false,null,"",null);
  var copies=products.findAll().stream().filter(x->"Nhật Bình cùng mẫu".equals(x.getName())).toList();
  assertEquals(5,copies.size());assertEquals(5,copies.stream().map(Product::getBarcode).distinct().count());
 }
 @Test void coupleComboRequiresTwoDistinctAccessories(){
  var male=products.save(Product.builder().name("Áo Tấc test").category("Áo Tấc").dailyPrice(100000).depositAmount(500000).barcode("MALE-"+UUID.randomUUID()).stockStatus("AVAILABLE").build());
  var a=products.save(Product.builder().name("Mấn test").category("Mấn").accessory(true).dailyPrice(20000).depositAmount(50000).barcode("ACC-"+UUID.randomUUID()).stockStatus("AVAILABLE").build());
  var b=products.save(Product.builder().name("Trâm test").category("Trâm").accessory(true).dailyPrice(20000).depositAmount(50000).barcode("ACC-"+UUID.randomUUID()).stockStatus("AVAILABLE").build());
  var promo=new Promotion();promo.setCode("COUPLE15");promo.setActive(true);promo.setPercent(15);promo.setStartDate(LocalDate.now());promo.setEndDate(LocalDate.now().plusDays(30));promo.setRequiredCategories("Nhật Bình,Áo Tấc,Phụ kiện,Phụ kiện");promotions.save(promo);
  var date=LocalDate.now().with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY));
  var q=ops.preview(List.of(p.getId(),male.getId(),a.getId(),b.getId()),date,date,"COUPLE15","PICKUP",false);
  assertEquals(240000,q.rent());assertEquals(36000,q.discount());
  assertThrows(IllegalArgumentException.class,()->ops.preview(List.of(p.getId(),male.getId(),a.getId()),date,date,"COUPLE15","PICKUP",false));
 }
 @Test void wrongScanAndUnpaidOrderAreRejected(){
  var o=book(p,false);
  assertThrows(IllegalArgumentException.class,()->ops.checkout(o.getId(),p.getBarcode(),"OK","CASH_DEPOSIT","r","CASH","staff"));
  ops.confirm(o.getId(),"CASH","1","staff");
  assertThrows(IllegalArgumentException.class,()->ops.checkout(o.getId(),"WRONG","OK","CASH_DEPOSIT","r","CASH","staff"));
 }
 @Test void holidayWeekendComboPricing(){
  var policy=policies.findById(1L).orElseThrow();var day=LocalDate.now().plusDays(5);
  policy.setHolidays(day.toString());policy.setHolidayPercent(150);policies.saveAndFlush(policy);
  var promo=new Promotion();promo.setCode("TEST15");promo.setActive(true);promo.setPercent(15);promo.setStartDate(LocalDate.now());promo.setEndDate(day.plusDays(1));promo.setRequiredCategories("Nhật Bình");promotions.save(promo);
  var q=ops.preview(List.of(p.getId()),day,day,"TEST15","DELIVERY",false);
  assertEquals(150000,q.rent());assertEquals(22500,q.discount());assertEquals(policy.getShippingEachWay()*2,q.shipping());
  assertEquals(30000,ops.lateFee(day.atStartOfDay(),day.atStartOfDay().plusSeconds(1),policy));
  assertEquals(300000,ops.lateFee(day.atStartOfDay(),day.atStartOfDay().plusHours(23),policy));
 }
 @Test void adminSavesHomeSettingsUsingTokenFromRenderedForm() throws Exception {
  var session=new MockHttpSession();
  var page=mvc.perform(get("/admin/home-settings").session(session).with(user("admin@test.vn").roles("ADMIN")))
   .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  var form=page.substring(page.indexOf("action=\"/admin/home-settings\""));
  form=form.substring(0,form.indexOf("</form>"));
  var token=java.util.regex.Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(form);
  assertTrue(token.find(),"The settings form must render its own CSRF token");
  mvc.perform(post("/admin/home-settings").session(session).with(user("admin@test.vn").roles("ADMIN"))
   .param("_csrf",token.group(1)).param("homeTitle","Test title").param("homeSubtitle","Test subtitle")
   .param("storeAddress","Test address").param("storePhone","0901234567")
   .param("storeMapUrl","https://maps.google.com").param("storeSocialUrl","https://facebook.com"))
   .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/home-settings?success"));
  assertEquals("Test address",policies.findById(1L).orElseThrow().getStoreAddress());
  mvc.perform(post("/admin/home-settings").with(user("admin@test.vn").roles("ADMIN")))
   .andExpect(status().isForbidden());
  mvc.perform(post("/admin/home-settings").with(user("customer@test.vn").roles("CUSTOMER")).with(csrf()))
   .andExpect(status().isForbidden());
 }
 @Test void pagesRenderAndAuthorizationIsEnforced() throws Exception{
  mvc.perform(get("/")).andExpect(status().isOk());
  mvc.perform(get("/collection")).andExpect(status().isOk());
  p.setSalePrice(250000L);products.saveAndFlush(p);
  var productPage=mvc.perform(get("/product/"+p.getId())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertTrue(productPage.contains("/purchase/"+p.getId()));
  mvc.perform(get("/purchase/"+p.getId())).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
  mvc.perform(get("/purchase/"+p.getId()).with(user("customer@test.vn").roles("CUSTOMER"))).andExpect(status().isOk());
  mvc.perform(get("/login")).andExpect(status().isOk());
  mvc.perform(get("/trang-khong-ton-tai").with(user("customer@test.vn").roles("CUSTOMER"))).andExpect(status().isNotFound());
  mvc.perform(get("/register")).andExpect(status().isOk());
  mvc.perform(get("/staff/register")).andExpect(status().isOk());
  mvc.perform(get("/staff")).andExpect(status().is3xxRedirection());
  mvc.perform(get("/admin").with(user("customer@test.vn").roles("CUSTOMER"))).andExpect(status().isForbidden());
  mvc.perform(post("/cart/add/"+p.getId()).with(csrf()))
   .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
  var session=new MockHttpSession();session.setAttribute("cart",new ArrayList<>(List.of(p.getId())));
  mvc.perform(get("/cart").session(session))
   .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
  mvc.perform(get("/appointments").with(user("customer@test.vn"))).andExpect(status().isNotFound());
  var accountPage=mvc.perform(get("/account").with(user("customer@test.vn"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertFalse(accountPage.contains("Lịch thử đồ"));
  assertTrue(accountPage.contains("Đơn mua"));
  mvc.perform(post("/checkout/preview").session(session).with(user("customer@test.vn")).with(csrf())
   .param("start",LocalDateTime.now().plusDays(1).withSecond(0).withNano(0).toString()).param("end",LocalDateTime.now().plusDays(1).plusHours(2).withSecond(0).withNano(0).toString()).param("fulfilment","PICKUP").param("payment","CASH"))
   .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/checkout/preview"));
  mvc.perform(get("/checkout/preview").session(session).with(user("customer@test.vn"))).andExpect(status().isOk());
  var o=book(p,false);
  p.setAccessory(true);products.saveAndFlush(p);
  var orderPage=mvc.perform(get("/orders/"+o.getId()).with(user("customer@test.vn"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertTrue(orderPage.contains("Sửa đơn thuê chưa cọc"));
  mvc.perform(get("/orders/"+o.getId()).with(user("other@test.vn"))).andExpect(status().isBadRequest());
  mvc.perform(get("/staff").with(user("employee@test.vn").roles("STAFF"))).andExpect(status().isOk());
  mvc.perform(get("/staff/label/"+p.getId()).with(user("employee@test.vn").roles("STAFF"))).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
  mvc.perform(get("/staff/barcode/"+p.getId()).with(user("employee@test.vn").roles("STAFF"))).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
  var adminPage=mvc.perform(get("/admin").with(user("admin@test.vn").roles("ADMIN"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertTrue(adminPage.contains("Quản lý sản phẩm"));
  assertTrue(adminPage.contains("/admin/products"));
  mvc.perform(get("/admin/categories").with(user("admin@test.vn").roles("ADMIN"))).andExpect(status().isOk());
  mvc.perform(get("/admin/users").with(user("admin@test.vn").roles("ADMIN"))).andExpect(status().isOk());
  mvc.perform(get("/admin/appointments").with(user("admin@test.vn").roles("ADMIN"))).andExpect(status().isNotFound());
  mvc.perform(get("/admin/home-settings").with(user("admin@test.vn").roles("ADMIN"))).andExpect(status().isOk());
  mvc.perform(get("/admin/product/"+p.getId()).with(user("admin@test.vn").roles("ADMIN"))).andExpect(status().isOk());
  mvc.perform(get("/admin/product/new").with(user("admin@test.vn").roles("ADMIN"))).andExpect(status().isOk());
  mvc.perform(get("/admin/media").with(user("admin@test.vn").roles("ADMIN"))).andExpect(status().isOk());
  var productManagementPage=mvc.perform(get("/admin/products").with(user("admin@test.vn").roles("ADMIN"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertTrue(productManagementPage.contains("Danh sách sản phẩm"));
  assertTrue(productManagementPage.contains("Trang phục"));
  assertTrue(productManagementPage.contains("Phụ kiện"));
  mvc.perform(get("/admin/qr").with(user("admin@test.vn").roles("ADMIN"))).andExpect(status().isOk());
 }

 @Test void customerCanSignUpAndLogInWithoutAdminApproval() throws Exception {
  var email="new-customer-"+UUID.randomUUID()+"@example.vn";
  var session=new MockHttpSession();
  mvc.perform(post("/register").session(session).with(csrf())
    .param("email",email).param("password","matkhau123").param("fullName","Khách mới").param("phone","0900000000"))
   .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?registered=customer"));
  assertTrue(customers.findByEmail(email).isPresent());
  mvc.perform(post("/login").session(session).with(csrf()).param("email",email).param("password","matkhau123"))
   .andExpect(status().is3xxRedirection());
 }
}
