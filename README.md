# Axon Framework Unique Constraint Extension
![Build status](https://img.shields.io/github/checks-status/AxonFramework/extension-unique-constraint/main)

**Experimental** extension to [Axon Framework](https://axoniq.io) that makes it easier for uniqueness
constraints to be enforced across multiple instances of an aggregate.

**No release is currently available yet.**

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

## Simple Usage

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

## Advanced usage
If you don't like to implement the interface, you can call the validator yourself. 
You need to do this using a `@CommandHandlerInterceptor`.

```java
@Aggregate
class Room {

    @AggregateIdentifier
    private UUID roomId;
    private Integer roomNumber;

    @CommandHandlerInterceptor
    public Object handle(InterceptorChain interceptorChain, UniqueConstraintValidator validator) throws Exception {
        return validator.forAggregate(() -> roomId)
                        .addConstraint("RoomNumber", () -> roomNumber)
                        .checkForInterceptor(interceptorChain);
    }
}
```

This configures the `UniqueConstraintValidator` to validate the `roomNumber` to be unique across aggregates.
The check will only execute if the field changed during command execution.

### Constructor command handlers

If you want to check this constraint during creation of the aggregate, things will work a little bit differently.
Constructor command handlers are not intercepted by `@CommandHandlerInterceptor`-annotated methods, so the constraint
does not trigger. We can solve this in two ways.

#### Creation policy method

Changing your constructor to a method with a `@CreationPolicy` annotation with value `ALWAYS` will act like a
constructor,
but will be intercepted by `CommandHandlerInterceptor`. Now your unique constraint will be validated. You can see how
this looks in the following sample.

```java
@Aggregate
class Room {
    // Fields and such omitted

    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.ALWAYS)
    void Room(CreateRoomCommand command) {
        apply(new RoomCreatedEvent(command.getRoomId(), command.getRoomNumber(), command.getRoomDescription()));
    }
}
```

#### Check during constructor

Alternatively, you can force the check during execution of the constructor. This would look like the following sample:

```java
@Aggregate
class Room {

    // Fields and such omitted
    @CommandHandler
    public Room(CreateRoomCommand command, UniqueConstraintValidator validator) {
        apply(new RoomCreatedEvent(command.getRoomId(), command.getRoomNumber(), command.getRoomDescription()));
        validator.forAggregate(() -> roomId)
                 .addConstraint("RoomNumber", () -> roomNumber)
                 .checkNow(); // Will force the check to be done
    }
}
```

Make sure you do this check after applying the events, so your aggregate contains the appropriate data in its fields.


## Feature requests and issue reporting

We use GitHub's [issue tracking system](https://github.com/AxonFramework/extension-unique-constraint/issues) for new feature requests, framework enhancements, and bugs.
Before filing an issue, please verify that it's not already reported by someone else.
Furthermore, make sure you are adding the issue to the correct repository!

When filing bugs:
* A description of your setup and what's happening helps us figure out what the issue might be.
* Do not forget to provide the versions of the Axon products you're using, as well as the language and version.
* If possible, share a stack trace.
  Please use Markdown semantics by starting and ending the trace with three backticks (```).

When filing a feature or enhancement:
* Please provide a description of the feature or enhancement at hand.
  Adding why you think this would be beneficial is also a great help to us.
* (Pseudo-)Code snippets showing what it might look like will help us understand your suggestion better.
  Similarly as with bugs, please use Markdown semantics for code snippets, starting and ending with three backticks (```).
* If you have any thoughts on where to plug this into the framework, that would be very helpful too.
* Lastly, we value contributions to the framework highly.
  So please provide a Pull Request as well!
