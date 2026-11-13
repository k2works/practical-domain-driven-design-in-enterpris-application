package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseStatus;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 有人案件の保存を覚えるだけのテスト用の代役（コマンドと照会のサービスのテストが共有する。Bolt 40-7〜40-9）。 */
public final class InMemoryServiceCaseRepository implements ServiceCaseRepository {

    /** 保存した案件（受付番号をキーにする）。 */
    public final Map<String, ServiceCase> cases = new HashMap<>();

    /** 真なら次の更新を競合にする。 */
    public boolean failNextUpdate;

    /** 保存した案件を重複終了にする（重複の判定の確かめ用）。 */
    public void markDuplicateClosed(CaseNumber number) {
        cases.put(number.value(), cases.get(number.value()).markDuplicate());
    }

    @Override
    public void save(ServiceCase serviceCase, UserId actor) {
        cases.put(serviceCase.number().value(), serviceCase);
    }

    @Override
    public void update(ServiceCase serviceCase, UserId actor) {
        if (failNextUpdate) {
            failNextUpdate = false;
            throw new ConcurrentServiceCaseUpdateException(serviceCase.number(), serviceCase.aggregateVersion());
        }
        cases.put(serviceCase.number().value(), serviceCase);
    }

    @Override
    public List<ServiceCase> findReceiptOverdue(UtcInstant now, int limit) {
        return cases.values().stream()
                .filter(c -> c.isReceiptOverdue(now))
                .sorted(Comparator.comparing(c -> c.receiveDueAt().instant()))
                .limit(limit)
                .toList();
    }

    @Override
    public Optional<ServiceCase> findByNumber(CaseNumber number) {
        return Optional.ofNullable(cases.get(number.value()));
    }

    @Override
    public Optional<CaseNumber> findUnclosedDuplicate(
            Optional<CompanyId> shipperCompanyId, Optional<UUID> bookingId, String cause) {
        return cases.values().stream()
                .filter(c -> c.status() != ServiceCaseStatus.CLOSED && c.status() != ServiceCaseStatus.DUPLICATE_CLOSED)
                .filter(c -> c.shipperCompanyId().equals(shipperCompanyId))
                .filter(c -> c.bookingId().equals(bookingId))
                .filter(c -> c.cause().equals(cause))
                .map(ServiceCase::number)
                .findFirst();
    }
}
