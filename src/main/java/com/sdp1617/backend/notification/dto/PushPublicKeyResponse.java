package com.sdp1617.backend.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record PushPublicKeyResponse(
        @Schema(description = "VAPID 공개키 (base64url). PushManager.subscribe의 applicationServerKey로 쓴다. "
                + "서버에서 푸시가 꺼져 있으면 null — 이때는 구독하지 않는다",
                example = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4",
                nullable = true)
        String publicKey
) {
}
