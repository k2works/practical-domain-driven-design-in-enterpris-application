package com.example.cargotracker.identity;

import com.example.cargotracker.TestcontainersConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * 時計を差し替える identity の統合テストの注釈（Bolt 32d の開発レビュー）。{@code @SpringBootTest}・{@code @AutoConfigureMockMvc}・
 * PostgreSQL と共通の時計の {@code @Import} を 1 つにまとめ、手で写して組がずれ、黙って Spring のコンテキスト（と PostgreSQL の
 * コンテナ）が増えることを防ぐ。同じ注釈のクラスはコンテキストと DB を共有するので、件数は利用者で絞るかテストの前後の差で
 * 確かめる（T-113）。JUnit の並列実行を入れるときは見直す。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, IdentityTestClock.class})
public @interface IdentityIntegrationTest {}
