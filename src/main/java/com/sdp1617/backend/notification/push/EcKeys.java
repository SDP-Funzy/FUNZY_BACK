package com.sdp1617.backend.notification.push;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * 웹 푸시에서 쓰는 P-256 키 변환. 브라우저 구독 키(p256dh)와 VAPID 공개키는 압축하지 않은 점(0x04 || x || y, 65바이트),
 * VAPID 비밀키는 32바이트 정수를 base64url로 주고받는다 ({@code npx web-push generate-vapid-keys} 형식).
 */
public final class EcKeys {

    private static final int COORDINATE_BYTES = 32;
    public static final int PUBLIC_KEY_BYTES = 1 + 2 * COORDINATE_BYTES;

    private static final ECParameterSpec P256 = p256();

    private EcKeys() {
    }

    public static byte[] decodeBase64Url(String value) {
        return Base64.getUrlDecoder().decode(value.trim().replace("=", ""));
    }

    public static String encodeBase64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    /** 압축하지 않은 P-256 점을 공개키로. 형식이 틀리거나 곡선 위의 점이 아니면 IllegalArgumentException. */
    public static ECPublicKey decodePublicKey(byte[] uncompressed) {
        if (uncompressed.length != PUBLIC_KEY_BYTES || uncompressed[0] != 0x04) {
            throw new IllegalArgumentException("P-256 공개키 형식이 아닙니다.");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 1 + COORDINATE_BYTES));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(uncompressed, 1 + COORDINATE_BYTES, PUBLIC_KEY_BYTES));
        if (!isOnCurve(x, y)) {
            throw new IllegalArgumentException("P-256 곡선 위의 점이 아닙니다.");
        }
        try {
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), P256));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("P-256 공개키 형식이 아닙니다.", e);
        }
    }

    public static ECPrivateKey decodePrivateKey(byte[] scalar) {
        if (scalar.length != COORDINATE_BYTES) {
            throw new IllegalArgumentException("P-256 비밀키 형식이 아닙니다.");
        }
        try {
            return (ECPrivateKey) KeyFactory.getInstance("EC")
                    .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, scalar), P256));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("P-256 비밀키 형식이 아닙니다.", e);
        }
    }

    public static byte[] encodePublicKey(ECPublicKey key) {
        byte[] out = new byte[PUBLIC_KEY_BYTES];
        out[0] = 0x04;
        writeCoordinate(key.getW().getAffineX(), out, 1);
        writeCoordinate(key.getW().getAffineY(), out, 1 + COORDINATE_BYTES);
        return out;
    }

    private static void writeCoordinate(BigInteger value, byte[] out, int offset) {
        byte[] bytes = value.toByteArray();
        // toByteArray는 부호 바이트(0x00)가 앞에 붙거나 앞자리 0이 빠질 수 있어 32바이트로 맞춘다
        int length = Math.min(bytes.length, COORDINATE_BYTES);
        System.arraycopy(bytes, bytes.length - length, out, offset + COORDINATE_BYTES - length, length);
    }

    /** y² = x³ + ax + b (mod p). 잘못된 점으로 ECDH를 하지 않도록 직접 확인한다. */
    private static boolean isOnCurve(BigInteger x, BigInteger y) {
        BigInteger p = ((ECFieldFp) P256.getCurve().getField()).getP();
        if (x.compareTo(p) >= 0 || y.compareTo(p) >= 0) {
            return false;
        }
        BigInteger left = y.modPow(BigInteger.TWO, p);
        BigInteger right = x.pow(3).add(P256.getCurve().getA().multiply(x)).add(P256.getCurve().getB()).mod(p);
        return left.equals(right);
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 곡선을 지원하지 않는 JVM입니다.", e);
        }
    }
}
