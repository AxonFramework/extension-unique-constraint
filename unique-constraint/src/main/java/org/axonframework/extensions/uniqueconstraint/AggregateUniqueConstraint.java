package org.axonframework.extensions.uniqueconstraint;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation indicating that the annotated field or method should be unique across all instances of this aggregate.
 * Using this annotation requires the following of your aggregate::
 * <ul>
 *     <li>The aggregate should implement {@link ConstraintCheckingAggregate}.</li>
 *     <li>There should be no constructors that handle messages. Use regular methods with {@code @CreationPolicy(ALWAYS)} instead./li>
 *     <li>A field or method should be annotated with {@code @AggregateIdentifier}. This will be used as the id for owning the claim.</li>
 * </ul>
 *
 * @since 0.0.1
 * @author Mitchell Herrijgers
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
