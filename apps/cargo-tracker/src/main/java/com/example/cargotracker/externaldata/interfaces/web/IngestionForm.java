package com.example.cargotracker.externaldata.interfaces.web;

import org.springframework.web.multipart.MultipartFile;

/**
 * S-16 の取込の入力（Bolt 39c）。情報源 ID は画面を経ない送信で形の誤った値も来るので文字列で受け、コントローラーが解釈する。ファイルは
 * ブラウザが選ばないときも名前の空の部分として送るので、名前の有無で選んだかを見る。
 */
public class IngestionForm {

    private String dataSourceId;
    private MultipartFile file;

    public String getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(String dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public MultipartFile getFile() {
        return file;
    }

    public void setFile(MultipartFile file) {
        this.file = file;
    }
}
