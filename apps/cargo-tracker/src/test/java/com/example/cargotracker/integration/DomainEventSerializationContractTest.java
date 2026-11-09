package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.booking.domain.events.BookingConfirmed;
import com.example.cargotracker.externaldata.domain.events.ExternalRecordQuarantined;
import com.example.cargotracker.externaldata.domain.events.MilestoneAdoptedValueAccepted;
import com.example.cargotracker.externaldata.domain.events.ReceivedRecordDecided;
import com.example.cargotracker.externaldata.domain.events.VoyageAdoptedValueUpdated;
import com.example.cargotracker.externaldata.domain.events.VoyageAdoptedValueUpdated.AdoptedPortCall;
import com.example.cargotracker.quotation.domain.events.TransportRequestReviewed;
import com.example.cargotracker.quotation.domain.events.TransportRequestSubmitted;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.events.TrackingStarted;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.core.EventSerializer;

/**
 * ドメインイベントの直列化の契約（バックエンドアーキテクチャのイベントの進化の規則）。
 * イベント発行記録には JSON で保存されるため、型・部品名（共有カーネルの record を含む）を変えると、
 * 未完了の発行記録を復元できなくなる。固定した JSON から復元できることを確かめる。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DomainEventSerializationContractTest {

    /** Bolt 1〜3 の形（業務番号なし）。発行記録に残っていても復元できなければならない。 */
    private static final String TRANSPORT_REQUEST_SUBMITTED_V1 = """
            {"transportRequestId":"11111111-1111-1111-1111-111111111111","versionNo":1,\
            "shipperCompanyId":{"value":"00000000-0000-0000-0000-000000000001"},\
            "submittedAt":{"instant":"2026-10-05T01:00:00.123456Z"}}""";

    /** Bolt 4 で業務番号（null を許す部品）を足した形。 */
    private static final String TRANSPORT_REQUEST_SUBMITTED_V2 = """
            {"transportRequestId":"11111111-1111-1111-1111-111111111111","versionNo":1,\
            "shipperCompanyId":{"value":"00000000-0000-0000-0000-000000000001"},\
            "submittedAt":{"instant":"2026-10-05T01:00:00.123456Z"},\
            "transportRequestNumber":"TR-2026-0001"}""";

    private static final TransportRequestSubmitted EVENT_V1 = new TransportRequestSubmitted(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            1,
            new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
            new UtcInstant(Instant.parse("2026-10-05T01:00:00.123456Z")),
            null);

    private static final TransportRequestSubmitted EVENT_V2 = new TransportRequestSubmitted(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            1,
            new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
            new UtcInstant(Instant.parse("2026-10-05T01:00:00.123456Z")),
            "TR-2026-0001");

    /** DE-02 輸送要求を審査した（Bolt 5）。判断は文字列で持つ（イベントはドメインの型を持たない）。 */
    private static final String TRANSPORT_REQUEST_REVIEWED_V1 = """
            {"transportRequestId":"11111111-1111-1111-1111-111111111111","versionNo":1,"decision":"APPROVED",\
            "reviewerId":{"value":"00000000-0000-0000-0000-000000000301"},\
            "decidedAt":{"instant":"2026-10-05T03:00:00.123456Z"}}""";

    private static final TransportRequestReviewed REVIEWED_V1 = new TransportRequestReviewed(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            1,
            "APPROVED",
            new UserId(UUID.fromString("00000000-0000-0000-0000-000000000301")),
            new UtcInstant(Instant.parse("2026-10-05T03:00:00.123456Z")));

    /** DE-07 本予約を確定した（Bolt 23）。追跡が購読する公表された言語（ADR-015）。 */
    private static final String BOOKING_CONFIRMED_V1 = """
            {"bookingId":"22222222-2222-2222-2222-222222222222","bookingVersionNo":1,\
            "trackingNumber":"CTABCDEFGH2345","quotationId":"33333333-3333-3333-3333-333333333333",\
            "transportRequestId":"11111111-1111-1111-1111-111111111111","transportRequestVersionNo":1,\
            "transportRequestNumber":"TR-2026-0001","routingCaseNumber":"RC-2026-0001","routeVersionNo":1,\
            "committedAt":{"instant":"2026-10-08T08:59:00.123456Z"},"aggregateVersion":0}""";

    /** 荷主・荷受人の企業 ID のない Bolt 23・24 の DE-07 は、企業 ID を null として復元する（追跡は警告のログで開始しない。Bolt 25）。 */
    private static final BookingConfirmed BOOKING_CONFIRMED = new BookingConfirmed(
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            1,
            "CTABCDEFGH2345",
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            1,
            "TR-2026-0001",
            "RC-2026-0001",
            1,
            new UtcInstant(Instant.parse("2026-10-08T08:59:00.123456Z")),
            0,
            null,
            null);

    /** DE-07 に荷主・荷受人の企業 ID を足した形（Bolt 25。追跡記録に要る値。区間は追跡が経路設計の公開 API で引く。ADR-015）。 */
    private static final String BOOKING_CONFIRMED_V2 = """
            {"bookingId":"22222222-2222-2222-2222-222222222222","bookingVersionNo":1,\
            "trackingNumber":"CTABCDEFGH2345","quotationId":"33333333-3333-3333-3333-333333333333",\
            "transportRequestId":"11111111-1111-1111-1111-111111111111","transportRequestVersionNo":1,\
            "transportRequestNumber":"TR-2026-0001","routingCaseNumber":"RC-2026-0001","routeVersionNo":1,\
            "committedAt":{"instant":"2026-10-08T08:59:00.123456Z"},"aggregateVersion":0,\
            "shipperCompanyId":{"value":"44444444-4444-4444-4444-444444444444"},\
            "consigneeCompanyId":{"value":"55555555-5555-5555-5555-555555555555"}}""";

    private static final BookingConfirmed BOOKING_CONFIRMED_WITH_COMPANIES = new BookingConfirmed(
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            1,
            "CTABCDEFGH2345",
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            1,
            "TR-2026-0001",
            "RC-2026-0001",
            1,
            new UtcInstant(Instant.parse("2026-10-08T08:59:00.123456Z")),
            0,
            new CompanyId(UUID.fromString("44444444-4444-4444-4444-444444444444")),
            new CompanyId(UUID.fromString("55555555-5555-5555-5555-555555555555")));

    /** DE-22 追跡を開始した（Bolt 25）。追跡の中だけで購読する。 */
    private static final String TRACKING_STARTED_V1 = """
            {"trackingNumber":"CTABCDEFGH2345","bookingId":"22222222-2222-2222-2222-222222222222",\
            "startedAt":{"instant":"2026-10-08T09:00:00.123456Z"},"aggregateVersion":0}""";

    private static final TrackingStarted TRACKING_STARTED = new TrackingStarted(
            "CTABCDEFGH2345",
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            new UtcInstant(Instant.parse("2026-10-08T09:00:00.123456Z")),
            0);

    private static final UserId DATA_STEWARD = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000501"));
    private static final CompanyId DATA_STEWARD_COMPANY =
            new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    /**
     * DE-12 航海の採用値を更新した（Bolt 39b3）。経路設計（Bolt 39e）と監査（39b3b）が購読する公表された言語。寄港は入れ子の record
     * {@code AdoptedPortCall}（空の時刻は null）、出典は共有カーネルの {@code Source}（参照は受信記録 ID）。
     */
    private static final String VOYAGE_ADOPTED_VALUE_UPDATED_V1 = """
            {"adoptedValueId":"66666666-6666-6666-6666-666666666666",\
            "ingestionId":"77777777-7777-7777-7777-777777777777",\
            "dataSourceId":"88888888-8888-8888-8888-888888888888",\
            "source":{"kind":"EXTERNAL_RECORD","reference":"99999999-9999-9999-9999-999999999999",\
            "acquiredAt":{"instant":"2026-11-03T08:30:00Z"}},\
            "voyageNumber":"V2611E",\
            "portCalls":[{"port":{"unLocode":"JPTYO"},"arrivalAt":null,"departureAt":{"instant":"2026-11-19T23:00:00Z"}},\
            {"port":{"unLocode":"USLAX"},"arrivalAt":{"instant":"2026-12-01T14:00:00Z"},"departureAt":null}],\
            "infoVersion":"3",\
            "operatorUserId":{"value":"00000000-0000-0000-0000-000000000501"},\
            "operatorCompanyId":{"value":"00000000-0000-0000-0000-000000000001"},\
            "publishedAt":{"instant":"2026-11-03T09:00:00.123456Z"}}""";

    private static final VoyageAdoptedValueUpdated VOYAGE_ADOPTED_VALUE_UPDATED = new VoyageAdoptedValueUpdated(
            UUID.fromString("66666666-6666-6666-6666-666666666666"),
            UUID.fromString("77777777-7777-7777-7777-777777777777"),
            UUID.fromString("88888888-8888-8888-8888-888888888888"),
            new Source(
                    SourceKind.EXTERNAL_RECORD,
                    "99999999-9999-9999-9999-999999999999",
                    new UtcInstant(Instant.parse("2026-11-03T08:30:00Z"))),
            "V2611E",
            List.of(
                    new AdoptedPortCall(
                            new Location("JPTYO"), null, new UtcInstant(Instant.parse("2026-11-19T23:00:00Z"))),
                    new AdoptedPortCall(
                            new Location("USLAX"), new UtcInstant(Instant.parse("2026-12-01T14:00:00Z")), null)),
            "3",
            DATA_STEWARD,
            DATA_STEWARD_COMPANY,
            new UtcInstant(Instant.parse("2026-11-03T09:00:00.123456Z")));

    /** DE-13 主要実績の採用値を受け付けた（Bolt 39b3）。追跡（39g）と監査（39b3b）が購読する。追跡番号と種類は文字列で持つ。 */
    private static final String MILESTONE_ADOPTED_VALUE_ACCEPTED_V1 = """
            {"adoptedValueId":"66666666-6666-6666-6666-666666666666",\
            "ingestionId":"77777777-7777-7777-7777-777777777777",\
            "dataSourceId":"88888888-8888-8888-8888-888888888888",\
            "source":{"kind":"EXTERNAL_RECORD","reference":"99999999-9999-9999-9999-999999999999",\
            "acquiredAt":{"instant":"2026-11-03T08:30:00Z"}},\
            "trackingNumber":"CTABCDEFGH2345","kind":"DEPARTURE","location":{"unLocode":"JPTYO"},\
            "occurredAt":{"instant":"2026-11-03T08:00:00Z"},"externalId":"MS-0001","infoVersion":"7",\
            "operatorUserId":{"value":"00000000-0000-0000-0000-000000000501"},\
            "operatorCompanyId":{"value":"00000000-0000-0000-0000-000000000001"},\
            "publishedAt":{"instant":"2026-11-03T09:00:00.123456Z"}}""";

    private static final MilestoneAdoptedValueAccepted MILESTONE_ADOPTED_VALUE_ACCEPTED =
            new MilestoneAdoptedValueAccepted(
                    UUID.fromString("66666666-6666-6666-6666-666666666666"),
                    UUID.fromString("77777777-7777-7777-7777-777777777777"),
                    UUID.fromString("88888888-8888-8888-8888-888888888888"),
                    new Source(
                            SourceKind.EXTERNAL_RECORD,
                            "99999999-9999-9999-9999-999999999999",
                            new UtcInstant(Instant.parse("2026-11-03T08:30:00Z"))),
                    "CTABCDEFGH2345",
                    "DEPARTURE",
                    new Location("JPTYO"),
                    new UtcInstant(Instant.parse("2026-11-03T08:00:00Z")),
                    "MS-0001",
                    "7",
                    DATA_STEWARD,
                    DATA_STEWARD_COMPANY,
                    new UtcInstant(Instant.parse("2026-11-03T09:00:00.123456Z")));

    /** DE-14 外部原本を隔離した（Bolt 39b3）。監査（39b3b）が購読する。キーのない隔離（ファイルの誤り）は外部 ID・外部版・物理行が null。 */
    private static final String EXTERNAL_RECORD_QUARANTINED_KEYLESS_V1 = """
            {"receivedRecordId":"99999999-9999-9999-9999-999999999999",\
            "ingestionId":"77777777-7777-7777-7777-777777777777",\
            "dataSourceId":"88888888-8888-8888-8888-888888888888",\
            "provider":"運送会社A","recordType":"MILESTONE","externalId":null,"externalVersion":null,\
            "reason":"UNDECODABLE","firstLine":null,"lastLine":null,\
            "operatorUserId":{"value":"00000000-0000-0000-0000-000000000501"},\
            "operatorCompanyId":{"value":"00000000-0000-0000-0000-000000000001"},\
            "quarantinedAt":{"instant":"2026-11-03T09:00:00.123456Z"}}""";

    private static final ExternalRecordQuarantined EXTERNAL_RECORD_QUARANTINED_KEYLESS = new ExternalRecordQuarantined(
            UUID.fromString("99999999-9999-9999-9999-999999999999"),
            UUID.fromString("77777777-7777-7777-7777-777777777777"),
            UUID.fromString("88888888-8888-8888-8888-888888888888"),
            "運送会社A",
            "MILESTONE",
            null,
            null,
            "UNDECODABLE",
            null,
            null,
            DATA_STEWARD,
            DATA_STEWARD_COMPANY,
            new UtcInstant(Instant.parse("2026-11-03T09:00:00.123456Z")));

    /** DE-29 受信記録を判断した（Bolt 39d2）。判断は文字列で持つ（イベントはドメインの型を持たない）。 */
    private static final String RECEIVED_RECORD_DECIDED_V1 = """
            {"receivedRecordId":"99999999-9999-9999-9999-999999999999",\
            "ingestionId":"77777777-7777-7777-7777-777777777777",\
            "dataSourceId":"88888888-8888-8888-8888-888888888888",\
            "decision":"REJECTED","reason":"提供元に確かめた; 誤送信",\
            "decidedBy":{"value":"00000000-0000-0000-0000-000000000501"},\
            "decidedByCompanyId":{"value":"00000000-0000-0000-0000-000000000001"},\
            "decidedAt":{"instant":"2026-11-16T01:05:00.123456Z"}}""";

    private static final ReceivedRecordDecided RECEIVED_RECORD_DECIDED = new ReceivedRecordDecided(
            UUID.fromString("99999999-9999-9999-9999-999999999999"),
            UUID.fromString("77777777-7777-7777-7777-777777777777"),
            UUID.fromString("88888888-8888-8888-8888-888888888888"),
            "REJECTED",
            "提供元に確かめた; 誤送信",
            DATA_STEWARD,
            DATA_STEWARD_COMPANY,
            new UtcInstant(Instant.parse("2026-11-16T01:05:00.123456Z")));

    @Autowired
    EventSerializer serializer;

    @Test
    void Bolt39d2_行10_保存済みのDE29のJSONから復元できる() {
        assertThat(serializer.deserialize(RECEIVED_RECORD_DECIDED_V1, ReceivedRecordDecided.class))
                .isEqualTo(RECEIVED_RECORD_DECIDED);
    }

    @Test
    void Bolt39d2_行10_DE29のJSONの形が変わっていない() {
        assertThat(serializer.serialize(RECEIVED_RECORD_DECIDED)).isEqualTo(RECEIVED_RECORD_DECIDED_V1);
    }

    @Test
    void 行10_保存済みのDE12のJSONから寄港の並びと出典を復元できる() {
        assertThat(serializer.deserialize(VOYAGE_ADOPTED_VALUE_UPDATED_V1, VoyageAdoptedValueUpdated.class))
                .isEqualTo(VOYAGE_ADOPTED_VALUE_UPDATED);
    }

    @Test
    void 行10_DE12のJSONの形が変わっていない() {
        assertThat(serializer.serialize(VOYAGE_ADOPTED_VALUE_UPDATED)).isEqualTo(VOYAGE_ADOPTED_VALUE_UPDATED_V1);
    }

    @Test
    void 行10_保存済みのDE13のJSONから復元できる() {
        assertThat(serializer.deserialize(MILESTONE_ADOPTED_VALUE_ACCEPTED_V1, MilestoneAdoptedValueAccepted.class))
                .isEqualTo(MILESTONE_ADOPTED_VALUE_ACCEPTED);
    }

    @Test
    void 行10_DE13のJSONの形が変わっていない() {
        assertThat(serializer.serialize(MILESTONE_ADOPTED_VALUE_ACCEPTED))
                .isEqualTo(MILESTONE_ADOPTED_VALUE_ACCEPTED_V1);
    }

    @Test
    void 行10_保存済みのキーのない隔離のDE14のJSONからnullを復元できる() {
        assertThat(serializer.deserialize(EXTERNAL_RECORD_QUARANTINED_KEYLESS_V1, ExternalRecordQuarantined.class))
                .isEqualTo(EXTERNAL_RECORD_QUARANTINED_KEYLESS);
    }

    @Test
    void 行10_キーのない隔離のDE14のJSONの形が変わっていない() {
        assertThat(serializer.serialize(EXTERNAL_RECORD_QUARANTINED_KEYLESS))
                .isEqualTo(EXTERNAL_RECORD_QUARANTINED_KEYLESS_V1);
    }

    @Test
    void 業務番号のない保存済みのDE01のJSONから業務番号をnullとして復元できる() {
        assertThat(serializer.deserialize(TRANSPORT_REQUEST_SUBMITTED_V1, TransportRequestSubmitted.class))
                .isEqualTo(EVENT_V1);
    }

    @Test
    void 業務番号のある保存済みのDE01のJSONから復元できる() {
        assertThat(serializer.deserialize(TRANSPORT_REQUEST_SUBMITTED_V2, TransportRequestSubmitted.class))
                .isEqualTo(EVENT_V2);
    }

    @Test
    void DE01のJSONの形が変わっていない() {
        assertThat(serializer.serialize(EVENT_V2)).isEqualTo(TRANSPORT_REQUEST_SUBMITTED_V2);
    }

    @Test
    void 保存済みのDE02のJSONから復元できる() {
        assertThat(serializer.deserialize(TRANSPORT_REQUEST_REVIEWED_V1, TransportRequestReviewed.class))
                .isEqualTo(REVIEWED_V1);
    }

    @Test
    void DE02のJSONの形が変わっていない() {
        assertThat(serializer.serialize(REVIEWED_V1)).isEqualTo(TRANSPORT_REQUEST_REVIEWED_V1);
    }

    @Test
    void 保存済みのDE07のJSONから復元できる() {
        assertThat(serializer.deserialize(BOOKING_CONFIRMED_V1, BookingConfirmed.class))
                .isEqualTo(BOOKING_CONFIRMED);
    }

    @Test
    void 企業IDのある保存済みのDE07のJSONから復元できる() {
        assertThat(serializer.deserialize(BOOKING_CONFIRMED_V2, BookingConfirmed.class))
                .isEqualTo(BOOKING_CONFIRMED_WITH_COMPANIES);
    }

    @Test
    void DE07のJSONの形が変わっていない() {
        assertThat(serializer.serialize(BOOKING_CONFIRMED_WITH_COMPANIES)).isEqualTo(BOOKING_CONFIRMED_V2);
    }

    @Test
    void DE07のJSONはH2の発行記録の列の4000文字に収まる() {
        assertThat(serializer.serialize(BOOKING_CONFIRMED_WITH_COMPANIES).toString())
                .hasSizeLessThan(4000);
    }

    @Test
    void 保存済みのDE22のJSONから復元できる() {
        assertThat(serializer.deserialize(TRACKING_STARTED_V1, TrackingStarted.class))
                .isEqualTo(TRACKING_STARTED);
    }

    @Test
    void DE22のJSONの形が変わっていない() {
        assertThat(serializer.serialize(TRACKING_STARTED)).isEqualTo(TRACKING_STARTED_V1);
    }
}
