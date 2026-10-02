package com.sdp1617.backend.auth.entity;

public enum AuthProvider {
    LOCAL("아이디"),
    KAKAO("카카오"),
    GOOGLE("구글"),
    NAVER("네이버");

    private final String displayName;

    AuthProvider(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
