package com.example.cargotracker.quotation.application.internal.commandservices;

import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.RouteRedesignMarkResult;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 再設計要の伝達の入力ポート（見積りの公開 API のアダプターが委ねる。ADR-014、Q-INV-10、B-INV-05。Bolt 37e）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class RouteRedesignService {

    private final QuotationRepository quotationRepository;

    public RouteRedesignService(QuotationRepository quotationRepository) {
        this.quotationRepository = quotationRepository;
    }

    /**
     * 経路版を割り当てた見積りすべてに再設計要の印を付ける。照会で見積りを引き、1 件ずつ読んで印を付けて保存し、照会し直して増えた見積りが
     * あれば同じことを繰り返す（再見積りがこの操作の照会と読み込みの間に終わっても、新しい見積りに印を付ける。届く順の C）。複数の見積り
     * （集約）を 1 つのトランザクションで書くのは、同じ経路版の写しをそろえるためで、再見積りの前例と同じ例外にする。読み込んだ後に見積りが
     * 更新されていれば楽観ロックの競合の例外にし、listener のトランザクションごと戻して再配信でやり直す（届く順の E。DE-05 の前例）。
     *
     * @param routingCaseNumber 案件番号
     * @param routeVersionNo 経路版番号
     * @param requiredAt 経路設計が再設計要にした時刻
     * @return 当たった見積りと新しく付けた見積りの数
     */
    @Transactional
    public RouteRedesignMarking markRedesignRequired(
            String routingCaseNumber, int routeVersionNo, UtcInstant requiredAt) {
        Set<QuotationId> processed = new HashSet<>();
        int newlyMarked = 0;
        List<QuotationId> unprocessed = unprocessed(routingCaseNumber, routeVersionNo, processed);
        while (!unprocessed.isEmpty()) {
            for (QuotationId id : unprocessed) {
                processed.add(id);
                if (mark(id, routingCaseNumber, routeVersionNo, requiredAt)) {
                    newlyMarked++;
                }
            }
            unprocessed = unprocessed(routingCaseNumber, routeVersionNo, processed);
        }
        return new RouteRedesignMarking(processed.size(), newlyMarked);
    }

    private List<QuotationId> unprocessed(String routingCaseNumber, int routeVersionNo, Set<QuotationId> processed) {
        return quotationRepository.findIdsByAssignedRoute(routingCaseNumber, routeVersionNo).stream()
                .filter(id -> !processed.contains(id))
                .toList();
    }

    /** 見積りに印を付け、付けたら保存する（競合は例外のまま上げる）。 */
    private boolean mark(QuotationId id, String routingCaseNumber, int routeVersionNo, UtcInstant requiredAt) {
        Optional<Quotation> found = quotationRepository.findById(id);
        if (found.isEmpty()) {
            return false;
        }
        Quotation quotation = found.get();
        if (quotation.markRouteRedesignRequired(routingCaseNumber, routeVersionNo, requiredAt)
                != RouteRedesignMarkResult.MARKED) {
            return false;
        }
        quotationRepository.update(quotation);
        return true;
    }
}
