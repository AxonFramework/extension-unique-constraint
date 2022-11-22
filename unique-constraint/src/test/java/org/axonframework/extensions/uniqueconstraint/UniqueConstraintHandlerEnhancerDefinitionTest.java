package org.axonframework.extensions.uniqueconstraint;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.messaging.Scope;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.messaging.annotation.DefaultParameterResolverFactory;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.modelling.command.AggregateRoot;
import org.axonframework.modelling.command.AggregateScopeDescriptor;
import org.axonframework.modelling.command.inspection.AggregateModel;
import org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
class UniqueConstraintHandlerEnhancerDefinitionTest {

    private final UniqueConstraintHandlerEnhancerDefinition definition = new UniqueConstraintHandlerEnhancerDefinition();
    private final UniqueConstraintStore store = Mockito.mock(UniqueConstraintStore.class);
    private final MessageHandlingMember actualHandler = Mockito.mock(MessageHandlingMember.class);

    @BeforeEach
    void setUp() {
        UniqueConstraintHandlerEnhancerDefinition.setUniqueConstraintValidator(
                UniqueConstraintValidator.builder()
                                         .constraintStore(store)
                                         .build());
    }

    @Test
    void doesNotWrapNonCommandHandler() throws Exception {
        MessageHandlingMember messageHandlingMember = definition.wrapHandler(actualHandler);
        assertSame(actualHandler, messageHandlingMember);
    }

    @Test
    void doesNotWrapWhenNoFieldsAreAnnotated() throws Exception {
        when(actualHandler.canHandleMessageType(CommandMessage.class)).thenReturn(true);
        when(actualHandler.declaringClass()).thenReturn(EmptyClass.class);
        MessageHandlingMember messageHandlingMember = definition.wrapHandler(actualHandler);
        assertSame(actualHandler, messageHandlingMember);
    }

    @Test
    void wrapsAndExecutesOnMessageWhenFieldConstraintIsPresentWhenConstructor() throws Exception {
        when(actualHandler.canHandleMessageType(CommandMessage.class)).thenReturn(true);
        when(actualHandler.declaringClass()).thenReturn(UniqueConstraintClass.class);
        MessageHandlingMember messageHandlingMember = definition.wrapHandler(actualHandler);
        assertNotSame(actualHandler, messageHandlingMember);

        UniqueConstraintClass result = new UniqueConstraintClass();
        result.email = "myEmail";
        when(actualHandler.handle(any(), isNull())).thenReturn(result);

        new MockedCommandHandlingScope().execute(() -> {
            messageHandlingMember.handle(GenericCommandMessage.asCommandMessage("myCommand"), null);
            return null;
        });
        verify(store).checkAndClaimValue("Email", "myEmail", "AGG_ID");
    }

    @Test
    void wrapsAndExecutesOnMessageWhenFieldConstraintIsPresentWhenNormalHandler() throws Exception {
        when(actualHandler.canHandleMessageType(CommandMessage.class)).thenReturn(true);
        when(actualHandler.declaringClass()).thenReturn(UniqueConstraintClass.class);
        MessageHandlingMember messageHandlingMember = definition.wrapHandler(actualHandler);
        assertNotSame(actualHandler, messageHandlingMember);

        UniqueConstraintClass result = new UniqueConstraintClass();
        result.email = "myEmail";
        when(actualHandler.handle(any(), any())).thenAnswer(invocationOnMock -> {
            result.email = "myEmail2";
            return result;
        });

        new MockedCommandHandlingScope().execute(() -> {
            messageHandlingMember.handle(GenericCommandMessage.asCommandMessage("myCommand"), result);
            return null;
        });
        verify(store).releaseClaimValue("Email", "myEmail", "AGG_ID");
        verify(store).checkAndClaimValue("Email", "myEmail2", "AGG_ID");
    }

    @Test
    void wrapsAndExecutesOnMessageWhenMethodConstraintIsPresentWhenConstructor() throws Exception {
        when(actualHandler.canHandleMessageType(CommandMessage.class)).thenReturn(true);
        when(actualHandler.declaringClass()).thenReturn(UniqueConstraintOnMethodClass.class);
        MessageHandlingMember messageHandlingMember = definition.wrapHandler(actualHandler);
        assertNotSame(actualHandler, messageHandlingMember);

        UniqueConstraintOnMethodClass result = new UniqueConstraintOnMethodClass();
        result.email = "myEmail";
        when(actualHandler.handle(any(), isNull())).thenReturn(result);

        new MockedCommandHandlingScope().execute(() -> {
            messageHandlingMember.handle(GenericCommandMessage.asCommandMessage("myCommand"), null);
            return null;
        });
        verify(store).checkAndClaimValue("Email", "myEmail", "AGG_ID");
    }

    @Test
    void wrapsAndExecutesOnMessageWhenMethodConstraintIsPresentWhenNormalHandler() throws Exception {
        when(actualHandler.canHandleMessageType(CommandMessage.class)).thenReturn(true);
        when(actualHandler.declaringClass()).thenReturn(UniqueConstraintOnMethodClass.class);
        MessageHandlingMember messageHandlingMember = definition.wrapHandler(actualHandler);
        assertNotSame(actualHandler, messageHandlingMember);

        UniqueConstraintOnMethodClass result = new UniqueConstraintOnMethodClass();
        result.email = "myEmail";
        when(actualHandler.handle(any(), any())).thenAnswer(invocationOnMock -> {
            result.email = "myEmail2";
            return result;
        });

        new MockedCommandHandlingScope().execute(() -> {
            messageHandlingMember.handle(GenericCommandMessage.asCommandMessage("myCommand"), result);
            return null;
        });
        verify(store).releaseClaimValue("Email", "myEmail", "AGG_ID");
        verify(store).checkAndClaimValue("Email", "myEmail2", "AGG_ID");
    }

    class MockedCommandHandlingScope extends Scope {

        @Override
        public ScopeDescriptor describeScope() {
            return new AggregateScopeDescriptor("UniqueConstraintClass", "AGG_ID");
        }

        public void execute(Callable<Void> callable) throws Exception {
            startScope();
            callable.call();
            endScope();
        }
    }

    class EmptyClass {

    }

    class UniqueConstraintClass {

        @AggregateUniqueConstraint(constraintName = "Email")
        public String email;
    }
    class UniqueConstraintOnMethodClass {

        private String email;

        @AggregateUniqueConstraint(constraintName = "Email")
        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }
}
