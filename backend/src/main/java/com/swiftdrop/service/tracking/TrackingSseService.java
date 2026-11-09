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
        var listeners = subscribers.computeIfAbsent(trackingNumber, ignored -> new CopyOnWriteArraySet<>());
        listeners.add(emitter);
        emitter.onCompletion(() -> remove(trackingNumber, emitter));
        emitter.onTimeout(() -> remove(trackingNumber, emitter));
        try {
            emitter.send(SseEmitter.event().name("tracking-update").data(current));
        } catch (IOException error) {
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
        } catch (IOException error) {
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
}
