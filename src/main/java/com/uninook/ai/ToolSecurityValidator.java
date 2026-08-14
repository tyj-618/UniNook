package com.uninook.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uninook.common.ErrorCode;
import com.uninook.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ToolSecurityValidator {

    private static final Set<String> SERVER_OWNED_PARAMETERS = Set.of(
            "user_id", "userId", "campus_id", "campusId", "school_id", "schoolId", "allowedSchoolIds", "scope");

    private final ObjectMapper objectMapper;

    public ToolSecurityValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> validateAndSecure(ToolDefinition definition, String argumentsJson,
                                                 ToolExecutionContext context) {
        Map<String, Object> arguments = parse(argumentsJson);
        SERVER_OWNED_PARAMETERS.forEach(arguments::remove);
        Map<String, Object> properties = map(definition.parameters().get("properties"));
        List<String> required = listOfStrings(definition.parameters().get("required"));
        for (String name : required) {
            if (!arguments.containsKey(name) || arguments.get(name) == null || String.valueOf(arguments.get(name)).isBlank()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "工具参数缺少必填字段：" + name);
            }
        }
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            Object value = arguments.get(entry.getKey());
            if (value == null) {
                continue;
            }
            Map<String, Object> schema = map(entry.getValue());
            validateType(entry.getKey(), value, (String) schema.get("type"));
            validateEnum(entry.getKey(), value, schema.get("enum"));
        }
        arguments.put("userId", context.userId());
        arguments.put("schoolId", context.userProfile().schoolId());
        arguments.put("campusId", context.userProfile().schoolId());
        arguments.put("scope", context.scope().name());
        return Map.copyOf(arguments);
    }

    private Map<String, Object> parse(String argumentsJson) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(argumentsJson == null ? "{}" : argumentsJson,
                    new TypeReference<>() { });
            return new LinkedHashMap<>(parsed);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "工具参数不是有效 JSON");
        }
    }

    private void validateType(String name, Object value, String type) {
        boolean matches = switch (type == null ? "" : type) {
            case "string" -> value instanceof String;
            case "number", "integer" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            default -> true;
        };
        if (!matches) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "工具参数类型不正确：" + name);
        }
    }

    private void validateEnum(String name, Object value, Object enumValues) {
        if (enumValues instanceof List<?> values && !values.contains(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "工具参数取值不支持：" + name);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private List<String> listOfStrings(Object value) {
        return value instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }
}
