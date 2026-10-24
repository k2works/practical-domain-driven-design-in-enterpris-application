package com.example.cargotracker.routing.domain.model.aggregates;

import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionReasonCode;
import com.example.cargotracker.routing.domain.model.valueobjects.PortCall;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.StreamSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Golden dataset v0 の 1 つの代表案件（US-06。Bolt 37f）。案件のデータ（{@code golden/v0/G-nn.json}。版・判断根拠・規制資料・承認者・
 * 適用期間・入力・期待結果）を読む。受入シナリオ（区分ごとの要約）と集約の単位のテスト（候補一覧の全体）が同じデータを使い、期待結果は
 * データの 1 か所に置く。値は仮の値で、業務責任者の承認（TST-02）を待つ。
 *
 * @param id 区分の番号（例: G-01）
 * @param version データの版
 * @param approver 承認者（v0 は未承認）
 * @param routeSpecification 案件の条件（受入シナリオの背景の輸送条件にそろえる）
 * @param judgedAt 候補を算出する判定時刻
 * @param rules 必要最小接続時間の規則（案件のデータだけから入れる）
 * @param voyages 航海（採用情報版は「航海番号@1」）
 * @param expected 期待する候補一覧（集約が並べた順）
 */
public record GoldenDatasetCase(
        String id,
        String version,
        String approver,
        RouteSpecification routeSpecification,
        UtcInstant judgedAt,
        List<ConnectionRule> rules,
        List<Voyage> voyages,
        List<ExpectedCandidate> expected) {

    /** v0 の区分（G-04 は提出の拒否の別の feature、G-05 は v0 に入れない）。 */
    public static final List<String> V0_CASES = List.of("G-01", "G-02", "G-03", "G-06");

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 期待する候補。
     *
     * @param voyages 航海番号を「,」でつないだもの
     * @param conforming 適合か
     * @param estimatedArrivalAt 到着予定
     * @param connectionSlack 接続余裕（直行は空）
     * @param reasons 除外の理由（区間の順）
     */
    public record ExpectedCandidate(
            String voyages,
            boolean conforming,
            UtcInstant estimatedArrivalAt,
            Duration connectionSlack,
            List<ExclusionReasonCode> reasons) {}

    public GoldenDatasetCase {
        Objects.requireNonNull(id, "id");
        rules = List.copyOf(rules);
        voyages = List.copyOf(voyages);
        expected = List.copyOf(expected);
    }

    /** {@code golden/v0/<id>.json} を読む。 */
    public static GoldenDatasetCase load(String id) {
        try (InputStream in = GoldenDatasetCase.class.getResourceAsStream("/golden/v0/" + id + ".json")) {
            if (in == null) {
                throw new IllegalArgumentException("Golden dataset v0 の案件がありません: " + id);
            }
            return parse(JSON.readTree(in));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static GoldenDatasetCase parse(JsonNode node) {
        JsonNode spec = node.get("routeSpecification");
        return new GoldenDatasetCase(
                node.get("id").asString(),
                node.get("version").asString(),
                node.get("approver").asString(),
                new RouteSpecification(
                        new Location(spec.get("origin").asString()),
                        new Location(spec.get("destination").asString()),
                        at(spec.get("arrivalDeadline")),
                        spec.get("cargoCategory").asString()),
                at(node.get("judgedAt")),
                list(node.get("connectionRules"), GoldenDatasetCase::rule),
                list(node.get("voyages"), GoldenDatasetCase::voyage),
                list(node.get("expected"), GoldenDatasetCase::candidate));
    }

    private static ConnectionRule rule(JsonNode node) {
        return new ConnectionRule(
                UUID.randomUUID(),
                new Location(node.get("port").asString()),
                Duration.parse(node.get("minimumConnection").asString()),
                at(node.get("validFrom")),
                null);
    }

    private static Voyage voyage(JsonNode node) {
        String number = node.get("number").asString();
        return new Voyage(
                number,
                List.of(
                        new PortCall(new Location(node.get("load").asString()), null, at(node.get("departureAt"))),
                        new PortCall(new Location(node.get("discharge").asString()), at(node.get("arrivalAt")), null)),
                number + "@1",
                at(node.get("acquiredAt")));
    }

    private static ExpectedCandidate candidate(JsonNode node) {
        JsonNode slack = node.get("connectionSlack");
        return new ExpectedCandidate(
                node.get("voyages").asString(),
                node.get("conforming").asBoolean(),
                at(node.get("estimatedArrivalAt")),
                slack == null || slack.isNull() ? null : Duration.parse(slack.asString()),
                list(node.get("reasons"), reason -> ExclusionReasonCode.valueOf(reason.asString())));
    }

    private static <T> List<T> list(JsonNode array, java.util.function.Function<JsonNode, T> mapper) {
        return StreamSupport.stream(array.spliterator(), false).map(mapper).toList();
    }

    private static UtcInstant at(JsonNode node) {
        return new UtcInstant(Instant.parse(node.asString()));
    }
}
