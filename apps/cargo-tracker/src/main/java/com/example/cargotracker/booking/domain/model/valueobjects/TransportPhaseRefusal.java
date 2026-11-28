package com.example.cargotracker.booking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;

/**
 * 輸送段階を進めない理由。集荷・引渡しの通知で予約の状態と輸送段階を進めないとき（Bolt 39g2。【業務の判断】(am)）。取消済みの予約と、変更・取消しの
 * 申請中（変更承認待ち・取消承認待ち・変更中）の予約は進めない。申請中の扱いは US-05（W11）の承認の規則と一緒に決め直す。
 */
@ValueObject
public enum TransportPhaseRefusal {
    /** 取消済みの予約。 */
    BOOKING_CANCELLED,
    /** 変更・取消しの申請中の予約（W11 で決め直す）。 */
    BOOKING_UNDER_REQUEST
}
