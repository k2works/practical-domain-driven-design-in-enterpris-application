package com.example.cargotracker.externaldata.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

/**
 * 外部原本の保存の契約（Bolt 39 の行 6〜8）。メモリの実装で流し、39a2 の DB の実装と W10 の S3 の実装が継いで同じ約束を確かめる。
 */
public abstract class ExternalPayloadStorageContract {

    protected abstract ExternalPayloadStorage storage();

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void ファイル全体と行のpayloadをそれぞれのキーで保存して読み戻せる() {
        PayloadObjectKey fileKey = PayloadObjectKey.newIngestionFile();
        PayloadObjectKey recordKey = PayloadObjectKey.newRecordPayload();
        byte[] file = bytes("external_id,external_version\r\nV-1,1\r\n");
        byte[] recordPayload = bytes("V-1,1");
        ExternalPayloadStorage storage = storage();

        storage.store(fileKey, file);
        storage.store(recordKey, recordPayload);

        assertThat(fileKey.value()).startsWith("externaldata/ingestions/");
        assertThat(recordKey.value()).startsWith("externaldata/records/");
        assertThat(storage.read(fileKey)).isEqualTo(file);
        assertThat(storage.read(recordKey)).isEqualTo(recordPayload);
    }

    @Test
    void 空のpayloadとUTF8でないバイト列もそのまま保存して読み戻せる() {
        PayloadObjectKey emptyKey = PayloadObjectKey.newRecordPayload();
        PayloadObjectKey binaryKey = PayloadObjectKey.newRecordPayload();
        byte[] binary = {0x00, (byte) 0xFF, (byte) 0xFE, 0x41, 0x00};
        ExternalPayloadStorage storage = storage();

        storage.store(emptyKey, new byte[0]);
        storage.store(binaryKey, binary);

        assertThat(storage.read(emptyKey)).isEmpty();
        assertThat(storage.read(binaryKey)).containsExactly(0x00, 0xFF, 0xFE, 0x41, 0x00);
    }

    @Test
    void 保存の後に呼び手の配列を書き換えても原本は変わらない() {
        PayloadObjectKey key = PayloadObjectKey.newRecordPayload();
        byte[] payload = bytes("V-1,1");
        ExternalPayloadStorage storage = storage();
        storage.store(key, payload);

        payload[0] = 'X';

        assertThat(storage.read(key)).isEqualTo(bytes("V-1,1"));
    }

    @Test
    void 読んだ配列を書き換えても読み直すと元のまま() {
        PayloadObjectKey key = PayloadObjectKey.newRecordPayload();
        ExternalPayloadStorage storage = storage();
        storage.store(key, bytes("V-1,1"));

        storage.read(key)[0] = 'X';

        assertThat(storage.read(key)).isEqualTo(bytes("V-1,1"));
    }

    @Test
    void キーとpayloadのnullは受け付けない() {
        ExternalPayloadStorage storage = storage();
        PayloadObjectKey key = PayloadObjectKey.newRecordPayload();
        byte[] payload = bytes("V-1,1");

        assertThatThrownBy(() -> storage.store(null, payload)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> storage.store(key, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> storage.read(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void 同じキーには上書きせず最初のバイト列が残る() {
        PayloadObjectKey key = PayloadObjectKey.newRecordPayload();
        ExternalPayloadStorage storage = storage();
        storage.store(key, bytes("first"));
        byte[] second = bytes("second");

        assertThatThrownBy(() -> storage.store(key, second)).isInstanceOf(IllegalStateException.class);
        assertThat(storage.read(key)).isEqualTo(bytes("first"));
    }

    @Test
    void 保存していないキーは読めない() {
        ExternalPayloadStorage storage = storage();
        PayloadObjectKey unknown = PayloadObjectKey.newRecordPayload();

        assertThatThrownBy(() -> storage.read(unknown)).isInstanceOf(NoSuchElementException.class);
    }
}
