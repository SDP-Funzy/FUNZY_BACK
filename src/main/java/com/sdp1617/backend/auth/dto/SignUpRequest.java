package com.sdp1617.backend.auth.dto;

import com.sdp1617.backend.auth.entity.Consent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
        @Schema(description = "인증번호 확인 API에서 받은 이메일 인증 완료 토큰", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        @NotBlank(message = "이메일 인증이 필요합니다.")
        String verificationToken,

        @Schema(description = "비밀번호 (8자 이상, 영문+숫자+특수문자 조합)", example = "Password1!")
        @NotBlank(message = "비밀번호를 입력해주세요.")
        @Pattern(regexp = PasswordPolicy.REGEXP, message = PasswordPolicy.MESSAGE)
        @MaxUtf8Bytes(value = PasswordPolicy.MAX_BYTES, message = PasswordPolicy.MAX_BYTES_MESSAGE)
        String password,

        @Schema(description = "비밀번호 확인", example = "Password1!")
        @NotBlank(message = "비밀번호 확인을 입력해주세요.")
        String passwordConfirm,

        @Schema(description = "아이디로 사용할 닉네임 (2~20자, 중복 불가)", example = "닉네임")
        @NotBlank(message = "닉네임을 입력해주세요.")
        @Size(min = 2, max = 20, message = "닉네임은 2~20자 이내여야 합니다.")
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
    public SignUpRequest {
        nickname = nickname == null ? null : nickname.trim();
    }

    public Consent toConsent() {
        return new Consent(termsAgreed, age14Confirmed, marketingConsent, adConsent);
    }
}
