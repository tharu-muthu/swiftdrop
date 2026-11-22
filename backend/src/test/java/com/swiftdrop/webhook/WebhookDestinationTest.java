package com.swiftdrop.webhook;

import com.swiftdrop.integration.webhook.JdkWebhookTransport;
import java.net.InetAddress;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WebhookDestinationTest {
    @Test void rejectsPrivateLoopbackMetadataAndSharedAddressSpace() throws Exception {
        for(String address:java.util.List.of("127.0.0.1","10.20.30.40","172.16.1.1","192.168.1.1","169.254.169.254","100.64.0.1","198.18.0.1","0.0.0.0","224.0.0.1","::1","fc00::1","fe80::1"))
            assertThat(JdkWebhookTransport.isPublicAddress(InetAddress.getByName(address))).as(address).isFalse();
        assertThat(JdkWebhookTransport.isPublicAddress(InetAddress.getByName("8.8.8.8"))).isTrue();
        assertThat(JdkWebhookTransport.isPublicAddress(InetAddress.getByName("2606:4700:4700::1111"))).isTrue();
    }
}
