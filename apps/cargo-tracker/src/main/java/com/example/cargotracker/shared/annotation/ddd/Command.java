package com.example.cargotracker.shared.annotation.ddd;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * コマンド。集約の状態を変える操作の種類を表す、ユースケースへの入力（第 1 章）。
 * アプリケーション層の commands パッケージに置き、コマンドサービスが受け付ける。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Command {}
