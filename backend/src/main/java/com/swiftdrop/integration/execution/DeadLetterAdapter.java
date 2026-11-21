package com.swiftdrop.integration.execution;

import java.util.*;
import org.springframework.amqp.rabbit.core.*;
import org.springframework.stereotype.Component;

@Component
public class DeadLetterAdapter {
    private static final Set<String> CONSUMERS=Set.of("tracking","notification","webhook");
    private final RabbitTemplate rabbit; private final RabbitAdmin admin;
    public DeadLetterAdapter(RabbitTemplate rabbit,org.springframework.amqp.rabbit.connection.ConnectionFactory connections) { this.rabbit=rabbit; this.admin=new RabbitAdmin(connections); }
    public List<Map<String,Object>> queues() {
        return CONSUMERS.stream().sorted().map(consumer->{
            var active=admin.getQueueInfo("swiftdrop."+consumer); var dead=admin.getQueueInfo("swiftdrop."+consumer+".dead");
            return Map.<String,Object>of("consumer",consumer,"ready",active==null?0:active.getMessageCount(),"dead",dead==null?0:dead.getMessageCount(),"available",active!=null&&dead!=null);
        }).toList();
    }
    public int replay(String consumer,int limit) {
        if(!CONSUMERS.contains(consumer)||limit<1||limit>20)throw new IllegalArgumentException("Invalid replay request");
        String queue="swiftdrop."+consumer;
        if(admin.getQueueInfo(queue)==null)throw new IllegalArgumentException("Consumer queue unavailable");
        return rabbit.execute(channel->{
            channel.confirmSelect(); int sent=0;
            for(int i=0;i<limit;i++) {
                var message=channel.basicGet(queue+".dead",false); if(message==null)break;
                try {
                    channel.basicPublish("",queue,true,message.getProps(),message.getBody()); channel.waitForConfirmsOrDie(5000);
                    channel.basicAck(message.getEnvelope().getDeliveryTag(),false); sent++;
                } catch(Exception failure) {
                    channel.basicNack(message.getEnvelope().getDeliveryTag(),false,true); throw failure;
                }
            }
            return sent;
        });
    }
}
