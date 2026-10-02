package com.lingjiuw.cms.module.ai.copilot.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lingjiuw.cms.common.exception.BizException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工具参数的 JSON Schema 派生 + 参数绑定（docs/ai-copilot.md §4.1）。
 *
 * <p><b>为什么从 record + jakarta 注解派生，不手写 schema</b>：模型看到的约束必须 ≡ 实际校验的约束。
 * 手写 schema 迟早和 DTO 漂移，表现是"模型以为能传 300 字符标题，实际 400"。
 *
 * <p><b>为什么不引 {@code jackson-module-jsonSchema}</b>：只需要 100 行左右的子集，
 * 而且引进来会多一套版本耦合（§7.10 零新增后端依赖）。
 *
 * <p>参数绑定走 {@code ObjectMapper.convertValue} + Spring {@code Validator.validate}，
 * AI 传的参数享受与 HTTP 请求完全一致（同一批注解、同一个校验器）的校验规则，不写第二套。
 */
@Component
@RequiredArgsConstructor
public class ToolArgs {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    /** 从 DTO record 派生 JSON Schema（object）。 */
    public ObjectNode schemaOf(Class<? extends Record> type) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = objectMapper.createArrayNode();
        for (RecordComponent component : type.getRecordComponents()) {
            ObjectNode property = properties.putObject(component.getName());
            property.put("type", jsonType(component.getType()));
            String description = describe(component);
            if (description != null) {
                property.put("description", description);
            }
            applyConstraints(property, component, required);
        }
        if (!required.isEmpty()) {
            schema.set("required", required);
        }
        // 额外字段一律不收：模型多传一个不存在的参数时直接报错，而不是静默忽略
        schema.put("additionalProperties", false);
        return schema;
    }

    /**
     * 把模型给的参数绑定到 DTO 并执行 {@code @Valid} 校验。
     * 校验不过抛 {@link BizException}，消息里带字段名与中文规则——这条消息会回灌给模型让它重试。
     */
    public <T> T bind(JsonNode args, Class<T> type) {
        JsonNode node = args == null || args.isNull() || !args.isObject()
                ? objectMapper.createObjectNode() : args;
        T value;
        try {
            value = objectMapper.convertValue(node, type);
        } catch (IllegalArgumentException e) {
            throw new BizException("参数格式不正确：" + rootMessage(e));
        }
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("；"));
            throw new BizException("参数校验失败：" + detail);
        }
        return value;
    }

    /** 校验并返回一个空参数对象（工具没有入参时用）。 */
    public <T> T bindEmpty(Class<T> type) {
        return bind(objectMapper.createObjectNode(), type);
    }

    private void applyConstraints(ObjectNode property, RecordComponent component, ArrayNode required) {
        String description = null;
        boolean array = "array".equals(property.path("type").asText());
        for (Annotation annotation : component.getAnnotations()) {
            if (annotation instanceof NotNull || annotation instanceof NotBlank
                    || annotation instanceof NotEmpty) {
                required.add(component.getName());
            }
            if (annotation instanceof Size size) {
                if (array) {
                    if (size.max() < Integer.MAX_VALUE) {
                        property.put("maxItems", size.max());
                    }
                    if (size.min() > 0) {
                        property.put("minItems", size.min());
                    }
                } else {
                    if (size.max() < Integer.MAX_VALUE) {
                        property.put("maxLength", size.max());
                    }
                    if (size.min() > 0) {
                        property.put("minLength", size.min());
                    }
                }
            }
            if (annotation instanceof Min min) {
                property.put("minimum", min.value());
            }
            if (annotation instanceof Max max) {
                property.put("maximum", max.value());
            }
            if (annotation instanceof Positive) {
                property.put("exclusiveMinimum", 0);
            }
            if (annotation instanceof PositiveOrZero) {
                property.put("minimum", 0);
            }
            if (annotation instanceof DecimalMin decimalMin) {
                property.put("minimum", new BigDecimal(decimalMin.value()));
                if (!decimalMin.inclusive()) {
                    property.put("exclusiveMinimum", new BigDecimal(decimalMin.value()));
                    property.remove("minimum");
                }
            }
            if (annotation instanceof DecimalMax decimalMax) {
                property.put("maximum", new BigDecimal(decimalMax.value()));
            }
            if (annotation instanceof Pattern pattern) {
                property.put("pattern", pattern.regexp());
            }
        }
        if (description != null) {
            property.put("description", description);
        }
    }

    /** 注解里的中文提示就是最好的参数说明（模型看到的约束 ≡ 实际校验的约束）。 */
    private static String describe(RecordComponent component) {
        for (Annotation annotation : component.getAnnotations()) {
            String message = messageOf(annotation);
            if (message != null && !message.isBlank()) {
                return message;
            }
        }
        return null;
    }

    private static String messageOf(Annotation annotation) {
        try {
            return (String) annotation.annotationType().getMethod("message").invoke(annotation);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static String jsonType(Class<?> type) {
        if (type == String.class || type == LocalDateTime.class) {
            return "string";
        }
        if (type == int.class || type == long.class || type == Integer.class || type == Long.class) {
            return "integer";
        }
        if (type == BigDecimal.class || type == double.class || type == Double.class
                || type == float.class || type == Float.class) {
            return "number";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        if (Collection.class.isAssignableFrom(type)) {
            return "array";
        }
        if (Map.class.isAssignableFrom(type)) {
            return "object";
        }
        return "object";
    }

    private static String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        return message.length() > 200 ? message.substring(0, 200) + "..." : message;
    }
}
