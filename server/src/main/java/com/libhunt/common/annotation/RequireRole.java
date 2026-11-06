package com.libhunt.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 Controller 类或方法上，限制可访问的角色。
 * 由 {@code RoleInterceptor} 在 JWT 认证之后检查。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    String[] value() default {"ADMIN"};
}
