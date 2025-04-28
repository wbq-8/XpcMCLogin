package xyz.xpcmc.login;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class PasswordHasher {
    private static final String ALGORITHM = "SHA-512";
    private static final int SALT_LENGTH = 16;
    private static final int ITERATIONS = 10000;

    public static String hashPassword(String password) {
        try {
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[SALT_LENGTH];
            random.nextBytes(salt);

            MessageDigest md = MessageDigest.getInstance(ALGORITHM);
            md.update(salt);
            byte[] hashedPassword = md.digest(password.getBytes());

            for (int i = 0; i < ITERATIONS - 1; i++) {
                md.reset();
                hashedPassword = md.digest(hashedPassword);
            }

            return Base64.getEncoder().encodeToString(salt) + "$" +
                    Base64.getEncoder().encodeToString(hashedPassword);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to hash password", e);
        }
    }

    public static boolean verifyPassword(String password, String storedHash) {
        try {
            String[] parts = storedHash.split("\\$");
            if (parts.length != 2) return false;

            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] storedPassword = Base64.getDecoder().decode(parts[1]);

            MessageDigest md = MessageDigest.getInstance(ALGORITHM);
            md.update(salt);
            byte[] hashedPassword = md.digest(password.getBytes());

            for (int i = 0; i < ITERATIONS - 1; i++) {
                md.reset();
                hashedPassword = md.digest(hashedPassword);
            }

            return MessageDigest.isEqual(hashedPassword, storedPassword);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to verify password", e);
        }
    }
}