package com.sdp1617.backend.global.security;

import com.sdp1617.backend.auth.jwt.JwtClaims;
import com.sdp1617.backend.auth.jwt.JwtProvider;
import com.sdp1617.backend.auth.jwt.TokenType;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.global.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider, memberRepository);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWithToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer access-token");
        when(jwtProvider.parse("access-token", TokenType.ACCESS)).thenReturn(new JwtClaims(1L, null));
        return request;
    }

    @Test
    void 활성_회원의_토큰이면_인증된다() throws Exception {
        MockHttpServletRequest request = requestWithToken();
        when(memberRepository.existsActiveById(1L)).thenReturn(true);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals(1L, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void 탈퇴한_회원의_남은_토큰이면_인증하지_않는다() throws Exception {
        MockHttpServletRequest request = requestWithToken();
        when(memberRepository.existsActiveById(1L)).thenReturn(false);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(ErrorCode.AUTH_003, request.getAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE));
    }
}
