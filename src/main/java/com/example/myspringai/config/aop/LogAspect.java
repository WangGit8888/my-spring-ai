package com.example.myspringai.config.aop;


import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;


@Slf4j
@Component
@Aspect
public class LogAspect {

    /**
     * 切点：拦截所有带有 @RestController 的类中的所有方法
     * 也可以改成拦截指定包：execution(* com.example.controller..*.*(..))
     */
    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void controllerPointcut() {
    }

    /**
     * 环绕通知：最强大的一种通知，可以控制目标方法是否执行
     */
    @Around("controllerPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 1. 获取请求信息
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

        // 2. 获取目标方法信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = method.getName();

        // 3. 获取方法上的自定义注解（用于权限校验）
        RequiresPermission requiresPermission = method.getAnnotation(RequiresPermission.class);
        if (requiresPermission != null) {
            String permission = requiresPermission.value();
            log.info("【AOP】该方法需要权限: {}", permission);
            // 这里可以做权限校验，如果没有权限可以抛异常
            // if (!hasPermission(permission)) {
            //     throw new BusinessException("无权限访问");
            // }
        }

        // 4. 记录请求参数
        Object[] args = joinPoint.getArgs();
        String requestInfo = request != null ?
                String.format("%s %s", request.getMethod(), request.getRequestURI()) : "非Web请求";
        log.info("【AOP】请求开始: {}, 方法: {}.{}, 参数: {}",
                requestInfo, className, methodName, JSON.toJSONString(args));

        // 5. 记录开始时间
        long startTime = System.currentTimeMillis();
        Object result = null;
        Exception exception = null;

        try {
            // 6. 执行目标方法（核心）
            result = joinPoint.proceed();
            return result;
        } catch (Exception e) {
            exception = e;
            throw e;  // 继续向上抛出
        } finally {
            // 7. 记录耗时和结果（无论是否异常都会执行）
            long cost = System.currentTimeMillis() - startTime;
            if (exception != null) {
                log.error("【AOP】请求异常: {}, 耗时: {}ms, 异常信息: {}",
                        requestInfo, cost, exception.getMessage());
            } else {
                String resultStr = result != null ? JSON.toJSONString(result) : "void";
                // 如果返回值太长可以截断
                if (resultStr.length() > 500) {
                    resultStr = resultStr.substring(0, 500) + "...(truncated)";
                }
                log.info("【AOP】请求结束: {}, 耗时: {}ms, 返回值: {}",
                        requestInfo, cost, resultStr);
            }
        }
    }

    /**
     * 前置通知：在目标方法执行之前执行
     * 可以和 Around 配合使用，但通常 Around 已经包含了前置和后置逻辑
     */
    @Before("controllerPointcut()")
    public void before(JoinPoint joinPoint) {
        log.debug("【AOP】@Before 前置通知执行");
    }

    /**
     * 后置通知：在目标方法执行之后执行（无论是否异常）
     */
    @After("controllerPointcut()")
    public void after(JoinPoint joinPoint) {
        log.debug("【AOP】@After 后置通知执行");
    }

    /**
     * 返回通知：在目标方法正常返回后执行
     */
    @AfterReturning(pointcut = "controllerPointcut()", returning = "result")
    public void afterReturning(JoinPoint joinPoint, Object result) {
        log.debug("【AOP】@AfterReturning 返回通知执行, 返回值: {}", result);
    }

    /**
     * 异常通知：在目标方法抛出异常后执行
     */
    @AfterThrowing(pointcut = "controllerPointcut()", throwing = "ex")
    public void afterThrowing(JoinPoint joinPoint, Exception ex) {
        log.debug("【AOP】@AfterThrowing 异常通知执行, 异常: {}", ex.getMessage());
    }
}