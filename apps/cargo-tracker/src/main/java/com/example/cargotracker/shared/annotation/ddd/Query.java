package com.example.cargotracker.shared.annotation.ddd;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * クエリ。集約の状態を変えない問い合わせの種類を表す、照会のユースケースへの入力（第 1 章）。
 * 照会の条件をまとめる型を作るときに付ける。いまのクエリサービスは、条件を引数で受けている。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Query {}
