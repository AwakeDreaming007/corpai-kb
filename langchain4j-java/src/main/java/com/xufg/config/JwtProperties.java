package com.xufg.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置属性。
 */
@Data
@Component
@ConfigurationProperties(prefix = "kb.jwt")
public class JwtProperties {

    /** JWT 签名密钥， 必须通过环境变量注入。 */
    private String secret;

    /** 令牌有效期， 单位分钟。 */
    private Integer expireMinutes;
}
