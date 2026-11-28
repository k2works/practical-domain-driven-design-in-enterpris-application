package com.example.cargotracker.operations.domain.model.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.operations.domain.model.valueobjects.BacklogSeverity;
import com.example.cargotracker.operations.domain.model.valueobjects.EventBacklog;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** OBS-03（発行から 5 分以上の未完了のイベント配信。警告は 1 件以上、重大は 10 件以上か 30 分以上）。W10-1 の Bolt 57-2。 */
class EventBacklogPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-27T03:00:00Z");

    private static Instant ago(Duration age) {
        return NOW.minus(age);
    }

    @Test
    void 未完了がなければ異常なし() {
        assertThat(EventBacklogPolicy.assess(List.of(), NOW).severity()).isEqualTo(BacklogSeverity.NONE);
    }

    @Test
    void 発行から5分に満たない未完了は数えない() {
        EventBacklog backlog =
                EventBacklogPolicy.assess(List.of(ago(Duration.ofMinutes(5).minusSeconds(1))), NOW);

        assertThat(backlog.staleCount()).isZero();
        assertThat(backlog.severity()).isEqualTo(BacklogSeverity.NONE);
    }

    @Test
    void ちょうど5分の未完了が1件なら警告() {
        EventBacklog backlog = EventBacklogPolicy.assess(List.of(ago(Duration.ofMinutes(5))), NOW);

        assertThat(backlog).isEqualTo(new EventBacklog(1, Duration.ofMinutes(5), BacklogSeverity.WARNING));
    }

    @Test
    void 滞留が9件までは警告で10件で重大() {
        Instant stale = ago(Duration.ofMinutes(6));

        assertThat(EventBacklogPolicy.assess(Collections.nCopies(9, stale), NOW).severity())
                .isEqualTo(BacklogSeverity.WARNING);
        assertThat(EventBacklogPolicy.assess(Collections.nCopies(10, stale), NOW)
                        .severity())
                .isEqualTo(BacklogSeverity.CRITICAL);
    }

    @Test
    void 最も古い滞留が30分に達したら1件でも重大() {
        assertThat(EventBacklogPolicy.assess(List.of(ago(Duration.ofMinutes(30).minusSeconds(1))), NOW)
                        .severity())
                .isEqualTo(BacklogSeverity.WARNING);

        EventBacklog backlog =
                EventBacklogPolicy.assess(List.of(ago(Duration.ofMinutes(6)), ago(Duration.ofMinutes(30))), NOW);

        assertThat(backlog).isEqualTo(new EventBacklog(2, Duration.ofMinutes(30), BacklogSeverity.CRITICAL));
    }
}
