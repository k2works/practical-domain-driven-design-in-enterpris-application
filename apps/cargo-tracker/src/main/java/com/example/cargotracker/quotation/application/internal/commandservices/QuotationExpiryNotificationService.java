package com.example.cargotracker.quotation.application.internal.commandservices;

import com.example.cargotracker.quotation.domain.events.QuotationExpiring;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 見積りの有効期限の 24 時間前の通知の検出（US-22 AC2。W9 の Bolt 43-2）。荷主の回答か承認を待つ見積り（提示済み・荷主承認待ち）のうち、有効期限が
 * 検出の時刻の 23 時間後より後で 24 時間後以前のものを、荷主企業の ID と業務番号つきの {@link QuotationExpiring} として発行する。検出は 1 時間ごとに
 * 動くので、1 つの見積りはちょうど 1 回の窓に入る。同じ窓を二重に検出しても（複数のインスタンス、再実行）、宛先の側の一意制約（契機の ID と宛先）が
 * 2 通を防ぐ（N-INV-02）。検出が動かなかった時間の見積りは通知されない（運用の監視は W10）。輸送要求が読めない見積りは発行せず警告に残す。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない。組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class QuotationExpiryNotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(QuotationExpiryNotificationService.class);

    /** 通知の窓の長さ。有効期限が 24 時間前の窓（23〜24 時間後）に入ったときに通知する。 */
    static final Duration LEAD = Duration.ofHours(24);

    static final Duration WINDOW = Duration.ofHours(1);

    private final QuotationRepository quotations;
    private final TransportRequestRepository transportRequests;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public QuotationExpiryNotificationService(
            QuotationRepository quotations,
            TransportRequestRepository transportRequests,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.quotations = quotations;
        this.transportRequests = transportRequests;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * 24 時間前の窓にある見積りを検出して発行する。
     *
     * @return 発行した数
     */
    @Transactional
    public int detect() {
        Instant now = clock.instant();
        List<Quotation> expiring = quotations.findAwaitingResponseExpiringBetween(
                new UtcInstant(now.plus(LEAD).minus(WINDOW)), new UtcInstant(now.plus(LEAD)));
        int published = 0;
        for (Quotation quotation : expiring) {
            var request = transportRequests.findById(quotation.transportRequestId());
            if (request.isEmpty()) {
                LOG.warn("有効期限が近い見積りの輸送要求が読めない: 見積り {}", quotation.id().value());
                continue;
            }
            eventPublisher.publishEvent(new QuotationExpiring(
                    quotation.id().value(),
                    quotation.quotationNo(),
                    quotation.transportRequestId().value(),
                    quotation.expiry().orElseThrow().expiresAt(),
                    request.get().shipperCompanyId().value(),
                    request.get().number().text()));
            published++;
        }
        return published;
    }
}
