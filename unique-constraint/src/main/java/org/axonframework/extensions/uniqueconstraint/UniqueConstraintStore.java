package org.axonframework.extensions.uniqueconstraint;

/**
 * Component responsible for claiming and releasing unique constraint keys.
 *
 * @author Mitchell Herrijgers
 * @since 0.0.1
 */
public interface UniqueConstraintStore {

    /**
     * Releases the claim on a value's key in the store. Will throw a {@link UniqueConstraintClaimException} if
     * the claim is not taken by the current owner.
     *
     * @param constraintName The name of the constraint
     * @param constraintKey  The key of the constraint, its unique value
     * @param owner          The current owner unclaiming the constraint
     */
    void releaseClaimValue(String constraintName, String constraintKey, String owner);

    /**
     * Claim the value in the store. Will throw a {@link UniqueConstraintClaimException} if the claim is not free
     * to take.
     *
     * @param constraintName The name of the constraint
     * @param constraintKey  The key of the constraint, its unique value
     * @param owner          The current owner unclaiming the constraint
     */
    void checkAndClaimValue(String constraintName, String constraintKey, String owner);
}
