package com.afriland.ticket2cash.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthSessionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String uri = request.getRequestURI();
        String method = request.getMethod();

        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        if (uri.equals("/api/health")) {
            return true;
        }

        if (uri.equals("/api/auth/login")) {
            return true;
        }

        // Allow all mobile API endpoints (they use their own session auth)
        if (uri.startsWith("/api/mobile/")) {
            return true;
        }

        // Allow webhook endpoints (authenticated via API key in production)
        if (uri.startsWith("/api/webhook/")) {
            return true;
        }

        // Public integration API for supermarkets: authenticated by X-API-Key
        // inside IntegrationApiController (not by the admin session).
        if (uri.startsWith("/api/v1/")) {
            return true;
        }


        if (uri.equals("/api/auth/me")) {
            return true;
        }

        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("AUTH_USER_ID") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"AUTHENTICATION_REQUIRED\",\"message\":\"Please login first\"}");
            return false;
        }

        String role = String.valueOf(session.getAttribute("AUTH_ROLE"));

        if (uri.equals("/api/auth/logout")) {
            return true;
        }

        if (uri.equals("/api/auth/change-password")) {
            return true;
        }

        if ("ADMIN".equals(role)) {
            return true;
        }

        if ("PARTNER".equals(role)) {
            return isPartnerAllowed(uri, method, response);
        }

        if ("OPERATEUR".equals(role)) {
            return isOperatorAllowed(uri, method, response);
        }

        if ("LECTEUR".equals(role)) {
            return isLecteurAllowed(uri, method, response);
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"UNKNOWN_ROLE\",\"message\":\"Unknown user role\"}");
        return false;
    }

    private boolean isPartnerAllowed(String uri,
                                      String method,
                                      HttpServletResponse response) throws Exception {

        // Partners can access their own dashboard
        if (uri.startsWith("/api/partner/")) {
            return true;
        }

        // Partners can read most data (filtered to their merchant on the frontend)
        if ("GET".equalsIgnoreCase(method)) {
            // Block user management
            if (uri.startsWith("/api/auth/users")) {
                return forbidden(response, "ADMIN role required");
            }
            // Block system settings
            if (uri.startsWith("/api/settings")) {
                return forbidden(response, "ADMIN role required");
            }
            // Block GLOBAL lists that would leak other partners' data.
            // Partners must use the scoped endpoints under /api/partner/.
            if (uri.equals("/api/claims") || uri.startsWith("/api/claims/status")) {
                return forbidden(response, "Use /api/partner/claims");
            }
            if (uri.equals("/api/merchants")) {
                return forbidden(response, "Use /api/partner/merchants");
            }
            if (uri.equals("/api/products")) {
                return forbidden(response, "Use /api/partner/products");
            }
            if (uri.startsWith("/api/api-keys")) {
                return forbidden(response, "ADMIN role required");
            }
            return true;
        }

        // Partners cannot create/modify data (read-only with some exceptions)
        return forbidden(response, "PARTNER role cannot perform this action");
    }

    private boolean isLecteurAllowed(String uri,
                                     String method,
                                     HttpServletResponse response) throws Exception {

        if ("GET".equalsIgnoreCase(method)) {
            if (uri.startsWith("/api/auth/users")) {
                return forbidden(response, "ADMIN role required");
            }

            return true;
        }

        return forbidden(response, "LECTEUR role is read-only");
    }

    private boolean isOperatorAllowed(String uri,
                                      String method,
                                      HttpServletResponse response) throws Exception {

        if ("GET".equalsIgnoreCase(method)) {
            if (uri.startsWith("/api/auth/users")) {
                return forbidden(response, "ADMIN role required");
            }

            return true;
        }

        if ("POST".equalsIgnoreCase(method) && uri.equals("/api/tickets/simulate-ocr")) {
            return true;
        }

        if ("PUT".equalsIgnoreCase(method) && uri.matches("/api/claims/[0-9]+/status")) {
            return true;
        }

        if ("POST".equalsIgnoreCase(method) && uri.equals("/api/cashback/batch/run")) {
            return true;
        }

        if ("PUT".equalsIgnoreCase(method) && uri.matches("/api/fraud/alerts/[0-9]+/status")) {
            return true;
        }

        return forbidden(response, "OPERATEUR role cannot perform this action");
    }

    private boolean forbidden(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"ACCESS_DENIED\",\"message\":\"" + message + "\"}");
        return false;
    }
}