package com.example.cargotracker.shared.annotation.ddd;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ドメインルール。純粋な業務ルールの定義で、集約の不変条件の判定や業務の判断を助ける（第 1 章）。
 * ドメイン層の rules パッケージに置いた規則に付ける。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DomainRule {}
