package com.example.demo;

import com.example.demo.entity.Product;
import com.example.demo.repository.ProductRepository;
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

@SpringBootTest @Transactional
class ProductSuggestionsTests {
 @org.junit.jupiter.api.io.TempDir static java.nio.file.Path uploadDirectory;
 @org.springframework.test.context.DynamicPropertySource
 static void uploadProperties(org.springframework.test.context.DynamicPropertyRegistry registry){
  registry.add("app.upload-dir",()->uploadDirectory.toString());
 }
 @Autowired com.example.demo.service.ImageStorage storage;
 @Autowired WebApplicationContext context;
 @Autowired ProductRepository products;
 @Autowired com.example.demo.repository.PolicyRepository policies;
 @Test void uploadedPhotoSurvivesRepresentativeChangeWithoutMutatingStock() throws Exception {
  var bytes=new java.io.ByteArrayOutputStream();
  javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
  String url=storage.save(new org.springframework.mock.web.MockMultipartFile("image","fan.png","image/png",bytes.toByteArray()));
  var original=accessory("Quạt có ảnh đã tải","RETIRED",35000);
  original.setImageUrl(url);products.save(original);
  var representative=accessory("Quạt có ảnh đã tải","AVAILABLE",95000);
  representative.setImageUrl("https://example.invalid/old.jpg");products.save(representative);
  var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  for(String route:List.of("/collection","/product/"+representative.getId())){
   var html=mvc.perform(get(route)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
   assertTrue(html.contains(url));
   assertFalse(html.contains("https://example.invalid/old.jpg"));
  }
  mvc.perform(get(url)).andExpect(status().isOk());
  assertEquals("https://example.invalid/old.jpg",products.findById(representative.getId()).orElseThrow().getImageUrl());
  assertEquals(url,products.findById(original.getId()).orElseThrow().getImageUrl());
  assertFalse(storage.hasUploadedImage("/uploads/missing.png"));
  assertFalse(storage.hasUploadedImage("/uploads/../pom.xml"));
 }
 @Test void collectionGroupsSameModelEvenWhenPriceSizeAndImageDiffer() throws Exception {
  var original=accessory("Hài thử trùng","AVAILABLE",35000);
  original.setSize("S");original.setColor("Vàng");products.save(original);
  var duplicate=accessory("  HÀI   THỬ TRÙNG ","AVAILABLE",140000);
  duplicate.setSize("XL");duplicate.setColor("Đỏ");duplicate.setImageUrl("/images/duplicate.png");products.save(duplicate);
  var other=accessory("Hài mẫu khác","AVAILABLE",40000);
  long count=products.count();
  var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  for(String query:List.of("","HÀI  THỬ TRÙNG")){
   var result=mvc.perform(get("/collection").param("q",query)).andExpect(status().isOk()).andReturn();
   @SuppressWarnings("unchecked") var shown=(List<Product>)result.getModelAndView().getModel().get("products");
   assertTrue(shown.stream().anyMatch(p->p.getId().equals(original.getId())));
   assertFalse(shown.stream().anyMatch(p->p.getId().equals(duplicate.getId())));
   if(query.isEmpty())assertTrue(shown.stream().anyMatch(p->p.getId().equals(other.getId())));
   var html=result.getResponse().getContentAsString();
   assertTrue(html.contains("/product/"+original.getId()+"\""));
   assertFalse(html.contains("/product/"+duplicate.getId()+"\""));
   assertFalse(html.contains("/images/duplicate.png"));
  }
  assertEquals(count,products.count());
 }
 @Test void homeConfiguredAndFallbackCardsDoNotRepeatModelNames() throws Exception {
  var first=accessory("Mẫu trang chủ trùng","AVAILABLE",100);
  first.setAccessory(false);products.save(first);
  var second=accessory("Mẫu trang chủ trùng","AVAILABLE",200);
  second.setAccessory(false);products.save(second);
  var policy=policies.findById(1L).orElseThrow();
  var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  for(String ids:List.of(first.getId()+","+second.getId(),"")){
   policy.setDayCollectionProductIds(ids);policy.setNightCollectionProductIds(ids);policies.save(policy);
   var result=mvc.perform(get("/")).andExpect(status().isOk()).andReturn();
   for(String key:List.of("dayProducts","nightProducts")){
    @SuppressWarnings("unchecked") var cards=(List<Product>)result.getModelAndView().getModel().get(key);
    assertEquals(cards.size(),cards.stream().map(Product::getName).distinct().count());
   }
  }
 }
 Product accessory(String name,String state,long price){
  return products.save(Product.builder().name(name).accessory(true).stockStatus(state).dailyPrice(price)
   .barcode(UUID.randomUUID().toString()).imageUrl("/images/test.png").build());
 }
 @Test void suggestionsGroupNamesWithoutDeletingStockOrMixingCardData() throws Exception {
  var current=accessory("Mẫu hiện tại","AVAILABLE",100);
  var same=accessory("Mẫu hiện tại","AVAILABLE",200);
  var retired=accessory("Quạt kiểm thử","RETIRED",100);
  var busy=accessory("Quạt kiểm thử","RENTED",200);
  var chosen=accessory("Quạt kiểm thử","AVAILABLE",35000);
  var duplicate=accessory("  QUẠT   KIỂM THỬ  ","AVAILABLE",95000);
  long count=products.count();
  var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  var result=mvc.perform(get("/product/"+current.getId())).andExpect(status().isOk()).andReturn();
  @SuppressWarnings("unchecked") var suggestions=(List<Product>)result.getModelAndView().getModel().get("suggestions");
  assertTrue(suggestions.stream().anyMatch(p->p.getId().equals(chosen.getId())));
  for(var excluded:List.of(current,same,retired,busy,duplicate))
   assertFalse(suggestions.stream().anyMatch(p->p.getId().equals(excluded.getId())));
  var html=result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
  assertEquals(1,html.split("Quạt kiểm thử",-1).length-1);
  assertTrue(html.contains("/cart/add/"+chosen.getId()));
  assertFalse(html.contains("/cart/add/"+duplicate.getId()));
  assertEquals(count,products.count());
 }
}
