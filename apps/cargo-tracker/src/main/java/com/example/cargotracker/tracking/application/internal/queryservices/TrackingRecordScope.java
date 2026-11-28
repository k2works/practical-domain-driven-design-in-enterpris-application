package com.example.cargotracker.tracking.application.internal.queryservices;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/**
 * 社内の側の追跡記録の担当範囲の判定（Bolt 38g。US-16 AC4、IA-INV-02）。追跡管理者が扱えるのは、担当企業（荷主企業）の追跡記録だけ。
 * 照会のサービスとコマンドのサービスが最初に呼ぶ（38f の {@code StaffTransportRequestScope} の写し）。判定は
 * {@link AuthenticatedActor#handlesCompany} の 1 か所で、担当企業がなければどの追跡記録も範囲の外（失敗に倒す）。荷主企業は追跡記録が
 * 持つ値（NOT NULL）を読み、予約と経路設計の公開 API は使わない。一覧の空の守り（担当企業がなければ SQL を流さない。{@code IN ()} は
 * 書けない）もここの 1 か所に置く。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない。組み立ては {@code TrackingConfiguration}。
 */
@Service
public class TrackingRecordScope {

    private final TrackingRecordRepository repository;

    public TrackingRecordScope(TrackingRecordRepository repository) {
        this.repository = repository;
    }

    /** 追跡番号の追跡記録を、操作者の担当範囲で引き当てる。 */
    public TrackingRecordLookup find(TrackingNumber trackingNumber, AuthenticatedActor actor) {
        return repository
                .findByTrackingNumber(trackingNumber)
                .<TrackingRecordLookup>map(trackingRecord -> actor.handlesCompany(trackingRecord.shipperCompanyId())
                        ? new TrackingRecordLookup.Found(trackingRecord)
                        : new TrackingRecordLookup.OutOfScope())
                .orElseGet(TrackingRecordLookup.NotFound::new);
    }

    /**
     * 担当企業で絞った一覧を読む。担当企業がなければ読まずに空を返す。
     *
     * @param query 担当企業（空でない）で絞って読む照会
     */
    public <T> List<T> listWithin(AuthenticatedActor actor, Function<Set<CompanyId>, List<T>> query) {
        if (actor.hasNoAssignedCompanies()) {
            return List.of();
        }
        return query.apply(actor.assignedCompanies());
    }
}
