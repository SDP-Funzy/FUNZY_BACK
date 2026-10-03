package com.sdp1617.backend.auth.entity;

import com.sdp1617.backend.auth.util.Emails;
import com.sdp1617.backend.auth.util.FollowCodeGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
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

    @Column(name = "follow_code", nullable = false, unique = true, length = 12)
    private String followCode;
    @Column(name = "push_notification_enabled", nullable = false)
    private boolean pushNotificationEnabled;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

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

    public boolean hasPassword() {
        return password != null;
    }

    public void changePassword(String newPassword) {
        this.password = newPassword;
    }

    public void reissueFollowCode() {
        this.followCode = FollowCodeGenerator.generate();
    }

    @PrePersist
    private void assignFollowCodeIfMissing() {
        if (this.followCode == null) {
            this.followCode = FollowCodeGenerator.generate();
        }
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
