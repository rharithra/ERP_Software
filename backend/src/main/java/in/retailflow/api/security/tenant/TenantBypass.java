package in.retailflow.api.security.tenant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks authentication bootstrap work that must run with RLS bypass.
 *
 * <p>The flag is set before the transactional interceptor begins, so the
 * transaction listener can enable bypass for that transaction only ({@code SET LOCAL}).
 * Do not use this on ordinary tenant-owned business operations.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantBypass {}
