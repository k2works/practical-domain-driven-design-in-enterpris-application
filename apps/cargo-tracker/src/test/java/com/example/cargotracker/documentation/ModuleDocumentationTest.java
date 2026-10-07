package com.example.cargotracker.documentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.CargoTrackerApplication;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * コードからモジュールの図と説明を生成する（開発戦略の Living Documentation「生成と公開」）。
 * 生成物は build/spring-modulith-docs に出し、CI の成果物にする。リポジトリにはコミットしない。
 */
class ModuleDocumentationTest {

    @Test
    void モジュールの図と説明を生成する() {
        Path output = Path.of("build", "spring-modulith-docs");

        new Documenter(
                        ApplicationModules.of(CargoTrackerApplication.class),
                        Documenter.Options.defaults().withOutputFolder(output.toString()))
                .writeDocumentation();

        assertThat(output.resolve("components.puml")).exists();
        assertThat(output.resolve("module-quotation.puml")).exists();
        assertThat(output.resolve("module-identity.puml")).exists();
        assertThat(output.resolve("module-routing.puml")).exists();
    }
}
