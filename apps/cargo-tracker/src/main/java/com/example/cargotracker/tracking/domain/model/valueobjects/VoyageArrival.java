package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.Optional;

/**
 * 航海の到着予定。最後の区間の航海の採用値から結んだ、最後の揚地への到着予定と、その知らせの公開の順（Bolt 39g4b。【業務の判断】(au)、台帳 D-137）。
 * 到着予定は結べなければ空（結べない新しい知らせを受けたら空にし、公開の順は進める。古い知らせで戻さない）。取得時刻は公開の順が持つ。追跡記録が持ち、
 * 最新の到着見込みと最終取得時刻に使う（【業務の判断】(at)・(av)）。
 *
 * <p>DE-12 の内容の欠けの検査と変換は腐敗防止の責務なので、{@code application.internal} の {@code VoyageArrivalInput} が担う（Bolt 40-1 の M3）。
 * 追跡のドメインは DE-12 の型を持たない。
 *
 * @param arrivalAt 最後の揚地への到着予定（結べなければ空）
 * @param order 知らせの公開の順
 */
@ValueObject
public record VoyageArrival(Optional<UtcInstant> arrivalAt, PublicationOrder order) {

    public VoyageArrival {
        Objects.requireNonNull(arrivalAt, "arrivalAt");
        Objects.requireNonNull(order, "order");
    }
}
