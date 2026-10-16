package com.sdp1617.backend.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 브라우저의 {@code PushSubscription.toJSON()} 그대로. expirationTime 등 다른 필드는 무시한다. */
public record PushSubscriptionRequest(
        @Schema(description = "구독 주소", example = "https://fcm.googleapis.com/fcm/send/dX2...")
        @NotBlank(message = "endpoint는 필수입니다.")
        @Size(max = 1024, message = "endpoint는 1024자 이하여야 합니다.")
        String endpoint,
        @NotNull(message = "keys는 필수입니다.")
        @Valid
        @Schema(description = "PushSubscription.toJSON()의 keys")
        Keys keys
) {
    public record Keys(
            @Schema(description = "브라우저 공개키 (base64url)",
                    example = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4")
            @NotBlank(message = "keys.p256dh는 필수입니다.")
            @Size(max = 100, message = "keys.p256dh가 올바르지 않습니다.")
            String p256dh,
            @Schema(description = "브라우저 auth 비밀값 (base64url)", example = "BTBZMqHH6r4Tts7J_aSIgg")
            @NotBlank(message = "keys.auth는 필수입니다.")
            @Size(max = 50, message = "keys.auth가 올바르지 않습니다.")
            String auth
    ) {
    }
}
