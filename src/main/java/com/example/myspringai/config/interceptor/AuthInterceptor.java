package com.example.myspringai.config.interceptor;




import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        // 1. 判断是不是访问 Controller 方法（静态资源直接放行）
        if (!(handler instanceof HandlerMethod handlerMethod)) {  // ← JDK 17 模式匹配语法
            return true;
        }

        // 2. 记录开始时间
        request.setAttribute("startTime", System.currentTimeMillis());

        // 3. 获取目标方法信息（可以做细粒度权限）
        String methodName = handlerMethod.getMethod().getName();
        String className = handlerMethod.getBeanType().getSimpleName();
        log.info("【Interceptor】preHandle: {}.{}", className, methodName);

        // 4. 从 Header 取 Token 做登录校验
        String token = request.getHeader("Authorization");
        if (token == null || token.isEmpty()) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"code\":401,\"msg\":\"未登录，请先登录\"}");
            return false;  // 拦截
        }

        // 5. 模拟解析 Token 得到用户 ID，放入 ThreadLocal
        String userId = parseTokenToUserId(token);
        UserContext.setUserId(userId);

        return true;  // 放行
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) throws Exception {
        log.info("【Interceptor】postHandle 执行");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) throws Exception {
        // 1. 统计耗时
        Long startTime = (Long) request.getAttribute("startTime");
        if (startTime != null) {
            long cost = System.currentTimeMillis() - startTime;
            log.info("【Interceptor】afterCompletion: {} 耗时: {}ms", request.getRequestURI(), cost);
        }

        // 2. 清理 ThreadLocal（重要！防止内存泄漏）
        UserContext.clear();

        // 3. 记录异常（如果有）
        if (ex != null) {
            log.error("【Interceptor】请求发生异常: {}", ex.getMessage(), ex);
        }
    }

    private String parseTokenToUserId(String token) {
        // TODO: 实际项目用 JWT 解析
        return "userId_123";
    }
}
