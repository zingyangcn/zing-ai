package com.zing.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
/**
 * 智能字段元数据注解（集成校验、说明、修改控制）
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface FieldComment {
    /**
     * 字段中文名称（用于界面展示）
     */
    String name() default "";

    /**
     * 技术说明（支持Markdown格式）
     */
    String value() default "";

    /**
     * 是否必填字段（默认true）
     */
    boolean required() default true;

    /**
     * 字段修改策略（默认允许修改）
     */
    ModifyPolicy modify() default ModifyPolicy.ALLOW;

    /**
     * 数值最小值（仅数字类型有效）
     */
    long min() default Long.MIN_VALUE;

    /**
     * 数值最大值（仅数字类型有效）
     */
    long max() default Long.MAX_VALUE;

    /**
     * 正则表达式模式（仅字符串类型有效）
     */
    String pattern() default "";

    /**
     * 校验失败提示信息
     */
    String message() default "字段校验未通过";

    /**
     * 修改策略枚举
     */
    enum ModifyPolicy {
        ALLOW,          // 允许修改
        READ_ONLY,      // 完全禁止修改
        CONDITIONAL,    // 有条件修改（需配合业务逻辑）
        AUDIT_REQUIRED  // 修改需审批
    }
}
