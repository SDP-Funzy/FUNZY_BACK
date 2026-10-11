package com.sdp1617.backend.notification.push;

public enum PushSendResult {
    /** 푸시 서비스가 받음 */
    SENT,
    /** 구독이 만료되거나 해제됨 (404·410). 다시 보내도 소용없으니 구독을 지운다 */
    EXPIRED,
    /** 일시적인 오류이거나 보내지 않음. 구독은 그대로 둔다 */
    FAILED
}
