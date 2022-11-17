package org.axonframework.extensions.uniqueconstraint;

import org.axonframework.eventsourcing.eventstore.EventStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UniqueConstraintConfiguration {

    @Bean
    public UniqueConstraintValidator uniqueConstraintValidator(EventStore eventStore) {
        return UniqueConstraintValidator.builder()
                                        .eventStore(eventStore)
                                        .build();
    }
}
