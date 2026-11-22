package com.swiftdrop.service.execution;

import com.swiftdrop.config.ParcelMessagingConfiguration;
import com.swiftdrop.repository.execution.OutboxRelayRepository;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="swiftdrop.events",name="enabled",havingValue="true",matchIfMissing=true)
public class OutboxRelay {
    private final OutboxRelayRepository outbox;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate transactions;
    public OutboxRelay(OutboxRelayRepository outbox,RabbitTemplate rabbit,PlatformTransactionManager manager) {
        this.outbox=outbox; this.rabbit=rabbit; this.transactions=new TransactionTemplate(manager);
    }
    @Scheduled(fixedDelayString="${swiftdrop.events.relay-delay-ms:1000}")
    public void publish() {
        for(int i=0;i<25;i++) {
            Boolean sent=transactions.execute(tx->{
                var entries=outbox.next(); if(entries.isEmpty()) return false;
                var event=entries.getFirst();
                try {
                    var properties=new MessageProperties(); properties.setContentType("application/json");
                    properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT); properties.setMessageId(event.id().toString());
                    var correlation=new CorrelationData(event.id().toString());
                    rabbit.send(ParcelMessagingConfiguration.EXCHANGE,"parcel.status.changed",
                            new Message(event.payload().getBytes(StandardCharsets.UTF_8),properties),correlation);
                    var confirm=correlation.getFuture().get(5,TimeUnit.SECONDS);
                    if(!confirm.isAck() || correlation.getReturned()!=null) throw new IllegalStateException("Broker did not accept event");
                    outbox.published(event.id()); return true;
                } catch(Exception failure) {
                    if(failure instanceof InterruptedException) Thread.currentThread().interrupt();
                    outbox.failed(event.id()); return false;
                }
            });
            if(!Boolean.TRUE.equals(sent)) break;
        }
    }
}

