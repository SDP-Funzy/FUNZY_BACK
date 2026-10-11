package com.sdp1617.backend.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record PushUnsubscribeRequest(
        @Schema(description = "해제할 구독 주소 (PushSubscription.endpoint)", example = "https://fcm.googleapis.com/fcm/send/dX2...")
        @NotBlank(message = "endpoint는 필수입니다.")
        String endpoint
) {
}
