package com.sdp1617.backend.global.config;

import com.sdp1617.backend.global.error.ErrorCode;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.in;
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

    /** 컨트롤러가 OpenApiConfig.TAGS에 없는 태그를 쓰면 맨 뒤에 설명 없이 붙으므로 전체 목록이 같아야 한다. */
    @Test
    void 태그는_정한_목록과_순서대로만_나온다() throws Exception {
        List<String> expected = OpenApiConfig.TAGS.stream().map(Tag::getName).toList();
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.tags[*].name").value(contains(expected.toArray())))
                .andExpect(jsonPath("$.paths.*.*.tags[*]").value(everyItem(in(expected))));
    }

    @Test
    void COMMON_002에는_message가_구체적으로_바뀐다는_안내가_붙는다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(ADD_CARD + "['400'].description").value(containsString("message는 잘못된 값에 따라")));
    }
}
