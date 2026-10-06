package com.example.cargotracker.quotation.infrastructure.persistence;

import com.example.cargotracker.quotation.domain.model.aggregates.ConcurrentQuotationUpdateException;
import com.example.cargotracker.quotation.domain.model.aggregates.DuplicateQuotationException;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.Currency;
import com.example.cargotracker.quotation.domain.model.valueobjects.PricingBasis;
import com.example.cargotracker.quotation.domain.model.valueobjects.PricingLine;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationExpiry;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotedRequestSummary;
import com.example.cargotracker.quotation.domain.model.valueobjects.RoutePolicy;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 見積りのリポジトリの MyBatis 実装。見積りと料金明細の表を組み立てて集約にする（Bolt 10）。
 * 更新は楽観ロック（集約の版）で照合する。料金明細は保存（新規）のときだけ書く（算出し直しは Bolt 10 で入れない）。
 * 経路方針の主な経由地は、UN/LOCODE のカンマ区切りで 1 つの列に持つ（データモデル）。
 */
@Repository
public class MyBatisQuotationRepository implements QuotationRepository {

    private static final String VIA_SEPARATOR = ",";

    private final QuotationMapper mapper;

    public MyBatisQuotationRepository(QuotationMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 新しい見積りを保存する。同時の算出で UK（見積り番号）か部分一意インデックス（作成中・承認待ち・提示済みは 1 つ。Q-INV-18）に
     * 違反したら、セーブポイントに戻してドメインの例外にする
     * （PostgreSQL は制約違反でトランザクションを中断するため。呼び出し側のトランザクションは続けられる。R-02）。
     */
    @Override
    @Transactional(propagation = Propagation.NESTED)
    public void save(Quotation quotation) {
        try {
            mapper.insertQuotation(toRow(quotation, quotation.aggregateVersion()));
        } catch (DuplicateKeyException _) {
            throw new DuplicateQuotationException(quotation.transportRequestId(), quotation.quotationNo());
        }
        quotation.pricingBasis().ifPresent(basis -> {
            for (int i = 0; i < basis.lines().size(); i++) {
                PricingLine line = basis.lines().get(i);
                mapper.insertPricingLine(new PricingLineRow(
                        quotation.id().value(),
                        i + 1,
                        line.description(),
                        line.amount(),
                        basis.currency().name(),
                        line.contractReference()));
            }
        });
    }

    @Override
    public void update(Quotation quotation) {
        int updated =
                mapper.updateQuotation(toRow(quotation, quotation.aggregateVersion()), quotation.aggregateVersion());
        if (updated == 0) {
            throw new ConcurrentQuotationUpdateException(quotation.id(), quotation.aggregateVersion());
        }
    }

    @Override
    public List<Quotation> findByTransportRequestId(TransportRequestId transportRequestId) {
        Map<UUID, List<PricingLineRow>> lines =
                mapper.selectPricingLinesByTransportRequestId(transportRequestId.value()).stream()
                        .collect(Collectors.groupingBy(PricingLineRow::quotationId));
        return mapper.selectByTransportRequestId(transportRequestId.value()).stream()
                .map(row -> toAggregate(row, lines.getOrDefault(row.id(), List.of())))
                .toList();
    }

    @Override
    public List<QuotedRequestSummary> findLatestOfQuotedRequests() {
        return mapper.selectLatestOfQuotedRequests().stream()
                .map(row -> new QuotedRequestSummary(
                        TransportRequestNumber.parse(row.requestNumber()),
                        row.quotationNo(),
                        QuotationStatus.valueOf(row.status()),
                        toUtc(row.expiresAt())))
                .toList();
    }

    @Override
    public Optional<Quotation> findByTransportRequestIdAndNo(TransportRequestId transportRequestId, int quotationNo) {
        return findByTransportRequestId(transportRequestId).stream()
                .filter(quotation -> quotation.quotationNo() == quotationNo)
                .findFirst();
    }

    private static QuotationRow toRow(Quotation quotation, long version) {
        Optional<PricingBasis> basis = quotation.pricingBasis();
        Optional<RoutePolicy> policy = quotation.routePolicy();
        return new QuotationRow(
                quotation.id().value(),
                quotation.transportRequestId().value(),
                quotation.quotationNo(),
                quotation.transportRequestVersionNo(),
                quotation.status().name(),
                quotation.expiry().map(expiry -> toOffset(expiry.expiresAt())).orElse(null),
                basis.map(PricingBasis::total).orElse(null),
                basis.map(value -> value.currency().name()).orElse(null),
                policy.map(value ->
                                value.via().stream().map(Location::unLocode).collect(Collectors.joining(VIA_SEPARATOR)))
                        .orElse(null),
                policy.map(value -> toOffset(value.departureAt())).orElse(null),
                policy.map(value -> toOffset(value.arrivalAt())).orElse(null),
                quotation.approvedBy().map(UserId::value).orElse(null),
                quotation
                        .presentedAt()
                        .map(MyBatisQuotationRepository::toOffset)
                        .orElse(null),
                quotation
                        .presentedAt()
                        .map(MyBatisQuotationRepository::toOffset)
                        .orElse(null),
                quotation.replacedBy().map(QuotationId::value).orElse(null),
                version);
    }

    private static Quotation toAggregate(QuotationRow row, List<PricingLineRow> lines) {
        PricingBasis basis = row.currency() == null || lines.isEmpty()
                ? null
                : new PricingBasis(
                        lines.stream()
                                .map(line ->
                                        new PricingLine(line.description(), line.amount(), line.contractReference()))
                                .toList(),
                        Currency.valueOf(row.currency().strip()));
        RoutePolicy policy = row.routePolicyDepartureAt() == null || row.routePolicyArrivalAt() == null
                ? null
                : new RoutePolicy(
                        via(row.routePolicyVia()),
                        toUtc(row.routePolicyDepartureAt()),
                        toUtc(row.routePolicyArrivalAt()));
        return Quotation.reconstitute(
                new QuotationId(row.id()),
                new TransportRequestId(row.transportRequestId()),
                row.quotationNo(),
                row.transportRequestVersionNo(),
                QuotationStatus.valueOf(row.status()),
                basis,
                row.expiresAt() == null ? null : new QuotationExpiry(toUtc(row.expiresAt())),
                policy,
                row.internalApprovedBy() == null ? null : new UserId(row.internalApprovedBy()),
                row.presentedAt() == null ? null : toUtc(row.presentedAt()),
                row.replacedByQuotationId() == null ? null : new QuotationId(row.replacedByQuotationId()),
                null,
                null,
                row.version());
    }

    private static List<Location> via(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(VIA_SEPARATOR))
                .map(String::strip)
                .map(Location::new)
                .toList();
    }

    private static OffsetDateTime toOffset(UtcInstant instant) {
        return instant.instant().atOffset(ZoneOffset.UTC);
    }

    private static UtcInstant toUtc(OffsetDateTime value) {
        return new UtcInstant(value.toInstant());
    }
}
