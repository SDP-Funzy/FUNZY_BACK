package com.sdp1617.backend.notification.controller;

import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.global.error.ApiErrorCodes;
import com.sdp1617.backend.global.common.response.ApiResponse;
import com.sdp1617.backend.notification.dto.PushPublicKeyResponse;
import com.sdp1617.backend.notification.dto.PushSubscriptionRequest;
import com.sdp1617.backend.notification.dto.PushUnsubscribeRequest;
import com.sdp1617.backend.notification.service.PushSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import com.sdp1617.backend.global.config.ApiTags;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mypage/notifications/push")
@Tag(name = ApiTags.MYPAGE_NOTIFICATION)
public class PushSubscriptionController {

    private final PushSubscriptionService pushSubscriptionService;

    @Operation(summary = "웹 푸시 공개키 조회", description = """
            브라우저에서 웹 푸시를 구독할 때 쓰는 VAPID 공개키를 조회합니다.
            - `registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: publicKey })`
              (publicKey는 base64url 문자열이므로 Uint8Array로 바꿔서 넘깁니다)
            - 서버에서 푸시가 꺼져 있으면 publicKey가 null입니다. 이때는 구독하지 않습니다.
            """)
    @GetMapping("/public-key")
    public ApiResponse<PushPublicKeyResponse> getPublicKey() {
        return ApiResponse.ok("웹 푸시 공개키를 조회했습니다.", pushSubscriptionService.getPublicKey());
    }

    @Operation(summary = "웹 푸시 구독 등록", description = """
            이 브라우저의 푸시 구독을 등록합니다. 브라우저의 `PushSubscription.toJSON()`을 그대로 보냅니다.
            - 같은 구독을 다시 보내도 됩니다. 앱을 열 때·로그인할 때마다 알림 권한이 허용되어 있으면 다시 등록해 주세요
              (비밀번호 변경 등으로 모든 기기에서 로그아웃되면 구독도 모두 지워집니다).
            - 같은 브라우저에서 다른 계정으로 로그인해 등록하면, 그 브라우저의 알림은 새 계정으로만 갑니다.
            - 회원당 최근에 등록한 브라우저 10개까지 받습니다.
            - 푸시는 알림함에 알림이 생길 때, 푸시 알림 수신 설정이 켜져 있으면 보냅니다. Service Worker가 받는 내용:
              `{ "title": "Funzy", "body": "티키님에게서 편지가 도착했어요.", "notificationId": 1, "type": "LETTER",
              "actorMemberId": 7, "letterId": 12, "archiveCardId": null }` — 누르면 이동할 곳은 알림 목록과 같습니다.
              - `title`은 항상 "Funzy", `body`는 알림 목록의 `content`와 같은 문구입니다.
              - `notificationId`·`type`·`actorMemberId`·`letterId`·`archiveCardId`는 알림 목록 응답의 `id`·`type`·`actorMemberId`·`letterId`·`archiveCardId`와 같은 값입니다.
            - 크롬·엣지·파이어폭스·사파리의 푸시 서비스 주소만 받습니다. iOS는 홈 화면에 추가한 웹앱(iOS 16.4+)에서만 구독할 수 있습니다.
            """)
    @PutMapping("/subscriptions")
    @ApiErrorCodes({ErrorCode.COMMON_002, ErrorCode.NOTIFICATION_002})
    public ApiResponse<Void> subscribe(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody PushSubscriptionRequest request
    ) {
        pushSubscriptionService.subscribe(memberId, request);
        return ApiResponse.ok("웹 푸시 구독을 등록했습니다.", null);
    }

    @Operation(summary = "웹 푸시 구독 해제", description = """
            이 브라우저의 푸시 구독을 해제합니다. 로그아웃하기 전에 호출해 주세요.
            - 내 구독이 아니거나 이미 없어도 성공으로 응답합니다.
            - 브라우저 쪽 구독도 `subscription.unsubscribe()`로 해제해 주세요.
            """)
    @DeleteMapping("/subscriptions")
    @ApiErrorCodes({ErrorCode.COMMON_002})
    public ApiResponse<Void> unsubscribe(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody PushUnsubscribeRequest request
    ) {
        pushSubscriptionService.unsubscribe(memberId, request.endpoint());
        return ApiResponse.ok("웹 푸시 구독을 해제했습니다.", null);
    }
}
