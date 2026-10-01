UPDATE rental_orders SET rental_paid=COALESCE(rental_paid,0), security_paid=COALESCE(security_paid,0),
 shipping_total=COALESCE(shipping_total,0), discount_total=COALESCE(discount_total,0),
 extra_due=COALESCE(extra_due,0), penalty_total=COALESCE(penalty_total,0), refunded_deposit=COALESCE(refunded_deposit,0);
UPDATE product SET wash_count=COALESCE(wash_count,0), rental_count=COALESCE(rental_count,0), stock_status=COALESCE(stock_status,'AVAILABLE');
UPDATE staff_account SET enabled=COALESCE(enabled,false), role=COALESCE(role,'STAFF');
UPDATE shop_policy SET accessory_loss_alert_percent=COALESCE(accessory_loss_alert_percent,30),
 accessory_restock_target=COALESCE(accessory_restock_target,3),
 retirement_rental_threshold=COALESCE(retirement_rental_threshold,80),
 retirement_wash_threshold=COALESCE(retirement_wash_threshold,80);
UPDATE penalty_rule SET incident_type=COALESCE(incident_type,CASE WHEN code='LOST' THEN 'LOSS' ELSE 'DAMAGE' END);
UPDATE penalty_rule SET incident_type='LOSS', next_stock_status='RETIRED' WHERE code='LOST';
