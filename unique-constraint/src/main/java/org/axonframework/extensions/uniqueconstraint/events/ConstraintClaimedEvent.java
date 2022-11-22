package org.axonframework.extensions.uniqueconstraint.events;

/**
 * Event indicating a constraint was claimed. If this is the last event in the store, the owner field indicates the
 * current owner. Other aggregates are not allowed to claim the value.
 *
 * @author Mitchell Herrijgers
 * @since 0.0.1
 */
public class ConstraintClaimedEvent {

    private String constraintName;
    private String constraintKey;
    private String owner;

    private ConstraintClaimedEvent() {
    }

    /**
     * Creates a new claimed event, indicating that the {@code constraintValue} is claimed by the {@code owner}.
     *
     * @param constraintName  The constraints' name.
     * @param constraintKey The constraints' unique value that was claimed.
     * @param owner           The new owner of the constraint, which is the aggregate identifier.
     */
    public ConstraintClaimedEvent(String constraintName, String constraintKey, String owner) {
        this.constraintName = constraintName;
        this.constraintKey = constraintKey;
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
    public String getConstraintKey() {
        return constraintKey;
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
