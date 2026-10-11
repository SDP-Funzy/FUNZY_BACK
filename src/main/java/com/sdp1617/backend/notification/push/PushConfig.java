package com.sdp1617.backend.notification.push;

import com.sdp1617.backend.global.config.properties.PushProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class PushConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.push", name = "enabled", havingValue = "true")
    public PushSender webPushSender(PushProperties properties, RestClient.Builder restClientBuilder) {
        VapidKeys vapidKeys = VapidKeys.of(properties.vapidPublicKey(), properties.vapidPrivateKey());
        return new WebPushSender(vapidKeys, properties.subject(), properties.ttl(), restClientBuilder);
    }

    /** 푸시가 꺼져 있으면 보내지 않는다. 구독은 그대로 둔다. */
    @Bean
    @ConditionalOnMissingBean(PushSender.class)
    public PushSender disabledPushSender() {
        return (endpoint, p256dh, auth, payload) -> PushSendResult.FAILED;
    }
}
