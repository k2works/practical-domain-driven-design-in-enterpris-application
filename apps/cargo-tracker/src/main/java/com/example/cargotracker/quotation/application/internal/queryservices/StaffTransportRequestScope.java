package com.example.cargotracker.quotation.application.internal.queryservices;

import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/**
 * 社内の側の輸送要求の担当範囲の判定（Bolt 38f の決定 (10)。US-16 AC4、IA-INV-02）。営業担当者が扱えるのは、担当企業（荷主企業）の
 * 輸送要求だけ。照会のサービスとコマンドのサービスが最初に呼び、範囲を知らないまま読み書きできる道を残さない（前例は identity の
 * {@code ManagedUserLookup}）。判定は {@link AuthenticatedActor#handlesCompany} の 1 か所で、担当企業がなければどの輸送要求も範囲の外
 * （失敗に倒す）。一覧の空の守り（担当企業がなければ SQL を流さない。{@code IN ()} は書けない）もここの 1 か所に置く。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない。組み立ては {@code QuotationConfiguration}。
 */
@Service
public class StaffTransportRequestScope {

    private final TransportRequestRepository repository;

    public StaffTransportRequestScope(TransportRequestRepository repository) {
        this.repository = repository;
    }

    /** 業務番号の輸送要求を、操作者の担当範囲で引き当てる。 */
    public StaffTransportRequestLookup find(TransportRequestNumber number, AuthenticatedActor actor) {
        return repository
                .findByNumberForStaff(number)
                .<StaffTransportRequestLookup>map(request -> actor.handlesCompany(request.shipperCompanyId())
                        ? new StaffTransportRequestLookup.Found(request)
                        : new StaffTransportRequestLookup.OutOfScope())
                .orElseGet(StaffTransportRequestLookup.NotFound::new);
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
