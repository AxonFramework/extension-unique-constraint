package org.axonframework.extensions.uniqueconstraint;

import org.axonframework.common.AxonNonTransientException;

/**
 * Indicates that a claim was owned by another node and an action, such releasing a claim, has failed.
 *
 * @author Mitchell Herrijgers
 * @since 0.0.1
 */
public class UniqueConstraintClaimException extends AxonNonTransientException {

    /**
     * Creates the {@link UniqueConstraintClaimException}
     *
     * @param message The message of the exception
     */
    public UniqueConstraintClaimException(String message) {
        super(message);
    }
}
