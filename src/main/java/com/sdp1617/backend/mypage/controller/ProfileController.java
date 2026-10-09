package com.sdp1617.backend.mypage.controller;

import com.sdp1617.backend.global.common.response.ApiResponse;
import com.sdp1617.backend.mypage.dto.NicknameUpdateRequest;
import com.sdp1617.backend.mypage.dto.ProfileImagePresignedUrlRequest;
import com.sdp1617.backend.mypage.dto.ProfileImagePresignedUrlResponse;
import com.sdp1617.backend.mypage.dto.ProfileImageUploadCompleteRequest;
import com.sdp1617.backend.mypage.dto.ProfileResponse;
import com.sdp1617.backend.mypage.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mypage/profile")
@Tag(name = "마이페이지 - 프로필", description = "프로필 조회, 아이디 변경, 프로필 이미지 업로드/초기화 API")
public class ProfileController {

    private final ProfileService profileService;

    @Operation(summary = "프로필 조회", description = "아이디(nickname), 프로필 이미지 URL, 가입 방식을 조회합니다.")
    @GetMapping
    public ApiResponse<ProfileResponse> getProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.ok("프로필을 조회했습니다.", profileService.getProfile(memberId));
    }

    @Operation(summary = "아이디 변경", description = "아이디(nickname)를 변경합니다. 2~20자 영문 소문자·숫자·마침표(.)·밑줄(_)만, 마침표로 시작·끝·연속 불가(COMMON_002). 다른 회원이 사용 중인 아이디면 실패합니다(AUTH_007).")
    @PatchMapping("/nickname")
    public ApiResponse<Void> updateNickname(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody NicknameUpdateRequest request
    ) {
        profileService.updateNickname(memberId, request);
        return ApiResponse.ok("아이디가 변경되었습니다.", null);
    }

    @Operation(summary = "프로필 이미지 업로드용 Presigned URL 발급", description = """
            S3에 이미지를 직접 업로드할 수 있는 presigned PUT URL을 발급합니다.
            발급받은 URL로 클라이언트가 직접 업로드한 뒤, /complete API로 업로드 완료를 알려야 프로필에 반영됩니다.
            """)
    @PostMapping("/image/presigned-url")
    public ApiResponse<ProfileImagePresignedUrlResponse> issueImagePresignedUrl(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody ProfileImagePresignedUrlRequest request
    ) {
        return ApiResponse.ok(
                "presigned URL을 발급했습니다.",
                profileService.issueProfileImagePresignedUrl(memberId, request)
        );
    }

    @Operation(summary = "프로필 이미지 업로드 완료 처리", description = "S3 업로드가 끝난 이미지를 검증한 뒤 프로필 이미지로 반영합니다.")
    @PostMapping("/image/complete")
    public ApiResponse<ProfileResponse> completeImageUpload(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody ProfileImageUploadCompleteRequest request
    ) {
        return ApiResponse.ok(
                "프로필 이미지가 변경되었습니다.",
                profileService.completeProfileImageUpload(memberId, request)
        );
    }

    @Operation(summary = "프로필 이미지 초기화", description = "프로필 이미지를 기본 이미지 상태(null)로 되돌립니다.")
    @DeleteMapping("/image")
    public ApiResponse<Void> resetImage(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
    ) {
        profileService.resetProfileImage(memberId);
        return ApiResponse.ok("프로필 이미지가 초기화되었습니다.", null);
    }
}
