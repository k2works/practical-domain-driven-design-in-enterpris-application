package com.example.cargotracker.operations.infrastructure.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.operations.domain.model.aggregates.IncompletePublications;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.events.core.TargetEventPublication;

/** 発行記録の未完了を Spring Modulith の公開の型で読む（W10-1 の Bolt 57-2。SQL は書かない）。 */
@SpringBootTest
class ModulithIncompletePublicationsIntegrationTest {

    /** 直列化できれば何でもよい、このテストだけのイベント。 */
    record BacklogProbe(String name) {}

    @Autowired
    IncompletePublications publications;

    @Autowired
    EventPublicationRepository repository;

    @Test
    void 指定の時刻より前に発行された未完了の発行の時刻だけを返し完了した発行は返さない() {
        // ほかのテストの発行と重ならない遠い過去にする（共有の DB では自分の行だけを比べる）
        Instant base = Instant.parse("2001-01-01T00:00:00Z").truncatedTo(ChronoUnit.MICROS);
        TargetEventPublication incomplete = create("incomplete", base);
        TargetEventPublication completed = create("completed", base.plusSeconds(1));
        TargetEventPublication later = create("later", base.plus(Duration.ofDays(1)));
        repository.markCompleted(completed.getIdentifier(), Instant.now());
        try {
            List<Instant> found = publications.publishedBefore(base.plus(Duration.ofHours(1)));

            assertThat(found).containsExactly(base);
        } finally {
            repository.deletePublications(
                    List.of(incomplete.getIdentifier(), completed.getIdentifier(), later.getIdentifier()));
        }
    }

    private TargetEventPublication create(String name, Instant publishedAt) {
        return repository.create(TargetEventPublication.of(
                new BacklogProbe(name), PublicationTargetIdentifier.of("backlog-probe." + name), publishedAt));
    }
}
