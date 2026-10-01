package com.example.demo.config;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.LocalDate;
import java.util.List;

@Configuration @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="app.seed-demo", havingValue="true")
public class SeedData {
 @Bean CommandLineRunner seed(ProductRepository repo, CustomerRepository customers, RentalOrderRepository orders, SiteImageRepository siteImages, StaffAccountRepository staff, CategoryRepository categories) { return args -> { if(repo.count()==0) repo.saveAll(List.of(
  product("Nhật Bình Phượng Vũ","Nhật Bình","M","Đỏ son","Lễ phục",850000,2500000,"https://images.unsplash.com/photo-1596704017254-9b121068fb31?auto=format&fit=crop&w=900&q=80","Lễ phục Nhật Bình thêu phượng thủ công, dành cho những dịp trọng đại."),
  product("Áo Ngũ Thân Lam Vân","Áo Ngũ Thân","L","Xanh lam","Thường phục",490000,1500000,"https://images.unsplash.com/photo-1530789253388-582c481c54b0?auto=format&fit=crop&w=900&q=80","Dáng áo truyền thống trang nhã, mềm mại và thanh lịch."),
  product("Áo Tấc Kim Tuyến","Áo Tấc","XL","Vàng đồng","Hoàng gia",950000,3000000,"https://images.unsplash.com/photo-1610030469983-98e550d6193c?auto=format&fit=crop&w=900&q=80","Áo Tấc dệt kim tuyến với những chi tiết hoàng gia tinh xảo."),
  product("Nhật Bình Bích Ngọc","Nhật Bình","S","Xanh ngọc","Lễ phục",780000,2200000,"https://images.unsplash.com/photo-1524250502761-1ac6f2e30d43?auto=format&fit=crop&w=900&q=80","Sắc bích ngọc dịu dàng, phù hợp chụp ảnh kỷ niệm."),
  accessory("Trâm cài Phượng",120000,"https://images.unsplash.com/photo-1617038260897-41a1f14a8ca0?auto=format&fit=crop&w=500&q=80"), accessory("Quạt xòe Cung Đình",90000,"https://images.unsplash.com/photo-1590373529824-2069d42f6e3f?auto=format&fit=crop&w=500&q=80"), accessory("Mấn đội đầu",150000,"https://images.unsplash.com/photo-1594744803329-e58b31de8bf5?auto=format&fit=crop&w=500&q=80")
 ));
  if (repo.count() < 20) repo.saveAll(List.of(
   product("Nhật Bình Hoàng Hạc","Nhật Bình","M","Vàng nghệ","Lễ phục",880000,2600000,"https://images.unsplash.com/photo-1604902396830-aca29e19eefa?auto=format&fit=crop&w=900&q=80","Nhật Bình sắc hoàng hạc với hoa văn cung đình."),
   product("Áo Tấc Huyền Vũ","Áo Tấc","L","Đen tuyền","Hoàng gia",990000,3200000,"https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?auto=format&fit=crop&w=900&q=80","Áo Tấc trầm mặc dành cho nghi lễ trang trọng."),
   product("Áo Ngũ Thân Trúc Bạch","Áo Ngũ Thân","M","Trắng ngà","Thường phục",450000,1300000,"https://images.unsplash.com/photo-1496747611176-843222e1e57c?auto=format&fit=crop&w=900&q=80","Nét thanh tân của áo ngũ thân truyền thống."),
   product("Áo Dài Cổ Phục Tường Vi","Áo Dài","S","Hồng phấn","Lễ phục",620000,1800000,"https://images.unsplash.com/photo-1529139574466-a303027c1d8b?auto=format&fit=crop&w=900&q=80","Sắc hồng nhẹ nhàng cho buổi chụp ảnh kỷ niệm."),
   product("Giao Lĩnh Vân Mây","Áo Giao Lĩnh","FreeSize","Nâu trầm","Thường phục",550000,1600000,"https://images.unsplash.com/photo-1551028719-00167b16eac5?auto=format&fit=crop&w=900&q=80","Áo giao lĩnh phóng khoáng và nền nã."),
   product("Viên Lĩnh Ngọc Lam","Áo Viên Lĩnh","L","Xanh ngọc","Thường phục",590000,1700000,"https://images.unsplash.com/photo-1485230895905-ec40ba36b9bc?auto=format&fit=crop&w=900&q=80","Dáng viên lĩnh cổ điển với sắc lam thanh nhã."),
   product("Áo Tấc Cửu Long","Áo Tấc","XL","Đỏ đô","Hoàng gia",1100000,3500000,"https://images.unsplash.com/photo-1618375531912-867984bdfd87?auto=format&fit=crop&w=900&q=80","Thiết kế quyền quý cho dịp lễ trọng đại."),
   product("Nhật Bình Liên Hoa","Nhật Bình","S","Tím Huế","Lễ phục",820000,2400000,"https://images.unsplash.com/photo-1581044777550-4cfa60707c03?auto=format&fit=crop&w=900&q=80","Hoa văn liên hoa, gợi nét dịu dàng xứ Huế."),
   accessory("Guốc gỗ Sơn Son",110000,"https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=500&q=80"),
   accessory("Bội thẻ Ngà Vàng",130000,"https://images.unsplash.com/photo-1603561596112-db1d36499b37?auto=format&fit=crop&w=500&q=80"),
   accessory("Thắt lưng Chiêu Văn",100000,"https://images.unsplash.com/photo-1584917865442-de89df76afd3?auto=format&fit=crop&w=500&q=80"),
   accessory("Hài Thêu Cung Đình",140000,"https://images.unsplash.com/photo-1543163521-1bf539c55dd2?auto=format&fit=crop&w=500&q=80"),
   accessory("Quạt Lụa Song Hạc",95000,"https://images.unsplash.com/photo-1506452305024-9d59d8d5c2ee?auto=format&fit=crop&w=500&q=80")
  ));
  repo.findAll().forEach(p -> { if (p.getBarcode() == null) { p.setBarcode("CVL-" + p.getId()); p.setStockStatus("AVAILABLE"); repo.save(p); } categories.findByNameIgnoreCase(p.getCategory()).orElseGet(() -> categories.save(Category.builder().name(p.getCategory()).description("").build())); });
  Customer customer = customers.findByEmail("lan.nguyen@covietlau.vn").orElseGet(() -> customers.save(Customer.builder().fullName("Nguyễn Thanh Lan").phone("0901234567").email("lan.nguyen@covietlau.vn").password(new BCryptPasswordEncoder().encode("123456")).build()));
  if (orders.count() == 0) {
   Product p = repo.findAll().stream().filter(x -> !x.isAccessory()).findFirst().orElseThrow();
   orders.save(RentalOrder.builder().customerEmail(customer.getEmail()).product(p).pickupDate(LocalDate.now().plusDays(7)).returnDate(LocalDate.now().plusDays(9)).fulfilment("PICKUP").paymentMethod("BANK").status("Đã cọc giữ lịch").rentalTotal(p.getDailyPrice()*3).depositTotal(p.getDepositAmount()).bookingDeposit(Math.round(p.getDailyPrice()*3*.30)).build());
  }
  if (siteImages.count() == 0) siteImages.saveAll(List.of(
   SiteImage.builder().slot("hero-left").title("Nền hero trái").imageUrl("https://images.unsplash.com/photo-1540962351504-03099e0a754b?auto=format&fit=crop&w=1800&q=85").build(),
   SiteImage.builder().slot("collection-one").title("Ô bộ sưu tập 01").imageUrl("https://images.unsplash.com/photo-1596704017254-9b121068fb31?auto=format&fit=crop&w=900&q=80").build(),
   SiteImage.builder().slot("collection-two").title("Ô bộ sưu tập 02").imageUrl("https://images.unsplash.com/photo-1524250502761-1ac6f2e30d43?auto=format&fit=crop&w=900&q=80").build(),
   SiteImage.builder().slot("right-top").title("Ô cột phải phía trên").imageUrl("https://images.unsplash.com/photo-1610030469983-98e550d6193c?auto=format&fit=crop&w=900&q=80").build(),
   SiteImage.builder().slot("right-bottom").title("Ô cột phải phía dưới").imageUrl("https://images.unsplash.com/photo-1519608487953-e999c86e7454?auto=format&fit=crop&w=900&q=80").build()
  ));
  staff.findByEmail("nhanvien@covietlau.vn").orElseGet(() -> staff.save(StaffAccount.builder().fullName("Trần Minh An").email("nhanvien@covietlau.vn").password(new BCryptPasswordEncoder().encode("123456")).role("STAFF").enabled(true).build()));
  System.out.printf("Cổ Việt Lâu seed complete: %d products, %d customers, %d rental orders.%n", repo.count(), customers.count(), orders.count());
 }; }
 private Product product(String n,String c,String s,String co,String st,long p,long d,String i,String desc){return Product.builder().name(n).category(c).size(s).color(co).style(st).dailyPrice(p).depositAmount(d).imageUrl(i).description(desc).stockStatus("AVAILABLE").build();}
 private Product accessory(String n,long p,String i){return Product.builder().name(n).category("Phụ kiện").size("FreeSize").color("Truyền thống").style("Phụ kiện").dailyPrice(p).depositAmount(p*2).imageUrl(i).description("Điểm xuyết hoàn hảo cho bộ Việt phục.").accessory(true).stockStatus("AVAILABLE").build();}
}
