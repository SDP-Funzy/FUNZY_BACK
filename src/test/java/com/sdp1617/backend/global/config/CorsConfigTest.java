package com.sdp1617.backend.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CORS 허용 주소를 설정값(CORS_ALLOWED_ORIGINS)으로 받는다 (#154). */
@SpringBootTest(properties = "app.cors.allowed-origins=https://funzy.kr/, https://www.funzy.kr")
@AutoConfigureMockMvc
class CorsConfigTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void 설정한_주소의_사전_요청은_허용한다() throws Exception {
        mockMvc.perform(options("/api/letters/received")
                        .header(HttpHeaders.ORIGIN, "https://funzy.kr")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://funzy.kr"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void 쉼표로_적은_두번째_주소도_허용한다() throws Exception {
        mockMvc.perform(get("/health").header(HttpHeaders.ORIGIN, "https://www.funzy.kr"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://www.funzy.kr"));
    }

    @Test
    void 설정하지_않은_주소는_거절한다() throws Exception {
        mockMvc.perform(options("/api/letters/received")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void 기본값이던_localhost도_설정에서_빼면_거절한다() throws Exception {
        mockMvc.perform(get("/health").header(HttpHeaders.ORIGIN, "http://localhost:3000"))
                .andExpect(status().isForbidden());
    }
}
