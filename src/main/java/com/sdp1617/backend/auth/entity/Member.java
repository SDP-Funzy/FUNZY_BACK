package com.sdp1617.backend.auth.entity;

import com.sdp1617.backend.auth.util.Emails;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;

@Getter
@Entity
// 바뀐 컬럼만 UPDATE한다. 전체 컬럼을 쓰면, 탈퇴와 동시에 처리되던 다른 수정(푸시 설정 등)이 예전 값으로
// 이메일·닉네임·탈퇴 시각까지 덮어써 탈퇴한 계정이 되살아날 수 있다.
@DynamicUpdate
@Table(
        name = "members",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_member_email", columnNames = "email"),
                @UniqueConstraint(name = "uk_member_nickname", columnNames = "nickname"),
                @UniqueConstraint(
                        name = "uk_member_provider_provider_id",
                        columnNames = {"provider", "provider_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    /**
     * 탈퇴한 회원의 닉네임 접두사. 탈퇴 시 닉네임을 "접두사 + 회원 ID"로 바꿔, 주고받은 카드 등에 "탈퇴한회원12"처럼 표시된다.
     * 일반 회원은 이 접두사로 시작하는 닉네임을 쓸 수 없다 ({@link #isReservedNickname}) — 탈퇴 시 닉네임 충돌 방지.
     */
    public static final String WITHDRAWN_NICKNAME_PREFIX = "탈퇴한회원";
    /** 새로 가입한 회원의 세션 버전. */
    public static final int INITIAL_SESSION_VERSION = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 255)
    private String email;

    @Column(length = 255)
    private String password;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Column(name = "profile_image_key", length = 512)
    private String profileImageKey;

    @Column(name = "profile_image_url", length = 1024)
    private String profileImageUrl;

    @Embedded
    private Consent consent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_id", length = 255)
    private String providerId;

    @Column(name = "push_notification_enabled", nullable = false)
    private boolean pushNotificationEnabled;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 탈퇴 시각. null이면 활성 회원. 탈퇴해도 행은 남기고 개인정보만 지운다 (주고받은 카드·편지가 참조하므로). */
    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    /**
     * 세션 버전. 로그인할 때 비밀번호와 함께 읽어 토큰에 담고, 비밀번호 변경·재설정, 잠금 해제 등 전체 세션을 끊을 때
     * {@link com.sdp1617.backend.auth.repository.MemberRepository#incrementSessionVersion}으로 1 올린다.
     * 토큰의 버전이 이보다 낮으면 거절한다 (#124). 시각이 아니라 같은 행에서 읽은 값으로 비교하므로, 변경이 커밋되기 직전에
     * 옛 비밀번호로 로그인하거나 재발급해도 그 토큰은 옛 버전을 갖게 되어 함께 무효가 된다. null은 0 (칼럼 추가 전 회원).
     */
    @Column(name = "session_version")
    private Integer sessionVersion;

    public Member(String email, String password, String nickname, Consent consent) {
        // 아이디/비밀번호 계정은 이메일이 본인 확인·비밀번호 재설정·아이디 찾기의 유일한 수단이라 비어 있으면 안 된다
        String normalizedEmail = Emails.normalize(email);
        if (normalizedEmail == null) {
            throw new IllegalArgumentException("아이디/비밀번호 회원은 이메일이 필요합니다.");
        }
        this.email = normalizedEmail;
        this.password = password;
        this.nickname = nickname;
        this.consent = consent;
        this.provider = AuthProvider.LOCAL;
        this.pushNotificationEnabled = true;
        this.createdAt = LocalDateTime.now();
    }

    public Member(String email, String nickname, Consent consent, AuthProvider provider, String providerId) {
        if (provider == AuthProvider.LOCAL || providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("소셜 회원은 LOCAL이 아닌 provider와 providerId가 필요합니다.");
        }
        this.email = Emails.normalize(email);
        this.password = null;
        this.nickname = nickname;
        this.consent = consent;
        this.provider = provider;
        this.providerId = providerId;
        this.pushNotificationEnabled = true;
        this.createdAt = LocalDateTime.now();
    }

    public static boolean isReservedNickname(String nickname) {
        return nickname != null && nickname.startsWith(WITHDRAWN_NICKNAME_PREFIX);
    }

    public boolean isWithdrawn() {
        return withdrawnAt != null;
    }

    /**
     * 탈퇴: 로그인·본인 확인에 쓰이는 값과 개인정보를 지우고 닉네임을 익명값으로 바꾼다.
     * 이메일·닉네임·소셜 계정이 비워지므로 같은 이메일/아이디/소셜 계정으로 다시 가입할 수 있다.
     */
    public void withdraw() {
        this.email = null;
        this.password = null;
        this.providerId = null;
        this.nickname = WITHDRAWN_NICKNAME_PREFIX + id;
        this.profileImageKey = null;
        this.profileImageUrl = null;
        this.pushNotificationEnabled = false;
        this.withdrawnAt = LocalDateTime.now();
    }

    public int getSessionVersion() {
        return sessionVersion == null ? INITIAL_SESSION_VERSION : sessionVersion;
    }


    public boolean hasPassword() {
        return password != null;
    }

    public void changePassword(String newPassword) {
        this.password = newPassword;
    }

    public void updatePushNotificationEnabled(boolean pushNotificationEnabled) {
        this.pushNotificationEnabled = pushNotificationEnabled;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void updateProfileImage(String profileImageKey, String profileImageUrl) {
        this.profileImageKey = profileImageKey;
        this.profileImageUrl = profileImageUrl;
    }

    public void resetProfileImage() {
        this.profileImageKey = null;
        this.profileImageUrl = null;
    }
}
