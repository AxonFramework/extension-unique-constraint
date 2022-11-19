package org.axonframework.extensions.uniqueconstraint;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.extensions.uniqueconstraint.UniqueConstraintValidator.ValidatorInstance;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.annotation.MessageHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.CommandHandlerInterceptor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Interface that an aggregate should implement when annotating members with the {@link AggregateUniqueConstraint}
 * annotation.
 * <p>
 * Will intercept any commands to the class and provision a {@link UniqueConstraintValidator} for that command, which
 * compares the unique constraints' values before and after. If any of the values changed during the command, the
 * validator will validate that the claims can be made and make changes to the claims.
 * <p>
 * Your aggregate needs to adhere to the following requirements:
 * <ul>
 *     <li>There should be no constructors that handle messages. Use regular methods with {@code @CreationPolicy(ALWAYS)} instead./li>
 *     <li>A field or method should be annotated with {@code @AggregateIdentifier}. This will be used as the id for owning the claim.</li>
 * </ul>
 *
 * @author Mitchell Herrijgers
 * @since 0.0.1
 */
public interface ConstraintCheckingAggregate {


    /**
     * Interceptors the commands to this aggregate and create a {@link UniqueConstraintValidator} based on (cached)
     * reflective information.
     *
     * @param interceptorChain The {@link InterceptorChain} to call proceed on, supplied by the framework.
     * @param validatorFactory The {@link UniqueConstraintValidator} responsible for creating a validator.
     * @return The result of the command execution, supplied by the chain.
     * @throws Exception Any execution that occurred during command execution.
     */
    @CommandHandlerInterceptor
    default Object interceptForUniqueConstraints(InterceptorChain interceptorChain,
                                                 UniqueConstraintValidator validatorFactory)
            throws Exception {
        AggregateInformation aggregateInformation = AggregateInformation.getInformation(this.getClass());
        ValidatorInstance validatorInstance = validatorFactory.forAggregate(
                () -> aggregateInformation.aggregateIdSupplier.apply(this));
        aggregateInformation.constraintFields.forEach(
                (name, supplier) -> validatorInstance.addConstraint(name, () -> supplier.apply(this)));
        return validatorInstance.checkForInterceptor(interceptorChain);
    }


    /**
     * Information regarding the aggregate class' fields, containing the aggregate identifier member and all members
     * annotated with {@link AggregateUniqueConstraint}.
     * <p>
     * Will throw an {@link IllegalStateException} if the aggregate is wrongly implemented according to the requirements
     * of the {@link ConstraintCheckingAggregate} interface. The requirements are:
     * <ul>
     *     <li>There should be no constructors that handle messages. Use regular methods with {@code @CreationPolicy(ALWAYS)} instead./li>
     *     <li>A field or method should be annotated with {@code @AggregateIdentifier}. This will be used as the id for owning the claim.</li>
     * </ul>
     */
    class AggregateInformation {

        private static final Map<Class<?>, AggregateInformation> cache = new ConcurrentHashMap<>();

        private final Function<Object, Object> aggregateIdSupplier;
        private final Map<String, Function<Object, Object>> constraintFields;

        protected static AggregateInformation getInformation(Class<?> clazz) {
            return cache.computeIfAbsent(clazz, AggregateInformation::new);
        }

        protected AggregateInformation(Class<?> clazz) {
            if (Arrays.stream(clazz.getConstructors()).anyMatch(c -> c.isAnnotationPresent(CommandHandler.class)
                    || c.isAnnotationPresent(MessageHandler.class))) {
                throw new IllegalStateException(String.format(
                        "Aggregate %s contains constructors which handle messages. This is not supported, please convert them to regular methods annotated with @CreationPolicy(ALWAYS).",
                        clazz.getSimpleName()));
            }

            this.aggregateIdSupplier = determineAggregateIdSupplier(clazz);
            this.constraintFields = determineConstraintFields(clazz);
        }

        private Function<Object, Object> determineAggregateIdSupplier(Class<?> clazz) {
            for (Field field : ReflectionUtils.fieldsOf(clazz)) {
                if (field.isAnnotationPresent(AggregateIdentifier.class)) {
                    return instance -> ReflectionUtils.getFieldValue(field, instance);
                }
            }

            for (Method method : ReflectionUtils.methodsOf(clazz)) {
                if (method.isAnnotationPresent(AggregateIdentifier.class)) {
                    return instance -> ReflectionUtils.invokeAndGetMethodValue(method, instance);
                }
            }
            throw new IllegalStateException(String.format(
                    "Aggregate %s does not contain a field or method annotated with @AggregateIdentifier. Please add it.",
                    clazz.getSimpleName()));
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
    }
}
