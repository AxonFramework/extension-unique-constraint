package org.axonframework.extensions.uniqueconstraint;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.extensions.uniqueconstraint.events.ConstraintClaimedEvent;
import org.axonframework.extensions.uniqueconstraint.events.ConstraintReleasedEvent;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventStoreUniqueConstraintStoreTest {

    private final EventStore eventStore = Mockito.mock(EventStore.class);
    private final EventStoreUniqueConstraintStore store = EventStoreUniqueConstraintStore
            .builder()
            .eventStore(eventStore)
            .constraintValueProvider((constraintName, value) -> value.toString())
            .build();

    private final ArgumentCaptor<DomainEventMessage<?>> captor = ArgumentCaptor.forClass(DomainEventMessage.class);

    @Test
    void storesClaimWhenNoPriorEventsExist() {
        when(eventStore.lastSequenceNumberFor("MyConstraintValue")).thenReturn(Optional.empty());

        store.checkAndClaimValue("MyConstraint", "MyConstraintValue", "AGG_ID_12");

        verify(eventStore).publish(captor.capture());
        verifyClaimedEvent(captor.getValue(), "MyConstraint", "MyConstraintValue", "AGG_ID_12");
    }

    @Test
    void storesClaimWhenPriorWasUnclaimed() {
        when(eventStore.lastSequenceNumberFor("MyConstraintValue")).thenReturn(Optional.of(2L));
        when(eventStore.readEvents("MyConstraintValue", 2L)).thenReturn(DomainEventStream.of(
                new GenericDomainEventMessage<>("MyConstraint",
                                                "MyConstraintValue",
                                                2L,
                                                new ConstraintReleasedEvent("MyConstraint", "MyConstraintValue"))
        ));

        store.checkAndClaimValue("MyConstraint", "MyConstraintValue", "AGG_ID_12");

        verify(eventStore).publish(captor.capture());
        verifyClaimedEvent(captor.getValue(), "MyConstraint", "MyConstraintValue", "AGG_ID_12");
    }

    @Test
    void rejectsClaimWhenValueIsAlreadyClaimed() {
        when(eventStore.lastSequenceNumberFor("MyConstraintValue")).thenReturn(Optional.of(2L));
        when(eventStore.readEvents("MyConstraintValue", 2L)).thenReturn(DomainEventStream.of(
                new GenericDomainEventMessage<>("MyConstraint",
                                                "MyConstraintValue",
                                                2L,
                                                new ConstraintClaimedEvent("MyConstraint",
                                                                           "MyConstraintValue",
                                                                           "AGG_11"))
        ));

        assertThrows(UniqueConstraintClaimException.class, () -> {
            store.checkAndClaimValue("MyConstraint", "MyConstraintValue", "AGG_ID_12");
        });
    }

    @Test
    void rejectsReleaseWhenValueIsClaimedByOtherOwner() {
        when(eventStore.lastSequenceNumberFor("MyConstraintValue")).thenReturn(Optional.of(2L));
        when(eventStore.readEvents("MyConstraintValue", 2L)).thenReturn(DomainEventStream.of(
                new GenericDomainEventMessage<>("MyConstraint",
                                                "MyConstraintValue",
                                                2L,
                                                new ConstraintClaimedEvent("MyConstraint",
                                                                           "MyConstraintValue",
                                                                           "AGG_11"))
        ));

        assertThrows(UniqueConstraintClaimException.class, () -> {
            store.releaseClaimValue("MyConstraint", "MyConstraintValue", "AGG_ID_12");
        });
    }

    @Test
    void releaseWhenValueIsClaimedBySameOwner() {
        when(eventStore.lastSequenceNumberFor("MyConstraintValue")).thenReturn(Optional.of(2L));
        when(eventStore.readEvents("MyConstraintValue", 2L)).thenReturn(DomainEventStream.of(
                new GenericDomainEventMessage<>("MyConstraint",
                                                "MyConstraintValue",
                                                2L,
                                                new ConstraintClaimedEvent("MyConstraint",
                                                                           "MyConstraintValue",
                                                                           "AGG_ID_12"))
        ));

        store.releaseClaimValue("MyConstraint", "MyConstraintValue", "AGG_ID_12");
        verify(eventStore).publish(captor.capture());
        verifyUnclaimedEvent(captor.getValue(), "MyConstraint", "MyConstraintValue");
    }

    private void verifyClaimedEvent(DomainEventMessage<?> value, String constraintName, String constraintValue,
                                    String owner) {
        assertEquals("Constraint" + constraintName, value.getType());
        assertEquals(constraintValue, value.getAggregateIdentifier());
        assertTrue(value.getPayload() instanceof ConstraintClaimedEvent);
        ConstraintClaimedEvent payload = (ConstraintClaimedEvent) value.getPayload();
        assertEquals(owner, payload.getOwner());
        assertEquals(constraintName, payload.getConstraintName());
        assertEquals(constraintValue, payload.getConstraintKey());
    }

    private void verifyUnclaimedEvent(DomainEventMessage<?> value, String constraintName, String constraintValue) {
        assertEquals("Constraint" + constraintName, value.getType());
        assertEquals(constraintValue, value.getAggregateIdentifier());
        assertTrue(value.getPayload() instanceof ConstraintReleasedEvent);
        ConstraintReleasedEvent payload = (ConstraintReleasedEvent) value.getPayload();
        assertEquals(constraintName, payload.getConstraintName());
        assertEquals(constraintValue, payload.getConstraintKey());
    }
}
