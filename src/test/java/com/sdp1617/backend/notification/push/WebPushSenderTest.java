package com.sdp1617.backend.notification.push;

import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 로컬 HTTP 서버를 푸시 서비스로 두고, 실제로 나가는 요청과 응답 처리를 확인한다. */
class WebPushSenderTest {

    // RFC 8291 부록 A의 키 쌍을 VAPID 키로 쓴다
    private static final String VAPID_PUBLIC =
            "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String VAPID_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String P256DH =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";

    private HttpServer server;
    private final AtomicInteger responseStatus = new AtomicInteger(201);
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> contentEncoding = new AtomicReference<>();
    private final AtomicReference<String> ttl = new AtomicReference<>();
    private final AtomicInteger bodyLength = new AtomicInteger();

    private WebPushSender sender;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/push", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            contentEncoding.set(exchange.getRequestHeaders().getFirst("Content-Encoding"));
            ttl.set(exchange.getRequestHeaders().getFirst("TTL"));
            bodyLength.set(exchange.getRequestBody().readAllBytes().length);
            exchange.sendResponseHeaders(responseStatus.get(), -1);
            exchange.close();
        });
        server.start();
        sender = new WebPushSender(VapidKeys.of(VAPID_PUBLIC, VAPID_PRIVATE), "mailto:push@test.example",
                Duration.ofDays(1), RestClient.builder());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/push/abc";
    }

    @Test
    void 암호화한_본문과_VAPID_서명을_보낸다() {
        byte[] payload = "{\"body\":\"hello\"}".getBytes();

        assertEquals(PushSendResult.SENT, sender.send(endpoint(), P256DH, AUTH, payload));

        assertEquals("aes128gcm", contentEncoding.get());
        assertEquals("86400", ttl.get());
        // 헤더 86바이트 + 본문 + 구분자 1 + GCM 태그 16
        assertEquals(86 + payload.length + 1 + 16, bodyLength.get());

        String header = authorization.get();
        assertTrue(header.startsWith("vapid t="));
        String token = header.substring("vapid t=".length(), header.indexOf(", k="));
        assertEquals(VAPID_PUBLIC, header.substring(header.indexOf(", k=") + ", k=".length()));
        Claims claims = Jwts.parser()
                .verifyWith(EcKeys.decodePublicKey(EcKeys.decodeBase64Url(VAPID_PUBLIC)))
                .build().parseSignedClaims(token).getPayload();
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        assertEquals(Set.of(origin), claims.getAudience());
        // 푸시 서비스는 aud를 배열이 아닌 문자열로 받는다
        String claimsJson = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        assertTrue(claimsJson.contains("\"aud\":\"" + origin + "\""), claimsJson);
        assertEquals("mailto:push@test.example", claims.getSubject());
        assertTrue(claims.getExpiration().getTime() - System.currentTimeMillis() <= Duration.ofHours(24).toMillis());
    }

    @Test
    void 만료된_구독이면_EXPIRED() {
        responseStatus.set(410);
        assertEquals(PushSendResult.EXPIRED, sender.send(endpoint(), P256DH, AUTH, new byte[]{1}));
        responseStatus.set(404);
        assertEquals(PushSendResult.EXPIRED, sender.send(endpoint(), P256DH, AUTH, new byte[]{1}));
    }

    @Test
    void 그_외_오류나_연결_실패는_FAILED() {
        responseStatus.set(302); // 리다이렉트는 따라가지 않는다
        assertEquals(PushSendResult.FAILED, sender.send(endpoint(), P256DH, AUTH, new byte[]{1}));
        responseStatus.set(429);
        assertEquals(PushSendResult.FAILED, sender.send(endpoint(), P256DH, AUTH, new byte[]{1}));
        responseStatus.set(500);
        assertEquals(PushSendResult.FAILED, sender.send(endpoint(), P256DH, AUTH, new byte[]{1}));

        String closed = endpoint();
        server.stop(0);
        assertEquals(PushSendResult.FAILED, sender.send(closed, P256DH, AUTH, new byte[]{1}));
    }

    @Test
    void 짝이_아닌_VAPID_키는_서버가_뜰_때_거부한다() {
        assertThrows(IllegalStateException.class, () -> VapidKeys.of(P256DH, VAPID_PRIVATE));
        assertThrows(IllegalStateException.class, () -> VapidKeys.of("not-a-key", VAPID_PRIVATE));
    }
}
