package com.sdp1617.backend.global.config;

import com.sdp1617.backend.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** @ApiErrorCodes에 적은 에러 코드가 Swagger 응답 칸에 HTTP 상태별로, ErrorCode의 메시지 그대로 나온다. */
@SpringBootTest
@AutoConfigureMockMvc
class ApiErrorCodesDocsTest {

    private static final String ADD_CARD = "$.paths['/api/letters/{letterId}/cards'].post.responses";

    @Autowired private MockMvc mockMvc;

    @Test
    void 에러_코드가_HTTP_상태별_예시로_나온다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(ADD_CARD + "['200'].description").value("성공"))
                .andExpect(jsonPath(ADD_CARD + "['404'].description").value(containsString("LETTER_001")))
                .andExpect(jsonPath(ADD_CARD + "['409'].content['application/json'].examples.LETTER_003.value.code").value("LETTER_003"))
                .andExpect(jsonPath(ADD_CARD + "['409'].content['application/json'].examples.LETTER_003.value.message")
                        .value(ErrorCode.LETTER_003.getMessage()))
                .andExpect(jsonPath(ADD_CARD + "['409'].content['application/json'].examples.LETTER_003.value.success").value(false))
                .andExpect(jsonPath(ADD_CARD + "['400'].content['application/json'].examples.CARD_003").exists());
    }

    @Test
    void 태그는_정한_순서대로_나온다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.tags[0].name").value(ApiTags.AUTH))
                .andExpect(jsonPath("$.tags[5].name").value(ApiTags.LETTER_WRITE))
                .andExpect(jsonPath("$.tags[14].name").value(ApiTags.MYPAGE_SUPPORT));
    }
}
