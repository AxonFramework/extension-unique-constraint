package org.axonframework.extensions.uniqueconstraint;

import org.axonframework.common.AxonNonTransientException;

/**
 * Indicates that a claim was already made by another aggregate.
 * This is a functional business error and should be handled as such.
 *
 * @since 0.0.1
 * @author Mitchell Herrijgers
 */
public class ConstraintAlreadyClaimedException extends AxonNonTransientException {

    /**
     * Creates the {@link ConstraintAlreadyClaimedException}
     * @param message The message of the exception
     */
    public ConstraintAlreadyClaimedException(String message) {
        super(message);
    }
}
