package com.example.cargotracker.routing.domain.model.aggregates;

import com.example.cargotracker.routing.domain.events.RouteConfirmed;
import com.example.cargotracker.routing.domain.model.entities.RouteCandidate;
import com.example.cargotracker.routing.domain.model.entities.RouteVersion;
import com.example.cargotracker.routing.domain.model.rules.ConstraintEvaluator;
import com.example.cargotracker.routing.domain.model.rules.InfoFreshnessPolicy;
import com.example.cargotracker.routing.domain.model.rules.RouteCandidateFinder;
import com.example.cargotracker.routing.domain.model.valueobjects.CandidateCalculation;
import com.example.cargotracker.routing.domain.model.valueobjects.ConstraintEvaluation;
import com.example.cargotracker.routing.domain.model.valueobjects.DecisionRationale;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteApprover;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteConfirmation;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteConfirmationRejectionReason;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteVersionStatus;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseId;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 経路設計案件。1 つの輸送要求について経路版を作り、確定・再設計する単位（集約ルート）。
 * 荷主の詳細経路設計の依頼（DE-16）を受けて、輸送要求版ごとに 1 つ作る（R-INV-10）。Bolt 17 は経路版 1 の候補の算出まで。
 * Bolt 19 で経路版の一覧（Bolt 17 レビュー D-64）と確定（US-07 AC1・AC2）を足した。
 *
 * <p>輸送要求・見積りの ID と業務番号は、見積りの公開 API とイベントから得た値の写し（見積りのドメインの型を持たない。ADR-001）。
 */
@AggregateRoot
public final class RoutingCase {

    /** 示す候補の上限（Bolt 17 の確認ポイント 8）。 */
    public static final int MAX_CANDIDATES = 20;

    private static final long INITIAL_AGGREGATE_VERSION = 0;

    private final RoutingCaseId id;
    private final RoutingCaseNumber number;
    private final UUID transportRequestId;
    private final String transportRequestNumber;
    private final int transportRequestVersionNo;
    private final UUID quotationId;
    private final List<Location> routePolicyVia;
    private final RouteSpecification specification;
    private final UtcInstant requestedAt;
    private final UtcInstant quotationExpiresAt;
    private final UUID requestedBy;
    private final long aggregateVersion;
    private final List<RouteVersion> routeVersions;

    @SuppressWarnings("java:S107") // 保存されている状態から組み立てるため、集約の値をすべて受け取る
    private RoutingCase(
            RoutingCaseId id,
            RoutingCaseNumber number,
            UUID transportRequestId,
            String transportRequestNumber,
            int transportRequestVersionNo,
            UUID quotationId,
            List<Location> routePolicyVia,
            RouteSpecification specification,
            UtcInstant requestedAt,
            UtcInstant quotationExpiresAt,
            UUID requestedBy,
            List<RouteVersion> routeVersions,
            long aggregateVersion) {
        this.id = Objects.requireNonNull(id, "id");
        this.number = Objects.requireNonNull(number, "number");
        this.transportRequestId = Objects.requireNonNull(transportRequestId, "transportRequestId");
        this.transportRequestNumber = Objects.requireNonNull(transportRequestNumber, "transportRequestNumber");
        if (transportRequestVersionNo < 1) {
            throw new IllegalArgumentException("版番号は 1 以上です: " + transportRequestVersionNo);
        }
        this.transportRequestVersionNo = transportRequestVersionNo;
        this.quotationId = Objects.requireNonNull(quotationId, "quotationId");
        this.routePolicyVia = List.copyOf(routePolicyVia);
        this.specification = Objects.requireNonNull(specification, "specification");
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        this.quotationExpiresAt = quotationExpiresAt;
        this.requestedBy = requestedBy;
        this.routeVersions = new ArrayList<>(validated(routeVersions));
        this.aggregateVersion = aggregateVersion;
    }

    /**
     * 詳細経路設計の依頼を受けて案件を作る（経路版 1 は作成中）。同じ輸送要求版の案件がないことは、アプリケーションサービスが
     * 確かめ、リポジトリの一意制約が最後に守る（R-INV-10）。見積有効期限と依頼者は DE-16 の値の写し（Bolt 19）。
     */
    @SuppressWarnings("java:S107") // 依頼と経路条件の値をすべて受け取る
    public static RoutingCase open(
            RoutingCaseId id,
            RoutingCaseNumber number,
            UUID transportRequestId,
            String transportRequestNumber,
            int transportRequestVersionNo,
            UUID quotationId,
            List<Location> routePolicyVia,
            RouteSpecification specification,
            UtcInstant requestedAt,
            UtcInstant quotationExpiresAt,
            UUID requestedBy) {
        return new RoutingCase(
                id,
                number,
                transportRequestId,
                transportRequestNumber,
                transportRequestVersionNo,
                quotationId,
                routePolicyVia,
                specification,
                requestedAt,
                Objects.requireNonNull(quotationExpiresAt, "quotationExpiresAt"),
                Objects.requireNonNull(requestedBy, "requestedBy"),
                List.of(RouteVersion.draft(1)),
                INITIAL_AGGREGATE_VERSION);
    }

    /**
     * 保存されている状態から案件を組み立てる（リポジトリが使う）。見積有効期限と依頼者は、Bolt 19 より前に作った案件にはない。
     */
    @SuppressWarnings("java:S107") // 保存されている状態から組み立てるため、集約の値をすべて受け取る
    public static RoutingCase reconstitute(
            RoutingCaseId id,
            RoutingCaseNumber number,
            UUID transportRequestId,
            String transportRequestNumber,
            int transportRequestVersionNo,
            UUID quotationId,
            List<Location> routePolicyVia,
            RouteSpecification specification,
            UtcInstant requestedAt,
            UtcInstant quotationExpiresAt,
            UUID requestedBy,
            List<RouteVersion> routeVersions,
            long aggregateVersion) {
        return new RoutingCase(
                id,
                number,
                transportRequestId,
                transportRequestNumber,
                transportRequestVersionNo,
                quotationId,
                routePolicyVia,
                specification,
                requestedAt,
                quotationExpiresAt,
                requestedBy,
                routeVersions,
                aggregateVersion);
    }

    /**
     * 候補を算出する（作成中・候補提示済み → 候補提示済み。US-06 AC1）。航海の一覧から区間の列を列挙し（候補探索）、
     * それぞれを制約適合判定にかけて（規則は接続の時刻で選ぶ。D-90）、適合を先に、到着予定の早い順に上限まで残し、1 から候補番号を振る。
     * 制約適合判定の後で情報鮮度の判定を判定時刻でかけ、情報不足の理由を後ろに足す（US-06 AC4、R-INV-09。Bolt 37b2。
     * 制約適合判定は判定時刻を受け取らない。Bolt 37）。
     * 候補提示済みで算出し直したら、候補を置き換え、判定時刻を更新する。
     *
     * @throws IllegalStateException 作成中・候補提示済みでない（確定の後の再算出は US-08 の再設計で行う）
     */
    @SuppressWarnings("java:S107") // 算出の入力と、候補探索・制約適合判定・情報鮮度の判定をすべて受け取る（確定と同じ形）
    public CandidateCalculation calculateCandidates(
            List<Voyage> voyages,
            List<ConnectionRule> rules,
            UtcInstant judgedAt,
            RouteCandidateFinder finder,
            ConstraintEvaluator evaluator,
            InfoFreshnessPolicy freshness) {
        RouteVersion routeVersion = routeVersion();
        RouteVersionStatus status = routeVersion.status();
        if (status != RouteVersionStatus.DRAFT && status != RouteVersionStatus.CANDIDATES_PRESENTED) {
            throw new IllegalStateException("候補を算出できない経路版の状態です: " + status);
        }
        List<Evaluated> evaluated = finder.find(specification, voyages, judgedAt).stream()
                .map(legs -> new Evaluated(
                        legs,
                        evaluator
                                .evaluate(specification, legs, rules)
                                .withAddedReasons(freshness.evaluate(legs, judgedAt))))
                .sorted(PRESENTATION_ORDER)
                .toList();
        List<RouteCandidate> candidates = new ArrayList<>();
        for (Evaluated candidate : evaluated.subList(0, Math.min(MAX_CANDIDATES, evaluated.size()))) {
            candidates.add(
                    new RouteCandidate(candidates.size() + 1, candidate.legs(), candidate.evaluation(), judgedAt));
        }
        replaceLatest(new RouteVersion(
                routeVersion.routeVersionNo(),
                RouteVersionStatus.CANDIDATES_PRESENTED,
                candidates,
                judgedAt,
                evaluated.size(),
                null));
        int conforming = (int) candidates.stream()
                .filter(candidate -> candidate.evaluation().conforming())
                .count();
        return new CandidateCalculation(evaluated.size(), candidates.size(), conforming, judgedAt);
    }

    /**
     * 経路を確定する（候補提示済み → 確定。US-07 AC1〜AC3）。承認者が経路設計者で、選んだ候補が適合で、判断根拠があることを
     * 確かめ、確定のときに読み直した航海・接続時間規則と希望到着期限で判定し直す（R-INV-03）。規則は確定の時刻ではなく、
     * 接続の時刻（次の区間の出発予定）に有効なものを使う（D-90、Bolt 37。算出の後に登録・変更された規則を拾う）。
     * 確かめる順は、状態・候補・除外 → 情報版の更新（区間の採用情報版と読み直した航海の採用情報版が違う。航海がないときも）→
     * 判断根拠 → 出発済み（最初の区間が確定の時刻と同時刻または前に出発する）→ 情報不足（読み直した航海の取得時刻で、確定の時刻に
     * 許容時間を過ぎている）→ 規則の判定し直し（Bolt 37c）。候補の比較に戻す理由を判断根拠より前に確かめる。
     *
     * @return DE-05 経路を確定した
     * @throws RouteConfirmationRejected 確定できない（理由を値で持つ）
     */
    @SuppressWarnings("java:S107") // 確定の入力と、再検証に使う航海・規則・判定をすべて受け取る（算出と同じ形）
    public RouteConfirmed confirm(
            int candidateNo,
            String rationale,
            RouteApprover approver,
            List<Voyage> voyages,
            List<ConnectionRule> rules,
            UtcInstant commitAt,
            ConstraintEvaluator evaluator,
            InfoFreshnessPolicy freshness) {
        if (!approver.routeDesigner()) {
            throw reject(RouteConfirmationRejectionReason.NOT_ROUTE_DESIGNER, "経路を確定できるのは経路設計者だけです");
        }
        RouteVersion routeVersion = routeVersion();
        if (!confirmable()) {
            throw reject(
                    RouteConfirmationRejectionReason.NOT_CONFIRMABLE_STATE, "確定できない経路版の状態です: " + routeVersion.status());
        }
        RouteCandidate candidate = routeVersion.candidates().stream()
                .filter(each -> each.candidateNo() == candidateNo)
                .findFirst()
                .orElseThrow(() ->
                        reject(RouteConfirmationRejectionReason.CANDIDATE_NOT_FOUND, "候補 " + candidateNo + " はありません"));
        if (!candidate.evaluation().conforming()) {
            throw reject(RouteConfirmationRejectionReason.CANDIDATE_EXCLUDED, "除外の候補は確定できません");
        }
        // 読み直した航海の全件（航海番号はデータベースの主キーで重ならない）
        Map<String, Voyage> current =
                voyages.stream().collect(Collectors.toMap(Voyage::voyageNumber, voyage -> voyage));
        Map<String, String> currentInfoVersions = new HashMap<>();
        current.forEach((voyageNumber, voyage) -> currentInfoVersions.put(voyageNumber, voyage.adoptedInfoVersion()));
        if (!candidate.legsWithChangedInfo(currentInfoVersions).isEmpty()) {
            throw reject(RouteConfirmationRejectionReason.INFO_VERSION_CHANGED, "候補を算出した後に区間の航海の情報版が更新されました");
        }
        DecisionRationale.rejectionOf(rationale).ifPresent(reason -> {
            throw reject(
                    reason,
                    reason == RouteConfirmationRejectionReason.RATIONALE_MISSING
                            ? "判断根拠を入れてください"
                            : "判断根拠は " + DecisionRationale.MAX_LENGTH + " 文字までです");
        });
        DecisionRationale decisionRationale = DecisionRationale.of(rationale);
        if (!candidate.legs().getFirst().departureAt().instant().isAfter(commitAt.instant())) {
            throw reject(RouteConfirmationRejectionReason.ALREADY_DEPARTED, "最初の区間が出発済みです");
        }
        // 航海がない区間は情報版の更新で先に拒否しているが、判定の順に頼らないよう、ないときも情報不足として扱う
        if (candidate.legs().stream()
                .anyMatch(leg -> Optional.ofNullable(current.get(leg.voyageNumber()))
                        .map(voyage -> freshness.insufficient(voyage.acquiredAt(), commitAt))
                        .orElse(true))) {
            throw reject(RouteConfirmationRejectionReason.INFO_INSUFFICIENT, "区間の航海の情報が取得から許容時間を過ぎています");
        }
        if (!evaluator.evaluate(specification, candidate.legs(), rules).conforming()) {
            throw reject(RouteConfirmationRejectionReason.NO_LONGER_CONFORMING, "確定のときに判定し直すと不適合です");
        }
        replaceLatest(new RouteVersion(
                routeVersion.routeVersionNo(),
                RouteVersionStatus.CONFIRMED,
                routeVersion.candidates(),
                routeVersion.candidatesEvaluatedAt(),
                routeVersion.candidatesFound(),
                new RouteConfirmation(candidateNo, decisionRationale, approver.userId(), commitAt)));
        return new RouteConfirmed(
                id.value(),
                number.text(),
                routeVersion.routeVersionNo(),
                quotationId,
                transportRequestId,
                transportRequestVersionNo,
                approver.userId(),
                commitAt,
                candidate.legs().stream().map(Leg::infoVersion).toList());
    }

    private static RouteConfirmationRejected reject(RouteConfirmationRejectionReason reason, String message) {
        return new RouteConfirmationRejected(reason, message);
    }

    private void replaceLatest(RouteVersion latest) {
        routeVersions.set(routeVersions.size() - 1, latest);
    }

    /** 経路版は 1 つ以上で、経路版番号が 1 から順に並び、確定は 1 つだけ（R-INV-05）。 */
    private static List<RouteVersion> validated(List<RouteVersion> routeVersions) {
        if (routeVersions.isEmpty()) {
            throw new IllegalArgumentException("経路設計案件には経路版が要ります");
        }
        for (int i = 0; i < routeVersions.size(); i++) {
            if (routeVersions.get(i).routeVersionNo() != i + 1) {
                throw new IllegalArgumentException("経路版番号は 1 から順に並べます: " + routeVersions);
            }
        }
        long confirmed = routeVersions.stream()
                .filter(version -> version.status() == RouteVersionStatus.CONFIRMED)
                .count();
        if (confirmed > 1) {
            throw new IllegalArgumentException("確定した経路版は案件に 1 つだけです");
        }
        return routeVersions;
    }

    /** 判定した区間の列（候補番号を振る前）。 */
    private record Evaluated(List<Leg> legs, ConstraintEvaluation evaluation) {}

    /** 示す順。適合を先に、到着予定の早い順、同時刻は航海番号の順（確定的にする）。 */
    private static final Comparator<Evaluated> PRESENTATION_ORDER = Comparator.comparing(
                    (Evaluated candidate) -> !candidate.evaluation().conforming())
            .thenComparing(
                    candidate -> candidate.evaluation().estimatedArrivalAt().instant())
            .thenComparing(candidate -> String.join(
                    ",", candidate.legs().stream().map(Leg::voyageNumber).toList()));

    public RoutingCaseId id() {
        return id;
    }

    public RoutingCaseNumber number() {
        return number;
    }

    public UUID transportRequestId() {
        return transportRequestId;
    }

    public String transportRequestNumber() {
        return transportRequestNumber;
    }

    public int transportRequestVersionNo() {
        return transportRequestVersionNo;
    }

    public UUID quotationId() {
        return quotationId;
    }

    /** 経路方針（参考）の主な経由地。判定には使わない。 */
    public List<Location> routePolicyVia() {
        return routePolicyVia;
    }

    public RouteSpecification specification() {
        return specification;
    }

    public UtcInstant requestedAt() {
        return requestedAt;
    }

    /** 見積有効期限（DE-16 の写し。Bolt 19 より前に作った案件にはない）。経路設計は期限で確定を拒否しない。 */
    public Optional<UtcInstant> quotationExpiresAt() {
        return Optional.ofNullable(quotationExpiresAt);
    }

    /** 詳細経路設計を依頼した荷主担当者の利用者 ID（DE-16 の写し。Bolt 19 より前に作った案件にはない）。 */
    public Optional<UUID> requestedBy() {
        return Optional.ofNullable(requestedBy);
    }

    /** いまの（最新の）経路版。 */
    public RouteVersion routeVersion() {
        return routeVersions.getLast();
    }

    /** 経路版の一覧（経路版番号の順）。 */
    public List<RouteVersion> routeVersions() {
        return List.copyOf(routeVersions);
    }

    /** いまの経路版を確定できるか（候補提示済みで、確定した経路版がない）。画面の入口と確定の判定が使う。 */
    public boolean confirmable() {
        return routeVersion().status() == RouteVersionStatus.CANDIDATES_PRESENTED
                && confirmedRouteVersion().isEmpty();
    }

    /** 確定した経路版（案件に 1 つだけ。R-INV-05）。 */
    public Optional<RouteVersion> confirmedRouteVersion() {
        return routeVersions.stream()
                .filter(version -> version.status() == RouteVersionStatus.CONFIRMED)
                .findFirst();
    }

    /**
     * 確定した経路版の、確定の記録の候補（区間の列）。見積りへの割当て（DE-05 の listener）と、追跡の予定（経路設計の公開 API。Bolt 25）が使う。
     * 経路版は不変なので、確定した後はいつ引いても同じ候補を返す。状態ではなく確定の記録で引くので、再設計要・旧版の経路版でも
     * 確定のときの候補を返す（旧情報版の保持。R-INV-07。Bolt 37d）。
     *
     * @param routeVersionNo 経路版番号
     * @return 確定の記録の候補。経路版がない、または確定の記録を持たなければ空
     */
    public Optional<RouteCandidate> confirmedCandidateOf(int routeVersionNo) {
        return routeVersions.stream()
                .filter(version -> version.routeVersionNo() == routeVersionNo)
                .findFirst()
                .flatMap(version -> version.confirmation()
                        .flatMap(confirmation -> version.candidates().stream()
                                .filter(candidate -> candidate.candidateNo() == confirmation.candidateNo())
                                .findFirst()));
    }

    public long aggregateVersion() {
        return aggregateVersion;
    }
}
