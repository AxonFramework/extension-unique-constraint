package org.axonframework.extensions.uniqueconstraint.events;

/**
 * Event indicating a constraint was unclaimed. If this is the last event in the store for the constraint value, the
 * constraint value is free to claim for other aggregates.
 *
 * @author Mitchell Herrijgers
 * @since 0.0.1
 */
public class ConstraintReleasedEvent {

    private String constraintName;
    private String constraintKey;

    private ConstraintReleasedEvent() {
    }

    /**
     * Creates a new unclaimed event with all required information.
     *
     * @param constraintName The constraints' name.
     * @param constraintKey  The constraints' unique value that was unclaimed.
     */
    public ConstraintReleasedEvent(String constraintName, String constraintKey) {
        this.constraintName = constraintName;
        this.constraintKey = constraintKey;
    }

    /**
     * The constraint's name.
     *
     * @return The constraint's name.
     */
    public String getConstraintName() {
        return constraintName;
    }

    /**
     * The constraint's value.
     *
     * @return The constraint's value.
     */
    public String getConstraintKey() {
        return constraintKey;
    }
}
