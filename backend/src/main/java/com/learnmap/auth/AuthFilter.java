package com.learnmap.auth;

import com.learnmap.common.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 把 “Authorization: Bearer 令牌” 解析成当前用户，写进内部请求头 X-User-Id，控制器照旧从这个头取当前用户。
 * 没有有效令牌时去掉客户端自己带的 X-User-Id（除非配置允许直接用这个头，仅限开发和测试）。
 */
@Component
@RequiredArgsConstructor
public class AuthFilter extends OncePerRequestFilter {

    private final AuthService auth;
    private final AuthProperties props;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        Optional<Long> user = Optional.empty();
        if (header != null && header.startsWith("Bearer ")) {
            user = auth.resolve(header.substring(7).strip());
        }
        if (user.isPresent()) {
            chain.doFilter(withUser(request, String.valueOf(user.get())), response);
        } else if (props.allowUserIdHeader()) {
            chain.doFilter(request, response);
        } else {
            chain.doFilter(withUser(request, null), response);
        }
    }

    /** 覆盖（或去掉）X-User-Id */
    static HttpServletRequest withUser(HttpServletRequest request, String userId) {
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                return CurrentUser.HEADER.equalsIgnoreCase(name) ? userId : super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                if (CurrentUser.HEADER.equalsIgnoreCase(name)) {
                    return userId == null ? Collections.emptyEnumeration() : Collections.enumeration(Set.of(userId));
                }
                return super.getHeaders(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                Set<String> names = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
                names.removeIf(CurrentUser.HEADER::equalsIgnoreCase);
                if (userId != null) {
                    names.add(CurrentUser.HEADER);
                }
                return Collections.enumeration(names);
            }
        };
    }
}
