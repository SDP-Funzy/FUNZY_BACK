package com.sdp1617.backend.social.service;

/** 친구 요청을 보냈다 (FR-011). 받는 사람에게 알림을 만든다. */
public record FollowRequestedEvent(
        Long requesterId,
        Long receiverId
) {
}
