package com.example.cargotracker.tracking.domain.model.entities;

import static com.example.cargotracker.tracking.domain.model.TrackingFixture.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalOrigin;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 主要実績の外部の識別の不変条件（Bolt 39g の行 1b。表の CHECK と同じ規則をドメインでも守る。8 引数のコンストラクタで作れるため）。 */
class MilestoneTest {

    private static final UtcInstant AT = at("2026-11-19T23:00:00Z");
    private static final UserId REGISTRANT = new UserId(UUID.fromString("00000000-0000-0000-0000-00000000390a"));
    private static final ExternalOrigin ORIGIN = new ExternalOrigin(UUID.randomUUID(), "MS-0001", AT);

    private static Milestone milestone(SourceKind sourceKind, ExternalOrigin origin) {
        return new Milestone(
                1,
                MilestoneKind.DEPARTURE,
                new Location("JPTYO"),
                AT,
                new Source(sourceKind, "R-1", AT),
                MilestoneState.DRAFT,
                REGISTRANT,
                AT,
                origin);
    }

    @Nested
    class Bolt39g_外部の識別の不変条件 {

        static Stream<Arguments> inconsistentPairs() {
            return Stream.of(
                    Arguments.of("外部原本で外部の識別がない", SourceKind.EXTERNAL_RECORD, null),
                    Arguments.of("現場記録で外部の識別がある", SourceKind.FIELD_RECORD, ORIGIN),
                    Arguments.of("社内確認で外部の識別がある", SourceKind.INTERNAL_CHECK, ORIGIN),
                    Arguments.of("手動入力で外部の識別がある", SourceKind.MANUAL_ENTRY, ORIGIN));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("inconsistentPairs")
        void 行1b_出典の種類と外部の識別がそろわない組は作れない(String description, SourceKind sourceKind, ExternalOrigin origin) {
            assertThatThrownBy(() -> milestone(sourceKind, origin))
                    .as(description)
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 行1b_外部原本は外部の識別を持てる() {
            assertThat(milestone(SourceKind.EXTERNAL_RECORD, ORIGIN).externalOrigin())
                    .isEqualTo(ORIGIN);
        }

        @Test
        void 行1b_8引数のコンストラクタで作った外部原本の実績は例外になる() {
            Source external = new Source(SourceKind.EXTERNAL_RECORD, "R-1", AT);
            Location tokyo = new Location("JPTYO");

            assertThatThrownBy(() -> new Milestone(
                            1, MilestoneKind.DEPARTURE, tokyo, AT, external, MilestoneState.DRAFT, REGISTRANT, AT))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 行1b_手入力の実績は外部の識別なしで作れる() {
            Milestone manual = new Milestone(
                    1,
                    MilestoneKind.DEPARTURE,
                    new Location("JPTYO"),
                    AT,
                    new Source(SourceKind.MANUAL_ENTRY, "R-1", AT),
                    MilestoneState.ADOPTED,
                    REGISTRANT,
                    AT);

            assertThat(manual.externalOrigin()).isNull();
        }
    }
}
