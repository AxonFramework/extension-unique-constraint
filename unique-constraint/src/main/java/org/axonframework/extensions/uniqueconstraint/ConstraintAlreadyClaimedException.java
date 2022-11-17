package org.axonframework.extensions.uniqueconstraint;

import org.axonframework.common.AxonNonTransientException;

public class ConstraintAlreadyClaimedException extends AxonNonTransientException {

    public ConstraintAlreadyClaimedException(String message) {
        super(message);
    }
}
