package com.example.cargotracker.quotation.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.quotation.domain.events.QuotationConsultationRequested;
import com.example.cargotracker.quotation.domain.events.QuotationPresented;
import com.example.cargotracker.quotation.domain.events.RouteDesignRequested;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** 見積りの集約（作成中 → 承認待ち → 提示済み。Q-INV-05・17。Bolt 10）。 */
class QuotationTest {

    private final QuotationId id = new QuotationId(UUID.randomUUID());
    private final TransportRequestId transportRequestId = new TransportRequestId(UUID.randomUUID());
    private final UserId approver = new UserId(UUID.randomUUID());
    private final UtcInstant now = new UtcInstant(Instant.parse("2026-10-05T04:00:00Z"));
    private final UtcInstant approvedAt = new UtcInstant(Instant.parse("2026-10-05T04:30:00Z"));

    private Quotation created() {
        return Quotation.create(id, transportRequestId, 1, 1);
    }

    @Test
    void 作った見積りは作成中で料金根拠も提示時刻もない() {
        Quotation quotation = created();

        assertThat(quotation.status()).isEqualTo(QuotationStatus.DRAFT);
        assertThat(quotation.pricingBasis()).isEmpty();
        assertThat(quotation.presentedAt()).isEmpty();
    }

    @Test
    void 算出すると承認待ちになり料金根拠と有効期限と経路方針を持つ() {
        Quotation quotation = created();

        assertThat(quotation.calculate(QuotationFixture.completeInput(), now)).isEmpty();

        assertThat(quotation.status()).isEqualTo(QuotationStatus.PENDING_APPROVAL);
        assertThat(quotation.pricingBasis())
                .hasValueSatisfying(basis -> assertThat(basis.total()).isEqualByComparingTo("3730.00"));
        assertThat(quotation.expiry())
                .hasValueSatisfying(expiry -> assertThat(expiry.expiresAt()).isEqualTo(QuotationFixture.EXPIRES_AT));
        assertThat(quotation.routePolicy())
                .hasValueSatisfying(policy -> assertThat(policy.via()).containsExactly(new Location("SGSIN")));
    }

    @Test
    void 誤りのある入力では算出せず作成中のまま違反を返す() {
        Quotation quotation = created();

        assertThat(quotation.calculate(new QuotationInput(List.of(), null, null, List.of(), null, null), now))
                .hasValueSatisfying(
                        violations -> assertThat(violations.isEmpty()).isFalse());
        assertThat(quotation.status()).isEqualTo(QuotationStatus.DRAFT);
    }

    @Test
    void 作成中でない見積りは算出し直せない() {
        Quotation quotation = created();
        QuotationInput input = QuotationFixture.completeInput();
        quotation.calculate(input, now);

        assertThatThrownBy(() -> quotation.calculate(input, now)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 通知の宛先を渡して提示するとDE03に荷主企業と業務番号が載る_渡さなければ載らない_Bolt43_1() {
        UUID company = UUID.randomUUID();
        Quotation notified = created();
        notified.calculate(QuotationFixture.completeInput(), now);
        notified.notifying(new com.example.cargotracker.shared.domain.CompanyId(company), "TR-2026-0001");
        Quotation plain = created();
        plain.calculate(QuotationFixture.completeInput(), now);

        notified.presentInternally(approver, approvedAt);
        plain.presentInternally(approver, approvedAt);

        assertThat(notified.domainEvents()).singleElement().isInstanceOfSatisfying(QuotationPresented.class, event -> {
            assertThat(event.shipperCompanyId()).isEqualTo(company);
            assertThat(event.transportRequestNumber()).isEqualTo("TR-2026-0001");
        });
        assertThat(plain.domainEvents()).singleElement().isInstanceOfSatisfying(QuotationPresented.class, event -> {
            assertThat(event.shipperCompanyId()).isNull();
            assertThat(event.transportRequestNumber()).isNull();
        });
    }

    @Test
    void 社内承認して提示すると提示済みになり承認者と提示時刻を残しDE03を生成する() {
        Quotation quotation = created();
        quotation.calculate(QuotationFixture.completeInput(), now);

        assertThat(quotation.presentInternally(approver, approvedAt)).isEmpty();

        assertThat(quotation.status()).isEqualTo(QuotationStatus.PRESENTED);
        assertThat(quotation.approvedBy()).contains(approver);
        assertThat(quotation.presentedAt()).contains(approvedAt);
        assertThat(quotation.domainEvents()).singleElement().isInstanceOfSatisfying(QuotationPresented.class, event -> {
            assertThat(event.quotationId()).isEqualTo(id.value());
            assertThat(event.quotationNo()).isEqualTo(1);
            assertThat(event.transportRequestId()).isEqualTo(transportRequestId.value());
            assertThat(event.transportRequestVersionNo()).isEqualTo(1);
            assertThat(event.expiresAt()).isEqualTo(QuotationFixture.EXPIRES_AT);
            assertThat(event.routeVia()).containsExactly("SGSIN");
            assertThat(event.presentedAt()).isEqualTo(approvedAt);
        });
    }

    @Test
    void 承認待ちでない見積りは提示できない() {
        Quotation draft = created();

        assertThat(draft.presentInternally(approver, now)).contains(QuotationRejection.NOT_PENDING_APPROVAL);
        assertThat(draft.status()).isEqualTo(QuotationStatus.DRAFT);
        assertThat(draft.domainEvents()).isEmpty();
    }

    /** 承認待ちの見積り（有効期限は QuotationFixture.EXPIRES_AT = 2099-10-08T09:00:00Z）。 */
    private Quotation pendingApproval() {
        Quotation quotation = created();
        quotation.calculate(QuotationFixture.completeInput(), now);
        return quotation;
    }

    private Quotation presented() {
        Quotation quotation = pendingApproval();
        quotation.presentInternally(approver, approvedAt);
        quotation.clearDomainEvents();
        return quotation;
    }

    private static UtcInstant at(String instant) {
        return new UtcInstant(Instant.parse(instant));
    }

    @ParameterizedTest
    @CsvSource({"2099-10-08T08:59:59Z, false", "2099-10-08T09:00:00Z, true", "2099-10-08T09:00:01Z, true"})
    void 有効期限の前は有効で同時刻からは失効として扱う(String judgedAt, boolean expired) {
        Quotation quotation = presented();

        assertThat(quotation.isExpiredAt(at(judgedAt))).isEqualTo(expired);
        assertThat(quotation.status()).isEqualTo(QuotationStatus.PRESENTED);
    }

    @Test
    void 有効期限を過ぎた提示済みの見積りを提示しようとすると失効しているとして拒否する() {
        Quotation quotation = presented();

        assertThat(quotation.presentInternally(approver, at("2099-10-08T09:00:00Z")))
                .contains(QuotationRejection.EXPIRED);
        assertThat(quotation.presentRejectionAt(at("2099-10-08T09:00:00Z"))).contains(QuotationRejection.EXPIRED);
        assertThat(quotation.presentRejectionAt(at("2099-10-08T08:59:59Z")))
                .contains(QuotationRejection.NOT_PENDING_APPROVAL);
    }

    @Test
    void 提示できるかと再見積りできるかを問い合わせられる() {
        Quotation pending = pendingApproval();
        Quotation replaced = presented();
        replaced.replaceWith(new QuotationId(UUID.randomUUID()), now);

        assertThat(pending.presentRejectionAt(now)).isEmpty();
        assertThat(replaced.presentRejectionAt(now)).contains(QuotationRejection.REPLACED);
        assertThat(pending.requoteRejectionAt(now)).as("判定時刻の判定（D-85。Bolt 35b）").isEmpty();
        assertThat(presented().requoteRejectionAt(at("2099-10-09T00:00:00Z")))
                .as("提示済みは失効していても再見積りできる")
                .isEmpty();
        assertThat(replaced.requoteRejectionAt(now)).contains(QuotationRejection.REPLACED);
    }

    @Test
    void 置換済みの見積りは有効期限を過ぎても失効としない() {
        Quotation replaced = presented();
        replaced.replaceWith(new QuotationId(UUID.randomUUID()), now);

        assertThat(replaced.isExpiredAt(at("2099-10-09T00:00:00Z"))).isFalse();
    }

    @Test
    void 有効期限の1秒前に置換すると置換済みになる() {
        Quotation quotation = presented();

        quotation.replaceWith(new QuotationId(UUID.randomUUID()), at("2099-10-08T08:59:59Z"));

        assertThat(quotation.status()).isEqualTo(QuotationStatus.REPLACED);
    }

    @Test
    void 有効期限と同時刻の承認待ちの見積りは提示できない() {
        Quotation quotation = pendingApproval();

        assertThat(quotation.presentInternally(approver, at("2099-10-08T09:00:00Z")))
                .contains(QuotationRejection.EXPIRED);
        assertThat(quotation.status()).isEqualTo(QuotationStatus.PENDING_APPROVAL);
        assertThat(quotation.domainEvents()).isEmpty();
    }

    @Test
    void 有効期限の1秒前なら承認待ちの見積りを提示できる() {
        Quotation quotation = pendingApproval();

        assertThat(quotation.presentInternally(approver, at("2099-10-08T08:59:59Z")))
                .isEmpty();
        assertThat(quotation.status()).isEqualTo(QuotationStatus.PRESENTED);
    }

    @Test
    void 有効な提示済みの見積りを置換すると置換済みになり置換先を指す() {
        Quotation quotation = presented();
        QuotationId replacement = new QuotationId(UUID.randomUUID());

        assertThat(quotation.replaceWith(replacement, at("2026-10-06T01:00:00Z")))
                .isEmpty();

        assertThat(quotation.status()).isEqualTo(QuotationStatus.REPLACED);
        assertThat(quotation.replacedBy()).contains(replacement);
        assertThat(quotation.isActive()).isFalse();
    }

    @Test
    void 有効な承認待ちの見積りも置換できる() {
        Quotation quotation = pendingApproval();

        assertThat(quotation.replaceWith(new QuotationId(UUID.randomUUID()), now))
                .isEmpty();

        assertThat(quotation.status()).isEqualTo(QuotationStatus.REPLACED);
    }

    @Test
    void 有効期限を過ぎた見積りを置換すると失効として記録し置換先は持たない() {
        Quotation quotation = presented();

        assertThat(quotation.replaceWith(new QuotationId(UUID.randomUUID()), at("2099-10-08T09:00:00Z")))
                .isEmpty();

        assertThat(quotation.status()).isEqualTo(QuotationStatus.EXPIRED);
        assertThat(quotation.replacedBy()).isEmpty();
        assertThat(quotation.isActive()).isFalse();
        assertThat(quotation.isExpiredAt(now)).isTrue();
    }

    @Test
    void 置換済みと失効の見積りは置換も提示もできない() {
        Quotation replaced = presented();
        replaced.replaceWith(new QuotationId(UUID.randomUUID()), now);
        Quotation expired = presented();
        expired.replaceWith(new QuotationId(UUID.randomUUID()), at("2099-10-09T00:00:00Z"));

        assertThat(replaced.replaceWith(new QuotationId(UUID.randomUUID()), now))
                .contains(QuotationRejection.REPLACED);
        assertThat(replaced.presentInternally(approver, now)).contains(QuotationRejection.REPLACED);
        assertThat(expired.replaceWith(new QuotationId(UUID.randomUUID()), now)).contains(QuotationRejection.EXPIRED);
        assertThat(expired.presentInternally(approver, now)).contains(QuotationRejection.EXPIRED);
    }

    @Test
    void 作成中の見積りは置換できない() {
        Quotation draft = created();

        QuotationId replacement = new QuotationId(UUID.randomUUID());

        assertThatThrownBy(() -> draft.replaceWith(replacement, now)).isInstanceOf(IllegalStateException.class);
    }

    // 荷主の回答（US-24 AC1、Q-INV-07・09、DE-16。Bolt 12）

    private final UserId respondent = new UserId(UUID.randomUUID());

    @Test
    void 提示済みの見積りに詳細経路設計を依頼すると詳細設計依頼済みになり回答者と回答時刻を残しDE16を生成する() {
        Quotation quotation = presented();
        UtcInstant respondedAt = at("2026-10-06T02:00:00Z");

        assertThat(quotation.requestRouteDesign(respondent, respondedAt)).isEmpty();

        assertThat(quotation.status()).isEqualTo(QuotationStatus.ROUTING_REQUESTED);
        assertThat(quotation.respondedBy()).contains(respondent);
        assertThat(quotation.respondedAt()).contains(respondedAt);
        assertThat(quotation.domainEvents())
                .singleElement()
                .isInstanceOfSatisfying(RouteDesignRequested.class, event -> {
                    assertThat(event.quotationId()).isEqualTo(id.value());
                    assertThat(event.quotationNo()).isEqualTo(1);
                    assertThat(event.transportRequestId()).isEqualTo(transportRequestId.value());
                    assertThat(event.transportRequestVersionNo()).isEqualTo(1);
                    assertThat(event.routeVia()).containsExactly("SGSIN");
                    assertThat(event.departureAt())
                            .isEqualTo(QuotationFixture.completeInput().departureAt());
                    assertThat(event.arrivalAt())
                            .isEqualTo(QuotationFixture.completeInput().arrivalAt());
                    assertThat(event.expiresAt()).isEqualTo(QuotationFixture.EXPIRES_AT);
                    assertThat(event.requestedBy()).isEqualTo(respondent.value());
                    assertThat(event.requestedAt()).isEqualTo(respondedAt);
                });
    }

    // 荷主の辞退（US-24 AC2。W9 の Bolt 46。D-86・ADR-018 の見直し: 辞退は失効として記録し、再見積りしない）

    @Test
    void 提示済みの見積りを理由を付けて辞退すると失効になり理由と回答者と回答時刻を残す() {
        Quotation quotation = presented();
        UtcInstant declinedAt = at("2026-10-06T02:00:00Z");

        assertThat(quotation.decline(respondent, "  他社の見積りで進めるため  ", declinedAt))
                .isEmpty();

        assertThat(quotation.status()).isEqualTo(QuotationStatus.EXPIRED);
        assertThat(quotation.isDeclined()).isTrue();
        assertThat(quotation.declineReason()).contains("他社の見積りで進めるため");
        assertThat(quotation.respondedBy()).contains(respondent);
        assertThat(quotation.respondedAt()).contains(declinedAt);
        // 経路設計の依頼（DE-16）は出さない。輸送要求の状態は変えない
        assertThat(quotation.domainEvents()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"2099-10-08T08:59:59Z, true", "2099-10-08T09:00:00Z, false"})
    void 辞退も有効期限の前だけで同時刻からは失効として拒否する(String declinedAt, boolean accepted) {
        Quotation quotation = presented();

        assertThat(quotation.decline(respondent, "理由", at(declinedAt)))
                .isEqualTo(accepted ? Optional.empty() : Optional.of(QuotationRejection.EXPIRED));
        assertThat(quotation.isDeclined()).isEqualTo(accepted);
    }

    @Test
    void 提示済みでない見積りと回答済み辞退済みの見積りは辞退できない() {
        Quotation pending = pendingApproval();
        Quotation routing = presented();
        routing.requestRouteDesign(respondent, now);
        Quotation declined = presented();
        declined.decline(respondent, "理由", now);

        assertThat(pending.decline(respondent, "理由", now)).contains(QuotationRejection.NOT_PRESENTED);
        assertThat(routing.decline(respondent, "理由", now)).contains(QuotationRejection.ROUTING_REQUESTED);
        assertThat(declined.decline(respondent, "理由", now)).contains(QuotationRejection.EXPIRED);
        assertThat(routing.isDeclined()).isFalse();
    }

    @Test
    void 辞退した見積りは再見積りも回答もできない() {
        Quotation declined = presented();
        declined.decline(respondent, "理由", now);

        assertThat(declined.requoteRejectionAt(now)).contains(QuotationRejection.EXPIRED);
        assertThat(declined.responseRejectionAt(now)).contains(QuotationRejection.EXPIRED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void 辞退の理由が空なら辞退できない(String reason) {
        Quotation quotation = presented();

        assertThatThrownBy(() -> quotation.decline(respondent, reason, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(quotation.status()).isEqualTo(QuotationStatus.PRESENTED);
    }

    @Test
    void 辞退の理由は500文字までで超えると辞退できない() {
        Quotation accepted = presented();
        Quotation rejected = presented();

        assertThat(accepted.decline(respondent, "あ".repeat(500), now)).isEmpty();
        String tooLong = "あ".repeat(501);
        assertThatThrownBy(() -> rejected.decline(respondent, tooLong, now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 荷主の相談（US-24 AC3。W9 の Bolt 46-1）。見積りの状態は変えず、相談のイベントだけを出す

    @Test
    void 提示済みの見積りの条件の相談は状態を変えず相談のイベントを生成する() {
        Quotation quotation = presented();
        UtcInstant requestedAt = at("2026-10-06T02:00:00Z");
        CompanyId shipper = new CompanyId(UUID.randomUUID());

        assertThat(quotation.requestConsultation(respondent, "  納期を 3 日早められるか  ", shipper, "TR-2026-0001", requestedAt))
                .isEmpty();

        assertThat(quotation.status()).isEqualTo(QuotationStatus.PRESENTED);
        assertThat(quotation.respondedBy()).isEmpty();
        assertThat(quotation.isDeclined()).isFalse();
        assertThat(quotation.domainEvents())
                .singleElement()
                .isInstanceOfSatisfying(QuotationConsultationRequested.class, event -> {
                    assertThat(event.quotationId()).isEqualTo(id.value());
                    assertThat(event.quotationNo()).isEqualTo(1);
                    assertThat(event.transportRequestId()).isEqualTo(transportRequestId.value());
                    assertThat(event.transportRequestNumber()).isEqualTo("TR-2026-0001");
                    assertThat(event.shipperCompanyId()).isEqualTo(shipper.value());
                    assertThat(event.requestedBy()).isEqualTo(respondent.value());
                    assertThat(event.content()).isEqualTo("納期を 3 日早められるか");
                    assertThat(event.requestedAt()).isEqualTo(requestedAt);
                });
    }

    @Test
    void 回答できない見積りの相談は拒否しイベントを出さない() {
        CompanyId shipper = new CompanyId(UUID.randomUUID());
        Quotation pending = pendingApproval();
        Quotation routing = presented();
        routing.requestRouteDesign(respondent, now);
        routing.clearDomainEvents();
        Quotation declined = presented();
        declined.decline(respondent, "理由", now);
        Quotation expired = presented();

        assertThat(pending.requestConsultation(respondent, "相談", shipper, "TR-2026-0001", now))
                .contains(QuotationRejection.NOT_PRESENTED);
        assertThat(routing.requestConsultation(respondent, "相談", shipper, "TR-2026-0001", now))
                .contains(QuotationRejection.ROUTING_REQUESTED);
        assertThat(declined.requestConsultation(respondent, "相談", shipper, "TR-2026-0001", now))
                .contains(QuotationRejection.EXPIRED);
        assertThat(expired.requestConsultation(respondent, "相談", shipper, "TR-2026-0001", at("2099-10-08T09:00:00Z")))
                .contains(QuotationRejection.EXPIRED);
        assertThat(routing.domainEvents()).isEmpty();
        assertThat(declined.domainEvents()).isEmpty();
        assertThat(expired.domainEvents()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void 相談の内容が空なら相談できない(String content) {
        Quotation quotation = presented();
        CompanyId shipper = new CompanyId(UUID.randomUUID());

        assertThatThrownBy(() -> quotation.requestConsultation(respondent, content, shipper, "TR-2026-0001", now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 相談の内容は500文字までで超えると相談できない() {
        Quotation accepted = presented();
        Quotation rejected = presented();
        CompanyId shipper = new CompanyId(UUID.randomUUID());

        String tooLong = "あ".repeat(501);
        assertThat(accepted.requestConsultation(respondent, "あ".repeat(500), shipper, "TR-2026-0001", now))
                .isEmpty();
        assertThatThrownBy(() -> rejected.requestConsultation(respondent, tooLong, shipper, "TR-2026-0001", now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"2099-10-08T08:59:59Z, true", "2099-10-08T09:00:00Z, false", "2099-10-08T09:00:01Z, false"})
    void 有効期限の前だけ回答でき同時刻からは失効として拒否する(String respondedAt, boolean accepted) {
        Quotation quotation = presented();

        assertThat(quotation.requestRouteDesign(respondent, at(respondedAt)))
                .isEqualTo(accepted ? Optional.empty() : Optional.of(QuotationRejection.EXPIRED));
        assertThat(quotation.status())
                .isEqualTo(accepted ? QuotationStatus.ROUTING_REQUESTED : QuotationStatus.PRESENTED);
    }

    @Test
    void 提示済みでない見積りと置換済み失効と回答済みの見積りには回答できない() {
        Quotation pending = pendingApproval();
        Quotation replaced = presented();
        replaced.replaceWith(new QuotationId(UUID.randomUUID()), now);
        Quotation expired = presented();
        expired.replaceWith(new QuotationId(UUID.randomUUID()), at("2099-10-09T00:00:00Z"));
        Quotation responded = presented();
        responded.requestRouteDesign(respondent, now);
        responded.clearDomainEvents();

        assertThat(pending.requestRouteDesign(respondent, now)).contains(QuotationRejection.NOT_PRESENTED);
        assertThat(replaced.requestRouteDesign(respondent, now)).contains(QuotationRejection.REPLACED);
        assertThat(expired.requestRouteDesign(respondent, now)).contains(QuotationRejection.EXPIRED);
        assertThat(responded.requestRouteDesign(respondent, now)).contains(QuotationRejection.ROUTING_REQUESTED);
        assertThat(responded.domainEvents()).isEmpty();
    }

    @Test
    void 回答できるかを判定時刻で問い合わせられる() {
        Quotation quotation = presented();

        assertThat(quotation.responseRejectionAt(now)).isEmpty();
        assertThat(quotation.responseRejectionAt(at("2099-10-08T09:00:00Z"))).contains(QuotationRejection.EXPIRED);
    }

    @Test
    void 詳細設計依頼済みの見積りは有効期限と同時刻から失効として扱い数える見積りに入る() {
        Quotation quotation = presented();
        quotation.requestRouteDesign(respondent, now);

        assertThat(quotation.isExpiredAt(at("2099-10-08T08:59:59Z"))).isFalse();
        assertThat(quotation.isExpiredAt(at("2099-10-08T09:00:00Z"))).isTrue();
        assertThat(quotation.isActive()).isTrue();
        assertThat(quotation.responseRejectionAt(at("2099-10-08T09:00:00Z"))).contains(QuotationRejection.EXPIRED);
    }

    @Test
    void 詳細設計依頼済みの見積りは再見積りも提示もできない() {
        Quotation quotation = presented();
        quotation.requestRouteDesign(respondent, now);

        assertThat(quotation.requoteRejectionAt(now)).contains(QuotationRejection.ROUTING_REQUESTED);
        assertThat(quotation.replaceWith(new QuotationId(UUID.randomUUID()), now))
                .contains(QuotationRejection.ROUTING_REQUESTED);
        assertThat(quotation.status()).isEqualTo(QuotationStatus.ROUTING_REQUESTED);
        assertThat(quotation.presentInternally(approver, now)).contains(QuotationRejection.NOT_PENDING_APPROVAL);
    }

    @Test
    void 荷主に見えるのは提示した見積りだけ() {
        Quotation pending = pendingApproval();
        Quotation replacedBeforePresentation = pendingApproval();
        replacedBeforePresentation.replaceWith(new QuotationId(UUID.randomUUID()), now);
        Quotation responded = presented();
        responded.requestRouteDesign(respondent, now);

        assertThat(pending.isVisibleToShipper()).isFalse();
        assertThat(replacedBeforePresentation.isVisibleToShipper()).isFalse();
        assertThat(presented().isVisibleToShipper()).isTrue();
        assertThat(responded.isVisibleToShipper()).isTrue();
    }
}
