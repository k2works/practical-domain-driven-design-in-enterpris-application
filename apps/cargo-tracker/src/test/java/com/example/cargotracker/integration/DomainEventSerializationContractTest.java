package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.quotation.domain.events.TransportRequestReviewed;
import com.example.cargotracker.quotation.domain.events.TransportRequestSubmitted;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
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

    @Autowired
    EventSerializer serializer;

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
}
