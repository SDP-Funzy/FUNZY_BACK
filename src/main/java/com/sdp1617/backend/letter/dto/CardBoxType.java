package com.sdp1617.backend.letter.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "마음카드 보관함 유형: SENT(보낸함) / RECEIVED(받은함)")
public enum CardBoxType {
    SENT,
    RECEIVED
}
