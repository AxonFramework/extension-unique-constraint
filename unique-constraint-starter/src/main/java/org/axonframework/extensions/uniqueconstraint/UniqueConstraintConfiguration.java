package org.axonframework.extensions.uniqueconstraint;

import org.axonframework.eventsourcing.eventstore.EventStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Automatically configures a {@link UniqueConstraintValidator} to be used within the application.
 *
 * @since 0.0.1
 * @author Mitchell Herrijgers
 */
@Configuration
public class UniqueConstraintConfiguration {

    @Bean
    public UniqueConstraintValidator uniqueConstraintValidator(EventStore eventStore) {
        return UniqueConstraintValidator.builder()
                                        .eventStore(eventStore)
                                        .build();
    }
}
