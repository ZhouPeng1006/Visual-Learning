package com.zp.visuallearningservice.config;

import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.Filter;
import javax.servlet.http.*;
import java.io.IOException;

/**
 * @author ZP
 * @date 2023/6/9 11:23
 * @description TODO
 */

@Component
public class CorsFilter implements Filter {

    public static final String OPTIONS = "OPTIONS";
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse res = (HttpServletResponse) response;
        res.addHeader("Access-Control-Allow-Credentials", "true");
        res.addHeader("Access-Control-Allow-Origin", "*");
        res.addHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT");
        res.addHeader("Access-Control-Allow-Headers", "*");
        res.addHeader("Access-Control-Max-Age","1800");
        if (OPTIONS.equals(((HttpServletRequest) request).getMethod())) {
            response.getWriter().println("ok");
            return;
        }
        chain.doFilter(request, response);
    }
    @Override
    public void destroy() {
    }
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }


}
