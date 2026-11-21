package com.swiftdrop.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="swiftdrop.events",name="enabled",havingValue="true",matchIfMissing=true)
public class ParcelMessagingConfiguration {
    public static final String EXCHANGE="swiftdrop.parcel.events";
    @Bean Declarables parcelBindings() {
        List<Declarable> declarations=new ArrayList<>();
        var exchange=new TopicExchange(EXCHANGE,true,false);
        var dead=new DirectExchange("swiftdrop.parcel.dead",true,false);
        declarations.add(exchange); declarations.add(dead);
        for(String consumer:List.of("tracking","notification","webhook")) {
            String name="swiftdrop."+consumer;
            var queue=QueueBuilder.durable(name).deadLetterExchange(dead.getName()).deadLetterRoutingKey(name).build();
            var dlq=QueueBuilder.durable(name+".dead").build();
            declarations.add(queue); declarations.add(dlq);
            declarations.add(BindingBuilder.bind(queue).to(exchange).with("parcel.status.changed"));
            declarations.add(BindingBuilder.bind(dlq).to(dead).with(name));
        }
        return new Declarables(declarations);
    }
}

