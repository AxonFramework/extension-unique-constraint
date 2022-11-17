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

public class UniqueConstraintValidator {

    private final EventStore eventStore;
    private final ValueProviderFunction valueProviderFunction;

    public UniqueConstraintValidator(Builder builder) {
        this.eventStore = builder.eventStore;
        this.valueProviderFunction = builder.valueProviderFunction;
    }

    public ValidatorInstance forAggregate(Supplier<Object> aggregateIdSupplier) {
        return new ValidatorInstance(aggregateIdSupplier);
    }

    public static Builder builder() {
        return new Builder();
    }

    public class ValidatorInstance {

        private final Supplier<Object> aggregateIdSupplier;
        private final Map<String, Supplier<Object>> constraintMap = new HashMap<>();

        private ValidatorInstance(Supplier<Object> aggregateIdSupplier) {
            this.aggregateIdSupplier = aggregateIdSupplier;
        }

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
         * @throws ConstraintAlreadyClaimedException If a constraint was already claimed
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
         * @throws ConstraintAlreadyClaimedException If a constraint was already claimed
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
            String valueBefore = getNullableValue(oldValue);
            String valueAfter = getNullableValue(newValue);

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
                        new ConstraintUnclaimedEvent(constraintName, constraintValue, getAggregateId()));
                eventStore.publish(message);
            });
        }

        private String getAggregateId() {
            return aggregateIdSupplier.get().toString();
        }

        private void checkAndClaimValue(String constraintKey, String constraintValue) {
            // Check if it's claimed
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
                                               constraintValue,
                                               getAggregateId()));
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
                    throw new ConstraintAlreadyClaimedException(
                            String.format("Unknown event of type %s. Can not process unique constraints.",
                                          previousClaim.getPayload().getClass().getName()));
                });
            }

            ConstraintClaimedEvent event = (ConstraintClaimedEvent) previousClaim.getPayload();

            if (!event.getAggregateId().equals(getAggregateId())) {
                CurrentUnitOfWork.get().onPrepareCommit(uow -> {
                    throw new IllegalStateException(
                            String.format(
                                    "Unique constraint %s was already claimed by aggregate %s. Can not claim is for aggregate %s.",
                                    constraintName,
                                    previousClaim.getPayload(),
                                    getAggregateId()));
                });
            }
        }

        private String getNullableValue(Object value) {
            if (value == null) {
                return null;
            }
            return valueProviderFunction.determineValue(value);
        }
    }


    public static class Builder {

        private EventStore eventStore;
        private ValueProviderFunction valueProviderFunction = new DefaultValueProviderFunction();

        public Builder valueProviderFunction(ValueProviderFunction valueProviderFunction) {
            BuilderUtils.assertNonNull(valueProviderFunction, "valueProviderFunction cannot be null!");
            this.valueProviderFunction = valueProviderFunction;
            return this;
        }

        public Builder eventStore(EventStore eventStore) {
            BuilderUtils.assertNonNull(eventStore, "eventStore cannot be null!");
            this.eventStore = eventStore;
            return this;
        }

        public UniqueConstraintValidator build() {
            return new UniqueConstraintValidator(this);
        }
    }
}
