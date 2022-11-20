package org.axonframework.extensions.uniqueconstraint;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation indicating that the annotated field or method should be unique across all instances of this aggregate.
 * If the constraint's value is owned by another aggregate, the command handling will end in an
 * {@link UniqueConstraintClaimException}.
 *
 * @author Mitchell Herrijgers
 * @since 0.0.1
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AggregateUniqueConstraint {

    /**
     * The name of the constraint. Will be used as the aggregate type under which the events are stored.
     *
     * @return The name of the constraint
     */
    String constraintName();
}
