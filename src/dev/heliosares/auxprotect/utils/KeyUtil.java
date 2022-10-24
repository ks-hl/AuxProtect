package dev.heliosares.auxprotect.utils;

import java.util.Base64;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.Cipher;

public class KeyUtil {

	private static final String RSA = "RSA";

	public static String do_RSADecryption(byte[] cipherText, Key key) throws Exception {
		Cipher cipher = Cipher.getInstance(RSA);

		cipher.init(Cipher.DECRYPT_MODE, key);
		byte[] result = cipher.doFinal(cipherText);

		return new String(result);
	}

	private static final long[] BLACKLIST = new long[] {};
	private static final PublicKey PUBLIC_KEY;
	static {
		PublicKey key = null;
		try {
			key = KeyFactory.getInstance(RSA).generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(
					"MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAILcEjoZ4U1e3YIBTcgbSdPjowBlXTUe23eB9rcf4DUS3WY5AZGCoYOaGsXJGgVduNf8Bk8VfbsN/gcYd2HwCzsCAwEAAQ==")));
		} catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		PUBLIC_KEY = key;
	}
	private final boolean isValid;
	private final boolean isBlacklisted;
	private final boolean isMalformed;
	private final boolean isPrivate;

	public KeyUtil(String key) {
		boolean isValid = false;
		boolean isBlacklisted = false;
		boolean isPrivate = false;
		boolean isMalformed = false;
		long gen = -1;
		out: try {
			String[] parts = key.split("\\.");
			String name = parts[0];
			key = parts[1];
			key = do_RSADecryption(decode(key), PUBLIC_KEY);
			if (!key.endsWith("." + name)) {
				isValid = false;
				isMalformed = true;
				break out;
			}
			gen = Long.parseLong(key.substring(0, key.indexOf(".")));
			for (long other : BLACKLIST) {
				if (other == gen) {
					isBlacklisted = true;
					isValid = false;
					break out;
				}
			}
			isValid = gen <= System.currentTimeMillis() && gen > 1577854800000L;
			isPrivate = name.matches("private(_.*)?");
		} catch (Exception e) {
			e.printStackTrace();
			isMalformed = true;
			isValid = false;
		}
		this.isBlacklisted = isBlacklisted;
		this.isPrivate = isPrivate;
		this.isValid = isValid;
		this.isMalformed = isMalformed;
	}

	public boolean isValid() {
		if (isBlacklisted || isMalformed) {
			return false;
		}
		return isValid;
	}

	public boolean isBlacklisted() {
		return isBlacklisted;
	}

	public boolean isPrivate() {
		if (!isValid()) {
			return false;
		}
		return isPrivate;
	}

	public boolean isMalformed() {
		return isMalformed;
	}

	private static byte[] decode(String str) {
		return Base64.getDecoder().decode(str);
	}
}
