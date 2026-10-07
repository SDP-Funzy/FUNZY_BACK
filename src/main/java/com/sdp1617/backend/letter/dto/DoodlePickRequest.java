package com.sdp1617.backend.letter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 두들픽: 선물 후보 2~3개와 선정 이유 (LW-610~621). 편지당 0~1개라 전체 교체로 저장한다. */
public record DoodlePickRequest(
        @Schema(description = "선정 이유 (선택, 150자 이내)", example = "요즘 피곤해 보여서 골라봤어")
        @Size(max = 150, message = "선정 이유는 150자 이내여야 합니다.")
        String reason,

        @Schema(description = "선물 후보 이름 2~3개 (각 30자 이내)", example = "[\"향초\", \"목베개\"]")
        @NotNull(message = "선물 후보는 필수입니다.")
        @Size(min = 2, max = 3, message = "선물 후보는 2~3개여야 합니다.")
        List<@NotBlank(message = "선물 이름은 필수입니다.") @Size(max = 30, message = "선물 이름은 30자 이내여야 합니다.") String> giftNames
) {
    public DoodlePickRequest {
        reason = reason == null || reason.isBlank() ? null : reason.trim();
        giftNames = giftNames == null ? null : giftNames.stream().map(name -> name == null ? null : name.trim()).toList();
    }
}
