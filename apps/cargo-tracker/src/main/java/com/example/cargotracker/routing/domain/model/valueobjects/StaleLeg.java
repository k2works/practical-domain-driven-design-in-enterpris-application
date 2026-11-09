package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.Objects;

/**
 * 古くなった区間。S-06 を開いた時刻の航海と比べた読み取りの結果で、保存しない（【業務の判断】(w)。Bolt 39f2）。
 *
 * @param candidateNo 候補番号
 * @param legNo 候補の中の区間の位置（1 始まり）
 * @param reason 古くなった理由の区分
 */
@ValueObject
public record StaleLeg(int candidateNo, int legNo, StaleLegReason reason) {

    public StaleLeg {
        if (candidateNo < 1) {
            throw new IllegalArgumentException("候補番号は 1 以上です: " + candidateNo);
        }
        if (legNo < 1) {
            throw new IllegalArgumentException("区間の位置は 1 以上です: " + legNo);
        }
        Objects.requireNonNull(reason, "reason");
    }
}
