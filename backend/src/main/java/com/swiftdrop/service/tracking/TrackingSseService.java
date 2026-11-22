package com.swiftdrop.service.tracking;

import com.swiftdrop.dto.tracking.PublicTrackingResponse;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class TrackingSseService {
    private final ConcurrentHashMap<String, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String trackingNumber, PublicTrackingResponse current) {
        var emitter = new SseEmitter(30 * 60 * 1000L);
        if(subscribers.size()>=1000 && !subscribers.containsKey(trackingNumber)) throw new com.swiftdrop.exception.RateLimitException(60);
        var listeners = subscribers.computeIfAbsent(trackingNumber, ignored -> new CopyOnWriteArraySet<>());
        if(listeners.size()>=50) throw new com.swiftdrop.exception.RateLimitException(60);
        listeners.add(emitter);
        emitter.onCompletion(() -> remove(trackingNumber, emitter));
        emitter.onTimeout(() -> remove(trackingNumber, emitter));
        emitter.onError(error -> remove(trackingNumber, emitter));
        try {
            emitter.send(SseEmitter.event().name("tracking-update").data(current));
        } catch (IOException | IllegalStateException error) {
            remove(trackingNumber, emitter);
            emitter.completeWithError(error);
        }
        return emitter;
    }

    public void publish(String trackingNumber, PublicTrackingResponse update) {
        var listeners = subscribers.get(trackingNumber);
        if (listeners == null) return;
        listeners.forEach(emitter -> send(trackingNumber, emitter, update));
    }

    private void send(String trackingNumber, SseEmitter emitter, PublicTrackingResponse update) {
        try {
            emitter.send(SseEmitter.event().name("tracking-update").data(update));
        } catch (IOException | IllegalStateException error) {
            remove(trackingNumber, emitter);
            emitter.completeWithError(error);
        }
    }

    private void remove(String trackingNumber, SseEmitter emitter) {
        var listeners = subscribers.get(trackingNumber);
        if (listeners == null) return;
        listeners.remove(emitter);
        if (listeners.isEmpty()) subscribers.remove(trackingNumber, listeners);
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay=20000)
    public void heartbeat() {
        subscribers.forEach((number,listeners)->listeners.forEach(emitter->{
            try { emitter.send(SseEmitter.event().comment("keepalive")); }
            catch(IOException|IllegalStateException error) { remove(number,emitter);emitter.complete(); }
        }));
    }
}
