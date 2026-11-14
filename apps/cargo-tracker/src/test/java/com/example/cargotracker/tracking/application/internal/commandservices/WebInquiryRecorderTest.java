package com.example.cargotracker.tracking.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.aggregates.InquiryRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.InquiryRecordRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquirerKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquiryChannel;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 顧客 Web の追跡照会の自動記録（IR-INV-01。Bolt 42-1）。同じ利用者・同じ追跡番号の 30 分以内の再表示は 1 件にまとめる。 */
class WebInquiryRecorderTest {

    private static final CompanyId COMPANY = new CompanyId(UUID.fromString("00000000-0000-0000-0000-0000042a0001"));
    private static final UserId USER = new UserId(UUID.fromString("00000000-0000-0000-0000-0000042a0002"));
    private static final UserId OTHER_USER = new UserId(UUID.fromString("00000000-0000-0000-0000-0000042a0003"));
    private static final TrackingNumber TRACKING = new TrackingNumber("CTABCDEFGH2345");
    private static final TrackingNumber OTHER_TRACKING = new TrackingNumber("CTABCDEFGH2346");
    private static final Instant NOW = Instant.parse("2026-11-16T01:00:00Z");

    /** 保存した記録を持つ入れ物。まとめる判定は保存した記録から行う。 */
    private static final class FakeRepository implements InquiryRecordRepository {
        final List<InquiryRecord> saved = new ArrayList<>();

        @Override
        public void save(InquiryRecord inquiryRecord) {
            saved.add(inquiryRecord);
        }

        @Override
        public boolean existsWebSelfAfter(UserId user, TrackingNumber trackingNumber, UtcInstant after) {
            return saved.stream()
                    .anyMatch(r -> r.channel() == InquiryChannel.WEB_SELF
                            && r.inquirerUserId().filter(user::equals).isPresent()
                            && r.trackingNumber().filter(trackingNumber::equals).isPresent()
                            && r.receivedAt().instant().isAfter(after.instant()));
        }
    }

    private final FakeRepository repository = new FakeRepository();

    private WebInquiryRecorder recorderAt(Instant now) {
        return new WebInquiryRecorder(repository, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void 行1_追跡の照会を顧客_Web_の自己照会として記録する() {
        recorderAt(NOW).recordSelfLookup(USER, COMPANY, TRACKING);

        assertThat(repository.saved).hasSize(1);
        InquiryRecord record = repository.saved.getFirst();
        assertThat(record.channel()).isEqualTo(InquiryChannel.WEB_SELF);
        assertThat(record.inquirerKind()).isEqualTo(InquirerKind.SHIPPER);
        assertThat(record.shipperCompanyId()).isEqualTo(COMPANY);
        assertThat(record.trackingNumber()).contains(TRACKING);
        assertThat(record.inquirerUserId()).contains(USER);
        assertThat(record.receivedAt()).isEqualTo(new UtcInstant(NOW));
        assertThat(record.recordedBy()).isEmpty();
        assertThat(record.escalated()).isFalse();
    }

    @Test
    void 行2_同じ利用者と追跡番号の30分以内の再表示は1件にまとめる() {
        recorderAt(NOW).recordSelfLookup(USER, COMPANY, TRACKING);

        recorderAt(NOW.plus(Duration.ofMinutes(29))).recordSelfLookup(USER, COMPANY, TRACKING);

        assertThat(repository.saved).hasSize(1);
    }

    @Test
    void 行3_30分ちょうどの再表示は別の照会として記録する_境界() {
        recorderAt(NOW).recordSelfLookup(USER, COMPANY, TRACKING);

        recorderAt(NOW.plus(Duration.ofMinutes(30))).recordSelfLookup(USER, COMPANY, TRACKING);

        assertThat(repository.saved).hasSize(2);
    }

    @Test
    void 行4_利用者か追跡番号が違えば別の照会として記録する() {
        recorderAt(NOW).recordSelfLookup(USER, COMPANY, TRACKING);

        recorderAt(NOW.plus(Duration.ofMinutes(1))).recordSelfLookup(OTHER_USER, COMPANY, TRACKING);
        recorderAt(NOW.plus(Duration.ofMinutes(2))).recordSelfLookup(USER, COMPANY, OTHER_TRACKING);

        assertThat(repository.saved).hasSize(3);
    }
}
