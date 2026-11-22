package com.swiftdrop.service.webhook;

import com.swiftdrop.repository.webhook.WebhookJobRepository;
import java.nio.charset.StandardCharsets;
import java.time.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="swiftdrop.events",name="enabled",havingValue="true",matchIfMissing=true)
public class WebhookWorker {
    private final WebhookJobRepository jobs; private final WebhookSecretProtector secrets; private final WebhookDeliveryService delivery;
    private final Clock clock; private final TransactionTemplate tx;
    private final com.swiftdrop.service.merchant.MerchantAccessService merchants;
    private final WebhookRetryPolicy retries=new WebhookRetryPolicy(5,Duration.ofSeconds(30),Duration.ofHours(1));
    public WebhookWorker(WebhookJobRepository jobs,WebhookSecretProtector secrets,WebhookDeliveryService delivery,Clock clock,PlatformTransactionManager manager,com.swiftdrop.service.merchant.MerchantAccessService merchants) {
        this.jobs=jobs; this.secrets=secrets; this.delivery=delivery; this.clock=clock; this.tx=new TransactionTemplate(manager);
        this.merchants=merchants;
    }
    @Scheduled(fixedDelayString="${swiftdrop.webhooks.worker-delay-ms:2000}")
    public void run() {
        for(int i=0;i<10;i++) {
            var claimed=tx.execute(status->jobs.claim()); if(claimed==null||claimed.isEmpty()) break;
            var job=claimed.get(); var started=clock.instant();
            WebhookDeliveryService.DeliveryResult result;
            boolean enabled=job.enabled()&&merchants.isApprovedForDelivery(job.merchantId());
            if(!enabled) result=new WebhookDeliveryService.DeliveryResult(false,0,0,"FAILED","SUBSCRIPTION_DISABLED");
            else try {
                var secret=secrets.decrypt(new WebhookSecretProtector.ProtectedSecret(job.encryptedSecret(),job.nonce()));
                result=delivery.deliver(job.eventId(),job.endpoint(),secret,job.body().getBytes(StandardCharsets.UTF_8));
            } catch(RuntimeException error) { result=new WebhookDeliveryService.DeliveryResult(false,0,0,"FAILED","INVALID_DESTINATION_OR_SECRET"); }
            var decision=result.statusCode()==0?retries.networkFailure(job.attempt()):retries.response(job.attempt(),result.statusCode());
            boolean retry=enabled&&!"FAILED".equals(result.state())&&!result.successful()&&decision.retry();
            // HTTP 5xx/408/429 are retryable even though the transport labels their single attempt FAILED.
            if(enabled&&result.statusCode()!=0&&!result.successful()) retry=decision.retry();
            String state=result.successful()?"DELIVERED":retry?"RETRYING":"FAILED";
            var completed=clock.instant(); var retryAt=retry?completed.plus(decision.delay()):null;
            final var outcome=result;
            tx.executeWithoutResult(status->jobs.complete(job,state,outcome.statusCode()==0?null:outcome.statusCode(),outcome.failureType(),started,completed,retryAt));
        }
    }
}

