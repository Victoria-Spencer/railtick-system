package org.rail.common.core.util.security;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 加密通用工具类
 * 提供字符串脱敏处理、数据哈希计算等通用加密操作
 */
public class CryptoUtils {

	/**
	 * 字符串脱敏处理（隐私数据打码）
	 * 规则：仅展示字符串前4位和后4位，中间用*替代
	 * 若字符串为空 或 长度≤12，直接返回16个*
	 * @param original 待脱敏的原始字符串
	 * @return 脱敏后的字符串
	 */
	public static String mask(String original) {
		if (StringUtils.isBlank(original) || original.length() <= 12) {
			return "*".repeat(16);
		}

		int prefix_length = 4;
		String start = original.substring(0, prefix_length);
		String end = original.substring(original.length() - prefix_length);
		int middleLength = original.length() - prefix_length * 2;

		return start + "*".repeat(middleLength) + end;
	}

	/**
	 * 使用SHA-512算法生成字符串的哈希值
	 * @param original 待计算哈希的原始字符串
	 * @return 十六进制格式的哈希值
	 * @throws RuntimeException 当SHA-512算法不可用时抛出
	 */
	public static String hashWithSha512(String original) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-512");
			byte[] encodedHash = digest.digest(original.getBytes(StandardCharsets.UTF_8));
			return bytesToHex(encodedHash);
		}
		catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("SHA-512哈希计算失败", e);
		}
	}

	/**
	 * 将字节数组转换为十六进制字符串
	 * @param hash 待转换的字节数组
	 * @return 十六进制格式的字符串
	 */
	private static String bytesToHex(byte[] hash) {
		StringBuilder hexString = new StringBuilder(2 * hash.length);
		for (byte b : hash) {
			String hex = Integer.toHexString(0xff & b);
			if (hex.length() == 1) {
				hexString.append('0');
			}
			hexString.append(hex);
		}
		return hexString.toString();
	}

}