package org.rail.userservice.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * MD5加密工具类（不可逆哈希算法）
 * 注意：MD5已存在安全隐患（易被碰撞攻击），生产环境建议结合盐值（salt）使用，或替换为SHA-256等更安全的算法
 */
public class MD5Util {

    /**
     * MD5加密（返回32位小写十六进制字符串）
     * @param content 待加密的内容
     * @return 加密后的MD5字符串
     */
    public static String encrypt(String content) {
        try {
            // 1. 获取MD5算法的MessageDigest实例
            MessageDigest md = MessageDigest.getInstance("MD5");

            // 2. 将内容转换为字节数组（指定UTF-8编码，避免不同环境编码差异）
            byte[] inputBytes = content.getBytes(StandardCharsets.UTF_8);

            // 3. 计算哈希值（MD5加密核心步骤）
            byte[] hashBytes = md.digest(inputBytes);

            // 4. 将字节数组转换为十六进制字符串（便于阅读和存储）
            StringBuilder hexBuilder = new StringBuilder();
            for (byte b : hashBytes) {
                // 将字节转换为十六进制（& 0xFF确保为正数，避免负数转换问题）
                String hex = Integer.toHexString(b & 0xFF);
                // 不足两位的补0（MD5结果为128位，转换为32位十六进制，每位对应4位二进制）
                if (hex.length() == 1) {
                    hexBuilder.append("0");
                }
                hexBuilder.append(hex);
            }
            return hexBuilder.toString();

        } catch (NoSuchAlgorithmException e) {
            // MD5是Java标准算法，此异常理论上不会发生
            throw new RuntimeException("MD5加密失败：" + e.getMessage(), e);
        }
    }
}