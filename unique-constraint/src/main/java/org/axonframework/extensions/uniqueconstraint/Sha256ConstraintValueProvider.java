package org.axonframework.extensions.uniqueconstraint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.xml.bind.DatatypeConverter;

/**
 * Creates a SHA-256 hash out of the constraint's name and value, to guarantee a unique value that is not reversible.
 * It's safe to use this provider together with personal data, as SHA-256 is a safe algorithm.
 *
 * @since 0.0.1
 * @author Mitchell Herrijgers
 */
public class Sha256ConstraintValueProvider implements ConstraintValueProvider {

    private final MessageDigest digest;

    /**
     * Creates the provider, looking up the SHA-256 algorithm in the JVM. The SHA-256 algorithm is present in all
     * JDK's since version 8.
     */
    public Sha256ConstraintValueProvider() {
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
        return DatatypeConverter.printHexBinary(byteDigest).toUpperCase();
    }
}
