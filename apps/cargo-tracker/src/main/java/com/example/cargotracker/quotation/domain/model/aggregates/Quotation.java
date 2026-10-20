package com.example.cargotracker.quotation.domain.model.aggregates;

import com.example.cargotracker.quotation.domain.events.QuotationApprovedByShipper;
import com.example.cargotracker.quotation.domain.events.QuotationPresented;
import com.example.cargotracker.quotation.domain.events.QuotationPresentedForReapproval;
import com.example.cargotracker.quotation.domain.events.QuotationRouteAssigned;
import com.example.cargotracker.quotation.domain.events.RouteDesignRequested;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRoute;
import com.example.cargotracker.quotation.domain.model.valueobjects.PricingBasis;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationExpiry;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationViolations;
import com.example.cargotracker.quotation.domain.model.valueobjects.RouteAssignmentResult;
import com.example.cargotracker.quotation.domain.model.valueobjects.RoutePolicy;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipperApproval;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 見積り。審査済みの輸送要求版に対する料金根拠・有効期限・経路方針の提示（US-03。集約ルート）。
 * 作成中 → 承認待ち（算出する。Q-INV-05・17）→ 提示済み（社内承認して提示する。DE-03）→ 詳細設計依頼済み（荷主が詳細経路設計へ
 * 進むと回答する。DE-16。Bolt 12）→ 荷主承認待ち（経路設計が確定した経路版を割り当てる。DE-21）→ 承認済み（荷主が承認する。DE-04。
 * Bolt 20）の順に進む。
 * 承認待ち・提示済みの見積りは、再見積りで置換済み（有効期限を過ぎていれば失効）になる（Q-INV-07。Bolt 11）。荷主の承認の後に
 * 有効期限を過ぎた承認済みの見積りも、再見積りで失効になる。そのとき新しい見積りは経路・荷主の回答・経路方針を引き継ぎ、承認待ちから
 * 社内承認して提示すると荷主承認待ちに直行する（D-85。Bolt 35b）。
 * 失効は状態を書き換えずに判定時刻で決める（{@link #isExpiredAt}）。
 *
 * <p>輸送要求と別の集約にする理由は、見積りが版ごとに複数作られ（置換）、有効期限で単独に失効するため
 * （ドメインモデル「見積りを輸送要求の中に入れない理由」）。輸送要求の状態は DE-03 を受けて別のトランザクションで変える。
 */
@AggregateRoot
public final class Quotation {

    private static final long INITIAL_AGGREGATE_VERSION = 0;

    private final QuotationId id;
    private final TransportRequestId transportRequestId;
    private final int quotationNo;
    private final int transportRequestVersionNo;
    private final long aggregateVersion;
    private final List<Object> domainEvents = new ArrayList<>();
    private QuotationStatus status;
    private PricingBasis pricingBasis;
    private QuotationExpiry expiry;
    private RoutePolicy routePolicy;
    private UserId approvedBy;
    private UtcInstant presentedAt;
    private QuotationId replacedBy;
    private UserId respondedBy;
    private UtcInstant respondedAt;
    private AssignedRoute assignedRoute;
    private ShipperApproval shipperApproval;

    @SuppressWarnings("java:S107") // 保存されている状態から組み立てるため、集約の値をすべて受け取る
    private Quotation(
            QuotationId id,
            TransportRequestId transportRequestId,
            int quotationNo,
            int transportRequestVersionNo,
            QuotationStatus status,
            PricingBasis pricingBasis,
            QuotationExpiry expiry,
            RoutePolicy routePolicy,
            UserId approvedBy,
            UtcInstant presentedAt,
            QuotationId replacedBy,
            UserId respondedBy,
            UtcInstant respondedAt,
            AssignedRoute assignedRoute,
            ShipperApproval shipperApproval,
            long aggregateVersion) {
        this.id = Objects.requireNonNull(id, "id");
        this.transportRequestId = Objects.requireNonNull(transportRequestId, "transportRequestId");
        if (quotationNo < 1 || transportRequestVersionNo < 1) {
            throw new IllegalArgumentException("見積り番号と版番号は 1 以上です: " + quotationNo + "、" + transportRequestVersionNo);
        }
        this.quotationNo = quotationNo;
        this.transportRequestVersionNo = transportRequestVersionNo;
        this.status = Objects.requireNonNull(status, "status");
        this.pricingBasis = pricingBasis;
        this.expiry = expiry;
        this.routePolicy = routePolicy;
        this.approvedBy = approvedBy;
        this.presentedAt = presentedAt;
        this.replacedBy = replacedBy;
        this.respondedBy = respondedBy;
        this.respondedAt = respondedAt;
        this.assignedRoute = assignedRoute;
        this.shipperApproval = shipperApproval;
        this.aggregateVersion = aggregateVersion;
    }

    /**
     * 見積りを作る（作成中）。見積りを作れるか（Q-INV-18）は、輸送要求とほかの見積りを読むアプリケーションサービスが確かめる。
     *
     * @param transportRequestVersionNo 見積りの対象の輸送要求の版番号（現在の版）
     * @param quotationNo 見積り番号（輸送要求の中で 1 から）
     */
    public static Quotation create(
            QuotationId id, TransportRequestId transportRequestId, int transportRequestVersionNo, int quotationNo) {
        return new Quotation(
                id,
                transportRequestId,
                quotationNo,
                transportRequestVersionNo,
                QuotationStatus.DRAFT,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                INITIAL_AGGREGATE_VERSION);
    }

    /** 保存されている状態から見積りを組み立てる（リポジトリが使う）。イベントは生成しない。値のない項目は null。 */
    @SuppressWarnings("java:S107") // 保存されている状態から組み立てるため、集約の値をすべて受け取る
    public static Quotation reconstitute(
            QuotationId id,
            TransportRequestId transportRequestId,
            int quotationNo,
            int transportRequestVersionNo,
            QuotationStatus status,
            PricingBasis pricingBasis,
            QuotationExpiry expiry,
            RoutePolicy routePolicy,
            UserId approvedBy,
            UtcInstant presentedAt,
            QuotationId replacedBy,
            UserId respondedBy,
            UtcInstant respondedAt,
            AssignedRoute assignedRoute,
            ShipperApproval shipperApproval,
            long aggregateVersion) {
        return new Quotation(
                id,
                transportRequestId,
                quotationNo,
                transportRequestVersionNo,
                status,
                pricingBasis,
                expiry,
                routePolicy,
                approvedBy,
                presentedAt,
                replacedBy,
                respondedBy,
                respondedAt,
                assignedRoute,
                shipperApproval,
                aggregateVersion);
    }

    /**
     * 算出する（作成中 → 承認待ち）。入力を算出の時刻で検証し（Q-INV-05・17）、違反があれば作成中のまま違反を返す。
     *
     * @return 違反（算出したら空）
     * @throws IllegalStateException 作成中でない（算出し直しは入れていない）
     */
    public Optional<QuotationViolations> calculate(QuotationInput input, UtcInstant calculatedAt) {
        if (status != QuotationStatus.DRAFT) {
            throw new IllegalStateException("作成中でない見積りは算出できません: " + status);
        }
        return switch (input.validate(calculatedAt)) {
            case QuotationInput.Invalid(QuotationViolations violations) -> Optional.of(violations);
            case QuotationInput.Valid(PricingBasis basis, QuotationExpiry validExpiry, RoutePolicy policy) -> {
                pricingBasis = basis;
                expiry = validExpiry;
                routePolicy = policy;
                status = QuotationStatus.PENDING_APPROVAL;
                yield Optional.empty();
            }
        };
    }

    /**
     * 社内承認して提示する（承認待ち → 提示済み）。承認者と提示時刻を記録し（KPI-01）、DE-03 を生成する。
     * 承認者はログインした営業担当者（Bolt 14 まで仮の営業担当者。2026-10-05 の決定）。
     * 置換済み・失効の見積りと、提示の時刻に有効期限を過ぎた承認待ちの見積りは提示できない（Q-INV-07。Bolt 11）。
     *
     * @return 受け付けなかった理由（受け付けたら空）
     */
    public Optional<QuotationRejection> presentInternally(UserId approver, UtcInstant at) {
        Objects.requireNonNull(approver, "approver");
        Optional<QuotationRejection> rejection = presentRejectionAt(at);
        if (rejection.isPresent()) {
            return rejection;
        }
        approvedBy = approver;
        presentedAt = at;
        // 経路を引き継いだ再見積りは、荷主の回答を経ずに荷主承認待ちにする（D-85。DE-21 は出さない。Bolt 35b）
        status = assignedRoute == null ? QuotationStatus.PRESENTED : QuotationStatus.AWAITING_SHIPPER_APPROVAL;
        domainEvents.add(new QuotationPresented(
                id.value(),
                quotationNo,
                transportRequestId.value(),
                transportRequestVersionNo,
                expiry.expiresAt(),
                routePolicy.via().stream().map(Location::unLocode).toList(),
                routePolicy.departureAt(),
                routePolicy.arrivalAt(),
                at));
        if (status == QuotationStatus.AWAITING_SHIPPER_APPROVAL) {
            // 輸送要求を予約待ちから荷主承認待ちへ戻す（D-85。Bolt 35b2。ADR-018）
            domainEvents.add(new QuotationPresentedForReapproval(
                    id.value(), quotationNo, transportRequestId.value(), transportRequestVersionNo, at));
        }
        return Optional.empty();
    }

    /**
     * 再見積りで新しい見積りに置き換える（承認待ち・提示済み → 置換済み）。置き換える時刻に有効期限を過ぎていれば、
     * 置換済みでなく失効として記録し、置換先は持たない（2026-10-05 の決定。Bolt 11）。荷主の承認の後に失効した見積りも失効にする
     * （荷主の承認の記録は残す。D-85。Bolt 35b）。新しい見積りの作成は、同じトランザクションで
     * アプリケーションサービスが行う（Q-INV-18）。経路を持つ見積りは、引き継ぎを落とさないよう
     * {@link #requoteInto(Quotation, UtcInstant)} で置き換える（D-85。Bolt 35b レビュー）。
     *
     * @param replacement 新しい見積りの ID
     * @param at 置き換える時刻（判定時刻）
     * @return 受け付けなかった理由（{@link #requoteRejectionAt(UtcInstant)} と同じ。受け付けたら空）
     * @throws IllegalStateException 作成中の見積り（算出の前の見積りは保存されない）、または経路を持つ見積り
     */
    public Optional<QuotationRejection> replaceWith(QuotationId replacement, UtcInstant at) {
        Objects.requireNonNull(replacement, "replacement");
        Objects.requireNonNull(at, "at");
        Optional<QuotationRejection> rejection = requoteRejectionAt(at);
        if (rejection.isPresent()) {
            return rejection;
        }
        requireSaved();
        if (assignedRoute != null) {
            throw new IllegalStateException("経路を持つ見積りは新しい見積りへ引き継いで置き換えます: " + id);
        }
        retire(replacement, at);
        return Optional.empty();
    }

    /**
     * 再見積りする（D-85、Q-INV-07・18。Bolt 35b レビュー）。判定時刻に再見積りできるなら、経路を持つ旧版の経路・荷主の詳細経路設計の
     * 回答・経路方針を新しい見積りに引き継ぎ（入力の経路方針は使わない）、旧版を置換済みか失効にする。判定・引き継ぎ・置き換えを
     * 1 つの操作にして、呼び手の順序に頼らない。新しい見積りは社内承認して提示すると荷主承認待ちに直行する（経路設計者の作業を
     * 繰り返さない）。
     *
     * @param replacement 同じ輸送要求の同じ版で算出した、承認待ちの新しい見積り
     * @param at 再見積りの時刻（判定時刻）
     * @return 受け付けなかった理由（{@link #requoteRejectionAt(UtcInstant)} と同じ。受け付けたら空で、どちらも変えない）
     * @throws IllegalArgumentException 別の輸送要求か別の版の新しい見積り（呼び手の誤り）
     * @throws IllegalStateException 作成中の旧版、または承認待ちでない新しい見積り
     */
    public Optional<QuotationRejection> requoteInto(Quotation replacement, UtcInstant at) {
        Objects.requireNonNull(replacement, "replacement");
        Objects.requireNonNull(at, "at");
        Optional<QuotationRejection> rejection = requoteRejectionAt(at);
        if (rejection.isPresent()) {
            return rejection;
        }
        requireSaved();
        if (!replacement.transportRequestId.equals(transportRequestId)
                || replacement.transportRequestVersionNo != transportRequestVersionNo) {
            throw new IllegalArgumentException("別の輸送要求か別の版の見積りには置き換えられません: " + replacement.id);
        }
        if (replacement.status != QuotationStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("承認待ちの見積りだけに置き換えられます: " + replacement.id);
        }
        if (assignedRoute != null) {
            replacement.assignedRoute = assignedRoute;
            replacement.respondedBy = respondedBy;
            replacement.respondedAt = respondedAt;
            replacement.routePolicy = routePolicy;
        }
        retire(replacement.id, at);
        return Optional.empty();
    }

    private void requireSaved() {
        if (status == QuotationStatus.DRAFT) {
            throw new IllegalStateException("作成中の見積りは置換できません: " + id);
        }
    }

    /** 置換済みにする。置き換える時刻に有効期限を過ぎていれば失効にし、置換先を持たない。 */
    private void retire(QuotationId replacement, UtcInstant at) {
        if (isExpiredAt(at)) {
            status = QuotationStatus.EXPIRED;
        } else {
            status = QuotationStatus.REPLACED;
            replacedBy = replacement;
        }
    }

    /**
     * 荷主が詳細経路設計へ進むと回答する（提示済み → 詳細設計依頼済み。US-24 AC1、Q-INV-09）。回答者と回答時刻を記録し、
     * DE-16 を生成する。回答者は、認証（US-18）ができるまで仮の荷主の利用者（2026-10-06 の決定。Bolt 12）。
     *
     * @param respondent 回答した荷主担当者
     * @param at 回答時刻（判定時刻）
     * @return 受け付けなかった理由（受け付けたら空）
     */
    public Optional<QuotationRejection> requestRouteDesign(UserId respondent, UtcInstant at) {
        Objects.requireNonNull(respondent, "respondent");
        Optional<QuotationRejection> rejection = responseRejectionAt(at);
        if (rejection.isPresent()) {
            return rejection;
        }
        respondedBy = respondent;
        respondedAt = at;
        status = QuotationStatus.ROUTING_REQUESTED;
        domainEvents.add(new RouteDesignRequested(
                id.value(),
                quotationNo,
                transportRequestId.value(),
                transportRequestVersionNo,
                routePolicy.via().stream().map(Location::unLocode).toList(),
                routePolicy.departureAt(),
                routePolicy.arrivalAt(),
                expiry.expiresAt(),
                respondent.value(),
                at));
        return Optional.empty();
    }

    /**
     * 判定時刻に荷主が回答できないなら、その理由（Q-INV-07・09。Bolt 12）。置換済み・失効（記録または判定時刻で）を先に見て、
     * そうでなければ状態に応じて、詳細設計依頼済み（回答済み）か提示済みでないかを返す。画面の操作の出し分けもこれを使う。
     *
     * @return 回答できない理由（回答できるなら空）
     */
    public Optional<QuotationRejection> responseRejectionAt(UtcInstant at) {
        Objects.requireNonNull(at, "at");
        Optional<QuotationRejection> retired = retiredRejection();
        if (retired.isPresent()) {
            return retired;
        }
        if (isExpiredAt(at)) {
            return Optional.of(QuotationRejection.EXPIRED);
        }
        return switch (status) {
            case PRESENTED -> Optional.empty();
            case ROUTING_REQUESTED, AWAITING_SHIPPER_APPROVAL, APPROVED ->
                Optional.of(QuotationRejection.ROUTING_REQUESTED);
            case DRAFT, PENDING_APPROVAL, EXPIRED, REPLACED -> Optional.of(QuotationRejection.NOT_PRESENTED);
        };
    }

    /**
     * 経路設計が確定した経路版を割り当てる（詳細設計依頼済み → 荷主承認待ち。R-INV-11、ADR-014。Bolt 20）。
     *
     * @param route 割り当てる経路（経路版の写し）
     * @param at 割当て時刻
     * @return 割当ての結果
     */
    public RouteAssignmentResult assignRoute(AssignedRoute route, UtcInstant at) {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(at, "at");
        RouteAssignmentResult result =
                switch (status) {
                    case ROUTING_REQUESTED -> RouteAssignmentResult.ASSIGNED;
                    case AWAITING_SHIPPER_APPROVAL, APPROVED ->
                        assignedRoute.isSameVersionAs(route)
                                ? RouteAssignmentResult.ALREADY_ASSIGNED
                                : RouteAssignmentResult.ANOTHER_ROUTE_VERSION_ASSIGNED;
                    case EXPIRED, REPLACED -> RouteAssignmentResult.RETIRED;
                    case DRAFT, PENDING_APPROVAL, PRESENTED -> RouteAssignmentResult.NOT_ROUTING_REQUESTED;
                };
        if (result != RouteAssignmentResult.ASSIGNED) {
            return result;
        }
        assignedRoute = route;
        status = QuotationStatus.AWAITING_SHIPPER_APPROVAL;
        domainEvents.add(new QuotationRouteAssigned(
                id.value(),
                quotationNo,
                transportRequestId.value(),
                transportRequestVersionNo,
                route.routingCaseNumber(),
                route.routeVersionNo(),
                at));
        return result;
    }

    /**
     * 荷主が見積りと割り当てた経路を承認する（荷主承認待ち → 承認済み。US-24 AC4・AC5、Q-INV-07・10。Bolt 20）。
     *
     * @param approver 承認した荷主担当者
     * @param at 承認時刻（判定時刻）
     * @return 受け付けなかった理由（受け付けたら空）
     */
    public Optional<QuotationRejection> approveByShipper(UserId approver, UtcInstant at) {
        Objects.requireNonNull(approver, "approver");
        Optional<QuotationRejection> rejection = approvalRejectionAt(at);
        if (rejection.isPresent()) {
            return rejection;
        }
        shipperApproval = new ShipperApproval(approver, at);
        status = QuotationStatus.APPROVED;
        domainEvents.add(new QuotationApprovedByShipper(
                id.value(),
                quotationNo,
                transportRequestId.value(),
                transportRequestVersionNo,
                assignedRoute.routingCaseNumber(),
                assignedRoute.routeVersionNo(),
                approver.value(),
                at));
        return Optional.empty();
    }

    /**
     * 判定時刻に荷主が承認できないなら、その理由（Q-INV-07・10。Bolt 20）。置換済み・失効の記録を先に見て、承認済みなら
     * 期限を過ぎていても承認済みとして示し（二重送信の結果を誤らせない）、そうでなければ判定時刻で失効か、荷主承認待ちでないかを返す。
     * 再設計要・旧版の経路版の判定は W6（US-08、DE-06）で足す。画面の操作の出し分けもこれを使う。
     *
     * @return 承認できない理由（承認できるなら空）
     */
    public Optional<QuotationRejection> approvalRejectionAt(UtcInstant at) {
        Objects.requireNonNull(at, "at");
        Optional<QuotationRejection> retired = retiredRejection();
        if (retired.isPresent()) {
            return retired;
        }
        if (status == QuotationStatus.APPROVED) {
            return Optional.of(QuotationRejection.ALREADY_APPROVED);
        }
        if (isExpiredAt(at)) {
            return Optional.of(QuotationRejection.EXPIRED);
        }
        if (status != QuotationStatus.AWAITING_SHIPPER_APPROVAL) {
            return Optional.of(QuotationRejection.NOT_AWAITING_SHIPPER_APPROVAL);
        }
        return Optional.empty();
    }

    /**
     * 判定時刻に失効しているか（Q-INV-06・07）。失効を記録した見積りと、承認待ち・提示済み・詳細設計依頼済みで判定時刻が有効期限と同時刻または後の
     * 見積りは失効。置換済みと作成中は失効でない。
     */
    public boolean isExpiredAt(UtcInstant judgedAt) {
        Objects.requireNonNull(judgedAt, "judgedAt");
        return status.isExpiredAt(expiry, judgedAt);
    }

    /**
     * 判定時刻に社内承認して提示できないなら、その理由（Q-INV-07。Bolt 11 レビュー R-07・R-16）。置換済み・失効の記録・
     * 判定時刻で失効（承認待ち・提示済みとも）を先に見て、そうでなければ承認待ちかを見る。画面の操作の出し分けもこれを使う。
     *
     * @return 提示できない理由（提示できるなら空）
     */
    public Optional<QuotationRejection> presentRejectionAt(UtcInstant at) {
        Objects.requireNonNull(at, "at");
        Optional<QuotationRejection> retired = retiredRejection();
        if (retired.isPresent()) {
            return retired;
        }
        if (isExpiredAt(at)) {
            return Optional.of(QuotationRejection.EXPIRED);
        }
        if (status != QuotationStatus.PENDING_APPROVAL) {
            return Optional.of(QuotationRejection.NOT_PENDING_APPROVAL);
        }
        return Optional.empty();
    }

    /**
     * 判定時刻に再見積りできないなら、その理由（Q-INV-07、D-85。Bolt 35b）。置換済み・失効の記録を先に見て、承認済みなら判定時刻が
     * 有効期限より前（期限内）のときだけ拒み、期限を過ぎていれば受け付ける（旧版を失効にして経路を引き継ぐ）。詳細設計依頼済みと
     * 荷主承認待ちは経路設計の途中なので再見積りできない。承認待ち・提示済みは失効していても再見積りできる。
     * {@link #requoteInto(Quotation, UtcInstant)} と {@link #replaceWith(QuotationId, UtcInstant)} が使い、画面のボタン・GET と再見積りの
     * コマンドは社内の見積りの照会のサービスを通して使う（輸送要求の状態と版を先に見る。Bolt 11 レビュー R-07、D-85。Bolt 35c）。
     *
     * @param at 判定時刻
     * @return 再見積りできない理由（再見積りできるなら空）
     */
    public Optional<QuotationRejection> requoteRejectionAt(UtcInstant at) {
        Objects.requireNonNull(at, "at");
        if (status == QuotationStatus.APPROVED) {
            return isExpiredAt(at) ? Optional.empty() : Optional.of(QuotationRejection.APPROVED_AND_STILL_VALID);
        }
        Optional<QuotationRejection> retired = retiredRejection();
        if (retired.isPresent()) {
            return retired;
        }
        if (status.isRoutingStarted()) {
            return Optional.of(QuotationRejection.ROUTING_REQUESTED);
        }
        return Optional.empty();
    }

    private Optional<QuotationRejection> retiredRejection() {
        return switch (status) {
            case EXPIRED -> Optional.of(QuotationRejection.EXPIRED);
            case REPLACED -> Optional.of(QuotationRejection.REPLACED);
            case DRAFT, PENDING_APPROVAL, PRESENTED, ROUTING_REQUESTED, AWAITING_SHIPPER_APPROVAL, APPROVED ->
                Optional.empty();
        };
    }

    /**
     * commit 時刻に予約確定に使えないなら、その理由（Q-INV-06、BR-10、ADR-016。Bolt 23）。置換済み・失効の記録を先に見て、
     * 次に commit 時刻での失効（有効期限と同時刻以後）、最後に荷主の承認済みかを見る。予約は commit 時刻を渡して公開 API で問い合わせる。
     *
     * @param committedAt 予約確定の commit 時刻
     * @return 使えない理由（使えるなら空）
     */
    public Optional<QuotationRejection> bookingRejectionAt(UtcInstant committedAt) {
        Objects.requireNonNull(committedAt, "committedAt");
        Optional<QuotationRejection> retired = retiredRejection();
        if (retired.isPresent()) {
            return retired;
        }
        if (isExpiredAt(committedAt)) {
            return Optional.of(QuotationRejection.EXPIRED);
        }
        if (status != QuotationStatus.APPROVED) {
            return Optional.of(QuotationRejection.NOT_APPROVED);
        }
        return Optional.empty();
    }

    /** 荷主に見えるか（提示した見積りだけ。承認待ちと、提示する前に置き換えた見積りは見えない。Q-INV-08。Bolt 12 レビュー R-19）。 */
    public boolean isVisibleToShipper() {
        return presentedAt != null;
    }

    /**
     * 荷主にもう一度の承認を求めた見積りか（荷主の承認の後に失効した見積りから経路と回答を引き継いだ再見積りを提示した。D-85。Bolt 35d）。
     * {@link #isReapprovalPresentation} で決める。どの見積りから引き継いだかの列は持たない（Bolt 35b の決定）。
     */
    public boolean isPresentedForReapproval() {
        return isReapprovalPresentation(respondedAt, presentedAt);
    }

    /**
     * 荷主の回答の時刻が提示の時刻より前なら、再承認の見積り（見積りの集約と読み取りモデル C-02 で共有する。D-85。Bolt 35d）。荷主の回答は
     * 提示の後にしかできない（{@link #responseRejectionAt}）ので、通常の流れでは回答の時刻は提示の時刻と同時刻か後になる。回答を引き継いだ
     * 再見積り（{@link #requoteInto}）だけが、提示の前の回答の時刻を持つ。この集約の中で守る順だけを使う（経路設計の時計に頼らない。Bolt 35d の
     * 開発レビューで経路の確定の時刻から改めた）。再設計（US-08）や荷主の辞退で回答の扱いが変わるときは見直す（D-88）。
     *
     * @param respondedAt 荷主の回答の時刻（回答していなければ null）
     * @param presentedAt 提示の時刻（提示していなければ null）
     */
    public static boolean isReapprovalPresentation(UtcInstant respondedAt, UtcInstant presentedAt) {
        return respondedAt != null
                && presentedAt != null
                && respondedAt.instant().isBefore(presentedAt.instant());
    }

    /** 作成中・承認待ち・提示済み・詳細設計依頼済みか（1 つの輸送要求に 1 つだけ。失効・置換済みは数えない。Q-INV-18）。 */
    public boolean isActive() {
        return status.countsAsActive();
    }

    public QuotationId id() {
        return id;
    }

    public TransportRequestId transportRequestId() {
        return transportRequestId;
    }

    public int quotationNo() {
        return quotationNo;
    }

    public int transportRequestVersionNo() {
        return transportRequestVersionNo;
    }

    public QuotationStatus status() {
        return status;
    }

    public Optional<PricingBasis> pricingBasis() {
        return Optional.ofNullable(pricingBasis);
    }

    public Optional<QuotationExpiry> expiry() {
        return Optional.ofNullable(expiry);
    }

    public Optional<RoutePolicy> routePolicy() {
        return Optional.ofNullable(routePolicy);
    }

    /** 社内承認者（提示済みのとき）。 */
    public Optional<UserId> approvedBy() {
        return Optional.ofNullable(approvedBy);
    }

    /** 提示時刻（提示済みのとき。社内承認の時刻と同じ）。 */
    public Optional<UtcInstant> presentedAt() {
        return Optional.ofNullable(presentedAt);
    }

    /** 置換先の見積り（置換済みのとき）。 */
    public Optional<QuotationId> replacedBy() {
        return Optional.ofNullable(replacedBy);
    }

    /** 回答者（荷主が回答したとき）。 */
    public Optional<UserId> respondedBy() {
        return Optional.ofNullable(respondedBy);
    }

    /** 回答時刻（荷主が回答したとき）。 */
    public Optional<UtcInstant> respondedAt() {
        return Optional.ofNullable(respondedAt);
    }

    /** 割り当てた経路（荷主承認待ち・承認済みのとき）。 */
    public Optional<AssignedRoute> assignedRoute() {
        return Optional.ofNullable(assignedRoute);
    }

    /** 荷主承認（承認済みのとき）。 */
    public Optional<ShipperApproval> shipperApproval() {
        return Optional.ofNullable(shipperApproval);
    }

    /** 楽観ロックの版（読み込んだときの集約の版）。リポジトリが更新のときに照合する。 */
    public long aggregateVersion() {
        return aggregateVersion;
    }

    public List<Object> domainEvents() {
        return List.copyOf(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }
}
