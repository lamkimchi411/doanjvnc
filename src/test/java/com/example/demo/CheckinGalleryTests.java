package com.example.demo;

import com.example.demo.repository.SiteImageRepository;
import com.example.demo.service.CheckinGallery;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest @Transactional
class CheckinGalleryTests {
 @TempDir static Path uploads;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){
  registry.add("app.upload-dir",()->uploads.toString());
 }
 @Autowired WebApplicationContext context;
 @Autowired SiteImageRepository images;
 @Autowired CheckinGallery gallery;
 @Autowired com.example.demo.repository.PolicyRepository policies;
 MockMvc mvc;
 @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
 byte[] png() throws Exception {
  var bytes=new java.io.ByteArrayOutputStream();
  javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
  return bytes.toByteArray();
 }
 MockMultipartHttpServletRequestBuilder form(boolean upload) throws Exception {
  return form(upload,java.util.stream.IntStream.rangeClosed(1,6).mapToObj(i->"Cảnh đẹp "+i).toArray(String[]::new));
 }
 MockMultipartHttpServletRequestBuilder form(boolean upload,String[] captions) throws Exception {
  var request=multipart("/admin/home-settings");
  for(int i=0;i<6;i++)request.file(new MockMultipartFile("checkinFiles",upload?"scene.png":"","image/png",upload?png():new byte[0]));
  request.param("checkinTitles",captions);
  request.param("homeTitle","Trang chủ").param("homeSubtitle","Di sản").param("storeAddress","Huế")
   .param("storePhone","0901234567").param("storeMapUrl","https://maps.google.com").param("storeSocialUrl","https://facebook.com");
  return request;
 }
 @Test void adminCanUploadSixKeepReplaceAndRemoveImages() throws Exception {
  mvc.perform(form(true).with(user("admin").roles("ADMIN")).with(csrf()))
   .andExpect(redirectedUrl("/admin/home-settings?success"));
  var frames=gallery.frames();
  assertEquals(6,frames.size());
  assertEquals(6,frames.stream().map(f->f.getImageUrl()).distinct().count());
  String first=frames.get(0).getImageUrl();
  mvc.perform(get(first)).andExpect(status().isOk()).andExpect(content().bytes(png()));
  mvc.perform(get(first).with(user("customer").roles("CUSTOMER"))).andExpect(status().isOk());
  mvc.perform(form(false).with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().is3xxRedirection());
  assertEquals(first,images.findBySlot("checkin-1").orElseThrow().getImageUrl());
  mvc.perform(form(true).with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().is3xxRedirection());
  assertNotEquals(first,images.findBySlot("checkin-1").orElseThrow().getImageUrl());
  mvc.perform(form(false).param("removeCheckin","checkin-1").with(user("admin").roles("ADMIN")).with(csrf()))
   .andExpect(status().is3xxRedirection());
  assertEquals("",images.findBySlot("checkin-1").orElseThrow().getImageUrl());
  var html=mvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
  assertEquals(6,html.split("data-checkin-slot=",-1).length-1);
  assertFalse(html.contains("Việt phục dành cho bạn"));
  var section=html.substring(html.indexOf("<section class=\"checkin-gallery\""));
  assertFalse(section.contains("/product/"));
  for(int i=2;i<=6;i++)assertTrue(section.contains(images.findBySlot("checkin-"+i).orElseThrow().getImageUrl()));
 }
 @Test void adminCanChangeCaptionsWithoutReplacingImages() throws Exception {
  mvc.perform(form(true).with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().is3xxRedirection());
  var first=images.findBySlot("checkin-1").orElseThrow();
  String originalUrl=first.getImageUrl();
  String caption="Hoàng thành Huế <b>ban chiều</b>";
  var request=form(false,java.util.stream.IntStream.rangeClosed(1,6).mapToObj(i->caption+" "+i).toArray(String[]::new));
  mvc.perform(request.with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().is3xxRedirection());
  assertEquals(caption+" 1",images.findBySlot("checkin-1").orElseThrow().getTitle());
  assertEquals(originalUrl,images.findBySlot("checkin-1").orElseThrow().getImageUrl());
  var html=mvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
  assertTrue(html.contains("Hoàng thành Huế &lt;b&gt;ban chiều&lt;/b&gt; 1"));
  assertFalse(html.contains("Sửa ảnh / phụ đề"));
  var admin=mvc.perform(get("/").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
   .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
  assertEquals(6,admin.split("Sửa ảnh / phụ đề",-1).length-1);
 }
 @Test void settingsHaveSixInputsAndRequireAdminAndCsrf() throws Exception {
  var html=mvc.perform(get("/admin/home-settings").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
   .andReturn().getResponse().getContentAsString();
  assertEquals(6,html.split("name=\"checkinFiles\"",-1).length-1);
  assertTrue(html.contains("name=\"_csrf\""));
  for(String role:List.of("CUSTOMER","STAFF"))
   mvc.perform(form(true).with(user("visitor").roles(role)).with(csrf())).andExpect(status().isForbidden());
  mvc.perform(form(true).with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
  mvc.perform(get("/")).andExpect(status().isOk());
 }
 @Test void adminCanReplaceAndHideHomeVideo() throws Exception {
  var html=mvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertTrue(html.contains("https://www.youtube-nocookie.com/embed/cr2MgwPmtXs"));
  assertTrue(html.indexOf("class=\"home-video\"")>html.indexOf("class=\"home-hero "));
  assertTrue(html.indexOf("class=\"home-video\"")<html.indexOf("class=\"checkin-gallery\""));
  mvc.perform(form(false).param("homeVideoUrl","https://youtu.be/abcdefghijk").with(user("admin").roles("ADMIN")).with(csrf()))
   .andExpect(redirectedUrl("/admin/home-settings?success"));
  html=mvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertTrue(html.contains("https://www.youtube-nocookie.com/embed/abcdefghijk"));
  for(String role:List.of("CUSTOMER","STAFF"))
   mvc.perform(form(false).param("homeVideoUrl","").with(user("visitor").roles(role)).with(csrf())).andExpect(status().isForbidden());
  mvc.perform(form(false).param("homeVideoUrl","").with(user("admin").roles("ADMIN")).with(csrf()))
   .andExpect(status().is3xxRedirection());
  html=mvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertFalse(html.contains("class=\"home-video\""));
 }
 @Test void adminCanReplaceOrHideHomeVideo() throws Exception {
  mvc.perform(form(false).param("homeVideoUrl","https://youtu.be/abcdefghijk")
   .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(redirectedUrl("/admin/home-settings?success"));
  assertEquals("https://www.youtube.com/watch?v=abcdefghijk",policies.findById(1L).orElseThrow().getHomeVideoUrl());
  var html=mvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertTrue(html.contains("https://www.youtube-nocookie.com/embed/abcdefghijk"));
  assertTrue(html.indexOf("class=\"home-hero ")<html.indexOf("class=\"home-video\""));
  assertTrue(html.indexOf("class=\"home-video\"")<html.indexOf("class=\"checkin-gallery\""));
  for(String role:List.of("CUSTOMER","STAFF"))
   mvc.perform(form(false).param("homeVideoUrl","").with(user("visitor").roles(role)).with(csrf()))
    .andExpect(status().isForbidden());
  assertEquals("https://www.youtube.com/watch?v=abcdefghijk",policies.findById(1L).orElseThrow().getHomeVideoUrl());
  mvc.perform(form(false).param("homeVideoUrl","").with(user("admin").roles("ADMIN")).with(csrf()))
   .andExpect(status().is3xxRedirection());
  var hidden=mvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertFalse(hidden.contains("youtube-nocookie.com/embed/"));
 }
 @Test void rejectsUnknownSlotsMissingFieldsAndInvalidFiles(){
  var titles=new ArrayList<>(Collections.nCopies(6,"Ảnh"));
  var files=new ArrayList<org.springframework.web.multipart.MultipartFile>();
  for(int i=0;i<6;i++)files.add(new MockMultipartFile("checkinFiles",new byte[0]));
  assertThrows(IllegalArgumentException.class,()->gallery.save(titles,files,List.of("hero-left")));
  assertThrows(IllegalArgumentException.class,()->gallery.save(titles.subList(0,5),files,null));
  titles.set(0," ");
  assertThrows(IllegalArgumentException.class,()->gallery.save(titles,files,null));
  titles.set(0,"Ảnh");
  files.set(0,new MockMultipartFile("checkinFiles","bad.png","image/png","not an image".getBytes()));
  assertThrows(IllegalArgumentException.class,()->gallery.save(titles,files,null));
 }
}
