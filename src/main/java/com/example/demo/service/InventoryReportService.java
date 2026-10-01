package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InventoryReportService {
    private final ProductRepository products;
    private final OrderLineRepository lines;
    private final EventRepository events;
    private final PolicyRepository policies;

    @Transactional(readOnly = true)
    public List<Map<String,Object>> report(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from))
            throw new IllegalArgumentException("Khoảng báo cáo không hợp lệ.");
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        if (end.isAfter(LocalDateTime.now())) end = LocalDateTime.now();
        var policy = policies.findById(1L).orElseThrow();
        var inventory = products.findAll();
        var allLines = lines.findAll();
        List<Map<String,Object>> rows = new ArrayList<>();
        for (var product : inventory) {
            LocalDateTime observedStart = product.getCreatedAt()!=null&&product.getCreatedAt().isAfter(start)?product.getCreatedAt():start;
            var rentals = allLines.stream().filter(l -> product.getId().equals(l.getProduct().getId())).toList();
            var history = new ArrayList<>(events.findByProductIdOrderByRecordedAtDesc(product.getId()));
            history.sort(Comparator.comparing(InventoryEvent::getRecordedAt).thenComparing(InventoryEvent::getId));
            Map<String,Long> seconds = new HashMap<>();
            String status = history.isEmpty() ? Objects.toString(product.getStockStatus(),"AVAILABLE") : history.get(0).getPreviousStatus();
            LocalDateTime cursor = observedStart;
            for (var event : history) {
                if (!event.getRecordedAt().isAfter(observedStart)) { status = event.getNextStatus(); continue; }
                if (!event.getRecordedAt().isBefore(end)) break;
                seconds.merge(Objects.toString(status,"AVAILABLE"),Math.max(0,Duration.between(cursor,event.getRecordedAt()).getSeconds()),Long::sum);
                cursor = event.getRecordedAt();status = event.getNextStatus();
            }
            seconds.merge(Objects.toString(status,"AVAILABLE"),Math.max(0,Duration.between(cursor,end).getSeconds()),Long::sum);
            long worn = seconds.getOrDefault("RENTED",0L);
            long shelf = seconds.getOrDefault("AVAILABLE",0L)+seconds.getOrDefault("RESERVED",0L);
            long maintenance = seconds.getOrDefault("WASHING",0L)+seconds.getOrDefault("REPAIRING",0L);
            long retired = seconds.getOrDefault("RETIRED",0L);
            long observed = Math.max(0,Duration.between(observedStart,end).getSeconds());
            long count=0,completed=0,lost=0,damaged=0;
            for (var line : rentals) {
                var order=line.getRentalOrder();
                if(order.getCheckedOutAt()!=null&&order.getCheckedOutAt().isBefore(end)&&
                   (order.getReturnedAt()==null||order.getReturnedAt().isAfter(start))) count++;
                if(order.getReturnedAt()!=null&&!order.getReturnedAt().isBefore(start)&&order.getReturnedAt().isBefore(end)) {
                    completed++;
                    if("LOSS".equals(line.getIncidentType())) lost++;
                    else if(line.getDamageCode()!=null&&!"NORMAL".equals(line.getDamageCode())) damaged++;
                }
            }
            Map<String,Object> row=new HashMap<>();
            row.put("product",product);row.put("count",count);row.put("completed",completed);
            row.put("damaged",damaged);row.put("lost",lost);
            row.put("wornDays",days(worn));row.put("shelfDays",days(shelf));row.put("maintenanceDays",days(maintenance));row.put("retiredDays",days(retired));
            row.put("utilization",percent(worn,observed));row.put("shelfRate",percent(shelf,observed));
            row.put("lossRate",percent(lost,completed));row.put("incidentRate",percent(lost+damaged,completed));
            long copies=inventory.stream().filter(p->p.isAccessory()&&Objects.equals(p.getName(),product.getName())&&Objects.equals(p.getSize(),product.getSize())&&Objects.equals(p.getColor(),product.getColor())&&!"RETIRED".equals(p.getStockStatus())).count();
            row.put("restockNeed",product.isAccessory()?Math.max(0,policy.getAccessoryRestockTarget()-copies):0L);
            row.put("lossAlert",lost>0&&percent(lost,completed)>=policy.getAccessoryLossAlertPercent());
            rows.add(row);
        }
        rows.sort(Comparator.comparingLong((Map<String,Object> r)->(Long)r.get("count")).reversed());
        return rows;
    }
    private double days(long seconds){return Math.round(seconds/86400.0*100)/100.0;}
    private long percent(long value,long total){return total<=0?0:Math.round(100.0*value/total);}

    @Transactional(readOnly=true)
    public List<Map<String,Object>> accessoryReport(List<Map<String,Object>> stats){
        var policy=policies.findById(1L).orElseThrow();
        Map<List<String>,List<Map<String,Object>>> groups=new LinkedHashMap<>();
        for(var row:stats){
            var p=(Product)row.get("product");
            if(p.isAccessory())groups.computeIfAbsent(Arrays.asList(p.getName(),p.getSize(),p.getColor()),k->new ArrayList<>()).add(row);
        }
        List<Map<String,Object>> result=new ArrayList<>();
        for(var group:groups.values()){
            var row=new HashMap<>(group.get(0));
            for(String key:List.of("count","completed","lost","damaged"))row.put(key,group.stream().mapToLong(r->(Long)r.get(key)).sum());
            long completed=(Long)row.get("completed"),lost=(Long)row.get("lost");
            row.put("lossRate",percent(lost,completed));row.put("lossAlert",lost>0&&percent(lost,completed)>=policy.getAccessoryLossAlertPercent());
            row.put("serials",String.join(", ",group.stream().map(r->((Product)r.get("product")).getBarcode()).toList()));
            result.add(row);
        }
        result.sort(Comparator.comparingLong((Map<String,Object> r)->(Long)r.get("lossRate")).reversed());
        return result;
    }
}
