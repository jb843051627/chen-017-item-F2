package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TItemAlertRule;
import com.fc.v2.model.custom.RuleVerdict;

/**
 * 办件逾期风险判定线 Service接口。
 *
 * 全市同一类事只准一套说法、取刻度只准走一处：落在哪一档、凭哪一条线，
 * 一律由 {@link #evaluate(BigDecimal, Date, String)} 这一个求值方法现算现回，
 * 取尺与定层之间不许再夹第二回计算。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITItemAlertRuleService {

    /** 按主键查询规则（含已收档者，供倒查来历；收档行不删） */
    TItemAlertRule selectTItemAlertRuleById(Long id);

    /**
     * 【旧·按代号取线，签名照原样留着】按 ruleCode 在 at 时刻生效的版本判档（1..4）。
     * 无生效版本 / 输入越界 / 参数缺失一律返回 0。口径与新求值方法同源，不另立算法。
     */
    int evaluate(String ruleCode, BigDecimal input, Date at);

    /**
     * 【唯一求值方法】取尺 + 定层同一回判定给回，别处不开第二道门。
     *
     * 取尺：凭受理那一天 acceptAt（不凭点开屏的今天），在该事由 bizType 下、
     *   半开窗口 [eff_start, eff_end) 内、未删除的尺里请尊卑次序最高者；
     *   priority 高者先开口，并列认代号排在后头（ruleCode 降序）者，季季同此一认法。
     *   受理日落在哪条窗口就请哪条（去年秋受理今年补结论，请当时在档的旧尺）；
     *   翻不出可用尺 → NO_RULE（无从依凭，不凑邻近日子的尺）。
     * 定层：拿逾期率撞三道刻度，齐平算撞上高段（往高里取）；某道刻度空着＝不设该段，
     *   随上一段往下落，不断档不报错。
     * 退回：input 为空 / &lt;0 / &gt;100，或受理日、事由缺失 → REJECTED，不进判定。
     *
     * @param input    算到今日的逾期率（百分比，合法段 0..100）
     * @param acceptAt 办件受理那一天（取尺所凭）
     * @param bizType  所管事由 IMMEDIATE/PROMISE/TRANSFER
     */
    RuleVerdict evaluate(BigDecimal input, Date acceptAt, String bizType);

    /**
     * 【与旧 int evaluate 并排的另一种取法，同名】按指定代号在受理日的生效版本判档，
     * 回证带齐代号与上架之日；窗口内无此尺 → NO_RULE。
     */
    RuleVerdict evaluate(String ruleCode, BigDecimal input, Date acceptAt, String bizType);

    /** 多规则求值（旧签名保留）：at 时刻可用尺中尊卑最高者的档位；无可用尺返回 0 */
    int evaluateTop(BigDecimal input, Date at);

    /** 多规则求值：限定事由，其余口径同 {@link #evaluate(BigDecimal, Date, String)}，仅回档位 */
    int evaluateTop(BigDecimal input, Date at, String bizType);

    /** at 时刻、该事由下的可用尺（priority 降序、ruleCode 降序）；bizType 可空表示不限事由 */
    List<TItemAlertRule> listAvailable(Date at, String bizType);

    /** @deprecated 用 {@link #listAvailable(Date, String)}；此处保留旧签名，口径=不限事由 */
    @Deprecated
    List<TItemAlertRule> listAvailable(Date at);

    /** 单条尺在 at 时刻是否在其生效窗口内（倒查/看板旁路复用，口径须与求值同源） */
    boolean usable(Long id, Date at);

    /** at 时刻、该事由下可用尺条数（列表页角标 / 看板）；bizType 可空表示不限事由 */
    int countAvailable(Date at, String bizType);

    /** @deprecated 用 {@link #countAvailable(Date, String)}；此处保留旧签名，口径=不限事由 */
    @Deprecated
    int countAvailable(Date at);
}
