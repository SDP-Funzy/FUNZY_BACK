package com.sdp1617.backend.notification.push;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

/**
 * VAPID 키 쌍 (RFC 8292). 푸시 서비스는 구독할 때 쓴 공개키와 발송 요청의 서명이 맞아야 받아준다.
 * 설정한 공개키·비밀키가 짝이 아니면 모든 발송이 거절되므로, 서버가 뜰 때 서명·검증으로 확인한다.
 */
public record VapidKeys(ECPrivateKey privateKey, String publicKey) {

    public static VapidKeys of(String publicKey, String privateKey) {
        ECPublicKey decodedPublic;
        ECPrivateKey decodedPrivate;
        try {
            decodedPublic = EcKeys.decodePublicKey(EcKeys.decodeBase64Url(publicKey));
            decodedPrivate = EcKeys.decodePrivateKey(EcKeys.decodeBase64Url(privateKey));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("VAPID 키 형식이 올바르지 않습니다. (npx web-push generate-vapid-keys 형식)", e);
        }
        if (!isPair(decodedPublic, decodedPrivate)) {
            throw new IllegalStateException("VAPID 공개키와 비밀키가 짝이 아닙니다.");
        }
        return new VapidKeys(decodedPrivate, EcKeys.encodeBase64Url(EcKeys.encodePublicKey(decodedPublic)));
    }

    private static boolean isPair(ECPublicKey publicKey, ECPrivateKey privateKey) {
        try {
            byte[] message = "vapid-key-check".getBytes(StandardCharsets.US_ASCII);
            Signature signer = Signature.getInstance("SHA256withECDSA");
            signer.initSign(privateKey);
            signer.update(message);
            byte[] signature = signer.sign();

            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(publicKey);
            verifier.update(message);
            return verifier.verify(signature);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }
}
