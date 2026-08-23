package in.retailflow.api.security.tenant;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantBypassAspect {

    @Around("@annotation(in.retailflow.api.security.tenant.TenantBypass) || "
            + "@within(in.retailflow.api.security.tenant.TenantBypass)")
    public Object aroundBypass(ProceedingJoinPoint joinPoint) throws Throwable {
        TenantBypassHolder.enter();
        try {
            return joinPoint.proceed();
        } finally {
            TenantBypassHolder.exit();
        }
    }
}
