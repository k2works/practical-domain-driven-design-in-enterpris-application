package com.example.cargotracker.quotation.acceptance;

import com.example.cargotracker.quotation.domain.model.aggregates.RequiredDocumentStorage;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 業務ルール層の受入シナリオで使う、メモリ上の書類の保存。本物と同じく、ファイル名を使わないキーを振る。
 */
public class InMemoryRequiredDocumentStorage implements RequiredDocumentStorage {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public String store(TransportRequestId transportRequestId, byte[] content) {
        String objectKey = "quotation/" + transportRequestId.value() + "/" + UUID.randomUUID();
        objects.put(objectKey, Arrays.copyOf(content, content.length));
        return objectKey;
    }

    @Override
    public byte[] read(String objectKey) {
        byte[] content = objects.get(objectKey);
        if (content == null) {
            throw new NoSuchElementException("書類がありません: " + objectKey);
        }
        return Arrays.copyOf(content, content.length);
    }

    /** 保存したファイルの数。受け付けなかった提出では何も保存しないことを確かめる。 */
    public int count() {
        return objects.size();
    }

    /** シナリオの開始時に消す。 */
    public void clear() {
        objects.clear();
    }
}
