package com.sdp1617.backend.global.config;

import com.sdp1617.backend.global.config.properties.CorsProperties;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** CORS 허용 주소 설정값 검증 (#154). 잘못 적으면 아무 요청도 허용되지 않으므로 기동할 때 막는다. */
class CorsPropertiesTest {

    @Test
    void 끝의_슬래시와_공백을_정리하고_중복을_없앤다() {
        CorsProperties properties = new CorsProperties(
                List.of(" https://funzy.kr/ ", "https://funzy.kr", "http://localhost:3000", ""));

        assertEquals(List.of("https://funzy.kr", "http://localhost:3000"), properties.allowedOrigins());
    }

    @Test
    void 포트가_있는_주소도_받는다() {
        assertEquals(List.of("https://dev.funzy.kr:8443"),
                new CorsProperties(List.of("https://dev.funzy.kr:8443")).allowedOrigins());
    }

    @Test
    void 브라우저가_적지_않는_기본_포트는_뗀다() {
        assertEquals(List.of("https://funzy.kr", "http://funzy.kr", "http://funzy.kr:443"),
                new CorsProperties(List.of("https://funzy.kr:443", "http://funzy.kr:80/", "http://funzy.kr:443"))
                        .allowedOrigins());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "*",
            "funzy.kr",
            "ftp://funzy.kr",
            "https://funzy.kr/app",
            "https://funzy.kr?x=1",
            "https://funzy.kr#top",
            "https://user@funzy.kr",
            "https://funzy .kr",
            "https://funzy.kr:",
            "https://funzy.kr:0",
            "https://funzy.kr:65536"
    })
    void 주소가_아니거나_경로가_붙으면_기동하지_않는다(String origin) {
        assertThrows(IllegalStateException.class, () -> new CorsProperties(List.of(origin)));
    }

    @Test
    void 비어_있으면_기동하지_않는다() {
        assertThrows(IllegalStateException.class, () -> new CorsProperties(null));
        assertThrows(IllegalStateException.class, () -> new CorsProperties(List.of()));
        assertThrows(IllegalStateException.class, () -> new CorsProperties(Arrays.asList(" ", "")));
    }
}
