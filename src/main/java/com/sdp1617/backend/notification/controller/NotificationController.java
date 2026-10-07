package com.sdp1617.backend.notification.controller;

import com.sdp1617.backend.global.common.response.ApiResponse;
import com.sdp1617.backend.notification.dto.NotificationResponse;
import com.sdp1617.backend.notification.dto.PushSettingResponse;
import com.sdp1617.backend.notification.dto.PushSettingUpdateRequest;
import com.sdp1617.backend.notification.dto.UnreadNotificationCountResponse;
import com.sdp1617.backend.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mypage/notifications")
@Tag(name = "마이페이지 - 알림", description = "푸시 알림 설정, 알림함 조회/읽음 처리 API")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "푸시 알림 수신 설정 조회", description = """
            현재 계정의 푸시 알림 수신 여부를 조회합니다.
            - 가입 시 기본값은 true(수신)입니다.
            """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PushSettingResponse.class),
                            examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "code": "200",
                              "message": "푸시 알림 설정을 조회했습니다.",
                              "data": { "pushNotificationEnabled": true }
                            }
                            """)))
    })
    @GetMapping("/settings")
    public ApiResponse<PushSettingResponse> getPushSetting(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.ok("푸시 알림 설정을 조회했습니다.", notificationService.getPushSetting(memberId));
    }

    @Operation(summary = "푸시 알림 수신 설정 변경", description = """
            푸시 알림 수신 여부를 켜거나 끕니다.
            - OS 알림 권한과는 별개의 앱 내부 설정입니다. OS 권한이 거부되어 있으면 이 설정과 무관하게 알림이 수신되지 않습니다.
            """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "변경 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "code": "200",
                              "message": "푸시 알림 설정이 변경되었습니다.",
                              "data": null
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "pushNotificationEnabled 값 누락",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "code": "COMMON_002",
                              "message": "pushNotificationEnabled는 필수입니다.",
                              "data": null
                            }
                            """)))
    })
    @PatchMapping("/settings")
    public ApiResponse<Void> updatePushSetting(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody PushSettingUpdateRequest request
    ) {
        notificationService.updatePushSetting(memberId, request.pushNotificationEnabled());
        return ApiResponse.ok("푸시 알림 설정이 변경되었습니다.", null);
    }

    @Operation(summary = "알림 목록 조회", description = """
            최신순으로 알림 목록을 조회합니다.
            - 알림이 생기는 경우 (알림 받는 사람):
              LETTER 편지 도착(받는 사람) / REACTION 마음카드·두들픽 선물 이모지를 처음 남김(보낸 사람) /
              COMMENT 마음카드 문구 코멘트(보낸 사람) / GIFT_SELECTED 선물을 새로 고르거나 바꿈(보낸 사람) /
              FOLLOW_REQUEST 친구 요청(요청 받은 사람) / FOLLOW_ACCEPTED 친구 요청 수락(요청한 사람)
            - 이모지 수정·삭제, 코멘트 수정·삭제, 콕, 선물 선택 취소, 친구 요청 거절·끊기는 알림이 없습니다.
            - 탭하면 이동: letterId가 있으면 편지 열기(GET /api/letters/{letterId}), cardId가 있으면 그 마음카드로.
              친구 알림은 actorMemberId(상대 회원)로 받은 요청·친구 화면을 엽니다.
            """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = NotificationResponse.class)),
                            examples = {
                            @ExampleObject(name = "알림 있음", value = """
                                    {
                                      "success": true,
                                      "code": "200",
                                      "message": "알림 목록을 조회했습니다.",
                                      "data": [
                                        {
                                          "id": 2,
                                          "type": "REACTION",
                                          "content": "은우님이 마음카드에 이모지를 남겼어요.",
                                          "read": false,
                                          "actorMemberId": 8,
                                          "letterId": 12,
                                          "cardId": 34,
                                          "createdAt": "2026-08-14T05:10:02.118201"
                                        },
                                        {
                                          "id": 1,
                                          "type": "LETTER",
                                          "content": "티키님에게서 편지가 도착했어요.",
                                          "read": true,
                                          "actorMemberId": 7,
                                          "letterId": 12,
                                          "cardId": null,
                                          "createdAt": "2026-08-14T05:06:13.340691"
                                        }
                                      ]
                                    }
                                    """),
                            @ExampleObject(name = "알림 없음", value = """
                                    {
                                      "success": true,
                                      "code": "200",
                                      "message": "알림 목록을 조회했습니다.",
                                      "data": []
                                    }
                                    """)
                    }))
    })
    @GetMapping
    public ApiResponse<List<NotificationResponse>> getNotifications(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.ok("알림 목록을 조회했습니다.", notificationService.getNotifications(memberId));
    }

    @Operation(summary = "읽지 않은 알림 수", description = """
            읽지 않은 알림 수를 조회합니다 (LB-222, MY-321). 0보다 크면 알림 점·뱃지를 표시합니다.
            - 알림 읽음 처리 후 다시 조회하면 바로 줄어든 개수가 내려옵니다.
            """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UnreadNotificationCountResponse.class),
                            examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "code": "200",
                              "message": "읽지 않은 알림 수를 조회했습니다.",
                              "data": { "unreadCount": 3 }
                            }
                            """)))
    })
    @GetMapping("/unread-count")
    public ApiResponse<UnreadNotificationCountResponse> getUnreadCount(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.ok("읽지 않은 알림 수를 조회했습니다.", notificationService.getUnreadCount(memberId));
    }

    @Operation(summary = "알림 읽음 처리", description = """
            알림 항목 탭 시 읽음 상태로 처리합니다.
            - 본인 알림만 읽음 처리할 수 있습니다. 존재하지 않거나 타인의 알림이면 동일하게 NOTIFICATION_001을 반환합니다.
            """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "읽음 처리 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "code": "200",
                              "message": "알림을 읽음 처리했습니다.",
                              "data": null
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않거나 본인 알림이 아님",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "code": "NOTIFICATION_001",
                              "message": "존재하지 않는 알림입니다.",
                              "data": null
                            }
                            """)))
    })
    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markAsRead(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @PathVariable Long notificationId
    ) {
        notificationService.markAsRead(memberId, notificationId);
        return ApiResponse.ok("알림을 읽음 처리했습니다.", null);
    }
}
