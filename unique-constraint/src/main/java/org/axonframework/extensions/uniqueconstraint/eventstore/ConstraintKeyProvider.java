package org.axonframework.extensions.uniqueconstraint.eventstore;

/**
 * Provides a value for a constraint, based on the name of the constraint and the value of the aggregate member.
 * The combination needs to be unique together, as the result is used as aggregate identifier in the event store.
 *
 * @since 0.0.1
 * @author Mitchell Herrijgers
 */
@FunctionalInterface
public interface ConstraintKeyProvider {
    String determineValue(String constraintName, Object value);
}
