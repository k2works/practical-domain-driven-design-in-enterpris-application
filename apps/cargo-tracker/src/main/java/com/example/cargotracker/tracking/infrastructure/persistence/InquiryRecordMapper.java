package com.example.cargotracker.tracking.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;

/**
 * 照会記録の表（`tracking.inquiry_record`）の MyBatis のマッパー（Bolt 40-15）。SQL は同じパッケージの InquiryRecordMapper.xml に置く。
 */
@Mapper
public interface InquiryRecordMapper {

    void insert(InquiryRecordRow row);
}
