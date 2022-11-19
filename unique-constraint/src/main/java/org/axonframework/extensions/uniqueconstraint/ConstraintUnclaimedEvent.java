package org.axonframework.extensions.uniqueconstraint;

/**
 * Event indicating a constraint was unclaimed. If this is the last event in the store for the constraint value, the
 * constraint value is free to claim for other aggregates.
 *
 * @author Mitchell Herrijgers
 * @since 0.0.1
 */
public class ConstraintUnclaimedEvent {

    private String constraintName;
    private String constraintValue;

    private ConstraintUnclaimedEvent() {
    }

    /**
     * Creates a new unclaimed event with all required information.
     *
     * @param constraintName  The constraints' name.
     * @param constraintValue The constraints' unique value that was unclaimed.
     */
    public ConstraintUnclaimedEvent(String constraintName, String constraintValue) {
        this.constraintName = constraintName;
        this.constraintValue = constraintValue;
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
    public String getConstraintValue() {
        return constraintValue;
    }
}
