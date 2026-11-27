package com.example.cargotracker.operations.infrastructure.events;

import com.example.cargotracker.operations.domain.model.aggregates.IncompletePublications;
import java.time.Instant;
import java.util.List;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.stereotype.Component;

/**
 * Spring Modulith の発行記録から読む。表（{@code platform.event_publication}）は直接読まない。イベントの内容は取り出さない。
 */
@Component
public class ModulithIncompletePublications implements IncompletePublications {

    private final EventPublicationRepository repository;

    public ModulithIncompletePublications(EventPublicationRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Instant> publishedBefore(Instant before) {
        return repository.findIncompletePublicationsPublishedBefore(before).stream()
                .map(EventPublication::getPublicationDate)
                .toList();
    }
}
