package com.sdp1617.backend.notification.push;

/** 브라우저 구독 하나로 푸시를 보낸다 (#89). 푸시가 꺼져 있으면 아무것도 보내지 않는 구현이 쓰인다. */
public interface PushSender {

    /**
     * @param endpoint 구독 주소 (브라우저 푸시 서비스)
     * @param p256dh   구독의 공개키 (base64url)
     * @param auth     구독의 auth (base64url)
     * @param payload  보낼 내용. 구독 키로 암호화해서 보낸다
     */
    PushSendResult send(String endpoint, String p256dh, String auth, byte[] payload);
}
