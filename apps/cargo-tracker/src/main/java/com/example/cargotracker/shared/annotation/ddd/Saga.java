package com.example.cargotracker.shared.annotation.ddd;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * サガ。複数の境界づけられたコンテキストにまたがる業務のプロセスを、イベントに応じて進める（第 1 章、ADR-003）。
 * 予約サガ（ドメインモデル、予約の Unit）を作る Bolt で使う。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Saga {}
