package groovymvc.security;

import groovymvc.util.Encode;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Based on Spring Security's MessageDigestPasswordEncoder.
 */
public class PasswordEncoder {
    private static final String ALGORITHM = "SHA-512";
    private static int ITERATIONS = 1000;

    /**
     * Encodes the rawPass using a MessageDigest.
     * If a salt is specified it will be merged with the password before encoding.
     *
     * @param rawPass The plain text password
     * @param salt    The salt to sprinkle
     * @return Hex string of password digest (or base64 encoded string if encodeHashAsBase64 is enabled.
     */
    public static String encode(String rawPass, Object salt) {
        String saltedPass = mergePasswordAndSalt(rawPass, salt, false);

        MessageDigest messageDigest = getMessageDigest();
        byte[] digest;

        try {
            digest = messageDigest.digest(saltedPass.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 not supported!");
        }

        // "stretch" the encoded value if configured to do so
        for (int i = 1; i < ITERATIONS; i++) {
            digest = messageDigest.digest(digest);
        }

        return new String(Encode.hex(digest));
    }

    /**
     * Takes a previously encoded password and compares it with a rawpassword after mixing in the salt and
     * encoding that value
     *
     * @param encPass previously encoded password
     * @param rawPass plain text password
     * @param salt    salt to mix into password
     * @return true or false
     */
    public static boolean isPasswordValid(String encPass, String rawPass, Object salt) {
        notNull(encPass, "encPass");
        notNull(rawPass, "rawPass");
        notNull(salt, "salt");
        String pass1 = "" + encPass;
        String pass2 = encode(rawPass, salt);

        return equals(pass1, pass2);
    }

    /**
     * Get a MessageDigest instance for the given ALGORITHM.
     * Throws an IllegalArgumentException if <i>ALGORITHM</i> is unknown
     *
     * @return MessageDigest instance
     * @throws IllegalArgumentException if NoSuchAlgorithmException is thrown
     */
    private static MessageDigest getMessageDigest() throws IllegalArgumentException {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("No such algorithm [" + ALGORITHM + "]");
        }
    }

    /**
     * Constant time comparison to prevent against timing attacks.
     */
    private static boolean equals(String expected, String actual) {
        byte[] expectedBytes = bytesUtf8(expected);
        byte[] actualBytes = bytesUtf8(actual);
        int expectedLength = expectedBytes == null ? -1 : expectedBytes.length;
        int actualLength = actualBytes == null ? -1 : actualBytes.length;
        if (expectedLength != actualLength) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < expectedLength; i++) {
            result |= expectedBytes[i] ^ actualBytes[i];
        }
        return result == 0;
    }

    private static byte[] bytesUtf8(String s) {
        try {
            return s.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Could not get bytes in UTF-8 format", e);
        }
    }

    /**
     * Used by subclasses to generate a merged password and salt <code>String</code>.<P>The generated password
     * will be in the form of <code>password{salt}</code>.</p>
     * <p>A <code>null</code> can be passed to either method, and will be handled correctly. If the
     * <code>salt</code> is <code>null</code> or empty, the resulting generated password will simply be the passed
     * <code>password</code>. The <code>toString</code> method of the <code>salt</code> will be used to represent the
     * salt.</p>
     *
     * @param password the password to be used (can be <code>null</code>)
     * @param salt     the salt to be used (can be <code>null</code>)
     * @param strict   ensures salt doesn't contain the delimiters
     * @return a merged password and salt <code>String</code>
     * @throws IllegalArgumentException if the salt contains '{' or '}' characters.
     */
    private static String mergePasswordAndSalt(String password, Object salt, boolean strict) {
        if (password == null) {
            password = "";
        }

        if (strict && (salt != null)) {
            if ((salt.toString().lastIndexOf("{") != -1) || (salt.toString().lastIndexOf("}") != -1)) {
                throw new IllegalArgumentException("Cannot use { or } in salt.toString()");
            }
        }

        if ((salt == null) || "".equals(salt)) {
            return password;
        } else {
            return password + "{" + salt.toString() + "}";
        }
    }

    private static void notNull(Object o, String name) {
        if (o == null)
            throw new IllegalArgumentException(name + " must not be null");
    }
}
