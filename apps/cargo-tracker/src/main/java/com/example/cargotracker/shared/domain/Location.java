package com.example.cargotracker.shared.domain;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.regex.Pattern;

/**
 * 場所。UN/LOCODE（国コード 2 文字 + 地点コード 3 文字）で識別する。
 *
 * @param unLocode UN/LOCODE
 */
@ValueObject
public record Location(String unLocode) {

    private static final Pattern UN_LOCODE = Pattern.compile("[A-Z]{2}[A-Z2-9]{3}");

    public Location {
        if (unLocode == null || !UN_LOCODE.matcher(unLocode).matches()) {
            throw new IllegalArgumentException("UN/LOCODE の形式（国コード 2 文字 + 地点コード 3 文字）ではありません: " + unLocode);
        }
    }
}
