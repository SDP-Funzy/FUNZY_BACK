package com.sdp1617.backend.social.controller;

import com.sdp1617.backend.global.common.response.ApiResponse;
import com.sdp1617.backend.social.dto.FollowCountResponse;
import com.sdp1617.backend.social.dto.FollowRequestCreateRequest;
import com.sdp1617.backend.social.dto.FollowRequestResponse;
import com.sdp1617.backend.social.dto.FriendResponse;
import com.sdp1617.backend.social.dto.SentFollowRequestResponse;
import com.sdp1617.backend.social.service.FollowService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/social/follow")
@Tag(name = "소셜 - 팔로우", description = "친구(맞팔) 요청/취소/수락/거절/끊기, 친구 목록·보낸 요청·친구 수 조회 API. 친구 찾기는 닉네임(아이디) 검색(GET /api/social/members/search)")
public class FollowController {

    private final FollowService followService;

    @Operation(summary = "친구 요청 보내기", description = """
            닉네임(아이디) 검색(GET /api/social/members/search) 결과의 memberId로 친구 요청을 보냅니다 (FR-010).
            - 상대가 수락하면 서로 친구가 됩니다.
            - 상대가 이미 나에게 요청을 보낸 상태(교차 요청)라면 새 요청을 만들지 않고 즉시 맞팔로 처리됩니다.
            - 친구 수 상한(기본 50명)에 도달하면 요청을 보낼 수 없습니다.
            """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 전송 성공 (교차 요청인 경우 즉시 맞팔 처리)",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "code": "200",
                              "message": "팔로우 요청을 보냈습니다.",
                              "data": null
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않거나 탈퇴한 회원",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "code": "SOCIAL_001",
                              "message": "존재하지 않는 회원입니다.",
                              "data": null
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "본인에게 요청 / 친구 수 상한 초과 / memberId 누락",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "본인에게 요청", value = """
                                    {
                                      "success": false,
                                      "code": "SOCIAL_002",
                                      "message": "본인에게는 팔로우 요청을 보낼 수 없습니다.",
                                      "data": null
                                    }
                                    """),
                            @ExampleObject(name = "친구 수 상한 초과", value = """
                                    {
                                      "success": false,
                                      "code": "SOCIAL_005",
                                      "message": "친구 수 상한을 초과했습니다.",
                                      "data": null
                                    }
                                    """)
                    })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 친구이거나 이미 보낸 요청이 있음",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "이미 친구인 회원", value = """
                                    {
                                      "success": false,
                                      "code": "SOCIAL_003",
                                      "message": "이미 친구인 회원입니다.",
                                      "data": null
                                    }
                                    """),
                            @ExampleObject(name = "이미 보낸 요청이 있음", value = """
                                    {
                                      "success": false,
                                      "code": "SOCIAL_004",
                                      "message": "이미 보낸 팔로우 요청이 있습니다.",
                                      "data": null
                                    }
                                    """)
                    }))
    })
    @PostMapping("/requests")
    public ApiResponse<Void> sendFollowRequest(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody FollowRequestCreateRequest request
    ) {
        followService.sendFollowRequest(memberId, request.memberId());
        return ApiResponse.ok("팔로우 요청을 보냈습니다.", null);
    }

    @Operation(summary = "받은 팔로우 요청 목록 조회", description = """
            내가 받은, 아직 수락/거절하지 않은 팔로우 요청 목록을 최신순으로 조회합니다.
            """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = FollowRequestResponse.class)),
                            examples = {
                            @ExampleObject(name = "요청 있음", value = """
                                    {
                                      "success": true,
                                      "code": "200",
                                      "message": "받은 팔로우 요청 목록을 조회했습니다.",
                                      "data": [
                                        {
                                          "requestId": 1,
                                          "requesterId": 2,
                                          "requesterNickname": "user_b",
                                          "createdAt": "2026-08-14T05:18:16.856342"
                                        }
                                      ]
                                    }
                                    """),
                            @ExampleObject(name = "요청 없음", value = """
                                    {
                                      "success": true,
                                      "code": "200",
                                      "message": "받은 팔로우 요청 목록을 조회했습니다.",
                                      "data": []
                                    }
                                    """)
                    }))
    })
    @GetMapping("/requests/received")
    public ApiResponse<List<FollowRequestResponse>> getReceivedRequests(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.ok("받은 팔로우 요청 목록을 조회했습니다.", followService.getReceivedRequests(memberId));
    }

    @Operation(summary = "보낸 팔로우 요청 목록 조회", description = """
            내가 보낸, 상대가 아직 수락/거절하지 않은 팔로우 요청 목록을 최신순으로 조회합니다.
            - 친구 찾기 화면의 "요청 중" 상태 표시에 사용합니다.
            - 상대가 거절한 요청은 목록에서 사라집니다(거절 사실은 알리지 않음).
            """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = SentFollowRequestResponse.class)),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "code": "200",
                                      "message": "보낸 팔로우 요청 목록을 조회했습니다.",
                                      "data": [
                                        {
                                          "requestId": 1,
                                          "receiverId": 2,
                                          "receiverNickname": "user_b",
                                          "receiverProfileImageUrl": null,
                                          "createdAt": "2026-08-14T05:18:16.856342"
                                        }
                                      ]
                                    }
                                    """)))
    })
    @GetMapping("/requests/sent")
    public ApiResponse<List<SentFollowRequestResponse>> getSentRequests(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.ok("보낸 팔로우 요청 목록을 조회했습니다.", followService.getSentRequests(memberId));
    }

    @Operation(summary = "보낸 팔로우 요청 취소", description = """
            내가 보낸 팔로우 요청을 취소합니다. 상대방에게 알림은 발송되지 않습니다.
            - 이미 상대가 수락/거절했거나 내가 보낸 요청이 아니면 SOCIAL_006입니다.
            """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "취소 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "code": "200",
                              "message": "팔로우 요청을 취소했습니다.",
                              "data": null
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않거나 내가 보낸 요청이 아님",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "code": "SOCIAL_006",
                              "message": "존재하지 않는 팔로우 요청입니다.",
                              "data": null
                            }
                            """)))
    })
    @DeleteMapping("/requests/{requestId}")
    public ApiResponse<Void> cancelFollowRequest(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @PathVariable Long requestId
    ) {
        followService.cancelFollowRequest(memberId, requestId);
        return ApiResponse.ok("팔로우 요청을 취소했습니다.", null);
    }

    @Operation(summary = "팔로우 요청 수락", description = """
            받은 팔로우 요청을 수락하여 맞팔 관계를 형성합니다.
            - 수락 시점에 나 또는 상대방이 친구 수 상한에 도달해 있으면 수락할 수 없습니다.
            """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수락 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "code": "200",
                              "message": "팔로우 요청을 수락했습니다.",
                              "data": null
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않거나 내가 받은 요청이 아님",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "code": "SOCIAL_006",
                              "message": "존재하지 않는 팔로우 요청입니다.",
                              "data": null
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "나 또는 상대방의 친구 수 상한 초과",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "code": "SOCIAL_005",
                              "message": "친구 수 상한을 초과했습니다.",
                              "data": null
                            }
                            """)))
    })
    @PostMapping("/requests/{requestId}/accept")
    public ApiResponse<Void> acceptFollowRequest(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @PathVariable Long requestId
    ) {
        followService.acceptFollowRequest(memberId, requestId);
        return ApiResponse.ok("팔로우 요청을 수락했습니다.", null);
    }

    @Operation(summary = "팔로우 요청 거절", description = """
            받은 팔로우 요청을 거절합니다.
            - 거절 시 요청자에게 알림이 발송되지 않습니다(조용한 거절).
            """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "거절 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "code": "200",
                              "message": "팔로우 요청을 거절했습니다.",
                              "data": null
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않거나 내가 받은 요청이 아님",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "code": "SOCIAL_006",
                              "message": "존재하지 않는 팔로우 요청입니다.",
                              "data": null
                            }
                            """)))
    })
    @PostMapping("/requests/{requestId}/reject")
    public ApiResponse<Void> rejectFollowRequest(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @PathVariable Long requestId
    ) {
        followService.rejectFollowRequest(memberId, requestId);
        return ApiResponse.ok("팔로우 요청을 거절했습니다.", null);
    }

    @Operation(summary = "팔로우 끊기", description = """
            기존 팔로우(맞팔) 관계를 해제합니다.
            - 양방향 관계가 모두 해제되며, 상대방에게 알림이 발송되지 않습니다.
            """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "끊기 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "code": "200",
                              "message": "팔로우를 끊었습니다.",
                              "data": null
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "친구 관계가 아님",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "code": "SOCIAL_007",
                              "message": "친구 관계가 아닙니다.",
                              "data": null
                            }
                            """)))
    })
    @DeleteMapping("/{followMemberId}")
    public ApiResponse<Void> unfollow(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @PathVariable Long followMemberId
    ) {
        followService.unfollow(memberId, followMemberId);
        return ApiResponse.ok("팔로우를 끊었습니다.", null);
    }

    @Operation(summary = "친구 목록 조회", description = """
            친구(맞팔) 목록을 최근에 친구가 된 순으로 조회합니다.
            - 친구 수 상한이 있어 페이지네이션 없이 전체를 반환합니다.
            - memberId는 팔로우 끊기(DELETE /api/social/follow/{followMemberId})에 사용합니다.
            """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = FriendResponse.class)),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "code": "200",
                                      "message": "친구 목록을 조회했습니다.",
                                      "data": [
                                        {
                                          "memberId": 2,
                                          "nickname": "user_b",
                                          "profileImageUrl": null,
                                          "friendSince": "2026-08-14T05:18:16.856342"
                                        }
                                      ]
                                    }
                                    """)))
    })
    @GetMapping("/friends")
    public ApiResponse<List<FriendResponse>> getFriends(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.ok("친구 목록을 조회했습니다.", followService.getFriends(memberId));
    }

    @Operation(summary = "친구 수 조회", description = """
            현재 친구(맞팔) 수와 상한을 함께 반환합니다.
            - 상한값은 기획 확정 전까지 임시값(기본 50명)입니다.
            """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FollowCountResponse.class),
                            examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "code": "200",
                              "message": "친구 수를 조회했습니다.",
                              "data": { "currentCount": 1, "maxCount": 50 }
                            }
                            """)))
    })
    @GetMapping("/count")
    public ApiResponse<FollowCountResponse> getFollowCount(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.ok("친구 수를 조회했습니다.", followService.getFollowCount(memberId));
    }
}
