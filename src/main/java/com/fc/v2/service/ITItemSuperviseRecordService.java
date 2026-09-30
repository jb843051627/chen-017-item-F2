package com.fc.v2.service;

import java.util.List;

import com.fc.v2.model.auto.TItemSuperviseRecord;
import com.fc.v2.model.custom.RuleVerdict;

/**
 * 办件监察记录 Service接口。
 *
 * 记录只追加、钉过不改口；唯一能改旧行的动作是降级"销警"（只把 alert_flag 置 0，
 * level/代号/上架之日一律不动）。取尺与定层仍只走
 * {@link ITItemAlertRuleService#evaluate(java.math.BigDecimal, java.util.Date, String)}
 * 那一道门，本服务不在中间另夹第二回计算。
 *
 * @author fuce
 * @date 2026-09-30
 */
public interface ITItemSuperviseRecordService {

    /** 警报挂在高段(3)与顶段(4)；落回中段(2)以下即销 */
    int ALERT_MIN_LEVEL = 3;

    /**
     * 对一桩办件走一趟监察：凭其受理日取尺、用其当日逾期率定层，落成一笔监察记录。
     * <ul>
     *   <li>REJECTED：逾期率越界/缺失等，不写记录，退回监察员重录；</li>
     *   <li>NO_RULE：翻不出可用尺，写一笔 level=0、代号留空的"无从依凭"，不凑尺、不硬给结论，
     *       也不动旧警；</li>
     *   <li>OK：追加带等级/代号/上架之日的记录；新等级落回中段以下则销掉先前挂着的警报
     *       （旧行保留，新旧两笔各带各的代号与日子）。</li>
     * </ul>
     * 承办部门即便已撤并也照出结论，绝不改名录。
     */
    RuleVerdict judgeCase(Long caseId);

    /** 按办件单号查它历次的监察记录（新的在前，含已销警行） */
    List<TItemSuperviseRecord> listByCase(String caseNo);
}
