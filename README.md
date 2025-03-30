
# Zing-AI

当前最新版本： 0.1（发布日期：2025-3-19）

## 项目简介

Zing-AI是一个Java注解工具库，提供了强大的字段元数据管理和校验功能。通过简单的注解配置，可以实现字段的校验、说明文档生成、以及修改控制等功能。

## 快速开始

### 安装依赖

- JDK 8+
- Maven 3.x

在项目的`pom.xml`中添加依赖：

```xml
<dependency>
    <groupId>com.zingyang</groupId>
    <artifactId>zing-ai</artifactId>
    <version>0.1</version>
</dependency>
```

### 基础使用示例

1. 在实体类字段上添加`@FieldComment`注解：

```java
public class User {
    @FieldComment(
        name = "用户名",
        value = "用户的登录账号名称",
        required = true,
        pattern = "^[a-zA-Z0-9]{4,16}$",
        message = "用户名必须是4-16位字母或数字"
    )
    private String username;
}
```

2. 使用`LLMJsonUtils`生成提示词：

```java
// 生成基础提示词
String prompt = LLMJsonUtils.createSchemaPrompt(User.class, exampleUser);
```

## 功能详解

### FieldComment注解

`@FieldComment`注解是本项目的核心功能，提供以下特性：

| 属性名 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| name | String | "" | 字段中文名称（用于界面展示） |
| value | String | "" | 技术说明（支持Markdown格式） |
| required | boolean | true | 是否必填字段 |
| modify | ModifyPolicy | ALLOW | 字段修改策略 |
| min | long | Long.MIN_VALUE | 数值最小值 |
| max | long | Long.MAX_VALUE | 数值最大值 |
| pattern | String | "" | 字符串格式正则表达式 |
| message | String | "字段校验未通过" | 校验失败提示信息 |

### LLMJsonUtils工具类

提供多种场景的JSON Schema生成功能：

```java
// 1. 基础场景 - 生成单个对象的Schema
String prompt = LLMJsonUtils.createSchemaPrompt(User.class, exampleUser);

// 2. 数据修改场景 - 包含原始数据
User original = userRepository.findById(1L);
String prompt = LLMJsonUtils.createSchemaPrompt(User.class, exampleUser, original);

// 3. 数组场景 - 处理列表数据
List<User> examples = Arrays.asList(new User("张三", 25));
String prompt = LLMJsonUtils.createArraySchemaPrompt(User.class, examples, originalList);
```

## 高级特性

### 字段修改策略

支持四种修改策略：

- ALLOW：允许修改
- READ_ONLY：禁止修改
- CONDITIONAL：有条件修改
- AUDIT_REQUIRED：修改需审批

### 数据校验规则

- 必填校验：`required = true`
- 数值范围：通过`min`和`max`设置
- 字符串格式：使用`pattern`配置正则表达式
- 自定义提示：通过`message`设置校验失败提示

## 常见问题（FAQ）

### Q1：如何处理复杂的数据校验场景？
A1：可以组合使用`pattern`、`min`、`max`等属性，或者通过自定义校验器扩展。

### Q2：修改策略如何与业务逻辑结合？
A2：可以在业务层通过`ModifyPolicy`枚举值进行判断，实现自定义的修改控制逻辑。

### Q3：如何扩展新的校验规则？
A3：可以通过继承`FieldCommentParser`类来实现自定义的校验规则。

## 项目结构

```
src/main/java/com/zingyang/
├── annotation/
│   ├── FieldComment.java          # 字段注解定义
│   └── FieldCommentParser.java    # 注解解析和校验实现
└── utils/
    └── LLMJsonUtils.java          # JSON Schema提示词生成工具
```

## 许可证

本项目采用MIT许可证。