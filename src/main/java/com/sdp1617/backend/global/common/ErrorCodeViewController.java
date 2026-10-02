package com.sdp1617.backend.global.common;

import com.sdp1617.backend.global.error.ErrorCode;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 프론트 연동용 에러 코드 목록 페이지. ErrorCode enum을 그대로 읽어 렌더링하므로
 * 코드가 추가·변경되면 별도 문서 수정 없이 페이지도 최신 상태가 된다.
 */
@Hidden
@Controller
public class ErrorCodeViewController {

    @GetMapping("/docs/error-codes")
    public String errorCodesPage(Model model) {
        Map<String, List<ErrorCode>> groupedErrorCodes = Arrays.stream(ErrorCode.values())
                .sorted(Comparator.comparing(ErrorCode::getCode))
                .collect(Collectors.groupingBy(
                        errorCode -> extractDomain(errorCode.getCode()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        model.addAttribute("groupedErrorCodes", groupedErrorCodes);
        model.addAttribute("totalCount", ErrorCode.values().length);
        return "docs/error-codes";
    }

    /** 네이밍 가이드(DOMAIN_nnn)의 접두사로 묶는다. 접두사가 없으면 UNCLASSIFIED. */
    private String extractDomain(String code) {
        int delimiterIndex = code.indexOf('_');
        if (delimiterIndex <= 0) {
            return "UNCLASSIFIED";
        }
        return code.substring(0, delimiterIndex);
    }
}
