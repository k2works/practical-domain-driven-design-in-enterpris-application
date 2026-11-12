package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 航海の到着予定。最後の区間の航海の採用値から結んだ、最後の揚地への到着予定と、その知らせの公開の順（Bolt 39g4b。【業務の判断】(au)、台帳 D-137）。
 * 到着予定は結べなければ空（結べない新しい知らせを受けたら空にし、公開の順は進める。古い知らせで戻さない）。取得時刻は公開の順が持つ。追跡記録が持ち、
 * 最新の到着見込みと最終取得時刻に使う（【業務の判断】(at)・(av)）。
 *
 * <p>DE-12 の内容の欠けは、listener がこの値の側の作り方（{@link #voyageNumberOf}・{@link #portCallsOf}・{@link #orderOf}・
 * {@link VoyagePortCall#of}）で {@link InvalidEventValueException} にする（前例 {@code ExternalMilestone}）。追跡のドメインは DE-12 の型を持たない。
 *
 * @param arrivalAt 最後の揚地への到着予定（結べなければ空）
 * @param order 知らせの公開の順
 */
@ValueObject
public record VoyageArrival(Optional<UtcInstant> arrivalAt, PublicationOrder order) {

    /** 航海の寄港の下限の数（経路設計の航海・外部データの航海の値と同じ）。 */
    public static final int MIN_PORT_CALLS = 2;

    public VoyageArrival {
        Objects.requireNonNull(arrivalAt, "arrivalAt");
        Objects.requireNonNull(order, "order");
    }

    /** 届いた航海番号（空なら内容の欠け）。 */
    public static String voyageNumberOf(String voyageNumber) {
        if (voyageNumber == null || voyageNumber.isBlank()) {
            throw new InvalidEventValueException("voyageNumber");
        }
        return voyageNumber;
    }

    /** 届いた寄港の並び（null か下限より少なければ内容の欠け）。 */
    public static List<VoyagePortCall> portCallsOf(List<VoyagePortCall> portCalls) {
        if (portCalls == null || portCalls.size() < MIN_PORT_CALLS) {
            throw new InvalidEventValueException("portCalls");
        }
        return List.copyOf(portCalls);
    }

    /** 届いた公開した時刻と出典から公開の順を作る（欠けていれば内容の欠け。参照は受信記録 ID）。 */
    public static PublicationOrder orderOf(UtcInstant publishedAt, Source source) {
        if (publishedAt == null) {
            throw new InvalidEventValueException("publishedAt");
        }
        if (source == null) {
            throw new InvalidEventValueException("source");
        }
        if (source.reference() == null || source.reference().isBlank()) {
            throw new InvalidEventValueException("source.reference");
        }
        if (source.acquiredAt() == null) {
            throw new InvalidEventValueException("source.acquiredAt");
        }
        return new PublicationOrder(publishedAt, source.acquiredAt(), source.reference());
    }

    /** 届いた DE-12 の値が欠けている、または規則に合わない（内容の欠けたイベント。listener はこの型だけを捨てる）。 */
    public static final class InvalidEventValueException extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        /** @param detail 欠けている値の名前（ログに出す） */
        public InvalidEventValueException(String detail) {
            super("航海の採用値の内容が欠けている: " + detail);
        }
    }
}
