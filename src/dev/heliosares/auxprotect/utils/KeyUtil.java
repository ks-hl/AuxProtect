package dev.heliosares.auxprotect.utils;

import javax.crypto.Cipher;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class KeyUtil {

    private static final String RSA = "RSA";

    public static String do_RSADecryption(byte[] cipherText, Key key) throws Exception {
        Cipher cipher = Cipher.getInstance(RSA);

        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] result = cipher.doFinal(cipherText);

        return new String(result);
    }

    private static final long[] BLACKLIST = new long[]{};
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
    private final String keyholder;

    public KeyUtil(String key) {
        boolean isValid = false;
        boolean isBlacklisted = false;
        boolean isPrivate = false;
        boolean isMalformed = false;
        String keyholder = null;
        long gen = -1;
        if (key != null && key.length() > 10) {
            out:
            try {
                String[] parts = key.split("\\.");
                keyholder = parts[0];
                key = parts[1];
                key = do_RSADecryption(decode(key), PUBLIC_KEY);
                if (!key.endsWith("." + keyholder)) {
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
                isPrivate = keyholder.matches("private(_.*)?");
            } catch (Exception e) {
                e.printStackTrace();
                isMalformed = true;
                isValid = false;
            }
        }
        this.isBlacklisted = isBlacklisted;
        this.isPrivate = isPrivate;
        this.isValid = isValid;
        this.isMalformed = isMalformed;
        this.keyholder = keyholder;
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

    public String getKeyHolder() {
        return keyholder;
    }

    private static byte[] decode(String str) {
        return Base64.getDecoder().decode(str);
    }
}
