package com.zing.annotation;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FieldCommentParser {
    // 使用双重校验锁保证线程安全
    private static final Map<Class<?>, Map<String, FieldMeta>> META_CACHE = new ConcurrentHashMap<>();

    /**
     * 获取字段完整元数据
     */
    public static Map<String, FieldMeta> getFieldMetas(Class<?> clazz) {
        return META_CACHE.computeIfAbsent(clazz, k -> {
            Map<String, FieldMeta> metas = new LinkedHashMap<>();
            for (Field field : clazz.getDeclaredFields()) {
                FieldComment annotation = field.getAnnotation(FieldComment.class);
                if (annotation != null) {
                    validateAnnotation(field, annotation);
                    metas.put(field.getName(), parseFieldMeta(field, annotation));
                }
            }
            return Collections.unmodifiableMap(metas);
        });
    }

    /**
     * 解析单个字段元数据
     */
    private static FieldMeta parseFieldMeta(Field field, FieldComment annotation) {
        return new FieldMeta(
                annotation.name().isEmpty() ? field.getName() : annotation.name(),
                annotation.value(),
                annotation.required(),
                annotation.modify(),
                getFieldType(field),
                getConstraints(field)
        );
    }

    /**
     * 获取字段类型信息
     */
    private static FieldType getFieldType(Field field) {
        Class<?> type = field.getType();
        if (Number.class.isAssignableFrom(type)) return FieldType.NUMBER;
        if (type == boolean.class || type == Boolean.class) return FieldType.BOOLEAN;
        if (type.isEnum()) return FieldType.ENUM;
        return FieldType.STRING;
    }

    /**
     * 收集字段约束条件
     */
    private static Map<String, Object> getConstraints(Field field) {
        Map<String, Object> constraints = new HashMap<>();
        FieldComment annotation = field.getAnnotation(FieldComment.class);

        // 数值范围约束
        if (annotation.min() != Long.MIN_VALUE) {
            constraints.put("min", annotation.min());
        }
        if (annotation.max() != Long.MAX_VALUE) {
            constraints.put("max", annotation.max());
        }

        // 正则表达式约束
        if (!annotation.pattern().isEmpty()) {
            constraints.put("pattern", annotation.pattern());
            constraints.put("patternMessage", annotation.message());
        }

        return constraints;
    }

    /**
     * 校验注解配置合法性
     */
    private static void validateAnnotation(Field field, FieldComment annotation) {
        // 必填字段必须提供名称
        if (annotation.required() && annotation.name().isEmpty()) {
            throw new IllegalConfigurationException(field, "必填字段必须指定name属性");
        }

        // 只读字段不能设置为非必填
        if (annotation.modify() == FieldComment.ModifyPolicy.READ_ONLY && !annotation.required()) {
            throw new IllegalConfigurationException(field, "只读字段必须设置为必填");
        }

        // 数值字段需要校验范围
        if (Number.class.isAssignableFrom(field.getType())) {
            if (annotation.min() > annotation.max()) {
                throw new IllegalConfigurationException(field, "最小值不能大于最大值");
            }
        }
    }

    @Getter
    @AllArgsConstructor
    @ToString
    public static class FieldMeta {
        private final String name;
        private final String description;
        private final boolean required;
        private final FieldComment.ModifyPolicy modifyPolicy;
        private final FieldType fieldType;
        private final Map<String, Object> constraints;
    }

    // 字段类型枚举
    public enum FieldType {STRING, NUMBER, BOOLEAN, ENUM}

    // 自定义异常
    public static class IllegalConfigurationException extends RuntimeException {
        public IllegalConfigurationException(Field field, String message) {
            super(String.format("字段配置错误 [%s.%s]: %s",
                    field.getDeclaringClass().getSimpleName(),
                    field.getName(),
                    message));
        }
    }

}
