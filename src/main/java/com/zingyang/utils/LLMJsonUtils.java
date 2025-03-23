package com.zingyang.utils;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zingyang.annotation.FieldComment;
import com.zingyang.annotation.FieldCommentParser;

import java.lang.reflect.Field;
import java.util.*;

public class LLMJsonUtils {
    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * 生成面向大模型的提示词模板
     *
     * @param clazz   目标类
     * @param example 示例对象（可选）
     * @return 结构化的提示词
     */
    public static <T> String createSchemaPrompt(Class<T> clazz, T example) {
        try {
            // 生成带注释的 schema
            Map<String, FieldCommentParser.FieldMeta> metas = FieldCommentParser.getFieldMetas(clazz);
            String schemaWithComments = mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(createAnnotatedSchema(clazz, metas));

            // 构建提示词
            String examplePart = example != null ?
                    "\n示例响应：\n" + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(example) : "";

//            return String.format(" 请严格按以下 JSON 格式响应： %s %s 请根据当前对话上下文生成数据", schemaWithComments, examplePart);
            return String.format(" 只输出严格按照 %s 的 JSON 格式响应，不需要任何额外文本，不要包含```json代码块标记, %s 请根据当前对话上下文生成数据", schemaWithComments, examplePart);

        } catch (JsonProcessingException e) {
            throw new RuntimeException("生成提示词失败", e);
        }
    }


    // 生成带注释的 schema 对象
    private static <T> ObjectNode createAnnotatedSchema(Class<T> clazz,
                                                        Map<String, FieldCommentParser.FieldMeta> metas) {
        ObjectNode schemaNode = mapper.createObjectNode();

        for (Field field : clazz.getDeclaredFields()) {
            String fieldName = field.getName();
            FieldCommentParser.FieldMeta meta = metas.get(fieldName);

            if (meta == null) continue;

            ObjectNode fieldNode = schemaNode.putObject(fieldName);
            fieldNode.put("type", getJsonType(field.getType()))
                    .put("description", buildFieldDescription(meta))
                    .put("required", meta.isRequired())
                    .put("modifyPolicy", meta.getModifyPolicy().name());

            // 添加约束条件
            addConstraints(field, fieldNode);
        }
        return schemaNode;
    }

    // 新增字段描述构建方法
    private static String buildFieldDescription(FieldCommentParser.FieldMeta meta) {
        return String.format("%s | %s%s",
                meta.getName(),
                meta.getDescription(),
                meta.isRequired() ? " [必填]" : ""
        );
    }


    // 获取 JSON 类型
    private static String getJsonType(Class<?> type) {
        if (type == Integer.class || type == int.class) return "integer";
        if (type == Boolean.class || type == boolean.class) return "boolean";
        if (type == Double.class || type == double.class) return "number";
        if (type.isEnum()) return "enum";
        return "string";
    }

    /**
     * 生成面向大模型的结构化提示词（支持数据修改场景）
     * <p>
     * 该方法通过以下步骤构建提示词：
     * 1. 解析目标类的字段元数据（包含注释和约束）
     * 2. 生成带注释的JSON Schema
     * 3. 组合示例数据和原始数据（如果存在）
     * 4. 构建包含格式要求的自然语言提示
     *
     * @param <T>          目标类型泛型参数
     * @param clazz        目标类的Class对象，用于反射获取字段结构
     *                     （通过FieldComment注解解析字段说明）
     * @param example      示例对象（可选），用于展示期望的响应格式
     *                     （建议包含典型字段值的实例）
     * @param originalData 原始数据对象（可选），在修改场景下提供现有数据
     *                     （大模型将基于此数据进行修改）
     * @return 结构化提示词，包含：
     * - JSON Schema格式要求
     * - 示例数据（当example存在时）
     * - 原始数据（当originalData存在时）
     * - 格式规范说明
     * @throws RuntimeException 当JSON序列化失败时抛出
     *                          <p>
     *                          使用示例：
     *                          // 修改用户信息场景
     *                          User original = userRepository.findById(1L);
     *                          User exampleUser = new User("张三", 25);
     *                          String prompt = createSchemaPrompt(User.class, exampleUser, original);
     *                          <p>
     *                          生成的提示词结构：
     *                          1. JSON Schema描述字段类型和约束
     *                          2. [示例响应]区块展示标准格式
     *                          3. [原始数据]区块展示当前数据
     *                          4. 明确的格式要求说明
     */
    public static <T> String createSchemaPrompt(Class<T> clazz, T example, T originalData) {
        try {

            Map<String, FieldCommentParser.FieldMeta> metas = FieldCommentParser.getFieldMetas(clazz);

            String schemaWithComments = mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(createAnnotatedSchema(clazz, metas));

            String examplePart = example != null ?
                    "\n示例响应：\n" + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(example) : "";

            String originalDataPart = originalData != null ?
                    "\n原始数据：\n" + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(originalData) : "";

            return String.format(
                    "请严格按照 %s 的 JSON 格式响应，不要包含额外文本或代码块标记。%s%s\n根据当前上下文和原始数据进行修改（保留不需要修改的字段）",
                    schemaWithComments, originalDataPart, examplePart
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("生成提示词失败", e);
        }
    }


    /**
     * 生成数组结构的提示词
     *
     * @param elementClass 数组元素类型
     * @param examples     示例数组（可选）
     * @param originalData 原始数组数据（可选）
     */
    public static <T> String createArraySchemaPrompt(Class<T> elementClass,
                                                     List<T> examples,
                                                     List<T> originalData) {
        try {
            // 生成元素schema
            ObjectNode elementSchema = createAnnotatedSchema(elementClass,
                    FieldCommentParser.getFieldMetas(elementClass));

            // 构建数组schema
            ObjectNode arraySchema = mapper.createObjectNode()
                    .put("type", "array")
                    .set("items", elementSchema);

            String schemaJson = mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(arraySchema);

            // 构建示例和原始数据
            String examplePart = examples != null && !examples.isEmpty() ?
                    "\n示例响应：\n" + mapper.writeValueAsString(examples) : "";

            String originalPart = originalData != null && !originalData.isEmpty() ?
                    "\n原始数据：\n" + mapper.writeValueAsString(originalData) : "";

            return String.format(
                    "请严格按照以下数组格式响应：%s%s%s\n根据上下文修改数据（保留不需要修改的元素）",
                    schemaJson, originalPart, examplePart
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("生成数组提示词失败", e);
        }
    }

    // 添加校验规则
    private static void addConstraints(Field field, ObjectNode node) {
        FieldComment meta = field.getAnnotation(FieldComment.class);
        if (meta == null) return;

        // 基础规则
        node.put("required", meta.required());
        node.put("modifiable", meta.modify().name());

        // 数值校验
        if (Number.class.isAssignableFrom(field.getType())) {
            if (meta.min() != Long.MIN_VALUE) node.put("minimum", meta.min());
            if (meta.max() != Long.MAX_VALUE) node.put("maximum", meta.max());
        }

        // 正则校验
        if (!meta.pattern().isEmpty()) {
            node.put("pattern", meta.pattern());
            node.put("patternMessage", meta.message());
        }
    }


    private static String formatRange(FieldComment meta) {
        if (meta.min() == Long.MIN_VALUE) return "≤" + meta.max();
        if (meta.max() == Long.MAX_VALUE) return "≥" + meta.min();
        return meta.min() + "~" + meta.max();
    }

    /**
     * 生成面向大模型的提示词模板
     *
     * @param clazz   目标类
     * @param example 示例对象（可选）
     * @return 结构化的提示词
     */
    public static <T> String createSchemaPrompt(String prompt, Class<T> clazz, T example) {
        try {


            // 生成带注释的 schema

            Map<String, FieldCommentParser.FieldMeta> metas = FieldCommentParser.getFieldMetas(clazz);
            String schemaWithComments = mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(createAnnotatedSchema(clazz, metas));

            // 构建提示词
            String examplePart = example != null ?
                    "\n示例响应：\n" + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(example) : "";
            return String.format(" 只输出严格按照 %s 的 JSON 格式响应，不需要任何额外文本，不要包含```json代码块标记, %s 请根据当前对话上下文生成数据", schemaWithComments, examplePart);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("生成提示词失败", e);
        }
    }


    /**
     * 解析大模型返回的 JSON
     *
     * @param json  大模型返回的字符串
     * @param clazz 目标类
     * @return 解析后的对象
     */
    public static <T> T parseResponse(String json, Class<T> clazz) {
        try {
            return mapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("解析 JSON 失败", e);
        }
    }

    public static <T> List<T> parseListResponse(String json, Class<T> elementType) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }

        try {
            return mapper.readValue(json,
                    mapper.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON格式错误: " + e.getOriginalMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("数据处理异常", e);
        }
    }


    /**
     * 解析大模型返回的 JSON
     *
     * @param json  大模型返回的字符串
     * @param clazz 目标类
     * @return 解析后的对象
     */
    public static <T> T parseResponseArr(String json, Class<T> clazz) {
        try {
            return mapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("解析 JSON 失败", e);
        }
    }

    /**
     * 创建空值示例对象（用于生成schema）
     */
    private static <T> T createEmptyInstance(Class<T> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("创建示例对象失败", e);
        }
    }


    private static String buildFieldDescription(FieldComment meta) {
        return String.format(
                "%s%s%s%s",
                meta.name(),
                meta.required() ? "[必填]" : "",
                !meta.pattern().isEmpty() ? "| 格式要求：" + meta.message() : "",
                (meta.min() != Long.MIN_VALUE || meta.max() != Long.MAX_VALUE) ?
                        "| 有效范围：" + formatRange(meta) : ""
        );
    }


}
