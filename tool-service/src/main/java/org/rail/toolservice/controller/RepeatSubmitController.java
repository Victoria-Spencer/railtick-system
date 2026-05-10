package org.rail.toolservice.controller;

import lombok.RequiredArgsConstructor;
import org.rail.common.redis.api.ICacheClient;
import org.rail.toolservice.util.RepeatTokenUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * 防重复提交 专用接口
 * 提供全局防重Token获取能力
 */
@RestController
@RequestMapping("api/tool-service/repeat")
@RequiredArgsConstructor
public class RepeatSubmitController {

    private final ICacheClient cacheClient;

    // 防重Token过期时间
    private static final long TOKEN_EXPIRE = 16;
    private static final TimeUnit TOKEN_UNIT = TimeUnit.MINUTES;
    private static final String TOKEN_PREFIX = "COMMON:REPEAT:TOKEN:";
    private static final String UNUSED_FLAG = "UNUSED";

    /**
     * 获取防重复提交令牌
     * 前端使用：表单提交/下单前 先调用此接口获取Token
     */
    @GetMapping("/token")
    public String getRepeatSubmitToken() {
        String token = RepeatTokenUtil.generateToken();

        String tokenKey = TOKEN_PREFIX + token;
        cacheClient.set(tokenKey, UNUSED_FLAG, TOKEN_EXPIRE, TOKEN_UNIT);

        return token;
    }
}