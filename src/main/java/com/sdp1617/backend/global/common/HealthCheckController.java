package com.sdp1617.backend.global.common;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 배포·로드밸런서 상태 확인용. 앱에서 쓰지 않으므로 Swagger에서 숨긴다. */
@Hidden
@RestController
public class HealthCheckController {

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("OK");
    }
}
