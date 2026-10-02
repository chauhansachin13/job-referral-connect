package com.referralconnect.service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Salted PBKDF2-HMAC-SHA256 password hashing using only the JDK. */
public final class PasswordHasher {

    private PasswordHasher() {
    }

    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Hashed(String hash, String salt) {
    }

    public static Hashed hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return new Hashed(Base64.getEncoder().encodeToString(derive(password, salt)),
                Base64.getEncoder().encodeToString(salt));
    }

    public static boolean verify(String password, String hash, String salt) {
        if (hash == null || salt == null || hash.isEmpty() || salt.isEmpty()) {
            return false;
        }
        byte[] expected = Base64.getDecoder().decode(hash);
        byte[] actual = derive(password, Base64.getDecoder().decode(salt));
        return MessageDigest.isEqual(expected, actual);
    }

    private static byte[] derive(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is not available in this JDK", e);
        } finally {
            spec.clearPassword();
        }
    }
}
