package com.example.cargotracker.identity;

import com.example.cargotracker.shared.acceptance.MutableClock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * identity の統合テストの共通の時計（Bolt 32d）。アプリケーションの {@code Clock} を進められる時計に差し替える。Spring のコンテキストは
 * {@code @Import} の組で区別されるので、時計を差し替えるテストはみなこのクラスを {@code @Import} し、コンテキストを共有する
 * （クラスごとの入れ子の設定にすると、クラスの数だけコンテキストができる）。時刻は各テストの前に置く。
 */
@TestConfiguration(proxyBeanMethods = false)
public class IdentityTestClock {

    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock();
    }
}
