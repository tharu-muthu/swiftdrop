package com.swiftdrop.service.notification;

import com.swiftdrop.integration.notification.SmsProvider;
import com.swiftdrop.repository.notification.NotificationRepository;
import java.time.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="swiftdrop.events",name="enabled",havingValue="true",matchIfMissing=true)
public class NotificationWorker {
    private final NotificationRepository jobs; private final SmsProvider provider; private final TransactionTemplate tx; private final Clock clock;
    public NotificationWorker(NotificationRepository jobs,SmsProvider provider,PlatformTransactionManager manager,Clock clock) {
        this.jobs=jobs; this.provider=provider; this.tx=new TransactionTemplate(manager); this.clock=clock;
    }
    @Scheduled(fixedDelayString="${swiftdrop.notifications.worker-delay-ms:2000}")
    public void run() {
        for(int i=0;i<25;i++) {
            var claimed=tx.execute(status->jobs.claim()); if(claimed==null||claimed.isEmpty()) break;
            var job=claimed.get(); boolean success=false; String reference=null; String error=null;
            try { reference=provider.send(job.id(),job.phone(),job.message()); success=true; }
            catch(RuntimeException failure) { error="PROVIDER_FAILURE"; }
            final boolean sent=success; final String ref=reference,code=error;
            tx.executeWithoutResult(status->jobs.complete(job,sent,ref,code,clock.instant().plusSeconds(Math.min(3600,30L<<Math.min(job.attempt()-1,7)))));
        }
    }
}

