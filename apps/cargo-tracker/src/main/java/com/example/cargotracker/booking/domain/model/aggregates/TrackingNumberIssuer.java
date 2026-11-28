package com.example.cargotracker.booking.domain.model.aggregates;

import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;

/**
 * 追跡番号の発行（ファクトリ、送信ポート）。推測されにくい一意な追跡番号を発行する（BR-07）。
 */
public interface TrackingNumberIssuer {

    TrackingNumber issue();
}
