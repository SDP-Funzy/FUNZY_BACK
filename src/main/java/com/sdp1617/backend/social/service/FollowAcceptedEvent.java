package com.sdp1617.backend.social.service;

/** 친구 요청이 수락되어 친구가 됐다 (FR-012). 요청했던 사람에게 알림을 만든다. */
public record FollowAcceptedEvent(
        Long requesterId,
        Long accepterId
) {
}
