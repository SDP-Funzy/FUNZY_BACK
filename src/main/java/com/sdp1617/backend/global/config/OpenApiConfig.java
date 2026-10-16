package com.sdp1617.backend.global.config;

import com.sdp1617.backend.global.common.response.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

import java.util.Collections;
import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";
    static final String ERROR_RESPONSE_SCHEMA_NAME = "ErrorResponse";

    /** Swagger 화면의 태그 순서와 설명 (가입 → 친구 → 편지 → 아카이브 → 마이페이지). */
    static final List<Tag> TAGS = List.of(
            tag(ApiTags.AUTH, "이메일 인증번호로 회원가입, 아이디 로그인, 아이디·비밀번호 찾기, 계정 잠금 해제"),
            tag(ApiTags.AUTH_SOCIAL, "카카오·네이버·구글 소셜 로그인과 소셜 회원가입"),
            tag(ApiTags.AUTH_TOKEN, "access token 재발급 (refresh token 교체)"),
            tag(ApiTags.FRIEND_SEARCH, "아이디로 회원 찾기, 회원 프로필과 나와의 친구 관계 조회"),
            tag(ApiTags.FRIEND, "팔로우 요청·취소·수락·거절·끊기(수락되면 서로 친구), 친구 목록·받은/보낸 팔로우 요청·친구 수"),
            tag(ApiTags.LETTER_WRITE, "펀지(편지) 작성: 봉투 → 마음카드 1~5장 → 두들픽(선택) → 완료. 보내기 전까지는 보낸 사람만 고칠 수 있음"),
            tag(ApiTags.LETTER_INBOX, "완료한 편지 보내기(회원에게·공유 링크로), 받은 편지함·보낸 편지함, 편지 열기·삭제, 두들픽 선물 고르기"),
            tag(ApiTags.LETTER_KOK, "받은 마음카드를 콕해서 아카이브(공개) 또는 잠긴편지(비공개)에 담기. 받은 사람만 사용"),
            tag(ApiTags.LETTER_CARD_BOX, "주고받은 마음카드를 보낸함·받은함으로 모아 보기(목록·상대방별 폴더·월별 캘린더), 카드 사진 업로드 URL 발급"),
            tag(ApiTags.ARCHIVE, "공개로 콕한 마음카드를 카테고리별 보드로 보기. 내 아카이브와 친구 아카이브"),
            tag(ApiTags.ARCHIVE_COMMENT, "아카이브 카드에 다는 인스타식 댓글·답글. 카드 주인과 주인의 친구가 쓰고 볼 수 있음"),
            tag(ApiTags.MYPAGE_PROFILE, "프로필 조회, 아이디 변경, 프로필 이미지 업로드·초기화"),
            tag(ApiTags.MYPAGE_ACCOUNT, "연결 계정 조회·추가·해제, 비밀번호 변경, 로그아웃, 회원 탈퇴"),
            tag(ApiTags.MYPAGE_NOTIFICATION, "푸시 알림 수신 설정, 알림함 조회·읽음 처리, 웹 푸시 구독"),
            tag(ApiTags.MYPAGE_SUPPORT, "이용약관·개인정보처리방침 링크"));

    /** 문서 맨 위에 보이는 설명. 기획서 용어와 API·ID의 대응을 함께 적어 팀원이 헷갈리지 않게 한다 (#82). */
    private static final String DESCRIPTION = """
            SDP1617 백엔드 API 문서입니다. 인증이 필요한 API는 Authorize 버튼에 JWT access token을 입력해서 테스트합니다.

            ### 응답 규칙
            모든 응답은 `{ "success", "code", "message", "data" }` 형식입니다.
            | 경우 | HTTP 상태 | 본문 `success` | 본문 `code` |
            |---|---|---|---|
            | 성공 (조회·생성·수정·삭제 모두) | **200** | `true` | `"200"` |
            | 실패 | 4xx·5xx (에러 코드마다 정해짐) | `false` | 에러 코드 (예: `"LETTER_001"`) |

            - 새로 만드는 API(회원가입, 팔로우 요청, 편지 만들기 등)도 201이 아니라 200입니다.
            - 오류 종류는 `code`로 분기하고 `message`는 화면 표시용입니다. 전체 목록과 HTTP 상태: [에러 코드 문서](/docs/error-codes)
            - API마다 돌려줄 수 있는 에러는 그 API의 **Responses** 칸에 HTTP 상태별로 있습니다 (코드·메시지 목록과 예시 본문).
              아래 인증 오류는 로그인이 필요한 모든 API 공통이라 각 API에는 적지 않습니다.
            - 경로 값 형식이 잘못되면(예: `/api/letters/abc`) 모든 API가 400 `COMMON_002`입니다. 이것도 각 API에는 적지 않습니다.
            - `COMMON_002`의 `message`는 잘못된 값에 따라 구체적인 안내로 바뀝니다 (예: "아이디를 입력해주세요."). 분기는 `code`로 하세요.

            ### 용어: 아이디와 이름
            - **아이디** = 응답·요청의 `nickname` 필드. 인스타 아이디처럼 다른 회원에게 보이는 공개 아이디이자 로그인 아이디입니다
              (2~20자 영문 소문자·숫자·마침표·밑줄). 문서·메시지에서는 "아이디"라고 부릅니다.
            - **이름** = 편지 봉투의 `toName`·`fromName`. 편지마다 자유롭게 적는 이름으로, 아이디와 별개입니다.

            ### 인증 오류 (로그인이 필요한 모든 API 공통, HTTP 401 — Authorization 헤더의 access token 기준)
            | code | 뜻 | 앱 처리 |
            |---|---|---|
            | `AUTH_004` | access token 만료 | 토큰 재발급(`POST /api/auth/token/reissue`) 후 다시 요청 |
            | `AUTH_027` | 비밀번호 변경·재설정, 잠금 해제로 로그인이 해제됨 (이 기기 포함 모든 기기) | 재발급도 실패하므로 바로 로그인 화면 |
            | `AUTH_003` | 유효하지 않은 토큰 (형식 오류, 탈퇴한 회원 등) | 로그인 화면 |
            | `COMMON_003` | 토큰 없이 로그인이 필요한 API 호출 | 로그인 유도 |

            ### 편지 용어 ↔ API 대응 (기획서 용어로 API 찾기)
            | 기획서 용어 | 뜻 | API / ID |
            |---|---|---|
            | **펀지** | 편지. 카드 1~5장 + 두들픽(선택)을 봉투에 담은 것 | `/api/letters`, `letterId` |
            | **펀지팩** | 받는 사람에게 도착한 펀지. 보낸 사람의 편지와 **같은 데이터**(받는 쪽 복사본 없음) | `letterId` 그대로 사용. 열기 `GET /api/letters/{letterId}`, 삭제 `DELETE /api/letters/{letterId}` |
            | **마음카드** | 펀지 속 카드 1장 | `cardId` (= 콕 API의 `heartCardId`, 아카이브의 `letterCardId`) |
            | **두들픽** | 펀지에 넣는 선물 후보 2~3개 + 선정 이유 | `giftItemId` (= 선물 고르기에 사용) |
            | **콕** | 받은 마음카드를 내 아카이브에 담기 | `PUT /api/heart-cards/{cardId}/kok` |

            ### 편지 흐름
            1. **쓰기** (태그 "편지 - ① 쓰기"): 봉투 → 카드 1~5장 → 두들픽(선택) → 완료
            2. **보내기·편지함** (태그 "편지 - ② 보내기·편지함"): 회원에게 보내기 또는 공유 링크로 보내기(로그인 없이 열람 → 로그인해서 받기), 받은 편지함(받은 펀지팩 목록), 보낸 편지함, 열기, 삭제, 선물 고르기
            3. **콕** (태그 "편지 - ③ 마음카드 콕"): 받은 마음카드를 아카이브에 담기
            4. **마음카드 보관함** (태그 "편지 - ④ 마음카드 보관함"): 주고받은 펀지 속 카드를 보낸함·받은함으로 모아 보기
            """;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SDP1617 API")
                        .description(DESCRIPTION)
                        .version("v1"))
                .tags(TAGS)
                .components(new Components()
                        .addSchemas(ERROR_RESPONSE_SCHEMA_NAME, ModelConverters.getInstance()
                                .readAllAsResolvedSchema(ErrorResponse.class).schema)
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    @Bean
    public OperationCustomizer publicEndpointSecurityCustomizer() {
        return (operation, handlerMethod) -> {
            if (isPublicEndpoint(handlerMethod)) {
                operation.setSecurity(Collections.emptyList());
            }
            return operation;
        };
    }

    /** 성공 응답(200) 설명을 기본값 "OK" 대신 "성공"으로 통일한다. */
    @Bean
    public OperationCustomizer successResponseDescriptionCustomizer() {
        return (operation, handlerMethod) -> {
            if (operation.getResponses() != null && operation.getResponses().get("200") != null) {
                operation.getResponses().get("200").setDescription("성공");
            }
            return operation;
        };
    }

    private static Tag tag(String name, String description) {
        return new Tag().name(name).description(description);
    }

    private boolean isPublicEndpoint(HandlerMethod handlerMethod) {
        Class<?> beanType = handlerMethod.getBeanType();
        return beanType.getPackageName().startsWith("com.sdp1617.backend.auth.controller")
                || beanType.getName().equals("com.sdp1617.backend.global.common.HealthCheckController");
    }
}
