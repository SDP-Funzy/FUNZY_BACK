package com.sdp1617.backend.letter.dto;

import com.sdp1617.backend.card.dto.DesignType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 봉투 구성: 받는 사람·보내는 사람 이름과 디자인 (LW-111/112). */
public record LetterEnvelopeRequest(
        @Schema(description = "받는 사람 이름 (봉투의 To, 1~10자)", example = "은우")
        @NotBlank(message = "받는 사람 이름은 필수입니다.")
        @Size(max = 10, message = "받는 사람 이름은 10자 이내여야 합니다.")
        String toName,

        @Schema(description = "보내는 사람 이름 (봉투의 From, 1~10자)", example = "티키")
        @NotBlank(message = "보내는 사람 이름은 필수입니다.")
        @Size(max = 10, message = "보내는 사람 이름은 10자 이내여야 합니다.")
        String fromName,

        @Schema(description = "봉투 디자인", example = "DesignType_A")
        @NotNull(message = "봉투 디자인은 필수입니다.")
        DesignType designType
) {
    public LetterEnvelopeRequest {
        toName = toName == null ? null : toName.trim();
        fromName = fromName == null ? null : fromName.trim();
    }
}
