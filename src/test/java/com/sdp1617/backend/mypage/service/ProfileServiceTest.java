package com.sdp1617.backend.mypage.service;

import com.sdp1617.backend.auth.entity.AuthProvider;
import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.global.config.properties.S3Properties;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.global.s3.S3ImageService;
import com.sdp1617.backend.mypage.dto.NicknameUpdateRequest;
import com.sdp1617.backend.mypage.dto.ProfileImagePresignedUrlRequest;
import com.sdp1617.backend.mypage.dto.ProfileImageUploadCompleteRequest;
import com.sdp1617.backend.mypage.dto.ProfileResponse;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private TransactionTemplate transactionTemplate;

    private final S3Properties s3Properties = new S3Properties(
            new S3Properties.Credentials(null, null),
            new S3Properties.Region("ap-northeast-2"),
            new S3Properties.S3("sdp-funzy", null, 10),
            new S3Properties.Stack(false)
    );

    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        S3ImageService s3ImageService = new S3ImageService(s3Client, s3Presigner, s3Properties);
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });
        profileService = new ProfileService(memberRepository, s3ImageService, transactionTemplate);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private Member localMember() {
        return new Member("test@sdp1617.com", "encoded", "닉네임", Consent.requiredOnly());
    }

    private void setId(Member member, Long id) {
        try {
            Field field = Member.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(member, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void 프로필을_조회한다() {
        Member member = localMember();
        setId(member, 1L);
        when(memberRepository.findActiveById(1L)).thenReturn(Optional.of(member));

        ProfileResponse response = profileService.getProfile(1L);

        assertEquals("닉네임", response.nickname());
        assertNull(response.profileImageUrl());
        assertEquals(AuthProvider.LOCAL, response.provider());
    }

    @Test
    void 존재하지_않는_회원_조회시_AUTH_002_예외를_던진다() {
        when(memberRepository.findActiveById(1L)).thenReturn(Optional.empty());

        CustomException exception = assertThrows(CustomException.class,
                () -> profileService.getProfile(1L));

        assertEquals(ErrorCode.AUTH_002, exception.getErrorCode());
    }

    @Test
    void 닉네임을_변경한다() {
        Member member = localMember();
        setId(member, 1L);
        when(memberRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(member));
        when(memberRepository.isNicknameTaken("새닉네임")).thenReturn(false);

        profileService.updateNickname(1L, new NicknameUpdateRequest("새닉네임"));

        assertEquals("새닉네임", member.getNickname());
        verify(memberRepository).saveWithNicknameUniqueness(member);
    }

    @Test
    void 기존_닉네임과_동일하면_중복_검사_없이_통과한다() {
        Member member = localMember();
        setId(member, 1L);
        when(memberRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(member));

        profileService.updateNickname(1L, new NicknameUpdateRequest("닉네임"));

        assertEquals("닉네임", member.getNickname());
    }

    @Test
    void 다른_회원이_사용중인_닉네임이면_AUTH_007_예외를_던진다() {
        Member member = localMember();
        setId(member, 1L);
        when(memberRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(member));
        when(memberRepository.isNicknameTaken("중복닉네임")).thenReturn(true);

        CustomException exception = assertThrows(CustomException.class,
                () -> profileService.updateNickname(1L, new NicknameUpdateRequest("중복닉네임")));

        assertEquals(ErrorCode.AUTH_007, exception.getErrorCode());
    }

    @Test
    void 동일한_이미지_키로_재요청해도_방금_반영한_이미지를_삭제하지_않는다() {
        Member member = localMember();
        setId(member, 1L);
        member.updateProfileImage("profiles/1/a.png", "https://example.com/profiles/1/a.png");
        when(memberRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(member));
        stubValidPngUpload();

        profileService.completeProfileImageUpload(1L, new ProfileImageUploadCompleteRequest("profiles/1/a.png"));

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void 다른_이미지로_교체하면_이전_이미지를_S3에서_삭제한다() {
        Member member = localMember();
        setId(member, 1L);
        member.updateProfileImage("profiles/1/old.png", "https://example.com/profiles/1/old.png");
        when(memberRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(member));
        stubValidPngUpload();

        profileService.completeProfileImageUpload(1L, new ProfileImageUploadCompleteRequest("profiles/1/new.png"));

        verify(s3Client).deleteObject(argThat((DeleteObjectRequest req) -> req.key().equals("profiles/1/old.png")));
    }

    @Test
    void 트랜잭션_커밋_후_콜백은_S3_삭제를_기다리지_않고_즉시_리턴한다() throws InterruptedException {
        Member member = localMember();
        setId(member, 1L);
        member.updateProfileImage("profiles/1/old.png", "https://example.com/profiles/1/old.png");
        when(memberRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(member));
        stubValidPngUpload();

        CountDownLatch deleteStarted = new CountDownLatch(1);
        CountDownLatch releaseDelete = new CountDownLatch(1);
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenAnswer(invocation -> {
            deleteStarted.countDown();
            assertTrue(releaseDelete.await(1, TimeUnit.SECONDS));
            return DeleteObjectResponse.builder().build();
        });

        TransactionSynchronizationManager.initSynchronization();
        try {
            profileService.completeProfileImageUpload(1L, new ProfileImageUploadCompleteRequest("profiles/1/new.png"));
            List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, synchronizations.size());

            long start = System.nanoTime();
            synchronizations.get(0).afterCommit();
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

            assertTrue(elapsedMillis < 500, "afterCommit()은 S3 삭제를 기다리지 않고 즉시 리턴해야 한다 (실제: " + elapsedMillis + "ms)");
            assertTrue(deleteStarted.await(1, TimeUnit.SECONDS), "S3 삭제는 별도 스레드에서 비동기로 실행되어야 한다");
        } finally {
            releaseDelete.countDown();
        }
    }

    private void stubValidPngUpload() {
        byte[] pngBytes = validPngBytes();
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentType("image/png").contentLength((long) pngBytes.length).build());
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(new ResponseInputStream<>(
                        GetObjectResponse.builder().build(),
                        AbortableInputStream.create(new ByteArrayInputStream(pngBytes))));
    }

    private byte[] validPngBytes() {
        try {
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Test
    void 프로필_이미지를_초기화한다() {
        Member member = localMember();
        setId(member, 1L);
        member.updateProfileImage("profiles/1/old.png", "https://example.com/profiles/1/old.png");
        when(memberRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(member));

        profileService.resetProfileImage(1L);

        assertNull(member.getProfileImageKey());
        assertNull(member.getProfileImageUrl());
    }

    @Test
    void 매직바이트만_흉내내고_실제로_디코딩되지_않는_이미지는_MYPAGE_002_예외를_던진다() {
        byte[] fakePng = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 0, 0, 0, 0};
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentType("image/png").contentLength((long) fakePng.length).build());
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(new ResponseInputStream<>(
                        GetObjectResponse.builder().build(),
                        AbortableInputStream.create(new ByteArrayInputStream(fakePng))));

        CustomException exception = assertThrows(CustomException.class,
                () -> profileService.completeProfileImageUpload(
                        1L, new ProfileImageUploadCompleteRequest("profiles/1/fake.png")));

        assertEquals(ErrorCode.MYPAGE_002, exception.getErrorCode());
    }

    @Test
    void 확장자와_실제_이미지_포맷이_다르면_MYPAGE_002_예외를_던진다() {
        byte[] jpegBytesLabeledAsPng = validJpegBytes();
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentType("image/png").contentLength((long) jpegBytesLabeledAsPng.length).build());
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(new ResponseInputStream<>(
                        GetObjectResponse.builder().build(),
                        AbortableInputStream.create(new ByteArrayInputStream(jpegBytesLabeledAsPng))));

        CustomException exception = assertThrows(CustomException.class,
                () -> profileService.completeProfileImageUpload(
                        1L, new ProfileImageUploadCompleteRequest("profiles/1/mismatch.png")));

        assertEquals(ErrorCode.MYPAGE_002, exception.getErrorCode());
    }

    private byte[] validJpegBytes() {
        try {
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "jpeg", out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Test
    void 지원하지_않는_이미지_형식이면_presigned_URL_발급시_MYPAGE_002_예외를_던진다() {
        CustomException exception = assertThrows(CustomException.class,
                () -> profileService.issueProfileImagePresignedUrl(
                        1L, new ProfileImagePresignedUrlRequest("application/octet-stream")));

        assertEquals(ErrorCode.MYPAGE_002, exception.getErrorCode());
    }

    @Test
    void 본인_소유가_아닌_이미지_키로_업로드_완료를_요청하면_COMMON_004_예외를_던지고_S3를_조회하지_않는다() {
        CustomException exception = assertThrows(CustomException.class,
                () -> profileService.completeProfileImageUpload(
                        1L, new ProfileImageUploadCompleteRequest("profiles/999/other.png")));

        assertEquals(ErrorCode.COMMON_004, exception.getErrorCode());
        verifyNoInteractions(s3Client);
    }
}
