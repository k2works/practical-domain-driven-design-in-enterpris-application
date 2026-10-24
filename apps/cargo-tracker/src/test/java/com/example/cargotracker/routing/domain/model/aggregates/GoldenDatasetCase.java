package com.example.cargotracker.routing.domain.model.aggregates;

import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionReasonCode;
import com.example.cargotracker.routing.domain.model.valueobjects.PortCall;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.StreamSupport;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Golden dataset v0 の 1 つの代表案件（US-06。Bolt 37f）。案件のデータ（{@code golden/v0/G-nn.json}。版・状態・判断根拠・規制資料・
 * 承認者・適用期間・入力・期待結果）を読む。集約の単位のテストは候補一覧の全体を期待結果（{@code expected}）と比べ、受入シナリオは
 * 例の表の要約を実際の候補と期待結果の両方と比べる（要約は例の表、全体はこのデータ）。値は仮の値で、業務責任者の承認（TST-02）を待つ。
 *
 * @param id 区分の番号（例: G-01）
 * @param version データの版（v0）
 * @param status 承認の状態（v0 は業務責任者の承認待ち）
 * @param rationale 判断根拠（業務ルール・決定）
 * @param approvers 承認者（要件定義では複数の経路設計者とコンプライアンス担当者。v0 は空）
 * @param routeSpecification 案件の条件（受入シナリオの背景の輸送条件にそろえる）
 * @param judgedAt 候補を算出する判定時刻
 * @param freshnessTolerance 情報鮮度の許容時間（受入の設定 {@code PT24H} と同じ値）
 * @param rules 必要最小接続時間の規則（案件のデータだけから入れる）
 * @param voyages 航海（採用情報版は「航海番号@1」）
 * @param expected 期待する候補一覧（集約が並べた順）
 */
public record GoldenDatasetCase(
        String id,
        String version,
        String status,
        List<String> rationale,
        List<String> approvers,
        RouteSpecification routeSpecification,
        UtcInstant judgedAt,
        Duration freshnessTolerance,
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
     * @param reasons 除外の理由（集約が付けた順）
     */
    public record ExpectedCandidate(
            String voyages,
            boolean conforming,
            UtcInstant estimatedArrivalAt,
            Duration connectionSlack,
            List<ExclusionReasonCode> reasons) {}

    public GoldenDatasetCase {
        Objects.requireNonNull(id, "id");
        rationale = List.copyOf(rationale);
        approvers = List.copyOf(approvers);
        rules = List.copyOf(rules);
        voyages = List.copyOf(voyages);
        expected = List.copyOf(expected);
    }

    /** {@code golden/v0/<id>.json} を読む。項目が欠けていたり型が違ったりしたら、案件の番号を添えて止める。 */
    public static GoldenDatasetCase load(String id) {
        try (InputStream in = GoldenDatasetCase.class.getResourceAsStream("/golden/v0/" + id + ".json")) {
            if (in == null) {
                throw new IllegalArgumentException("Golden dataset v0 の案件がありません: " + id);
            }
            return parse(JSON.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException("Golden dataset v0 の案件を読めません: " + id, e);
        } catch (JacksonException | IllegalArgumentException | IllegalStateException e) {
            throw new IllegalStateException("Golden dataset v0 の案件 " + id + " の形が違います: " + e.getMessage(), e);
        }
    }

    private static GoldenDatasetCase parse(JsonNode node) {
        JsonNode spec = node.required("routeSpecification");
        return new GoldenDatasetCase(
                text(node, "id"),
                text(node, "version"),
                text(node, "status"),
                list(node.required("rationale"), JsonNode::stringValue),
                list(node.required("approvers"), JsonNode::stringValue),
                new RouteSpecification(
                        new Location(text(spec, "origin")),
                        new Location(text(spec, "destination")),
                        at(spec, "arrivalDeadline"),
                        text(spec, "cargoCategory")),
                at(node, "judgedAt"),
                Duration.parse(text(node, "freshnessTolerance")),
                list(node.required("connectionRules"), GoldenDatasetCase::rule),
                list(node.required("voyages"), GoldenDatasetCase::voyage),
                list(node.required("expected"), GoldenDatasetCase::candidate));
    }

    private static ConnectionRule rule(JsonNode node) {
        String port = text(node, "port");
        return new ConnectionRule(
                // 判定は規則の ID を使わないが、再現できる値にする
                UUID.nameUUIDFromBytes(
                        ("golden-v0-" + port + "-" + text(node, "validFrom")).getBytes(StandardCharsets.UTF_8)),
                new Location(port),
                Duration.parse(text(node, "minimumConnection")),
                at(node, "validFrom"),
                null);
    }

    private static Voyage voyage(JsonNode node) {
        String number = text(node, "number");
        return new Voyage(
                number,
                List.of(
                        new PortCall(new Location(text(node, "load")), null, at(node, "departureAt")),
                        new PortCall(new Location(text(node, "discharge")), at(node, "arrivalAt"), null)),
                number + "@1",
                at(node, "acquiredAt"));
    }

    private static ExpectedCandidate candidate(JsonNode node) {
        JsonNode slack = node.required("connectionSlack");
        return new ExpectedCandidate(
                text(node, "voyages"),
                node.required("conforming").booleanValue(),
                at(node, "estimatedArrivalAt"),
                slack.isNull() ? null : Duration.parse(slack.stringValue()),
                list(node.required("reasons"), reason -> ExclusionReasonCode.valueOf(reason.stringValue())));
    }

    private static String text(JsonNode node, String field) {
        return node.required(field).stringValue();
    }

    private static <T> List<T> list(JsonNode array, Function<JsonNode, T> mapper) {
        return StreamSupport.stream(array.spliterator(), false).map(mapper).toList();
    }

    private static UtcInstant at(JsonNode node, String field) {
        return new UtcInstant(Instant.parse(text(node, field)));
    }
}
