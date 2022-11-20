package org.axonframework.extensions.uniqueconstraint;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.extensions.uniqueconstraint.UniqueConstraintValidator.ValidatorInstance;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.WrappedMessageHandlingMember;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.modelling.command.AggregateScopeDescriptor;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Implementation of a {@link HandlerEnhancerDefinition} that configures a {@link UniqueConstraintValidator} for each
 * command handler if a field in the aggregate is annotated using {@link TargetAggregateIdentifier}.
 * <p>
 * This enhancer is capable of enhancing both constructors and regular commands. However, it is only able to check
 * constraints in top level class of the entity hierarchy; the aggregate root.
 *
 * @author Mitchell Herrijgers
 * @since 0.0.1
 */
public class UniqueConstraintHandlerEnhancerDefinition implements HandlerEnhancerDefinition {

    private static UniqueConstraintValidator uniqueConstraintValidator;

    /**
     * Sets the {@link UniqueConstraintValidator} to use for this enhancer definition. IT will be used to check the
     * constraint of the aggregate if any {@link AggregateUniqueConstraint} annotations are detected.
     * <p>
     * Since current {@link HandlerEnhancerDefinition}s can only be registered with no arguments, this workaround is
     * used. See {@link UniqueConstraintConfigurerModule}. We are planning to make a change in the framework to allow
     * this. As such, this setter will be removed in the future.
     *
     * @param uniqueConstraintValidator The {@link UniqueConstraintValidator} to use during command handling.
     */
    public static void setUniqueConstraintValidator(UniqueConstraintValidator uniqueConstraintValidator) {
        UniqueConstraintHandlerEnhancerDefinition.uniqueConstraintValidator = uniqueConstraintValidator;
    }

    @Override
    public <T> MessageHandlingMember<T> wrapHandler(MessageHandlingMember<T> messageHandlingMember) {
        if (!messageHandlingMember.canHandleMessageType(CommandMessage.class)) {
            return messageHandlingMember;
        }

        Map<String, Function<Object, Object>> constraintFields = determineConstraintFields(messageHandlingMember.declaringClass());
        if (constraintFields.isEmpty()) {
            return messageHandlingMember;
        }

        return new WrappedMessageHandlingMember<T>(messageHandlingMember) {
            @Override
            public Object handle(Message<?> message, T target) throws Exception {
                if (uniqueConstraintValidator == null) {
                    throw new IllegalArgumentException(
                            "Unique constraint validator was not initialized! Check the documentation for more information");
                }
                if (target == null) {
                    Object aggregate = super.handle(message, null);
                    configureValidator(aggregate, constraintFields).check();
                    return aggregate;
                } else {
                    return configureValidator(target, constraintFields)
                            .checkForInterceptor(() -> super.handle(message, target));
                }
            }
        };
    }

    private Map<String, Function<Object, Object>> determineConstraintFields(Class<?> clazz) {
        Map<String, Function<Object, Object>> resultMap = new HashMap<>();
        for (Field field : ReflectionUtils.fieldsOf(clazz)) {
            if (field.isAnnotationPresent(AggregateUniqueConstraint.class)) {
                AggregateUniqueConstraint annotation = field.getAnnotation(AggregateUniqueConstraint.class);
                resultMap.put(annotation.constraintName(),
                              (instance) -> ReflectionUtils.getFieldValue(field, instance));
            }
        }

        for (Method method : ReflectionUtils.methodsOf(clazz)) {
            if (method.isAnnotationPresent(AggregateUniqueConstraint.class)) {
                AggregateUniqueConstraint annotation = method.getAnnotation(AggregateUniqueConstraint.class);
                resultMap.put(annotation.constraintName(),
                              (instance) -> ReflectionUtils.invokeAndGetMethodValue(method, instance));
            }
        }
        return resultMap;
    }

    private ValidatorInstance configureValidator(Object constructedAggregate,
                                                 Map<String, Function<Object, Object>> constraintFields) {
        ValidatorInstance validator = uniqueConstraintValidator.forAggregate(this::getAggregateIdentifier);
        for (Map.Entry<String, Function<Object, Object>> entry : constraintFields.entrySet()) {
            validator.addConstraint(entry.getKey(), () -> entry.getValue().apply(constructedAggregate));
        }
        return validator;
    }

    private Object getAggregateIdentifier() {
        AggregateScopeDescriptor scopeDescriptor = (AggregateScopeDescriptor) AggregateLifecycle.describeCurrentScope();
        return scopeDescriptor.getIdentifier();
    }
}
