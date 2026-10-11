package com.sdp1617.backend.notification.push;

import io.jsonwebtoken.Jwts;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 웹 푸시 발송 (RFC 8030). 본문은 구독 키로 암호화하고(RFC 8291), VAPID로 서명해(RFC 8292) 브라우저 푸시 서비스에 보낸다.
 * 푸시 서비스가 404·410을 주면 만료된 구독이다.
 */
@Slf4j
public class WebPushSender implements PushSender {

    /** VAPID 토큰 유효 기간. 푸시 서비스는 24시간 이하만 받는다. */
    private static final Duration TOKEN_LIFETIME = Duration.ofHours(12);

    private final VapidKeys vapidKeys;
    private final String subject;
    private final Duration ttl;
    private final RestClient restClient;

    public WebPushSender(VapidKeys vapidKeys, String subject, Duration ttl, RestClient.Builder restClientBuilder) {
        this.vapidKeys = vapidKeys;
        this.subject = subject;
        this.ttl = ttl;
        // 구독 주소의 도메인만 확인하므로, 푸시 서비스가 리다이렉트로 다른 곳(내부망 등)을 가리켜도 따라가지 않는다
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
    }

    @Override
    public PushSendResult send(String endpoint, String p256dh, String auth, byte[] payload) {
        URI uri = URI.create(endpoint);
        byte[] body = WebPushEncryptor.encrypt(payload, EcKeys.decodeBase64Url(p256dh), EcKeys.decodeBase64Url(auth));
        try {
            HttpStatusCode status = restClient.post()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "vapid t=" + vapidToken(uri) + ", k=" + vapidKeys.publicKey())
                    .header(HttpHeaders.CONTENT_ENCODING, "aes128gcm")
                    .header("TTL", String.valueOf(ttl.toSeconds()))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(body)
                    .exchange((request, response) -> response.getStatusCode());
            if (status.is2xxSuccessful()) {
                return PushSendResult.SENT;
            }
            if (status.value() == 404 || status.value() == 410) {
                return PushSendResult.EXPIRED;
            }
            log.warn("웹 푸시 발송 실패: host={}, status={}", uri.getHost(), status.value());
            return PushSendResult.FAILED;
        } catch (RestClientException e) {
            log.warn("웹 푸시 발송 실패: host={}, error={}", uri.getHost(), e.getMessage());
            return PushSendResult.FAILED;
        }
    }

    /**
     * 푸시 서비스 주소의 origin을 aud로 하는 ES256 JWT.
     * VAPID(RFC 8292)는 aud를 문자열 하나로 정해, 배열로 만드는 audience().add 대신 single을 쓴다 (jjwt에서는 deprecated).
     */
    @SuppressWarnings("deprecation")
    private String vapidToken(URI endpoint) {
        String audience = endpoint.getScheme() + "://" + endpoint.getHost()
                + (endpoint.getPort() == -1 ? "" : ":" + endpoint.getPort());
        return Jwts.builder()
                .header().add("typ", "JWT").and()
                .audience().single(audience)
                .subject(subject)
                .expiration(Date.from(Instant.now().plus(TOKEN_LIFETIME)))
                .signWith(vapidKeys.privateKey(), Jwts.SIG.ES256)
                .compact();
    }
}
