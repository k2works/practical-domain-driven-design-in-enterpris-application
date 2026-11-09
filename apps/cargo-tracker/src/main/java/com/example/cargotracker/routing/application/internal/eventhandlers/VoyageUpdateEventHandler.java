package com.example.cargotracker.routing.application.internal.eventhandlers;

import com.example.cargotracker.externaldata.domain.events.VoyageAdoptedValueUpdated;
import com.example.cargotracker.routing.domain.model.aggregates.ConcurrentVoyageUpdateException;
import com.example.cargotracker.routing.domain.model.aggregates.DuplicateVoyageException;
import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.aggregates.VoyageRepository;
import com.example.cargotracker.routing.domain.model.valueobjects.VoyageAdoption;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * DE-12 航海の採用値を更新した を購読し、経路設計の航海を書く（US-14 AC1、ADR-004。Bolt 39e）。外部データは経路設計の表を書かず、採用値だけを公開する。
 * 取込か採用の判断のコミットの後に、非同期で、新しいトランザクションの中で処理する。配信は少なくとも 1 回で順も保たれないので、航海が今の値より
 * 厳密に新しい採用値だけを適用する（【業務の判断】(r)。比べるのは {@link Voyage#apply}）。
 *
 * <p>版の競合と初めての航海の同時（一意制約）は読み直して適用し直す（3 回まで。読み直した航海の方が新しければ適用しない）。3 回とも負けたら例外を
 * 上げ、発行記録に残して再起動の再配信に任せる。内容の欠けた DE-12 は再配信しても直らないので、ERROR のログを残して捨てる。DB の失敗と
 * プログラムの誤りは例外を上げて配り直させる。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない。組み立ては {@code RoutingConfiguration} が担う。
 */
@Service
public class VoyageUpdateEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(VoyageUpdateEventHandler.class);

    /** 適用を試みる回数（読み直しの上限）。 */
    private static final int MAX_ATTEMPTS = 3;

    private final VoyageRepository repository;

    public VoyageUpdateEventHandler(VoyageRepository repository) {
        this.repository = repository;
    }

    @ApplicationModuleListener
    public void on(VoyageAdoptedValueUpdated event) {
        VoyageAdoption adoption;
        try {
            adoption = toAdoption(event);
        } catch (VoyageAdoption.InvalidEventValueException e) {
            LOG.error(
                    "DE-12 を航海に書かずに捨てた（イベントの内容が欠けている。再配信しても直らない。調査が要る）: 航海 {}、採用値 {}、欠け {}",
                    event.voyageNumber(),
                    event.adoptedValueId(),
                    e.getMessage());
            return;
        }
        for (int attempt = 1; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                apply(adoption);
                return;
            } catch (ConcurrentVoyageUpdateException | DuplicateVoyageException _) {
                // ほかの DE-12 に先を越された。読み直して適用し直す
            }
        }
        apply(adoption);
    }

    private void apply(VoyageAdoption adoption) {
        Optional<Voyage> current = repository.findByVoyageNumber(adoption.voyageNumber());
        if (current.isEmpty()) {
            repository.save(Voyage.adoptedFrom(adoption));
            return;
        }
        Voyage voyage = current.get();
        if (voyage.apply(adoption)) {
            repository.update(voyage);
        }
    }

    private static VoyageAdoption toAdoption(VoyageAdoptedValueUpdated event) {
        return new VoyageAdoption(
                event.voyageNumber(),
                event.portCalls().stream()
                        .map(call -> VoyageAdoption.portCall(call.port(), call.arrivalAt(), call.departureAt()))
                        .toList(),
                event.infoVersion(),
                event.source(),
                event.publishedAt());
    }
}
