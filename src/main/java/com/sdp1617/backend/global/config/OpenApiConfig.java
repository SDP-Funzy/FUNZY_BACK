package com.sdp1617.backend.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

import java.util.Collections;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    /** 문서 맨 위에 보이는 설명. 기획서 용어와 API·ID의 대응을 함께 적어 팀원이 헷갈리지 않게 한다 (#82). */
    private static final String DESCRIPTION = """
            SDP1617 백엔드 API 문서입니다. 인증이 필요한 API는 Authorize 버튼에 JWT access token을 입력해서 테스트합니다.

            ### 응답 규칙
            모든 응답은 `{ "success", "code", "message", "data" }` 형식입니다.
            | 경우 | HTTP 상태 | 본문 `success` | 본문 `code` |
            |---|---|---|---|
            | 성공 (조회·생성·수정·삭제 모두) | **200** | `true` | `"200"` |
            | 실패 | 4xx·5xx (에러 코드마다 정해짐) | `false` | 에러 코드 (예: `"LETTER_001"`) |

            - 새로 만드는 API(회원가입, 친구 요청, 편지 만들기 등)도 201이 아니라 200입니다.
            - 오류 종류는 `code`로 분기하고 `message`는 화면 표시용입니다. 전체 목록과 HTTP 상태: [에러 코드 문서](/docs/error-codes)

            ### 인증 오류 (로그인이 필요한 모든 API 공통, HTTP 401)
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
            | **마음카드** | 펀지 속 카드 1장 | `cardId` (= 반응 API의 `heartCardId`, 아카이브의 `letterCardId`) |
            | **두들픽** | 펀지에 넣는 선물 후보 2~3개 + 선정 이유 | `giftItemId` (= 선물 이모지·선물 고르기에 사용) |
            | **콕** | 받은 마음카드를 내 아카이브에 담기 | `PUT /api/heart-cards/{cardId}/kok` |

            ### 편지 흐름
            1. **쓰기** (태그 "편지 ① 쓰기"): 봉투 → 카드 1~5장 → 두들픽(선택) → 완료
            2. **보내기·편지함** (태그 "편지 ② 보내기·편지함"): 회원에게 보내기 또는 공유 링크로 보내기(로그인 없이 열람 → 로그인해서 받기), 받은 편지함(받은 펀지팩 목록), 보낸 편지함, 열기, 삭제, 선물 고르기
            3. **받은 사람의 반응** (태그 "편지 ③ …"): 마음카드 이모지·문구 코멘트·콕, 두들픽 선물 이모지, 편지 리액션·댓글·찜
            4. **마음카드 보관함**: 주고받은 펀지 속 카드를 보낸함·받은함으로 모아 보기
            """;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SDP1617 API")
                        .description(DESCRIPTION)
                        .version("v1"))
                .components(new Components()
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

    private boolean isPublicEndpoint(HandlerMethod handlerMethod) {
        Class<?> beanType = handlerMethod.getBeanType();
        return beanType.getPackageName().startsWith("com.sdp1617.backend.auth.controller")
                || beanType.getName().equals("com.sdp1617.backend.global.common.HealthCheckController");
    }
}
