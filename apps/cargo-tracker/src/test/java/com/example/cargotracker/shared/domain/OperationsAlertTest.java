package com.example.cargotracker.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** 運用の警告のログの印（W10-1 の Bolt 57-1。CP-2）。印と CloudWatch のメトリクスフィルターが食い違うと、警告が黙って届かない。 */
class OperationsAlertTest {

    private static final Path TERRAFORM = Path.of("../../ops/terraform/modules/alerts/main.tf");

    /** Terraform の log_alerts の 1 行（引用符で囲んだ code と topics の組）。 */
    private static final Pattern LOG_ALERT = Pattern.compile("(?m)^\\s*\"([a-z-]+)\"\\s*=\\s*\\{ topics");

    @Test
    void 印は決まった形でcodeを含む() {
        assertThat(OperationsAlert.POISON_EVENT.marker()).isEqualTo("[ALERT:poison-event]");
    }

    @Test
    void Terraformのメトリクスフィルターのキーは警告のcodeと過不足なく一致する() throws IOException {
        String terraform = Files.readString(TERRAFORM);

        assertThat(LOG_ALERT.matcher(terraform).results().map(match -> match.group(1)))
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(OperationsAlert.values())
                        .map(OperationsAlert::code)
                        .toList());
    }

    @Test
    void Terraformのフィルターの形は印の形と一致する() throws IOException {
        assertThat(Files.readString(TERRAFORM)).contains("pattern        = \"\\\"[ALERT:${each.key}]\\\"\"");
    }
}
