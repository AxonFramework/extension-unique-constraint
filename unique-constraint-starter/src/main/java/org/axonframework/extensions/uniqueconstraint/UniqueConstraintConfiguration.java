package org.axonframework.extensions.uniqueconstraint;

import org.axonframework.config.ConfigurerModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Automatically configures a {@link UniqueConstraintValidator} to be used within the application.
 * Does so by configuring the {@link UniqueConstraintConfigurerModule} to be used in the axon configuration.
 *
 * @author Mitchell Herrijgers
 * @since 0.0.1
 */
@Configuration
public class UniqueConstraintConfiguration {

    @Bean
    public ConfigurerModule uniqueConstraintConfigurerModule() {
        return new UniqueConstraintConfigurerModule();
    }
}
