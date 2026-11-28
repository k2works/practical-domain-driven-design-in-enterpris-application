package com.example.cargotracker.externaldata.domain.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.externaldata.domain.model.valueobjects.FormatDefect;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * DE-14 外部原本を隔離した の理由の区分（Bolt 39b3）。理由の区分は文字列で、内容の違う原本は {@link ExternalRecordQuarantined#CONTENT_MISMATCH}、
 * 形式の誤りは {@link FormatDefect} の名前。購読する側（監査 39b3b、表示 39c・39d）が区分を取り違えないよう、名前がぶつからないことを見張る。
 */
class ExternalRecordQuarantinedTest {

    @Test
    void 行3_内容の違う原本の理由の区分は形式の誤りのどの名前ともぶつからない() {
        assertThat(Arrays.stream(FormatDefect.values()).map(FormatDefect::name))
                .isNotEmpty()
                .doesNotContain(ExternalRecordQuarantined.CONTENT_MISMATCH);
    }
}
