package org.axonframework.extensions.uniqueconstraint;

/**
 * Event indicating a constraint was claimed. If this is the last event in the store, the owner field indicates the
 * current owner. Other aggregates are not allowed to claim the value.
 *
 * @author Mitchell Herrijgers
 * @since 0.0.1
 */
public class ConstraintClaimedEvent {

    private String constraintName;
    private String constraintValue;
    private String owner;

    private ConstraintClaimedEvent() {
    }

    /**
     * Creates a new claimed event, indicating that the {@code constraintValue} is claimed by the {@code owner}.
     *
     * @param constraintName  The constraints' name.
     * @param constraintValue The constraints' unique value that was claimed.
     * @param owner           The new owner of the constraint, which is the aggregate identifier.
     */
    public ConstraintClaimedEvent(String constraintName, String constraintValue, String owner) {
        this.constraintName = constraintName;
        this.constraintValue = constraintValue;
        this.owner = owner;
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

    /**
     * The constraint's owner.
     *
     * @return The constraint's value.
     */
    public String getOwner() {
        return owner;
    }
}
