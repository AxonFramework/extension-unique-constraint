package org.axonframework.extensions.uniqueconstraint;

import org.axonframework.common.BuilderUtils;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Component which valides unique constraints agains the {@link EventStore}. Will check values before and after command
 * execution and when will try to (un)claim values when appropriate. When claims are already taken, a
 * {@link ConstraintAlreadyClaimedException} is thrown.
 *
 * @author Mitchell Herrijgers
 * @since 0.0.1
 */
public class UniqueConstraintValidator {

    private final EventStore eventStore;
    private final ConstraintValueProvider constraintValueProvider;

    /**
     * Creates a new {@link UniqueConstraintValidator} with the builder's configuration.
     *
     * @param builder The builder to use.
     */
    protected UniqueConstraintValidator(Builder builder) {
        builder.validate();
        this.eventStore = builder.eventStore;
        this.constraintValueProvider = builder.constraintValueProvider;
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
     * Requires the {@link EventStore} to be configured. The {@link ConstraintValueProvider} defaults to a
     * {@link Sha256ConstraintValueProvider}.
     *
     * @return A builder suitable to construct a new {@link UniqueConstraintValidator}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The {@link ValidatorInstance} executes all checks for the unique constraints of the aggregate. You can create one
     * using the {@link #forAggregate(Supplier)} function, and build it further. Executed immediately (without previous
     * values) when calling {@link #checkNow()}.
     * <p>
     * Recommended usage is through a {@code @CommandHandlerInterceptor} method that uses the
     * {@link #checkForInterceptor(InterceptorChain)} method. This will use change detection and only execute on
     * changes.
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
         * @throws ConstraintAlreadyClaimedException If a constraint was already claimed.
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
         *
         * @throws ConstraintAlreadyClaimedException If a constraint was already claimed.
         */
        public void checkNow() {
            getValues().forEach((key, value) -> executeChecksAndClaimsForConstraint(key, null, value));
        }

        private Map<String, Object> getValues() {
            Map<String, Object> values = new HashMap<>();
            constraintMap.forEach((key, value) -> values.put(key, value.get()));
            return values;
        }

        private void executeChecksAndClaimsForConstraint(String constraintName, Object oldValue, Object newValue) {
            String valueBefore = getNullableValue(constraintName, oldValue);
            String valueAfter = getNullableValue(constraintName, newValue);

            if (valueBefore == null && valueAfter == null) {
                return;
            }
            if (valueBefore != null && (valueAfter == null || !valueAfter.equals(valueBefore))) {
                unclaimValue(constraintName, valueBefore);
            }

            if (valueAfter != null && (valueBefore == null || !valueBefore.equals(valueAfter))) {
                checkAndClaimValue(constraintName, valueAfter);
            }
        }

        private void unclaimValue(String constraintName, String constraintValue) {
            eventStore.lastSequenceNumberFor(constraintValue).ifPresent(sequence -> {
                GenericDomainEventMessage<ConstraintUnclaimedEvent> message = new GenericDomainEventMessage<>(
                        "Constraint" + constraintName,
                        constraintValue,
                        sequence + 1,
                        new ConstraintUnclaimedEvent(constraintName, constraintValue));
                eventStore.publish(message);
            });
        }

        private String getAggregateId() {
            return aggregateIdSupplier.get().toString();
        }

        private void checkAndClaimValue(String constraintKey, String constraintValue) {
            Optional<Long> lastSequence = eventStore.lastSequenceNumberFor(constraintValue);
            if (lastSequence.isPresent()) {
                checkClaim(constraintKey, constraintValue, lastSequence.get());
            } else {
                doClaim(constraintKey, constraintValue, 0);
            }
        }

        private void doClaim(String constraintKey, String constraintValue, long sequenceNumber) {
            GenericDomainEventMessage<ConstraintClaimedEvent> message = new GenericDomainEventMessage<>(
                    "Constraint" + constraintKey,
                    constraintValue,
                    sequenceNumber,
                    new ConstraintClaimedEvent(constraintKey,
                                               constraintValue, getAggregateId()));
            eventStore.publish(message);
        }

        private void checkClaim(String constraintName, String valueAfter, Long lastSequence) {
            DomainEventMessage<?> previousClaim = eventStore.readEvents(valueAfter, lastSequence).next();
            if (previousClaim.getPayload() instanceof ConstraintUnclaimedEvent) {
                doClaim(constraintName, valueAfter, lastSequence + 1);
                return;
            }

            if (!(previousClaim instanceof ConstraintClaimedEvent)) {
                CurrentUnitOfWork.get().onPrepareCommit(uow -> {
                    throw new IllegalArgumentException(
                            String.format("Unknown event of type %s. Can not process unique constraints.",
                                          previousClaim.getPayload().getClass().getName()));
                });
            }

            ConstraintClaimedEvent event = (ConstraintClaimedEvent) previousClaim.getPayload();

            if (!event.getOwner().equals(getAggregateId())) {
                CurrentUnitOfWork.get().onPrepareCommit(uow -> {
                    throw new ConstraintAlreadyClaimedException(
                            String.format(
                                    "Unique constraint %s was already claimed by aggregate %s. Can not claim is for aggregate %s.",
                                    constraintName,
                                    previousClaim.getPayload(),
                                    getAggregateId()));
                });
            }
        }

        private String getNullableValue(String constraintName, Object value) {
            if (value == null) {
                return null;
            }
            return constraintValueProvider.determineValue(constraintName, value);
        }
    }


    /**
     * A new builder to construct a new {@link UniqueConstraintValidator}.
     * <p>
     * Requires the {@link EventStore} to be configured. The {@link ConstraintValueProvider} defaults to a
     * {@link Sha256ConstraintValueProvider}.
     */
    public static class Builder {

        private EventStore eventStore;
        private ConstraintValueProvider constraintValueProvider = new Sha256ConstraintValueProvider();

        /**
         * Changes the {@link ConstraintValueProvider} to be used when determining the value of the constraint. Defaults
         * to the {@link Sha256ConstraintValueProvider} unless changed.
         *
         * @param constraintValueProvider The new {@link ConstraintValueProvider}.
         * @return The builder, for fluent interfacing.
         */
        public Builder constraintValueProvider(ConstraintValueProvider constraintValueProvider) {
            BuilderUtils.assertNonNull(constraintValueProvider, "valueProviderFunction cannot be null!");
            this.constraintValueProvider = constraintValueProvider;
            return this;
        }

        /**
         * The {@link EventStore} to use when checking constraints against. Required to be able to build the builder.
         *
         * @param eventStore The {@link EventStore} to use.
         * @return
         */
        public Builder eventStore(EventStore eventStore) {
            BuilderUtils.assertNonNull(eventStore, "eventStore cannot be null!");
            this.eventStore = eventStore;
            return this;
        }

        protected void validate() {
            BuilderUtils.assertNonNull(eventStore, "eventStore cannot be null!");
        }

        /**
         * Builds the {@link UniqueConstraintValidator} using the configuration acquired.
         *
         * @return
         */
        public UniqueConstraintValidator build() {
            return new UniqueConstraintValidator(this);
        }
    }
}
