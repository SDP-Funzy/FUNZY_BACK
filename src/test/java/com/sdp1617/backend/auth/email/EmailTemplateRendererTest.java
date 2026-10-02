package com.sdp1617.backend.auth.email;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailTemplateRendererTest {

    private EmailTemplateRenderer renderer;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");

        // 일반 TemplateEngine의 기본 표현식 평가기(OGNL)는 spring-boot-starter-thymeleaf에 없어서(SpringEL로 대체됨)
        // NoClassDefFoundError가 나므로, 운영과 동일하게 SpringTemplateEngine을 써야 한다.
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        renderer = new EmailTemplateRenderer(templateEngine);
    }

    @Test
    void verification_link_템플릿을_변수와_함께_렌더링한다() {
        String html = renderer.render("verification-link", Map.of(
                "title", "이메일 인증",
                "message", "아래 버튼을 눌러 이메일 인증을 완료해주세요.",
                "link", "http://localhost:3000/verify-email?token=abc123",
                "buttonText", "이메일 인증하기",
                "ttlMinutes", 15L,
                "logoUrl", "https://sdp-funzy.s3.ap-northeast-2.amazonaws.com/static/funzy-logo.png"
        ));

        assertTrue(html.contains("이메일 인증"));
        assertTrue(html.contains("아래 버튼을 눌러 이메일 인증을 완료해주세요."));
        assertTrue(html.contains("http://localhost:3000/verify-email?token=abc123"));
        assertTrue(html.contains("이메일 인증하기"));
        assertTrue(html.contains(">15<"));
        assertTrue(html.contains("https://sdp-funzy.s3.ap-northeast-2.amazonaws.com/static/funzy-logo.png"));
        assertTrue(html.contains("<!DOCTYPE html>"));
    }

    @Test
    void verification_code_템플릿에_인증번호와_유효시간을_렌더링한다() {
        String html = renderer.render("verification-code", Map.of(
                "code", "123456",
                "ttlMinutes", 5L,
                "logoUrl", "https://sdp-funzy.s3.ap-northeast-2.amazonaws.com/static/funzy-logo.png"
        ));

        assertTrue(html.contains(">123456<"));
        assertTrue(html.contains(">5<"));
        assertTrue(html.contains("https://sdp-funzy.s3.ap-northeast-2.amazonaws.com/static/funzy-logo.png"));
    }

    @Test
    void login_id_템플릿은_비밀번호_계정이면_아이디와_비밀번호_찾기_안내를_보여준다() {
        String html = renderer.render("login-id", Map.of(
                "message", "요청하신 계정의 아이디입니다. 이 아이디와 비밀번호로 로그인해주세요.",
                "loginId", "funzy_id",
                "hasPassword", true,
                "logoUrl", "https://example.com/logo.png"
        ));

        assertTrue(html.contains(">funzy_id<"));
        assertTrue(html.contains("이 아이디와 비밀번호로 로그인해주세요."));
        assertTrue(html.contains("비밀번호 찾기를 이용해주세요"));
    }

    @Test
    void login_id_템플릿은_소셜_전용_계정이면_아이디와_비밀번호_찾기_안내를_숨긴다() {
        String html = renderer.render("login-id", Map.of(
                "message", "이 이메일은 카카오 로그인으로 가입된 계정입니다. 해당 소셜 계정으로 로그인해주세요.",
                "hasPassword", false,
                "logoUrl", "https://example.com/logo.png"
        ));

        assertFalse(html.contains("funzy_id"));
        // 소셜 전용 계정은 비밀번호 재설정 메일이 발송되지 않으므로 안내하면 안 된다
        assertFalse(html.contains("비밀번호 찾기"));
        assertTrue(html.contains("카카오 로그인으로 가입된 계정입니다."));
    }
}
