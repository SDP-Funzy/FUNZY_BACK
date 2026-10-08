package com.sdp1617.backend.global.security;

import com.sdp1617.backend.auth.jwt.JwtClaims;
import com.sdp1617.backend.auth.jwt.JwtProvider;
import com.sdp1617.backend.auth.jwt.TokenType;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.auth.repository.MemberTokenState;
import com.sdp1617.backend.global.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

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

    private static final int TOKEN_SESSION_VERSION = 2;

    private MockHttpServletRequest requestWithToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer access-token");
        when(jwtProvider.parse("access-token", TokenType.ACCESS)).thenReturn(new JwtClaims(1L, null, TOKEN_SESSION_VERSION));
        return request;
    }


    @Test
    void 활성_회원의_토큰이면_인증된다() throws Exception {
        MockHttpServletRequest request = requestWithToken();
        when(memberRepository.findActiveTokenStateById(1L)).thenReturn(Optional.of(new MemberTokenState(null)));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals(1L, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void 토큰의_세션_버전이_회원의_현재_버전과_같으면_인증된다() throws Exception {
        MockHttpServletRequest request = requestWithToken();
        when(memberRepository.findActiveTokenStateById(1L))
                .thenReturn(Optional.of(new MemberTokenState(TOKEN_SESSION_VERSION)));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals(1L, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void 그_뒤에_세션을_끊어_회원의_세션_버전이_올라갔으면_AUTH_027로_거절한다() throws Exception {
        MockHttpServletRequest request = requestWithToken();
        when(memberRepository.findActiveTokenStateById(1L))
                .thenReturn(Optional.of(new MemberTokenState(TOKEN_SESSION_VERSION + 1)));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(ErrorCode.AUTH_027, request.getAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE));
    }

    @Test
    void 칼럼이_추가되기_전_회원의_세션_버전은_0으로_본다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer access-token");
        when(jwtProvider.parse("access-token", TokenType.ACCESS)).thenReturn(new JwtClaims(1L, null, 0));
        when(memberRepository.findActiveTokenStateById(1L)).thenReturn(Optional.of(new MemberTokenState(null)));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals(1L, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void 탈퇴한_회원의_남은_토큰이면_인증하지_않는다() throws Exception {
        MockHttpServletRequest request = requestWithToken();
        when(memberRepository.findActiveTokenStateById(1L)).thenReturn(Optional.empty());

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(ErrorCode.AUTH_003, request.getAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE));
    }
}
