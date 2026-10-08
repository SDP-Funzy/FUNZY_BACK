package com.sdp1617.backend.global.security;

import com.sdp1617.backend.auth.jwt.JwtClaims;
import com.sdp1617.backend.auth.jwt.JwtProvider;
import com.sdp1617.backend.auth.jwt.TokenType;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.auth.repository.MemberTokenState;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ERROR_CODE_ATTRIBUTE = "jwtErrorCode";

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final MemberRepository memberRepository;

    public JwtAuthenticationFilter(JwtProvider jwtProvider, MemberRepository memberRepository) {
        this.jwtProvider = jwtProvider;
        this.memberRepository = memberRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null) {
            try {
                JwtClaims claims = jwtProvider.parse(token, TokenType.ACCESS);
                // 탈퇴해도 이미 발급된 access token은 만료 전까지 서명상 유효하다. 탈퇴한(익명화된) 회원 행으로
                // 소셜 계정을 다시 연결하거나 닉네임을 바꾸는 등 계정이 되살아나지 않게 여기서 막는다.
                MemberTokenState state = memberRepository.findActiveTokenStateById(claims.memberId())
                        .orElseThrow(() -> new CustomException(ErrorCode.AUTH_003));
                // 비밀번호 변경·재설정 등으로 전체 세션을 끊었으면 그 전에 로그인한 토큰도 바로 막는다 (#124)
                if (state.isRevoked(claims.sessionVersion())) {
                    throw new CustomException(ErrorCode.AUTH_027);
                }
                var authentication = new UsernamePasswordAuthenticationToken(claims.memberId(), null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (CustomException e) {
                request.setAttribute(ERROR_CODE_ATTRIBUTE, e.getErrorCode());
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            return header.substring(PREFIX.length());
        }
        return null;
    }
}
