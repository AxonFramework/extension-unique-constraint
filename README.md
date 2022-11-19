# Axon Framework Unique Constraint Extension

**Experimental** extension to [Axon Framework](https://axoniq.io) that makes it easier for uniqueness
constraints to be enforced across multiple instances of an aggregate.

## Background

Withing Axon Framework, aggregates are your consistency boundary.
But what if you need to ensure one of the properties is unique across all instances of the aggregate?

You can keep a database table that is updated during command execution,
as [suggested in this blog](https://developer.axoniq.io/w/set-based-consistency-validation).
There are a few problems with this approach:

- When using a non-transactional datastore, or Axon Server, the updates are not atomic.
- You need to create plumbing for the database tables.
- The data is not recoverable from the event store.

More recently, a new method came to light: creating another aggregate with the value as part of the identifier,
guaranteeing uniqueness. You can read about
it [in this blog](https://developer.axoniq.io/w/set-based-consistency-validation-revisited).
Again, we encounter the problem that writes across multiple commands are not atomic.
A lot of compensating logic is required to ensure everything stays consistent.

This extension works differently, **guaranteeing consistency** for your unique constraint.
It uses the event store to register claims in a consistent way, updating the claims in the same transaction.
The datasource will guarantee that the sequence number of all events is correct, ensuring the values are consistent.

## Usage

When you want to validate a constraint in your aggregate, you can implement the `ConstraintCheckingAggregate` interface and 
annotate any constraints with `@AggregateUniqueConstraint`.

```java
@Aggregate
class Room implements ConstraintCheckingAggregate {

    @AggregateIdentifier
    private UUID roomId;
    @AggregateUniqueConstraint
    private Integer roomNumber;

    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.ALWAYS)
    void Room(CreateRoomCommand command) {
        apply(new RoomCreatedEvent(command.getRoomId(), command.getRoomNumber(), command.getRoomDescription()));
    }
}
```

This configures the `UniqueConstraintValidator` to validate the `roomNumber` to be unique across aggregates.
The check will only execute if the field changed during command execution.

This approach requires your aggregate to have no constructors that are able to handle commands.
Instead of constructors, use a method with a `@CreationPolicy` annotation with value `ALWAYS`.
This will function in the same way.

## Configuration

This section explains how you can configure this extension to work with your Axon Framework application.

### Spring Boot

You can add this project as a Spring Boot starter to your project, for example using Maven:

```xml

<dependency>
    <groupId>org.axonframework.extensions.uniqueconstraint</groupId>
    <artifactId>extension-unique-constraint-starter</artifactId>
    <version>0.0.1</version>
</dependency>
```

With Spring Boot, everything is configured out of the box.

### Non-Spring
Unfortunately, only Spring is currently supported. We will support non-Spring configurations in the future.

## Storage

The claims are stored using events.
The aggregate type will be the constraints' name.
The aggregate key is the SHA-256 hash of the value.

There are two events: `ConstraintClaimedEvent` and `ConstraintUnclaimedEvent`. When validating, the last event is read
from the store.
If there are no events, the value has never been used and is thus free. If the last event is
a `ConstraintUnclaimedEvent`, the value is free to claim as well since it was released by its previous owner.

If the last event is a `ConstraintClaimedEvent`, it already has an owner. If the owner of that value is not the current
aggreagte, a `ConstraintAlreadyClaimedException` is thrown.

The payload of both events will contain all information necessary and looks like this:

```json lines
{
  "constraintKey": "RoomNumber",
  "constraintValue": "E87537C45B02505FDA597F2669CC7A3694D263232A5462D2B48255385004B55C",
  "owner": "33bfcb4b-f910-4258-aee9-e567463931b3"
}
```

As you can see, the value is safely masked so personal data can be used. In this case, the `constraintValue` was `627030788`. It was claimed
by an aggregate with id `33bfcb4b-f910-4258-aee9-e567463931b3`.
