package com.example.cargotracker.quotation.domain.model.aggregates;

import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;

/**
 * 業務番号の採番。年を受け取り、その年の次の業務番号を返す（D-10）。
 * 年の決め方（日本時間の年）は業務番号が持ち、このポートは年ごとに重複も欠番もなく数えることだけを受け持つ。
 * 提出と同じトランザクションで呼び、提出が失敗すれば番号も戻るようにする。
 */
public interface TransportRequestNumberIssuer {

    /** その年の次の業務番号を振る。 */
    TransportRequestNumber next(int year);
}
