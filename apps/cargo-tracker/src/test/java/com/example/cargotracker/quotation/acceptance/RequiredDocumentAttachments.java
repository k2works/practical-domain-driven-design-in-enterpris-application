package com.example.cargotracker.quotation.acceptance;

import com.example.cargotracker.quotation.domain.model.valueobjects.RequiredDocumentAttachment;
import io.cucumber.spring.ScenarioScope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * シナリオの中で、荷主が次の提出・出し直しで添付する書類を持つ。送ったら空にする。
 * 取得した中身と比べるため、これまでに添付した書類をファイル名で覚えておく。
 */
@ScenarioScope
public class RequiredDocumentAttachments {

    private final List<RequiredDocumentAttachment> pending = new ArrayList<>();
    private final Map<String, RequiredDocumentAttachment> attachedByFileName = new LinkedHashMap<>();

    public void add(RequiredDocumentAttachment attachment) {
        pending.add(attachment);
        attachedByFileName.put(attachment.fileName(), attachment);
    }

    /** 次の提出・出し直しで送る書類を取り出し、空にする。 */
    public List<RequiredDocumentAttachment> takePending() {
        List<RequiredDocumentAttachment> taken = List.copyOf(pending);
        pending.clear();
        return taken;
    }

    public RequiredDocumentAttachment attached(String fileName) {
        return attachedByFileName.get(fileName);
    }
}
