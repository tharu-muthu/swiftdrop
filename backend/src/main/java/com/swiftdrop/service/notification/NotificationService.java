package com.swiftdrop.service.notification;

import com.swiftdrop.dto.execution.ParcelEvent;
import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.exception.LifecycleConflictException;
import com.swiftdrop.service.execution.IntegrationReceiptService;
import com.swiftdrop.repository.notification.NotificationRepository;
import com.swiftdrop.service.booking.BookingReadService;
import com.swiftdrop.service.identity.IdentityAccountService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final NotificationRepository jobs;
    private final IntegrationReceiptService receipts;
    private final BookingReadService bookings;
    private final IdentityAccountService identities;
    public NotificationService(NotificationRepository jobs,IntegrationReceiptService receipts,BookingReadService bookings,IdentityAccountService identities) {
        this.jobs=jobs; this.receipts=receipts; this.bookings=bookings; this.identities=identities;
    }
    @Transactional
    public void enqueue(ParcelEvent event) {
        if(!receipts.claim("notification",event.eventId())) return;
        var body=jobs.template(event.status()); if(body.isEmpty()) return;
        var booking=bookings.find(event.parcelId()).orElseThrow();
        if(booking.recipientPhone()==null || booking.recipientPhone().isBlank()) return;
        jobs.enqueue(event.eventId(),event.parcelId(),booking.recipientPhone(),body.get().replace("{trackingNumber}",event.trackingNumber()).replace("{status}",event.status()));
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> templates() { admin(); return jobs.templates(); }
    @Transactional
    public void update(String status,String body,boolean enabled,long version) {
        admin();
        if(body.contains("{") && !body.replace("{trackingNumber}","").replace("{status}","").matches("[^{}]*")) throw new IllegalArgumentException("Unsupported template variable");
        if(!jobs.updateTemplate(status,body,enabled,version)) throw new LifecycleConflictException("Template changed. Reload before saving.");
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> jobs() { admin(); return jobs.jobs(); }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> attempts(UUID id) { admin(); return jobs.attempts(id); }
    @Transactional public void retry(UUID id) { admin(); if(!jobs.retry(id)) throw new LifecycleConflictException("Only dead notifications can be retried."); }
    private void admin() { identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN); }
}

