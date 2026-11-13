package com.example.cargotracker.tracking.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.entities.CaseAnswer;
import com.example.cargotracker.tracking.domain.model.entities.CaseProgress;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.MissingItem;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseDraft;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceDeadlines;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServicePriority;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/** 有人案件の集約（Bolt 40-5。SC-INV-01〜03、US-20 AC1〜AC7）。計画の行 1〜12。 */
class ServiceCaseTest {

    private static final CaseNumber NUMBER = new CaseNumber("SC-2026-0001");
    private static final UtcInstant OPENED_AT = at("2026-11-16T01:00:00Z");
    private static final ServiceDeadlines DEADLINES = new ServiceDeadlines(
            Instant.parse("2026-11-16T01:00:00Z"),
            Instant.parse("2026-11-16T05:00:00Z"),
            Instant.parse("2026-11-18T01:00:00Z"));
    private static final UserId STAFF = new UserId(UUID.fromString("00000000-0000-0000-0000-0000040c5001"));
    private static final CompanyId SHIPPER = new CompanyId(UUID.fromString("00000000-0000-0000-0000-0000040c5002"));
    private static final UUID BOOKING = UUID.fromString("00000000-0000-0000-0000-0000040c5003");
    private static final TrackingNumber TRACKING = new TrackingNumber("CTABCDEFGH2345");

    private static UtcInstant at(String instant) {
        return new UtcInstant(Instant.parse(instant));
    }

    private static Source source() {
        return new Source(SourceKind.INTERNAL_CHECK, "check-1", at("2026-11-16T00:00:00Z"));
    }

    /** 種類ごとに必須項目をそろえた下書き。 */
    private static ServiceCaseDraft complete(ServiceCaseKind kind) {
        return switch (kind) {
            case CUSTOMER_INQUIRY, QUOTATION_CONSULTATION ->
                ServiceCaseDraft.of(kind, ServicePriority.NORMAL, "荷主の照会", "カスタマーサポート")
                        .withShipper(SHIPPER);
            case AMENDMENT, REDESIGN ->
                ServiceCaseDraft.of(kind, ServicePriority.NORMAL, "変更の要求", "運用")
                        .withBooking(BOOKING)
                        .withVersions("V1", "V2");
            case EXTERNAL_OUTAGE ->
                ServiceCaseDraft.of(kind, ServicePriority.HIGH, "外部停止", "運用").withManualSource("manual-1");
            case REJECTED_MILESTONE ->
                ServiceCaseDraft.of(kind, ServicePriority.NORMAL, "拒否した実績", "運用")
                        .withTracking(TRACKING)
                        .withSources(List.of(source()));
            case SAGA_FAILURE ->
                ServiceCaseDraft.of(kind, ServicePriority.HIGH, "予約サガの失敗", "運用").withBooking(BOOKING);
        };
    }

    private static ServiceCase opened(ServiceCaseKind kind) {
        return ((ServiceCaseOpening.Opened) ServiceCase.open(complete(kind), NUMBER, DEADLINES, OPENED_AT))
                .serviceCase();
    }

    private static ServiceCase inProgress() {
        return opened(ServiceCaseKind.CUSTOMER_INQUIRY).receive(STAFF, at("2026-11-16T02:00:00Z"));
    }

    private static ServiceCase answered() {
        return inProgress().recordAnswer("回答します", STAFF, at("2026-11-16T03:00:00Z"));
    }

    // --- 行 1・2: 起票と必須項目 ---

    static Stream<Arguments> missing() {
        return Stream.of(
                Arguments.of(
                        "原因がない",
                        ServiceCaseDraft.of(ServiceCaseKind.CUSTOMER_INQUIRY, ServicePriority.NORMAL, " ", "CS")
                                .withShipper(SHIPPER),
                        Set.of(MissingItem.CAUSE)),
                Arguments.of(
                        "担当部門がない",
                        ServiceCaseDraft.of(ServiceCaseKind.CUSTOMER_INQUIRY, ServicePriority.NORMAL, "原因", "")
                                .withShipper(SHIPPER),
                        Set.of(MissingItem.DEPARTMENT)),
                Arguments.of(
                        "照会に荷主企業がない",
                        ServiceCaseDraft.of(ServiceCaseKind.CUSTOMER_INQUIRY, ServicePriority.NORMAL, "原因", "CS"),
                        Set.of(MissingItem.SHIPPER_COMPANY)),
                Arguments.of(
                        "見積り相談に荷主企業がない",
                        ServiceCaseDraft.of(ServiceCaseKind.QUOTATION_CONSULTATION, ServicePriority.NORMAL, "原因", "CS"),
                        Set.of(MissingItem.SHIPPER_COMPANY)),
                Arguments.of(
                        "変更に予約も追跡番号もなく旧版・新版もない",
                        ServiceCaseDraft.of(ServiceCaseKind.AMENDMENT, ServicePriority.NORMAL, "原因", "運用"),
                        Set.of(MissingItem.BOOKING_OR_TRACKING, MissingItem.OLD_VERSION, MissingItem.NEW_VERSION)),
                Arguments.of(
                        "再設計に新版がない",
                        ServiceCaseDraft.of(ServiceCaseKind.REDESIGN, ServicePriority.NORMAL, "原因", "運用")
                                .withTracking(TRACKING)
                                .withVersions("V1", null),
                        Set.of(MissingItem.NEW_VERSION)),
                Arguments.of(
                        "外部停止に手動入力の出典がない",
                        ServiceCaseDraft.of(ServiceCaseKind.EXTERNAL_OUTAGE, ServicePriority.HIGH, "原因", "運用"),
                        Set.of(MissingItem.MANUAL_SOURCE)),
                Arguments.of(
                        "登録拒否に追跡番号も出典もない",
                        ServiceCaseDraft.of(ServiceCaseKind.REJECTED_MILESTONE, ServicePriority.NORMAL, "原因", "運用"),
                        Set.of(MissingItem.TRACKING_NUMBER, MissingItem.SOURCES)),
                Arguments.of(
                        "サガの失敗に予約がない",
                        ServiceCaseDraft.of(ServiceCaseKind.SAGA_FAILURE, ServicePriority.HIGH, "原因", "運用"),
                        Set.of(MissingItem.BOOKING)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("missing")
    void 行1_必須項目が足りない起票は集約を作らず不足の項目を返す(String name, ServiceCaseDraft draft, Set<MissingItem> expected) {
        ServiceCaseOpening opening = ServiceCase.open(draft, NUMBER, DEADLINES, OPENED_AT);

        assertThat(opening).isInstanceOf(ServiceCaseOpening.Incomplete.class);
        assertThat(((ServiceCaseOpening.Incomplete) opening).missing()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ServiceCaseKind.class)
    void 行2_必須項目がそろえば受付済みで期限と次回更新日時を写す(ServiceCaseKind kind) {
        ServiceCase serviceCase = opened(kind);

        assertThat(serviceCase.number()).isEqualTo(NUMBER);
        assertThat(serviceCase.kind()).isEqualTo(kind);
        assertThat(serviceCase.status()).isEqualTo(ServiceCaseStatus.RECEIVED);
        assertThat(serviceCase.receiveDueAt()).isEqualTo(new UtcInstant(DEADLINES.receiptDue()));
        assertThat(serviceCase.answerDueAt()).isEqualTo(new UtcInstant(DEADLINES.responseDue()));
        assertThat(serviceCase.nextUpdateAt()).isEqualTo(new UtcInstant(DEADLINES.nextUpdateAt()));
        assertThat(serviceCase.assignee()).isEmpty();
        assertThat(serviceCase.progress()).isEmpty();
        assertThat(serviceCase.answers()).isEmpty();
        // 外部停止は種類で復旧照合が必要、ほかは指定がなければ不要
        assertThat(serviceCase.reconciliationRequired()).isEqualTo(kind == ServiceCaseKind.EXTERNAL_OUTAGE);
    }

    @Test
    void 行2_復旧照合の指定があれば種類にかかわらず必要() {
        ServiceCaseDraft draft = complete(ServiceCaseKind.CUSTOMER_INQUIRY).requiringReconciliation();

        ServiceCase serviceCase =
                ((ServiceCaseOpening.Opened) ServiceCase.open(draft, NUMBER, DEADLINES, OPENED_AT)).serviceCase();

        assertThat(serviceCase.reconciliationRequired()).isTrue();
    }

    // --- 行 3: 営業時間外の優先度高 ---

    @Test
    void 行3_数え始めが起票より後なら起票の時刻を受付の時刻に持つ() {
        ServiceDeadlines afterHours = new ServiceDeadlines(
                Instant.parse("2026-11-17T00:00:00Z"),
                Instant.parse("2026-11-17T01:00:00Z"),
                Instant.parse("2026-11-17T09:00:00Z"));
        UtcInstant lateNight = at("2026-11-16T10:00:00Z");

        ServiceCase serviceCase = ((ServiceCaseOpening.Opened)
                        ServiceCase.open(complete(ServiceCaseKind.EXTERNAL_OUTAGE), NUMBER, afterHours, lateNight))
                .serviceCase();

        assertThat(serviceCase.acknowledgedAt()).contains(lateNight);
        assertThat(serviceCase.nextUpdateAt()).isEqualTo(new UtcInstant(afterHours.receiptDue()));
    }

    @Test
    void 行3_営業時間内は受付の時刻を持たない() {
        assertThat(opened(ServiceCaseKind.EXTERNAL_OUTAGE).acknowledgedAt()).isEmpty();
    }

    // --- 行 4: 受領 ---

    @Test
    void 行4_受領すると担当者と受領時刻を記録して対応中になる() {
        ServiceCase received = opened(ServiceCaseKind.CUSTOMER_INQUIRY).receive(STAFF, at("2026-11-16T02:00:00Z"));

        assertThat(received.status()).isEqualTo(ServiceCaseStatus.IN_PROGRESS);
        assertThat(received.assignee()).contains(STAFF);
        assertThat(received.receivedAt()).contains(at("2026-11-16T02:00:00Z"));
    }

    @Test
    void 行4_上位対応中も受領できる() {
        ServiceCase escalated = opened(ServiceCaseKind.CUSTOMER_INQUIRY)
                .escalate(at("2026-11-16T06:00:00Z"))
                .orElseThrow();

        assertThat(escalated.receive(STAFF, at("2026-11-16T07:00:00Z")).status())
                .isEqualTo(ServiceCaseStatus.IN_PROGRESS);
    }

    @Test
    void 行4_対応中の案件は受領できない() {
        ServiceCase received = inProgress();
        UserId other = new UserId(UUID.randomUUID());
        UtcInstant now = at("2026-11-16T03:00:00Z");

        assertThatThrownBy(() -> received.receive(other, now)).isInstanceOf(IllegalStateException.class);
    }

    // --- 行 5: escalation ---

    @Test
    void 行5_受領期限を過ぎた受付済みは上位対応中になる() {
        Optional<ServiceCase> escalated =
                opened(ServiceCaseKind.CUSTOMER_INQUIRY).escalate(at("2026-11-16T05:00:01Z"));

        assertThat(escalated)
                .hasValueSatisfying(
                        serviceCase -> assertThat(serviceCase.status()).isEqualTo(ServiceCaseStatus.ESCALATED));
    }

    @Test
    void 行5_受領期限ちょうどまでは変えない() {
        assertThat(opened(ServiceCaseKind.CUSTOMER_INQUIRY).escalate(at("2026-11-16T05:00:00Z")))
                .isEmpty();
    }

    @Test
    void 行5_受付済み以外は変えない() {
        assertThat(inProgress().escalate(at("2026-11-20T00:00:00Z"))).isEmpty();
    }

    // --- 行 6: 進捗 ---

    @Test
    void 行6_進捗を更新すると進捗番号を採番して次回更新日時を更新する() {
        UtcInstant next = at("2026-11-17T01:00:00Z");

        ServiceCase updated = inProgress().updateProgress("調査中", next, STAFF, at("2026-11-16T02:30:00Z"));
        ServiceCase updatedAgain = updated.updateProgress("手配した", null, STAFF, at("2026-11-16T04:00:00Z"));

        assertThat(updated.progress()).singleElement().satisfies(progress -> {
            assertThat(progress.progressNo()).isEqualTo(1);
            assertThat(progress.content()).isEqualTo("調査中");
            assertThat(progress.nextUpdateAt()).contains(next);
            assertThat(progress.updatedBy()).isEqualTo(STAFF);
        });
        assertThat(updated.nextUpdateAt()).isEqualTo(next);
        assertThat(updatedAgain.progress()).extracting(CaseProgress::progressNo).containsExactly(1, 2);
        // 次回更新日時を指定しなければ今のまま
        assertThat(updatedAgain.nextUpdateAt()).isEqualTo(next);
    }

    @Test
    void 行6_対応中以外は進捗を更新できない() {
        ServiceCase received = opened(ServiceCaseKind.CUSTOMER_INQUIRY);
        UtcInstant now = at("2026-11-16T02:30:00Z");

        assertThatThrownBy(() -> received.updateProgress("調査中", null, STAFF, now))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 行6_進捗の内容が空なら拒む() {
        ServiceCase received = inProgress();
        UtcInstant now = at("2026-11-16T02:30:00Z");

        assertThatThrownBy(() -> received.updateProgress(" ", null, STAFF, now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- 行 7: 回答 ---

    @Test
    void 行7_回答を記録すると回答済みになり回答番号を採番する() {
        ServiceCase answered = answered();

        assertThat(answered.status()).isEqualTo(ServiceCaseStatus.ANSWERED);
        assertThat(answered.answers()).singleElement().satisfies(answer -> {
            assertThat(answer.answerNo()).isEqualTo(1);
            assertThat(answer.content()).isEqualTo("回答します");
            assertThat(answer.customerResponse()).isEmpty();
        });
    }

    @Test
    void 行7_対応中以外は回答を記録できない() {
        ServiceCase received = opened(ServiceCaseKind.CUSTOMER_INQUIRY);
        UtcInstant now = at("2026-11-16T03:00:00Z");

        assertThatThrownBy(() -> received.recordAnswer("回答", STAFF, now)).isInstanceOf(IllegalStateException.class);
    }

    // --- 行 8〜10: 終結と復旧照合 ---

    @Test
    void 行8_復旧照合が不要なら回答済みから終結できる() {
        ServiceCaseClosing closing = answered().close();

        assertThat(closing).isInstanceOf(ServiceCaseClosing.Closed.class);
        assertThat(((ServiceCaseClosing.Closed) closing).serviceCase().status()).isEqualTo(ServiceCaseStatus.CLOSED);
    }

    private static ServiceCase reconciliationCase() {
        return opened(ServiceCaseKind.EXTERNAL_OUTAGE)
                .receive(STAFF, at("2026-11-16T02:00:00Z"))
                .recordAnswer("回答します", STAFF, at("2026-11-16T03:00:00Z"));
    }

    @Test
    void 行9_復旧照合が未完了の終結は拒否して不足する照合結果を返す() {
        ServiceCase answered = reconciliationCase();

        ServiceCaseClosing closing = answered.close();

        assertThat(closing)
                .isEqualTo(new ServiceCaseClosing.ReconciliationIncomplete(Set.of(MissingItem.RECONCILIATION_RESULT)));
        assertThat(answered.status()).isEqualTo(ServiceCaseStatus.ANSWERED);
    }

    @Test
    void 行10_復旧照合の完了を記録すれば終結できる() {
        UUID reconciliation = UUID.randomUUID();

        ServiceCase done = reconciliationCase().completeReconciliation(reconciliation);

        assertThat(done.reconciliationId()).contains(reconciliation);
        assertThat(done.close()).isInstanceOf(ServiceCaseClosing.Closed.class);
    }

    @Test
    void 行10_復旧照合が要らない案件には記録できない() {
        ServiceCase answered = answered();
        UUID reconciliation = UUID.randomUUID();

        assertThatThrownBy(() -> answered.completeReconciliation(reconciliation))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 行8_回答済み以外は終結できない() {
        ServiceCase received = inProgress();

        assertThatThrownBy(received::close).isInstanceOf(IllegalStateException.class);
    }

    // --- 行 11: 再開 ---

    @Test
    void 行11_再開しても受付番号と進捗と過去の回答を保持して対応中へ戻る() {
        ServiceCase closed = ((ServiceCaseClosing.Closed) answered().close()).serviceCase();

        ServiceCase reopened = closed.reopen("まだ解決していません", at("2026-11-17T00:00:00Z"));

        assertThat(reopened.status()).isEqualTo(ServiceCaseStatus.IN_PROGRESS);
        assertThat(reopened.number()).isEqualTo(closed.number());
        assertThat(reopened.progress()).isEqualTo(closed.progress());
        assertThat(reopened.answers()).singleElement().satisfies(answer -> {
            assertThat(answer.content()).isEqualTo("回答します");
            assertThat(answer.customerResponse()).contains("まだ解決していません");
            assertThat(answer.customerRespondedAt()).contains(at("2026-11-17T00:00:00Z"));
        });
    }

    @Test
    void 行11_再開の後に回答すると回答番号が続く() {
        ServiceCase closed = ((ServiceCaseClosing.Closed) answered().close()).serviceCase();

        ServiceCase second =
                closed.reopen("未解決", at("2026-11-17T00:00:00Z")).recordAnswer("再回答", STAFF, at("2026-11-17T01:00:00Z"));

        assertThat(second.answers()).extracting(CaseAnswer::answerNo).containsExactly(1, 2);
    }

    @Test
    void 行11_終結以外は再開できない() {
        ServiceCase received = inProgress();
        UtcInstant now = at("2026-11-17T00:00:00Z");

        assertThatThrownBy(() -> received.reopen("未解決", now)).isInstanceOf(IllegalStateException.class);
    }

    // --- 行 14〜15: 受領期限の超過の判定と依頼元に示す内容（Bolt 40-9。読むときの判定） ---

    @Test
    void 行14_受領期限を厳密に過ぎた受付済みと上位対応中は期限超過() {
        ServiceCase received = opened(ServiceCaseKind.CUSTOMER_INQUIRY);

        assertThat(received.isReceiptOverdue(at("2026-11-16T05:00:00Z"))).isFalse();
        assertThat(received.isReceiptOverdue(at("2026-11-16T05:00:01Z"))).isTrue();
        assertThat(received.escalate(at("2026-11-16T06:00:00Z"))
                        .orElseThrow()
                        .isReceiptOverdue(at("2026-11-16T06:00:00Z")))
                .isTrue();
    }

    @Test
    void 行14_受領した案件は期限を過ぎても期限超過にしない() {
        assertThat(inProgress().isReceiptOverdue(at("2026-12-01T00:00:00Z"))).isFalse();
    }

    @Test
    void 行15_担当が決まっていない案件は依頼元に担当未確定と次回更新日時を示す() {
        ServiceCase received = opened(ServiceCaseKind.CUSTOMER_INQUIRY);
        ServiceCase escalated = received.escalate(at("2026-11-16T06:00:00Z")).orElseThrow();

        assertThat(received.requesterNotice().assigneePending()).isTrue();
        assertThat(escalated.requesterNotice().assigneePending()).isTrue();
        assertThat(received.requesterNotice().nextUpdateAt()).isEqualTo(received.nextUpdateAt());
    }

    @Test
    void 行15_受領した案件は担当未確定でない() {
        assertThat(inProgress().requesterNotice().assigneePending()).isFalse();
    }

    // --- 行 13: 回答期限の変更（Bolt 40-8。UC-20 の期限を超える場合） ---

    @Test
    void 行13_回答期限を後ろへ変更すると理由つきで進捗に記録して期限を更新する() {
        UtcInstant newDue = at("2026-11-20T01:00:00Z");

        ServiceCase revised = inProgress().reviseAnswerDue("部品の手配に時間がかかる", newDue, STAFF, at("2026-11-17T02:00:00Z"));

        assertThat(revised.answerDueAt()).isEqualTo(newDue);
        assertThat(revised.progress()).singleElement().satisfies(progress -> {
            assertThat(progress.progressNo()).isEqualTo(1);
            assertThat(progress.content()).contains("部品の手配に時間がかかる");
            assertThat(progress.updatedBy()).isEqualTo(STAFF);
        });
        assertThat(revised.status()).isEqualTo(ServiceCaseStatus.IN_PROGRESS);
    }

    @Test
    void 行13_理由が空か期限が後でないと拒む() {
        ServiceCase received = inProgress();
        UtcInstant later = at("2026-11-20T01:00:00Z");
        UtcInstant same = new UtcInstant(DEADLINES.responseDue());
        UtcInstant now = at("2026-11-17T02:00:00Z");

        assertThatThrownBy(() -> received.reviseAnswerDue(" ", later, STAFF, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> received.reviseAnswerDue("理由", same, STAFF, now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 行13_対応中以外は回答期限を変更できない() {
        ServiceCase received = opened(ServiceCaseKind.CUSTOMER_INQUIRY);
        UtcInstant later = at("2026-11-20T01:00:00Z");
        UtcInstant now = at("2026-11-17T02:00:00Z");

        assertThatThrownBy(() -> received.reviseAnswerDue("理由", later, STAFF, now))
                .isInstanceOf(IllegalStateException.class);
    }

    // --- 行 12: 重複終了 ---

    @Test
    void 行12_受付済みを重複と判定すると重複終了になる() {
        assertThat(opened(ServiceCaseKind.CUSTOMER_INQUIRY).markDuplicate().status())
                .isEqualTo(ServiceCaseStatus.DUPLICATE_CLOSED);
    }

    @Test
    void 行12_受付済み以外は重複終了にできない() {
        ServiceCase received = inProgress();

        assertThatThrownBy(received::markDuplicate).isInstanceOf(IllegalStateException.class);
    }
}
