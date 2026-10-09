package com.example.cargotracker.booking.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.domain.model.BookingFixture;
import com.example.cargotracker.shared.domain.CommandId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 処理済みコマンド（B-INV-03。Bolt 24）。同じコマンド ID の再送が同じ内容かを、業務番号・見積り番号・操作者で照合する。 */
class ProcessedCommandTest {

    private static final CommandId COMMAND = new CommandId(UUID.fromString("00000000-0000-0000-0000-0000000000c1"));
    private static final UserId SALES = new UserId(BookingFixture.SALES);
    private static final UtcInstant NOW = new UtcInstant(Instant.parse("2026-10-08T08:59:00Z"));

    private final ProcessedCommand processed = ProcessedCommand.confirmBooking(
            COMMAND, "TR-2026-0001", 1, SALES, new BookingId(UUID.randomUUID()), BookingFixture.TRACKING_NUMBER, NOW);

    @Test
    void 内容の照合値はSHA256の16進64文字で同じ内容なら同じ値になる() {
        String hash = ProcessedCommand.confirmBookingPayloadHash("TR-2026-0001", 1, SALES);

        assertThat(hash).matches("[0-9a-f]{64}");
        assertThat(processed.payloadHash()).isEqualTo(hash);
    }

    @Test
    void 業務番号と見積り番号と操作者が同じなら同じ内容とする() {
        assertThat(processed.sameContentAs(ProcessedCommand.confirmBookingPayloadHash("TR-2026-0001", 1, SALES)))
                .isTrue();
    }

    @Test
    void 業務番号か見積り番号か操作者が違えば違う内容とする() {
        assertThat(processed.sameContentAs(ProcessedCommand.confirmBookingPayloadHash("TR-2026-0002", 1, SALES)))
                .isFalse();
        assertThat(processed.sameContentAs(ProcessedCommand.confirmBookingPayloadHash("TR-2026-0001", 2, SALES)))
                .isFalse();
        assertThat(processed.sameContentAs(
                        ProcessedCommand.confirmBookingPayloadHash("TR-2026-0001", 1, new UserId(UUID.randomUUID()))))
                .isFalse();
    }

    @Test
    void 本予約の確定の内容が同じかを業務番号と見積り番号と操作者で答える() {
        assertThat(processed.isSameConfirmation("TR-2026-0001", 1, SALES)).isTrue();
        assertThat(processed.isSameConfirmation("TR-2026-0001", 2, SALES)).isFalse();
        assertThat(processed.isSameConfirmation("TR-2026-0001", 1, new UserId(UUID.randomUUID())))
                .isFalse();
    }

    @Test
    void 業務番号と見積り番号の区切りで別の内容が同じ照合値にならない() {
        assertThat(ProcessedCommand.confirmBookingPayloadHash("TR-2026-00011", 1, SALES))
                .isNotEqualTo(ProcessedCommand.confirmBookingPayloadHash("TR-2026-0001", 11, SALES));
    }
}
