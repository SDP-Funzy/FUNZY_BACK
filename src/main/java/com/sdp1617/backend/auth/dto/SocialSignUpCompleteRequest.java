package com.sdp1617.backend.auth.dto;

import com.sdp1617.backend.auth.entity.Consent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SocialSignUpCompleteRequest(
        @Schema(description = "소셜 로그인 응답으로 받은 signupToken", example = "e7a4e358-2b9b-40f2-ad19-cf85641806f9")
        @NotBlank(message = "signupToken은 필수입니다.")
        String signupToken,

        @Schema(description = NicknamePolicy.DESCRIPTION, example = "tiki_kim")
        @NotBlank(message = NicknamePolicy.REQUIRED_MESSAGE)
        @Pattern(regexp = NicknamePolicy.REGEXP, message = NicknamePolicy.MESSAGE)
        String nickname,

        @Schema(description = "서비스 이용약관 및 개인정보 수집·이용(서비스 운영) 동의 여부 (true만 허용)", example = "true")
        @AssertTrue(message = "약관에 동의해야 가입할 수 있습니다.")
        boolean termsAgreed,

        @Schema(description = "만 14세 이상 확인 (true만 허용)", example = "true")
        @AssertTrue(message = "만 14세 이상만 가입할 수 있습니다.")
        boolean age14Confirmed,

        @Schema(description = "마케팅 목적 개인정보 수집·이용 동의 여부 (선택)", example = "false")
        boolean marketingConsent,

        @Schema(description = "광고성 정보 수신 동의 여부 (선택)", example = "false")
        boolean adConsent
) {
    public SocialSignUpCompleteRequest {
        nickname = nickname == null ? null : nickname.trim();
    }

    public Consent toConsent() {
        return new Consent(termsAgreed, age14Confirmed, marketingConsent, adConsent);
    }
}
