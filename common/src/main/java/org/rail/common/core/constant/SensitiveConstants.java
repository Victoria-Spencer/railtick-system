package org.rail.common.core.constant;

/**
 * 敏感数据加解密 常量
 */
public final class SensitiveConstants {

    private SensitiveConstants() {}

    /**
     * 敏感字段 密文前缀
     * 用于判断是否已加密，避免重复加密/重复解密
     */
    public static final String CIPHER_PREFIX = "AES:";
}