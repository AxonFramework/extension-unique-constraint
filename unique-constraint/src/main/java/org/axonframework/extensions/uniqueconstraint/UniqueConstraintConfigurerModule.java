package org.axonframework.extensions.uniqueconstraint;

import org.axonframework.config.Configurer;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.extensions.uniqueconstraint.eventstore.EventStoreUniqueConstraintStore;

/**
 * Configures Axon Framework's configuration to understand the {@link AggregateUniqueConstraint} annotation by
 * configuring the {@link UniqueConstraintHandlerEnhancerDefinition}.
 * <p>
 * The enhancer will use the {@link EventStoreUniqueConstraintStore} with SHA-256 hashing as its key by default. This
 * can be overridden by adding your own configuration.
 *
 * @author Mitchell Herrijgers
 * @since 0.0.1
 */
public class UniqueConstraintConfigurerModule implements ConfigurerModule {

    @Override
    public void configureModule(Configurer configurer) {
        configurer.registerComponent(
                UniqueConstraintStore.class,
                config -> EventStoreUniqueConstraintStore
                        .builder()
                        .eventStore(config.eventStore()).build());
        configurer.registerComponent(
                UniqueConstraintValidator.class,
                config -> UniqueConstraintValidator.builder()
                                                   .constraintStore(config.getComponent(UniqueConstraintStore.class))
                                                   .build());

        configurer.onStart(() -> {
            UniqueConstraintValidator validator = configurer.buildConfiguration()
                                                            .getComponent(UniqueConstraintValidator.class);
            UniqueConstraintHandlerEnhancerDefinition.setUniqueConstraintValidator(validator);
        });
    }
}
