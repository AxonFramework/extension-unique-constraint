package org.axonframework.extensions.uniqueconstraint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.xml.bind.DatatypeConverter;

public class DefaultValueProviderFunction implements ValueProviderFunction {

    private MessageDigest digest;

    public DefaultValueProviderFunction() {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            digest = null;
        }
    }

    @Override
    public String determineValue(Object value) {
        if (digest == null) {
            return "" + value.hashCode();
        }

        byte[] byteDigest = digest.digest(value.toString().getBytes(StandardCharsets.UTF_8));
        return DatatypeConverter.printHexBinary(byteDigest).toUpperCase();
    }
}
