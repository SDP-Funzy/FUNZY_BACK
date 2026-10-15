package com.sdp1617.backend.notification.controller;

import com.sdp1617.backend.global.common.response.ApiResponse;
import com.sdp1617.backend.notification.dto.PushPublicKeyResponse;
import com.sdp1617.backend.notification.dto.PushSubscriptionRequest;
import com.sdp1617.backend.notification.dto.PushUnsubscribeRequest;
import com.sdp1617.backend.notification.service.PushSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@Tag(name = "마이페이지 - 알림", description = "푸시 알림 설정, 알림함 조회/읽음 처리 API")
public class PushSubscriptionController {

    private final PushSubscriptionService pushSubscriptionService;

    @Operation(summary = "웹 푸시 공개키 조회", description = """
            브라우저에서 웹 푸시를 구독할 때 쓰는 VAPID 공개키를 조회합니다 (#89).
            - `registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: publicKey })`
              (publicKey는 base64url 문자열이므로 Uint8Array로 바꿔서 넘깁니다)
            - 서버에서 푸시가 꺼져 있으면 publicKey가 null입니다. 이때는 구독하지 않습니다.
            """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PushPublicKeyResponse.class),
                            examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "code": "200",
                              "message": "웹 푸시 공개키를 조회했습니다.",
                              "data": { "publicKey": "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4" }
                            }
                            """)))
    })
    @GetMapping("/public-key")
    public ApiResponse<PushPublicKeyResponse> getPublicKey() {
        return ApiResponse.ok("웹 푸시 공개키를 조회했습니다.", pushSubscriptionService.getPublicKey());
    }

    @Operation(summary = "웹 푸시 구독 등록", description = """
            이 브라우저의 푸시 구독을 등록합니다 (#89). 브라우저의 `PushSubscription.toJSON()`을 그대로 보냅니다.
            - 같은 구독을 다시 보내도 됩니다. 앱을 열 때·로그인할 때마다 알림 권한이 허용되어 있으면 다시 등록해 주세요
              (비밀번호 변경 등으로 모든 기기에서 로그아웃되면 구독도 모두 지워집니다).
            - 같은 브라우저에서 다른 계정으로 로그인해 등록하면, 그 브라우저의 알림은 새 계정으로만 갑니다.
            - 회원당 최근에 등록한 브라우저 10개까지 받습니다.
            - 푸시는 알림함에 알림이 생길 때, 푸시 알림 수신 설정이 켜져 있으면 보냅니다. Service Worker가 받는 내용:
              `{ "title": "Funzy", "body": "티키님에게서 편지가 도착했어요.", "notificationId": 1, "type": "LETTER",
              "actorMemberId": 7, "letterId": 12, "archiveCardId": null }` — 누르면 이동할 곳은 알림 목록과 같습니다.
            - 크롬·엣지·파이어폭스·사파리의 푸시 서비스 주소만 받습니다. iOS는 홈 화면에 추가한 웹앱(iOS 16.4+)에서만 구독할 수 있습니다.
            """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "등록 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "code": "200",
                              "message": "웹 푸시 구독을 등록했습니다.",
                              "data": null
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "지원하지 않는 푸시 서비스 주소이거나 키 형식이 올바르지 않음 / 필수 값 누락",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "구독 정보 오류", value = """
                                    {
                                      "success": false,
                                      "code": "NOTIFICATION_002",
                                      "message": "지원하지 않는 푸시 구독 정보입니다.",
                                      "data": null
                                    }
                                    """),
                            @ExampleObject(name = "필수 값 누락", value = """
                                    {
                                      "success": false,
                                      "code": "COMMON_002",
                                      "message": "endpoint는 필수입니다.",
                                      "data": null
                                    }
                                    """)
                    }))
    })
    @PutMapping("/subscriptions")
    public ApiResponse<Void> subscribe(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody PushSubscriptionRequest request
    ) {
        pushSubscriptionService.subscribe(memberId, request);
        return ApiResponse.ok("웹 푸시 구독을 등록했습니다.", null);
    }

    @Operation(summary = "웹 푸시 구독 해제", description = """
            이 브라우저의 푸시 구독을 해제합니다 (#89). 로그아웃하기 전에 호출해 주세요.
            - 내 구독이 아니거나 이미 없어도 성공으로 응답합니다.
            - 브라우저 쪽 구독도 `subscription.unsubscribe()`로 해제해 주세요.
            """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "해제 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "code": "200",
                              "message": "웹 푸시 구독을 해제했습니다.",
                              "data": null
                            }
                            """)))
    })
    @DeleteMapping("/subscriptions")
    public ApiResponse<Void> unsubscribe(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody PushUnsubscribeRequest request
    ) {
        pushSubscriptionService.unsubscribe(memberId, request.endpoint());
        return ApiResponse.ok("웹 푸시 구독을 해제했습니다.", null);
    }
}
