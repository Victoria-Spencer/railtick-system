package org.rail.common.core.util.security;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.security.*;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA 加密解密工具类
 * 提供基于 RSA-OAEP 算法的加密、解密、密钥加载、密钥生成等操作
 */
public class RSACryptUtils {

	/** RSA 算法名称 */
	private static final String RSA_ALGORITHM = "RSA";

	/** RSA OAEP 加密算法（使用SHA-256哈希 + MGF1填充，安全级别更高） */
	private static final String RSA_OAEP_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

	/** 从资源文件加载的公钥 */
	private static final String publicKey;

	/** 从资源文件加载的私钥 */
	private static final String privateKey;

	/**
	 * 静态代码块：项目启动时加载 RSA 公钥和私钥
	 */
	static {
		try {
			KeyPair keyPair = RSACryptUtils.loadKeyPair(new ClassPathResource("keys/private.pem"),
					new ClassPathResource("keys/public.pem"));
			publicKey = RSACryptUtils.keyToString(keyPair.getPublic());
			privateKey = RSACryptUtils.keyToString(keyPair.getPrivate());
		}
		catch (Exception e) {
			throw new RuntimeException("RSA密钥加载失败", e);
		}
	}

	/**
	 * 使用默认公钥加密字符串
	 * @param original 待加密的原始字符串
	 * @return Base64 编码格式的加密字符串
	 */
	public static String encrypt(String original) {
		try {
			return encrypt(original, publicKey);
		}
		catch (Exception e) {
			throw new RuntimeException("RSA加密失败", e);
		}
	}

	/**
	 * 使用默认私钥解密字符串
	 * @param encrypted Base64 编码格式的加密字符串
	 * @return 解密后的原始字符串
	 */
	public static String decrypt(String encrypted) {
		try {
			return decrypt(encrypted, privateKey);
		}
		catch (Exception e) {
			throw new RuntimeException("RSA解密失败", e);
		}
	}

	/**
	 * 生成新的 RSA 密钥对
	 * @param keySize 密钥长度（单位：bit，推荐 2048/4096）
	 * @return 包含公钥和私钥的密钥对对象
	 * @throws NoSuchAlgorithmException 当 RSA 算法不可用时抛出
	 */
	public static KeyPair generateKeyPair(int keySize) throws NoSuchAlgorithmException {
		KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(RSA_ALGORITHM);
		keyPairGenerator.initialize(keySize);
		return keyPairGenerator.generateKeyPair();
	}

	/**
	 * 从 PEM 格式的资源文件中加载 RSA 密钥对
	 * @param privateKeyResource 私钥文件资源
	 * @param publicKeyResource 公钥文件资源
	 * @return 加载完成的密钥对
	 * @throws Exception 密钥文件读取/解析失败时抛出
	 */
	public static KeyPair loadKeyPair(Resource privateKeyResource, Resource publicKeyResource) throws Exception {
		// 加载私钥
		byte[] privateKeyBytes = privateKeyResource.getContentAsByteArray();
		String privateKeyPEM = new String(privateKeyBytes).replace("-----BEGIN PRIVATE KEY-----", "")
				.replace("-----END PRIVATE KEY-----", "")
				.replaceAll("\\s+", "");
		byte[] decodedPrivateKey = Base64.getDecoder().decode(privateKeyPEM);
		PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(decodedPrivateKey);
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

		// 加载公钥
		byte[] publicKeyBytes = publicKeyResource.getContentAsByteArray();
		String publicKeyPEM = new String(publicKeyBytes).replace("-----BEGIN PUBLIC KEY-----", "")
				.replace("-----END PUBLIC KEY-----", "")
				.replaceAll("\\s+", "");
		byte[] decodedPublicKey = Base64.getDecoder().decode(publicKeyPEM);
		X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(decodedPublicKey);
		PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

		return new KeyPair(publicKey, privateKey);
	}

	/**
	 * 使用指定公钥进行 RSA-OAEP 加密
	 * @param data 待加密的原始数据
	 * @param publicKey Base64 编码格式的公钥
	 * @return Base64 编码格式的加密结果
	 * @throws Exception 加密过程失败时抛出
	 */
	public static String encrypt(String data, String publicKey) throws Exception {
		byte[] publicKeyBytes = Base64.getDecoder().decode(publicKey);
		X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
		KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
		PublicKey key = keyFactory.generatePublic(keySpec);

		Cipher cipher = Cipher.getInstance(RSA_OAEP_ALGORITHM);
		OAEPParameterSpec oaepParams = new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
				PSource.PSpecified.DEFAULT);
		cipher.init(Cipher.ENCRYPT_MODE, key, oaepParams);

		byte[] encryptedBytes = cipher.doFinal(data.getBytes());
		return Base64.getEncoder().encodeToString(encryptedBytes);
	}

	/**
	 * 使用指定私钥进行 RSA-OAEP 解密
	 * @param encryptedData Base64 编码格式的加密数据
	 * @param privateKey Base64 编码格式的私钥
	 * @return 解密后的原始字符串
	 * @throws Exception 解密过程失败时抛出
	 */
	public static String decrypt(String encryptedData, String privateKey) throws Exception {
		byte[] privateKeyBytes = Base64.getDecoder().decode(privateKey);
		PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
		KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
		PrivateKey key = keyFactory.generatePrivate(keySpec);

		Cipher cipher = Cipher.getInstance(RSA_OAEP_ALGORITHM);
		OAEPParameterSpec oaepParams = new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
				PSource.PSpecified.DEFAULT);
		cipher.init(Cipher.DECRYPT_MODE, key, oaepParams);

		byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
		return new String(decryptedBytes);
	}

	/**
	 * 将密钥对象转换为 Base64 编码字符串
	 * @param key 密钥对象（公钥/私钥）
	 * @return Base64 编码格式的密钥字符串
	 */
	public static String keyToString(Key key) {
		return Base64.getEncoder().encodeToString(key.getEncoded());
	}

}