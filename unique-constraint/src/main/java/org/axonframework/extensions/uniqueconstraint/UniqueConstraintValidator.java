package org.axonframework.extensions.uniqueconstraint;

import org.axonframework.common.BuilderUtils;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.InterceptorChain;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Component which valides unique constraints against the {@link EventStore}. Will check values before and after command
 * execution and when will try to (un)claim values when appropriate. When claims are already taken, a
 * {@link UniqueConstraintClaimException} is thrown.
 *
 * @author Mitchell Herrijgers
 * @since 0.0.1
 */
public class UniqueConstraintValidator {

    private final UniqueConstraintStore constraintStore;

    /**
     * Creates a new {@link UniqueConstraintValidator} with the builder's configuration.
     *
     * @param builder The builder to use.
     */
    protected UniqueConstraintValidator(Builder builder) {
        builder.validate();
        this.constraintStore = builder.constraintStore;
    }

    /**
     * Creates a new {@link ValidatorInstance} which can be used to build further constraints.
     *
     * @param aggregateIdSupplier Supplier of the aggregate id value
     * @return The {@link ValidatorInstance}, for fluent interfacing
     */
    public ValidatorInstance forAggregate(Supplier<Object> aggregateIdSupplier) {
        return new ValidatorInstance(aggregateIdSupplier);
    }

    /**
     * Creates a new builder to construct a new {@link UniqueConstraintValidator}.
     * <p>
     * Requires the {@link UniqueConstraintStore} to be configured.
     *
     * @return A builder suitable to construct a new {@link UniqueConstraintValidator}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The {@link ValidatorInstance} executes all checks for the unique constraints of the aggregate. You can create one
     * using the {@link #forAggregate(Supplier)} function, and build it further. Executed immediately (without previous
     * values) when calling {@link #check()} .
     * <p>
     * Recommended usage is by wrapping the command handling method using the
     * {@link #checkForInterceptor(InterceptorChain)} method. This will use change detection and only execute checks and
     * claims on changes.
     */
    public class ValidatorInstance {

        private final Supplier<Object> aggregateIdSupplier;
        private final Map<String, Supplier<Object>> constraintMap = new HashMap<>();

        private ValidatorInstance(Supplier<Object> aggregateIdSupplier) {
            this.aggregateIdSupplier = aggregateIdSupplier;
        }

        /**
         * Adds a constraint to check after execution of the command.
         *
         * @param constraintName The name of the constraint
         * @param supplier       The value supplier of the constraint.
         * @return The {@link ValidatorInstance}, for fluent interfacing.
         */
        public ValidatorInstance addConstraint(String constraintName, Supplier<Object> supplier) {
            constraintMap.put(constraintName, supplier);
            return this;
        }

        /**
         * Will store the current values, execute the interceptorChain provided and compare the values. If values have
         * changed, will try to claim or unclaim the constraints.
         *
         * @param interceptorChain The {@link InterceptorChain} provided by the
         *                         {@link org.axonframework.modelling.command.CommandHandlerInterceptor} annotated
         *                         method.
         */
        public Object checkForInterceptor(InterceptorChain interceptorChain) throws Exception {
            Map<String, Object> valuesBefore = getValues();
            Object proceed = interceptorChain.proceed();
            Map<String, Object> valuesAfter = getValues();

            constraintMap.keySet().forEach(key -> executeChecksAndClaimsForConstraint(key,
                                                                                      valuesBefore.get(key),
                                                                                      valuesAfter.get(key)));
            return proceed;
        }

        /**
         * Will check the values of the aggregate as they are now. Usable as the last statement in your constructor,
         * after all EventSourcingHandlers have been invoked.
         */
        public void check() {
            getValues().forEach((key, value) -> executeChecksAndClaimsForConstraint(key, null, value));
        }

        private Map<String, Object> getValues() {
            Map<String, Object> values = new HashMap<>();
            constraintMap.forEach((key, value) -> values.put(key, value.get()));
            return values;
        }

        private void executeChecksAndClaimsForConstraint(String constraintName, Object oldValue, Object newValue) {
            String valueBefore = getNullableValue(oldValue);
            String valueAfter = getNullableValue(newValue);

            if (valueBefore == null && valueAfter == null) {
                return;
            }
            if (valueBefore != null && (valueAfter == null || !valueAfter.equals(valueBefore))) {
                constraintStore.releaseClaimValue(constraintName, valueBefore, getAggregateId());
            }

            if (valueAfter != null && (valueBefore == null || !valueBefore.equals(valueAfter))) {
                constraintStore.checkAndClaimValue(constraintName, valueAfter, getAggregateId());
            }
        }


        private String getNullableValue(Object value) {
            if (value == null) {
                return null;
            }
            return value.toString();
        }

        private String getAggregateId() {
            return aggregateIdSupplier.get().toString();
        }
    }


    /**
     * A new builder to construct a new {@link UniqueConstraintValidator}.
     * <p>
     * Requires the {@link UniqueConstraintStore} to be configured.
     */
    public static class Builder {

        private UniqueConstraintStore constraintStore;

        /**
         * The {@link UniqueConstraintStore} to use when checking constraints. Required to be able to build the
         * builder.
         *
         * @param constraintStore The {@link UniqueConstraintStore} to use.
         * @return The builder, for fluent interfacing.
         */
        public Builder constraintStore(UniqueConstraintStore constraintStore) {
            BuilderUtils.assertNonNull(constraintStore, "constraintStore cannot be null!");
            this.constraintStore = constraintStore;
            return this;
        }

        protected void validate() {
            BuilderUtils.assertNonNull(constraintStore, "eventStore cannot be null!");
        }

        /**
         * Builds the {@link UniqueConstraintValidator} using the configuration acquired.
         *
         * @return The {@link UniqueConstraintValidator}
         */
        public UniqueConstraintValidator build() {
            return new UniqueConstraintValidator(this);
        }
    }
}
