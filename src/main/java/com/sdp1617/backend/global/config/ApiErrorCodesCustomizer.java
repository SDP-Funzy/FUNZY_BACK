package com.sdp1617.backend.global.config;

import com.sdp1617.backend.global.error.ApiErrorCodes;
import com.sdp1617.backend.global.error.ErrorCode;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

/**
 * {@link ApiErrorCodes}에 적은 에러 코드를 Swagger 응답 칸에 HTTP 상태별로 넣는다.
 * 상태마다 설명에 코드·메시지 목록을, 예시에 코드별 실제 응답 본문을 둔다.
 */
@Component
public class ApiErrorCodesCustomizer implements OperationCustomizer {

    static final String ERROR_RESPONSE_SCHEMA = "#/components/schemas/" + OpenApiConfig.ERROR_RESPONSE_SCHEMA_NAME;

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        ApiErrorCodes errorCodes = handlerMethod.getMethodAnnotation(ApiErrorCodes.class);
        if (errorCodes == null) {
            return operation;
        }
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        Map<Integer, List<ErrorCode>> byStatus = Arrays.stream(errorCodes.value())
                .distinct()
                .sorted(Comparator.comparing(ErrorCode::getCode))
                .collect(Collectors.groupingBy(code -> code.getHttpStatus().value(), TreeMap::new, Collectors.toList()));
        byStatus.forEach((status, codes) ->
                operation.getResponses().addApiResponse(String.valueOf(status), toResponse(codes)));
        return operation;
    }

    private static ApiResponse toResponse(List<ErrorCode> codes) {
        MediaType mediaType = new MediaType().schema(new Schema<>().$ref(ERROR_RESPONSE_SCHEMA));
        codes.forEach(code -> mediaType.addExamples(code.getCode(), new Example()
                .summary(code.getCode() + " " + code.getMessage())
                .value(body(code))));
        String description = codes.stream()
                .map(code -> "- `" + code.getCode() + "` " + code.getMessage())
                .collect(Collectors.joining("\n"));
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE, mediaType));
    }

    private static Map<String, Object> body(ErrorCode code) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("code", code.getCode());
        body.put("message", code.getMessage());
        body.put("data", null);
        return body;
    }
}
