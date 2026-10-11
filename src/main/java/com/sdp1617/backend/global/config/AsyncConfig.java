package com.sdp1617.backend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** 커밋 후 웹 푸시 발송(#89)처럼 요청을 기다리게 하지 않을 작업을 별도 스레드에서 실행한다. */
@Configuration
@EnableAsync
public class AsyncConfig {
}
