package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TItemAlertRule;
import com.fc.v2.model.custom.RuleVerdict;

/**
 * 办件逾期风险判定线 Service接口。
 * <p>全市一类事只准一套说法：逾期率落到哪一档、凭的是哪一条线，全收在本服务的求值方法里，
 * 别处不开第二道门。取尺与定层由 {@link #evaluate(Integer, BigDecimal, Date)} 同一回判定给出，
 * 页面不得自己请一条线出来再贴结论。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITItemAlertRuleService {

    /**
     * <b>唯一求值门（事由取法）</b>：凭办件<b>受理那一天</b>与事由取在档线（不凭开屏今日，不拿邻近日子凑），
     * 取尺与定层同一回给回 {@link RuleVerdict}（等级＋所依代号＋上架之日都以它为准）。
     * <ul>
     *   <li>逾期率为空或不在 0..100（负数、120 之类）：REJECTED，退回监察员重录，不落结论；</li>
     *   <li>受理日在档、事由对得上的线一条也没有：NO_RULE，写明无从依凭，不硬判；</li>
     *   <li>几条同在档：尊卑次序高者开口；次序并列认代号排在后头者，季季同一认法；</li>
     *   <li>与刻度齐平算撞进高一段，往高里取；高段空着=不设这一段，随中段往下落。</li>
     * </ul>
     */
    RuleVerdict evaluate(Integer affairType, BigDecimal overdueRate, Date acceptAt);

    /**
     * <b>旧·按代号取线的求值方法，签名照旧不改</b>。按代号定位、且该线在 at 时点在档才判档
     * （收了档的线只许经 {@link #selectTItemAlertRuleById} 倒查来历，不进判定）；
     * 输入越界/参数缺失/代号无在档线一律返回 0。等档取高、空刻度回落口径与求值门一致。
     */
    int evaluate(String ruleCode, BigDecimal overdueRate, Date at);

    /** 按主键查询（收档旧尺经此倒查来历，签名照旧） */
    TItemAlertRule selectTItemAlertRuleById(Long id);

    /**
     * at 时点、某事由在档可用的线，按"尊卑次序降序、次序并列代号降序"排好——
     * 窗口自核一屏与局里监察一屏都经此看界，两处日子界限是同一种讲法。
     */
    List<TItemAlertRule> listAvailable(Integer affairType, Date at);

    /** 某条线在 at 时点是否在档可用（批量/旁路复用，口径与求值门一致） */
    boolean usable(Long id, Date at);

    /**
     * 立新尺入册：代号全局独占（省里给的、局里加的都各占一个），重复拒收。
     * 换尺不删旧——新尺另立一行，旧尺历次留册。
     */
    TItemAlertRule register(TItemAlertRule rule);

    /**
     * 收档：把一条在档尺放倒（旗置收档、记收档之日）。收了档只许倒查来历，不进当下判定。
     * 收档日应与接手新尺的上架之日同一刻，半开交接。
     */
    int archive(Long id, Date effEnd);

    /** 翻册（含已收档的历次行次，供查来历）；事由/旗可空（空=不限） */
    List<TItemAlertRule> listLedger(Integer affairType, Integer status);
}
