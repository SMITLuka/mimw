package com.smit.mimw.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Every request and response of MIMW is UTF-8.
 * <ul>
 *   <li>A request body without a charset is read as UTF-8.</li>
 *   <li>Every response carries the charset explicitly, e.g. {@code Content-Type: application/json;charset=UTF-8},
 *       so clients (Ares / MSXML) never guess the encoding of Croatian letters (č, ć, ž, š, đ).</li>
 * </ul>
 * Header values (mandatorId / companyId) cannot be UTF-8 in HTTP; Ares percent-encodes them and
 * {@link com.smit.mimw.tenant.TenantInterceptor} decodes them as UTF-8.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class Utf8EncodingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getCharacterEncoding() == null) {
            request.setCharacterEncoding(StandardCharsets.UTF_8.name()); //$NON-NLS-1$
        }
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        chain.doFilter(request, response);
    }
}
