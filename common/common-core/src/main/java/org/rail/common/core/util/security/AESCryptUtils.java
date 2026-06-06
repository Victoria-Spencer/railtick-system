package org.rail.common.core.util.security;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * AES 对称加密解密工具类
 */
public class AESCryptUtils {

	/** AES 加密算法：ECB模式 + PKCS5填充方式 */
	private static final String AES_CIPHER = "AES/ECB/PKCS5Padding";

	/** AES 固定加密密钥 */
	public static final String AES_KEY = "agentscope_5qAI8#nO-d@xK7$kdF+Dh";

	/**
	 * 使用 AES 算法加密字符串
	 * @param original 待加密的原始字符串
	 * @return 经过 Base64 编码的加密字符串
	 */
	public static String encrypt(String original) {
		try {
			byte[] raw = AES_KEY.getBytes();
			SecretKeySpec secKey = new SecretKeySpec(raw, "AES");
			Cipher cipher = Cipher.getInstance(AES_CIPHER);
			cipher.init(Cipher.ENCRYPT_MODE, secKey);
			byte[] byte_content = original.getBytes(StandardCharsets.UTF_8);
			byte[] encode_content = cipher.doFinal(byte_content);
			return org.apache.commons.codec.binary.Base64.encodeBase64String(encode_content);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 解密 AES 加密后的字符串
	 * @param encrypted 经过 Base64 编码的加密字符串
	 * @return 解密后的原始字符串
	 */
	public static String decrypt(String encrypted) {
		try {
			byte[] raw = AES_KEY.getBytes();
			SecretKeySpec secKey = new SecretKeySpec(raw, "AES");
			Cipher cipher = Cipher.getInstance(AES_CIPHER);
			cipher.init(Cipher.DECRYPT_MODE, secKey);
			byte[] encode_content = org.apache.commons.codec.binary.Base64.decodeBase64(encrypted);
			byte[] byte_content = cipher.doFinal(encode_content);
			return new String(byte_content, StandardCharsets.UTF_8);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 加密测试方法
	 */
	public static void main(String[] args) {
		String encrypted = encrypt("sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
		System.out.println(encrypted);
	}

}