package org.axonframework.extensions.uniqueconstraint.eventstore;

import org.axonframework.common.BuilderUtils;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.extensions.uniqueconstraint.UniqueConstraintClaimException;
import org.axonframework.extensions.uniqueconstraint.UniqueConstraintStore;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.LongConsumer;

/**
 * Implementation of the {@link UniqueConstraintStore} that stores the constraints in an {@link EventStore}. Adds events
 * to the database to mark constraint keys as claimed or unclaimed.
 *
 * @author Mitchell Herrijgers
 * @since 0.0.1
 */
public class EventStoreUniqueConstraintStore implements UniqueConstraintStore {

    private final EventStore eventStore;
    private final ConstraintKeyProvider constraintKeyProvider;

    /**
     * Creates a new {@link EventStoreUniqueConstraintStore} with the builder's configuration.
     *
     * @param builder The builder to use.
     */
    protected EventStoreUniqueConstraintStore(Builder builder) {
        builder.validate();
        this.eventStore = builder.eventStore;
        this.constraintKeyProvider = builder.constraintKeyProvider;
    }

    /**
     * Creates a new builder to construct a new {@link EventStoreUniqueConstraintStore}.
     * <p>
     * Requires the {@link EventStore} to be configured. The {@link ConstraintKeyProvider} defaults to a
     * {@link Sha256ConstraintKeyProvider}.
     *
     * @return A builder suitable to construct a new {@link EventStoreUniqueConstraintStore}.
     */
    public static EventStoreUniqueConstraintStore.Builder builder() {
        return new EventStoreUniqueConstraintStore.Builder();
    }


    @Override
    public void releaseClaimValue(String constraintName, String constraintValue, String owner) {
        String constraintKey = constraintKeyProvider.determineValue(constraintName, constraintValue);
        whenClaimedOrUnclaimed(
                constraintKey,
                (lastSequenceNumber, event) -> {
                    if (!event.getOwner().equals(owner)) {
                        throwDifferentOwnerException(constraintName, owner, event);
                    }
                    doRelease(constraintName, constraintKey, lastSequenceNumber);
                },
                lastSequenceNumber -> {
                    // Do nothing
                });
        eventStore.lastSequenceNumberFor(constraintKey).ifPresent(sequence -> {
        });
    }

    private void doRelease(String constraintName, String constraintKey, Long lastSequenceNumber) {
        GenericDomainEventMessage<ConstraintUnclaimedEvent> message = new GenericDomainEventMessage<>(
                "Constraint" + constraintName,
                constraintKey,
                lastSequenceNumber + 1,
                new ConstraintUnclaimedEvent(constraintName, constraintKey));
        eventStore.publish(message);
    }

    @Override
    public void checkAndClaimValue(String constraintName, String constraintValue, String owner) {
        String constraintKey = constraintKeyProvider.determineValue(constraintName, constraintValue);
        whenClaimedOrUnclaimed(
                constraintKey,
                (lastSequenceNumber, event) -> {
                    if (!event.getOwner().equals(owner)) {
                        throwDifferentOwnerException(constraintName, owner, event);
                    }
                },
                lastSequenceNumber -> doClaim(constraintName, constraintKey, lastSequenceNumber, owner));
    }

    private void throwDifferentOwnerException(String constraintName, String owner, ConstraintClaimedEvent event) {
        CurrentUnitOfWork.get().onPrepareCommit(uow -> {
            throw new UniqueConstraintClaimException(
                    String.format(
                            "Unique constraint %s was claimed by owner %s. Can not change claims is for aggregate %s.",
                            constraintName,
                            event.getOwner(),
                            owner));
        });
    }

    private void doClaim(String constraintName, String constraintKey, long previousSequenceNumber, String owner) {
        GenericDomainEventMessage<ConstraintClaimedEvent> message = new GenericDomainEventMessage<>(
                "Constraint" + constraintName,
                constraintKey,
                previousSequenceNumber + 1,
                new ConstraintClaimedEvent(constraintName,
                                           constraintKey, owner));
        eventStore.publish(message);
    }

    private void whenClaimedOrUnclaimed(String constraintKey,
                                        BiConsumer<Long, ConstraintClaimedEvent> claimedEventConsumer,
                                        LongConsumer unclaimedEventConsumer) {
        Optional<Long> lastSequenceNumber = eventStore.lastSequenceNumberFor(constraintKey);
        if (!lastSequenceNumber.isPresent()) {
            unclaimedEventConsumer.accept(-1L);
            return;
        }
        Optional<DomainEventMessage<?>> eventMessage = lastSequenceNumber
                .map(lastSequence -> eventStore.readEvents(constraintKey, lastSequence).next());
        if (!eventMessage.isPresent()) {
            CurrentUnitOfWork.get().onPrepareCommit(uow -> {
                throw new IllegalArgumentException(
                        String.format("Was unable to fetch event for constraint key %s and sequence number %s",
                                      constraintKey, lastSequenceNumber.get()));
            });
            return;
        }
        Object payload = eventMessage.get().getPayload();
        if (payload instanceof ConstraintClaimedEvent) {
            claimedEventConsumer.accept(eventMessage.get().getSequenceNumber(), (ConstraintClaimedEvent) payload);
            return;
        }
        if (payload instanceof ConstraintUnclaimedEvent) {
            unclaimedEventConsumer.accept(eventMessage.get().getSequenceNumber());
            return;
        }

        CurrentUnitOfWork.get().onPrepareCommit(uow -> {
            throw new IllegalArgumentException(
                    String.format("Unknown event of type %s. Can not process unique constraints.",
                                  payload.getClass().getName()));
        });
    }

    /**
     * A new builder to construct a new {@link EventStoreUniqueConstraintStore}.
     * <p>
     * Requires the {@link EventStore} to be configured. The {@link ConstraintKeyProvider} defaults to a
     * {@link Sha256ConstraintKeyProvider}.
     */
    public static class Builder {

        private EventStore eventStore;
        private ConstraintKeyProvider constraintKeyProvider = new Sha256ConstraintKeyProvider();

        /**
         * Changes the {@link ConstraintKeyProvider} to be used when determining the value of the constraint. Defaults
         * to the {@link Sha256ConstraintKeyProvider} unless changed.
         *
         * @param constraintKeyProvider The new {@link ConstraintKeyProvider}.
         * @return The builder, for fluent interfacing.
         */
        public Builder constraintValueProvider(ConstraintKeyProvider constraintKeyProvider) {
            BuilderUtils.assertNonNull(constraintKeyProvider, "valueProviderFunction cannot be null!");
            this.constraintKeyProvider = constraintKeyProvider;
            return this;
        }

        /**
         * The {@link EventStore} to use when checking constraints against. Required to be able to build the builder.
         *
         * @param eventStore The {@link EventStore} to use.
         * @return The builder, for fluent interfacing.
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
         * Builds the {@link EventStoreUniqueConstraintStore} using the configuration acquired.
         *
         * @return
         */
        public EventStoreUniqueConstraintStore build() {
            return new EventStoreUniqueConstraintStore(this);
        }
    }
}
