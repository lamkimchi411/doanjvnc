package com.example.demo.config;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.*;
@Configuration public class BusinessData {
 @Bean @Order(100) CommandLineRunner initializeBusinessData(PolicyRepository policy,PenaltyRepository penalties,PromotionRepository promos,
   StaffAccountRepository staff,OrderLineRepository lines,RentalOrderRepository orders,
   @Value("${ADMIN_EMAIL:admin@covietlau.vn}") String email,@Value("${app.admin-password:${ADMIN_PASSWORD:}}") String password){
  return args->{
   if(!policy.existsById(1L))policy.save(new ShopPolicy());
   String[][] rules={{"NORMAL","Bình thường","0","DAMAGE","WASHING"},{"STAIN","Vết bẩn","100000","DAMAGE","WASHING"},{"TEAR","Rách vải","300000","DAMAGE","REPAIRING"},{"LOST","Mất đồ/phụ kiện","500000","LOSS","RETIRED"}};
   for(var values:rules){
    var r=penalties.findById(values[0]).orElseGet(PenaltyRule::new);
    if(r.getCode()==null){r.setCode(values[0]);r.setLabel(values[1]);r.setAmount(Long.parseLong(values[2]));r.setNextStockStatus(values[4]);r.setIncidentType(values[3]);}
    if(r.getIncidentType()==null)r.setIncidentType(values[3]);
    if("LOST".equals(r.getCode())){r.setIncidentType("LOSS");r.setNextStockStatus("RETIRED");}
    penalties.save(r);
   }
   var adminEmail=email.toLowerCase();
   var existingAdmin=staff.findByEmail(adminEmail);
   if(!password.isBlank()&&existingAdmin.isEmpty()){
    if(password.length()<10)throw new IllegalArgumentException("ADMIN_PASSWORD cần ít nhất 10 ký tự.");
    staff.save(StaffAccount.builder().email(adminEmail).fullName("Quản lý cửa hàng").password(new BCryptPasswordEncoder().encode(password)).role("ADMIN").enabled(true).build());
   }
   // Preserve existing accounts; approval of staff accounts happens in the admin screen.
   for(var o:orders.findAll())if(lines.findByRentalOrderId(o.getId()).isEmpty()){
    var l=new OrderLine();l.setRentalOrder(o);l.setProduct(o.getProduct());l.setName(o.getProduct().getName());
    l.setBarcode(o.getProduct().getBarcode());l.setRent(o.getRentalTotal());l.setDeposit(o.getDepositTotal());l.setComponents(o.getProduct().getComponents());lines.save(l);
   }
   for(var l:lines.findAll())if(l.getDamageCode()!=null&&l.getIncidentType()==null){
    var rule=penalties.findById(l.getDamageCode()).orElse(null);
    l.setIncidentType("LOST".equals(l.getDamageCode())?"LOSS":rule==null?"DAMAGE":rule.getIncidentType());
    l.setDamageLabel(rule==null?l.getDamageCode():rule.getLabel());lines.save(l);
   }
  };
 }
}
