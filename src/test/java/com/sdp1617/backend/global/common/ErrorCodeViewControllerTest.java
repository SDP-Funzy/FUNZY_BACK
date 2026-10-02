package com.sdp1617.backend.global.common;

import com.sdp1617.backend.global.error.ErrorCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorCodeViewControllerTest {

    private final ErrorCodeViewController controller = new ErrorCodeViewController();

    @Test
    @SuppressWarnings("unchecked")
    void 에러_코드를_코드_접두사별로_빠짐없이_묶어_정렬한다() {
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.errorCodesPage(model);

        assertEquals("docs/error-codes", view);
        assertEquals(ErrorCode.values().length, model.get("totalCount"));

        Map<String, List<ErrorCode>> grouped = (Map<String, List<ErrorCode>>) model.get("groupedErrorCodes");
        assertEquals(ErrorCode.values().length, grouped.values().stream().mapToInt(List::size).sum());
        grouped.forEach((domain, codes) -> {
            codes.forEach(code -> assertTrue(code.getCode().startsWith(domain + "_")));
            for (int i = 1; i < codes.size(); i++) {
                assertTrue(codes.get(i - 1).getCode().compareTo(codes.get(i).getCode()) < 0);
            }
        });
        assertTrue(grouped.containsKey("AUTH"));
        assertTrue(grouped.containsKey("COMMON"));
    }
}
