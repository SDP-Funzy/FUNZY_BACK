package com.sdp1617.backend.notification.push;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 웹 푸시 메시지 본문 암호화 (RFC 8291, aes128gcm 형식은 RFC 8188).
 * 브라우저가 구독할 때 준 공개키(p256dh)와 auth로 암호화해, 중간의 푸시 서버는 내용을 볼 수 없다.
 * 메시지마다 새 임시 키 쌍과 salt를 만든다.
 */
public final class WebPushEncryptor {

    /** 레코드 크기. 본문이 한 레코드에 들어가야 하므로 암호화한 본문(+구분자 1바이트, 태그 16바이트)이 이보다 작아야 한다. */
    private static final int RECORD_SIZE = 4096;
    /** 푸시 서비스가 받는 최대 본문 크기(4096)에서 헤더 86바이트·구분자·태그를 뺀 값. */
    public static final int MAX_PAYLOAD_BYTES = 3993;

    private static final SecureRandom RANDOM = new SecureRandom();

    private WebPushEncryptor() {
    }

    /**
     * @param payload          보낼 내용 (JSON 등)
     * @param userAgentPublic  구독의 p256dh — P-256 공개키(압축하지 않은 65바이트)
     * @param authSecret       구독의 auth — 16바이트
     * @return 푸시 서비스에 그대로 보낼 본문 (Content-Encoding: aes128gcm)
     */
    public static byte[] encrypt(byte[] payload, byte[] userAgentPublic, byte[] authSecret) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(payload, userAgentPublic, authSecret, newEphemeralKeyPair(), salt);
    }

    /** 임시 키 쌍과 salt를 정해 암호화한다. RFC 8291 부록 A의 예시 값으로 검증하려고 분리했다. */
    static byte[] encrypt(byte[] payload, byte[] userAgentPublic, byte[] authSecret, KeyPair ephemeral, byte[] salt) {
        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("푸시 본문이 너무 큽니다: " + payload.length + "바이트");
        }
        try {
            byte[] serverPublic = EcKeys.encodePublicKey((ECPublicKey) ephemeral.getPublic());

            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(ephemeral.getPrivate());
            agreement.doPhase(EcKeys.decodePublicKey(userAgentPublic), true);
            byte[] ecdhSecret = agreement.generateSecret();

            // IKM = HKDF(auth, ecdh, "WebPush: info" || 0x00 || ua_public || as_public, 32)
            byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), userAgentPublic, serverPublic);
            byte[] ikm = hkdf(authSecret, ecdhSecret, keyInfo, 32);

            byte[] cek = hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
            byte[] nonce = hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

            // 레코드가 하나뿐이라 마지막 레코드 구분자(0x02)만 붙이고 패딩은 넣지 않는다
            byte[] plaintext = Arrays.copyOf(payload, payload.length + 1);
            plaintext[payload.length] = 0x02;

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext);

            // 헤더: salt(16) || rs(4) || idlen(1) || keyid(서버 임시 공개키 65)
            ByteBuffer body = ByteBuffer.allocate(16 + 4 + 1 + serverPublic.length + ciphertext.length);
            body.put(salt).putInt(RECORD_SIZE).put((byte) serverPublic.length).put(serverPublic).put(ciphertext);
            return body.array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("푸시 본문 암호화에 실패했습니다.", e);
        }
    }

    private static KeyPair newEphemeralKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 키 생성에 실패했습니다.", e);
        }
    }

    /** HKDF-SHA256 (RFC 5869). 결과가 32바이트 이하라 expand는 블록 하나로 끝난다. */
    private static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        byte[] prk = hmacSha256(salt, ikm);
        return Arrays.copyOf(hmacSha256(prk, concat(info, new byte[]{0x01})), length);
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
