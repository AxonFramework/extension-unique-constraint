package org.axonframework.extensions.uniqueconstraint;

import org.axonframework.config.Configurer;
import org.axonframework.config.ConfigurerModule;

public class UniqueConstraintConfigurerModule implements ConfigurerModule {

    @Override
    public void configureModule(Configurer configurer) {
        configurer.registerComponent(UniqueConstraintValidator.class, configuration ->
                UniqueConstraintValidator.builder()
                                         .eventStore(configuration.eventStore())
                                         .build());
    }
}
