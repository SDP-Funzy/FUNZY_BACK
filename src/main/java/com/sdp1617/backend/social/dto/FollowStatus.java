package com.sdp1617.backend.social.dto;

/** 나와 상대 회원의 친구 관계 상태 (#153). 친구 아카이브 블러 화면·검색 결과 버튼에 쓴다. */
public enum FollowStatus {
    /** 아무 관계 없음 — 친구 요청 가능 */
    NONE,
    /** 내가 요청함 — followRequestId로 취소 */
    REQUESTED,
    /** 상대가 나에게 요청함 — followRequestId로 수락·거절, 또는 내가 요청하면 바로 친구 */
    RECEIVED,
    /** 친구(맞팔) — 친구 아카이브를 볼 수 있음 */
    FRIEND,
    /** 나 자신 */
    SELF
}
