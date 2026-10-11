package com.sdp1617.backend.notification.push;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

import static com.sdp1617.backend.notification.push.EcKeys.decodeBase64Url;
import static com.sdp1617.backend.notification.push.EcKeys.encodeBase64Url;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebPushEncryptorTest {

    /** RFC 8291 부록 A의 예시: 같은 키·salt면 RFC에 적힌 본문과 한 바이트도 다르지 않아야 한다. */
    @Test
    void RFC_8291_예시와_같은_본문을_만든다() {
        byte[] plaintext = "When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8);
        byte[] uaPublic = decodeBase64Url(
                "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
        byte[] authSecret = decodeBase64Url("BTBZMqHH6r4Tts7J_aSIgg");
        KeyPair asKeys = new KeyPair(
                EcKeys.decodePublicKey(decodeBase64Url(
                        "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8")),
                EcKeys.decodePrivateKey(decodeBase64Url("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw")));
        byte[] salt = decodeBase64Url("DGv6ra1nlYgDCS1FRnbzlw");

        byte[] body = WebPushEncryptor.encrypt(plaintext, uaPublic, authSecret, asKeys, salt);

        assertEquals("DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS"
                + "6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN",
                encodeBase64Url(body));
    }

    /** 브라우저 입장에서 받은 본문을 복호화해 원래 내용이 나오는지 (RFC 8291 3.4 수신 측 절차). */
    @Test
    void 브라우저가_자기_비밀키로_복호화할_수_있다() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair browser = generator.generateKeyPair();
        byte[] uaPublic = EcKeys.encodePublicKey((ECPublicKey) browser.getPublic());
        byte[] authSecret = new byte[16];
        Arrays.fill(authSecret, (byte) 7);
        byte[] payload = "{\"content\":\"티키님에게서 편지가 도착했어요.\"}".getBytes(StandardCharsets.UTF_8);

        byte[] body = WebPushEncryptor.encrypt(payload, uaPublic, authSecret);

        ByteBuffer buffer = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        buffer.get(salt);
        assertEquals(4096, buffer.getInt());
        byte[] asPublic = new byte[buffer.get()];
        buffer.get(asPublic);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(browser.getPrivate());
        agreement.doPhase(EcKeys.decodePublicKey(asPublic), true);
        byte[] ikm = hkdf(authSecret, agreement.generateSecret(),
                concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic), 32);
        byte[] cek = hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(ciphertext);

        assertEquals(0x02, padded[padded.length - 1]);
        assertArrayEquals(payload, Arrays.copyOf(padded, padded.length - 1));
    }

    @Test
    void 본문이_너무_크면_암호화하지_않는다() {
        byte[] uaPublic = decodeBase64Url(
                "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");

        assertThrows(IllegalArgumentException.class, () -> WebPushEncryptor.encrypt(
                new byte[WebPushEncryptor.MAX_PAYLOAD_BYTES + 1], uaPublic, new byte[16]));
    }

    @Test
    void 곡선_위의_점이_아닌_공개키는_거부한다() {
        byte[] notOnCurve = new byte[EcKeys.PUBLIC_KEY_BYTES];
        notOnCurve[0] = 0x04;
        notOnCurve[64] = 1;

        assertThrows(IllegalArgumentException.class, () -> EcKeys.decodePublicKey(notOnCurve));
    }

    private static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        return Arrays.copyOf(mac.doFinal(concat(info, new byte[]{0x01})), length);
    }

    private static byte[] concat(byte[]... parts) {
        int length = Arrays.stream(parts).mapToInt(part -> part.length).sum();
        ByteBuffer buffer = ByteBuffer.allocate(length);
        Arrays.stream(parts).forEach(buffer::put);
        return buffer.array();
    }
}
