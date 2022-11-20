package org.axonframework.extensions.uniqueconstraint.eventstore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Creates a SHA-256 hash out of the constraint's name and value, to guarantee a unique value that is not reversible.
 * It's safe to use this provider together with personal data, as SHA-256 is a safe algorithm.
 *
 * @since 0.0.1
 * @author Mitchell Herrijgers
 */
public class Sha256ConstraintKeyProvider implements ConstraintKeyProvider {

    private final MessageDigest digest;

    /**
     * Creates the provider, looking up the SHA-256 algorithm in the JVM. The SHA-256 algorithm is present in all
     * JDK's since version 8.
     */
    public Sha256ConstraintKeyProvider() {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not initialize the Sha256ConstraintValueProvider!", e);
        }
    }

    @Override
    public String determineValue(String constraintName, Object value) {
        if (digest == null) {
            return constraintName + "__" + value.hashCode();
        }
        String toHash = constraintName + "__" + value.toString();

        byte[] byteDigest = digest.digest(toHash.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(byteDigest).toUpperCase();
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    /**
     * Thanks to <a href="https://stackoverflow.com/questions/9655181/how-to-convert-a-byte-array-to-a-hex-string-in-java">this StackOverflow post</a>
     * for removing the dependency to JAXB.
     */
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
}
