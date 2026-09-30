package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TItemSuperviseRecord;
import com.fc.v2.model.custom.JudgeResult;

/**
 * 办件监察台账 Service。
 * <p>评定只追加、不改口：等级连同所依代号、那条尺的上架之日一道钉进台账；
 * 逾期率回落则先前的警报另成一笔销掉，销警笔与现下评定笔各留各的代号与日子，绝不并成一条。
 * 取尺与定层只向 {@link ITItemAlertRuleService} 求一回，本服务不再算第二回。</p>
 *
 * @author fuce
 * @date 2026-09-30
 */
public interface ITItemSuperviseRecordService {

    /**
     * 对单个办件走一回判定并落账（窗口自核与局监察两屏走的都是这一个口子）。
     *
     * @param caseNo      办件编号
     * @param affairType  事由 1即办件 2承诺件 3转报件
     * @param acceptAt    办件受理那一天（取尺凭它，不凭今日）
     * @param overdueRate 算到今日的逾期率（0..100，越界退回重录、不落账）
     */
    JudgeResult judge(String caseNo, Integer affairType, Date acceptAt, BigDecimal overdueRate);

    /** 按办件编号翻台账（按落账之时正序，来历一笔笔可查） */
    List<TItemSuperviseRecord> listByCaseNo(String caseNo);

    /** 当下还在警上的评定笔（高段起警、销警另笔勾销；被销警笔指向的不再算在警） */
    List<TItemSuperviseRecord> listActiveAlerts();

    /** 按主键查笔（倒查来历） */
    TItemSuperviseRecord selectById(Long id);
}
