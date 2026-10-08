package com.example.demo;

import com.example.demo.entity.Product;
import com.example.demo.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest @Transactional
class HomeCollectionTests {
 @Autowired WebApplicationContext context;
 @Autowired ProductRepository products;
 @Autowired PolicyRepository policies;
 @Test void homeRendersConfiguredThreeDayAndThreeNightProducts() throws Exception {
  var selected=new ArrayList<Product>();
  for(int i=0;i<6;i++)selected.add(products.save(Product.builder().name("Khung ảnh "+i)
   .barcode("HOME-"+UUID.randomUUID()).stockStatus("AVAILABLE").imageUrl("/images/frame-"+i+".png").build()));
  var policy=policies.findById(1L).orElseThrow();
  policy.setDayCollectionProductIds(String.join(",",selected.subList(0,3).stream().map(p->p.getId().toString()).toList()));
  policy.setNightCollectionProductIds(String.join(",",selected.subList(3,6).stream().map(p->p.getId().toString()).toList()));
  var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  var response=mvc.perform(get("/")).andExpect(status().isOk()).andReturn();
  assertEquals(selected.subList(0,3),response.getModelAndView().getModel().get("dayProducts"));
  assertEquals(selected.subList(3,6),response.getModelAndView().getModel().get("nightProducts"));
  var html=response.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
  for(var p:selected)assertTrue(html.contains(p.getImageUrl()),"Phải hiển thị ảnh được chọn");
  assertTrue(html.contains("data-home-collection=\"day\""));
  assertTrue(html.contains("data-home-collection=\"night\""));
  var settings=mvc.perform(get("/admin/home-settings").with(user("admin@test.vn").roles("ADMIN"))).andExpect(status().isOk())
   .andReturn().getResponse().getContentAsString();
  assertEquals(3,settings.split("name=\"dayProductIds\"", -1).length-1);
  assertEquals(3,settings.split("name=\"nightProductIds\"", -1).length-1);
 }
}
