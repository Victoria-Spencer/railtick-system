package org.rail.common.core.util.security;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密码加密工具类
 * 基于 Argon2id 算法实现，提供密码加密和密码校验功能
 */
public class PasswordCryptUtils {

	/** 密码哈希迭代次数 */
	private static final int ITERATIONS = 2;

	/** 内存开销，单位：KB */
	private static final int MEMORY = 66536;

	/** 生成的哈希值长度，单位：字节 */
	private static final int HASH_LENGTH = 32;

	/** 并行线程数 */
	private static final int PARALLELISM = 1;

	/** 无填充格式的 Base64 编码器 */
	private static final Base64.Encoder b64encoder = Base64.getEncoder().withoutPadding();

	/** Base64 解码器 */
	private static final Base64.Decoder b64decoder = Base64.getDecoder();

	/**
	 * 使用 Argon2id 算法加密明文密码
	 * @param password 明文密码
	 * @return 符合Argon2id格式的加密密码字符串
	 */
	public static String encode(String password) {
		byte[] salt = genSalt();
		byte[] hash = new byte[HASH_LENGTH];
		Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).withSalt(salt)
				.withParallelism(PARALLELISM)
				.withMemoryAsKB(MEMORY)
				.withIterations(ITERATIONS)
				.build();
		Argon2BytesGenerator generator = new Argon2BytesGenerator();
		generator.init(params);
		generator.generateBytes(password.toCharArray(), hash);

		StringBuilder stringBuilder = new StringBuilder("$argon2id");
		stringBuilder.append("$v=")
				.append(params.getVersion())
				.append("$m=")
				.append(params.getMemory())
				.append(",t=")
				.append(params.getIterations())
				.append(",p=")
				.append(params.getLanes())
				.append("$")
				.append(b64encoder.encodeToString(salt))
				.append("$")
				.append(b64encoder.encodeToString(hash));
		return stringBuilder.toString();
	}

	/**
	 * 校验明文密码与加密后的密码是否匹配
	 * @param password 待校验的明文密码
	 * @param encodedPassword 数据库中存储的加密密码
	 * @return 密码匹配返回true，不匹配返回false
	 * @throws IllegalArgumentException 加密密码格式非法时抛出异常
	 */
	public static boolean match(String password, String encodedPassword) {
		String[] parts = encodedPassword.split("\\$");
		if (parts.length < 4) {
			throw new IllegalArgumentException("无效的Argon2加密哈希格式");
		}

		Argon2Parameters.Builder builder = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id);

		if (parts[2].startsWith("v=")) {
			int version = Integer.parseInt(parts[2].substring(2));
			builder.withVersion(version);
		}

		String[] perfParams = parts[3].split(",");
		if (perfParams.length != 3) {
			throw new IllegalArgumentException("性能参数数量无效");
		}

		if (!perfParams[0].startsWith("m=")) {
			throw new IllegalArgumentException("内存参数无效");
		}
		builder.withMemoryAsKB(Integer.parseInt(perfParams[0].substring(2)));
		if (!perfParams[1].startsWith("t=")) {
			throw new IllegalArgumentException("迭代次数参数无效");
		}
		builder.withIterations(Integer.parseInt(perfParams[1].substring(2)));
		if (!perfParams[2].startsWith("p=")) {
			throw new IllegalArgumentException("并行度参数无效");
		}
		builder.withParallelism(Integer.parseInt(perfParams[2].substring(2)));

		builder.withSalt(b64decoder.decode(parts[4]));

		byte[] decoded = b64decoder.decode(parts[5]);
		byte[] hashBytes = new byte[decoded.length];

		Argon2BytesGenerator generator = new Argon2BytesGenerator();
		generator.init(builder.build());
		generator.generateBytes(password.toCharArray(), hashBytes);

		int result = 0;
		for (int i = 0; i < decoded.length; i++) {
			result |= decoded[i] ^ hashBytes[i];
		}
		return result == 0;
	}

	/**
	 * 生成密码加密使用的随机盐值
	 * @return 16字节的随机盐值
	 */
	private static byte[] genSalt() {
		SecureRandom secureRandom = new SecureRandom();
		byte[] salt = new byte[16];
		secureRandom.nextBytes(salt);

		return salt;
	}

}