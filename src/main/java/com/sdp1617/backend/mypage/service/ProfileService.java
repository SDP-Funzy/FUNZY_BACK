package com.sdp1617.backend.mypage.service;

import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.global.s3.S3ImageService;
import com.sdp1617.backend.mypage.dto.NicknameUpdateRequest;
import com.sdp1617.backend.mypage.dto.ProfileImagePresignedUrlRequest;
import com.sdp1617.backend.mypage.dto.ProfileImagePresignedUrlResponse;
import com.sdp1617.backend.mypage.dto.ProfileImageUploadCompleteRequest;
import com.sdp1617.backend.mypage.dto.ProfileResponse;
import com.sdp1617.backend.global.common.AfterCommit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

    private final MemberRepository memberRepository;
    private final S3ImageService s3ImageService;
    private final TransactionTemplate transactionTemplate;

    public ProfileResponse getProfile(Long memberId) {
        Member member = findMember(memberId);
        return new ProfileResponse(member.getNickname(), member.getProfileImageUrl(), member.getProvider());
    }

    @Transactional
    public void updateNickname(Long memberId, NicknameUpdateRequest request) {
        Member member = findMemberForUpdate(memberId);
        if (request.nickname().equals(member.getNickname())) {
            return;
        }
        if (memberRepository.isNicknameTaken(request.nickname())) {
            throw new CustomException(ErrorCode.AUTH_007);
        }
        member.updateNickname(request.nickname());
        memberRepository.saveWithNicknameUniqueness(member);
    }

    public ProfileImagePresignedUrlResponse issueProfileImagePresignedUrl(
            Long memberId,
            ProfileImagePresignedUrlRequest request
    ) {
        S3ImageService.PresignedUpload upload = s3ImageService.issuePresignedUpload(
                imageKeyPrefix(memberId), request.contentType(), ErrorCode.MYPAGE_002);
        return ProfileImagePresignedUrlResponse.from(upload);
    }

    /**
     * S3 HEAD/GET 검증을 트랜잭션 밖에서 먼저 끝낸 뒤, DB 쓰기만 별도의 짧은 트랜잭션으로 묶는다.
     * (같은 클래스 내 @Transactional 메서드를 this로 호출하면 프록시를 안 타므로 TransactionTemplate을 사용)
     * 주의: NOT_SUPPORTED가 외부 트랜잭션을 중단시키고 TransactionTemplate이 별도로 즉시 커밋하므로,
     * 이미 트랜잭션이 진행 중인 다른 @Transactional 메서드 안에서 이 메서드를 호출하면 안 된다(원자성이 깨짐).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ProfileResponse completeProfileImageUpload(Long memberId, ProfileImageUploadCompleteRequest request) {
        s3ImageService.validateOwnership(request.imageKey(), imageKeyPrefix(memberId));
        s3ImageService.validateUploadedImage(
                request.imageKey(), ErrorCode.MYPAGE_001, ErrorCode.MYPAGE_002, ErrorCode.MYPAGE_003);

        return transactionTemplate.execute(status -> {
            Member member = findMemberForUpdate(memberId);
            String previousImageKey = member.getProfileImageKey();
            member.updateProfileImage(request.imageKey(), s3ImageService.buildImageUrl(request.imageKey()));
            if (previousImageKey != null && !previousImageKey.equals(request.imageKey())) {
                deleteAfterCommit(previousImageKey);
            }
            return new ProfileResponse(member.getNickname(), member.getProfileImageUrl(), member.getProvider());
        });
    }

    @Transactional
    public void resetProfileImage(Long memberId) {
        Member member = findMemberForUpdate(memberId);
        String previousImageKey = member.getProfileImageKey();
        member.resetProfileImage();
        deleteAfterCommit(previousImageKey);
    }

    private Member findMemberForUpdate(Long memberId) {
        return memberRepository.findActiveByIdForUpdate(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_002));
    }

    private Member findMember(Long memberId) {
        return memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_002));
    }

    private String imageKeyPrefix(Long memberId) {
        return "profiles/" + memberId + "/";
    }

    /**
     * DB 트랜잭션 커밋 전에 S3 객체를 지우면, 이후 커밋 실패 시 DB는 롤백되는데
     * 이미 지워진 이미지를 계속 참조하게 된다. 커밋이 실제로 끝난 뒤에만 지우도록 미룬다.
     * (트랜잭션이 없는 컨텍스트, 예: 테스트에서는 즉시 삭제로 폴백)
     */
    private void deleteAfterCommit(String imageKey) {
        if (imageKey == null || imageKey.isBlank()) {
            return;
        }
        // afterCommit()은 커밋 직후, 커넥션이 풀에 반납되기 전에 실행된다.
        // 여기서 블로킹 S3 호출을 동기로 하면 그만큼 커넥션 반납이 늦어지므로 별도 스레드로 던진다.
        AfterCommit.run(
                () -> s3ImageService.deleteImageQuietlyAsync(imageKey),
                () -> s3ImageService.deleteImageQuietly(imageKey));
    }
}
