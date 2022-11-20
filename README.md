# Axon Framework Unique Constraint Extension
![Build status](https://img.shields.io/github/checks-status/AxonFramework/extension-unique-constraint/main)

**Experimental** extension to [Axon Framework](https://axoniq.io) that makes it easier for uniqueness
constraints to be enforced across multiple instances of an aggregate.

**No release is currently available yet.**

## Simple Usage

When you want to validate a constraint in your aggregate, you can
annotate any fields you want to have checked for uniqueness with `@AggregateUniqueConstraint`.

```java
@Aggregate
class Room {

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

## Warnings
> :warning: Having constraints **can** be a symptom of bad aggregate design.
Unique values are best kept as aggregate identifiers since their uniqueness is guaranteed.
However, sometimes this can be useful when the identity of an object remains the same despite the unique field being changed.
For example, a person does not get a new identity with a new email address.

> :warning: This extension is able to guarantee consistent writes by persisting
multiple aggregate identifiers in one transaction; the one of the aggregate and the constraint keys involved.
**Using any form of sharding in the future will void this guarantee**. 
Currently Axon Server does not support this, but it might in the future. Use with care.

These two warnings are probably why this extension will stay an extension,
and not make it into the core framework. 

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
  "constraintName": "RoomNumber",
  "constraintKey": "E87537C45B02505FDA597F2669CC7A3694D263232A5462D2B48255385004B55C",
  "owner": "33bfcb4b-f910-4258-aee9-e567463931b3"
}
```

As you can see, the value is safely masked so personal data can be used. In this case, the `constraintValue` was `627030788`. It was claimed
by an aggregate with id `33bfcb4b-f910-4258-aee9-e567463931b3`.


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
