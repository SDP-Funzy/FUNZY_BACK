package com.sdp1617.backend.mypage.dto;

import com.sdp1617.backend.auth.dto.NicknamePolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record NicknameUpdateRequest(
        @Schema(description = "새 " + NicknamePolicy.DESCRIPTION, example = "sweet.day")
        @NotBlank(message = NicknamePolicy.REQUIRED_MESSAGE)
        @Pattern(regexp = NicknamePolicy.REGEXP, message = NicknamePolicy.MESSAGE)
        String nickname
) {
    public NicknameUpdateRequest {
        nickname = nickname == null ? null : nickname.trim();
    }
}
