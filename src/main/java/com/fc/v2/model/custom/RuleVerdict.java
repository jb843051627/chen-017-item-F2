package com.fc.v2.model.custom;

import com.fc.v2.model.auto.TItemAlertRule;

/**
 * 判定线求值结果——取尺与定层由同一回判定给出，中间不许再夹第二回计算。
 * <ul>
 *   <li>{@link #REJECTED}：递进来的数不在 0..100（负数、一百二、空着）或缺要件，不进判定，退回监察员重录；</li>
 *   <li>{@link #NO_RULE}：受理那一日在档且事由对得上的线一条也翻不出——写明无从依凭，不许拿邻近日子凑；</li>
 *   <li>{@link #OK}：取到唯一开口的线并定出等级，代号/上架之日从 {@link #getRule()} 取，页面不得另请一条线。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-09-30
 */
public class RuleVerdict {

    /** 输入越界/缺失：退回重录，不落任何结论 */
    public static final int REJECTED = 0;

    /** 受理日无在档可用线：无从依凭，不许硬给结论 */
    public static final int NO_RULE = 1;

    /** 取线、定层同一回完成 */
    public static final int OK = 2;

    private final int status;
    private final TItemAlertRule rule;
    private final Integer grade;

    private RuleVerdict(int status, TItemAlertRule rule, Integer grade) {
        this.status = status;
        this.rule = rule;
        this.grade = grade;
    }

    public static RuleVerdict rejected() {
        return new RuleVerdict(REJECTED, null, null);
    }

    public static RuleVerdict noRule() {
        return new RuleVerdict(NO_RULE, null, null);
    }

    public static RuleVerdict of(TItemAlertRule rule, int grade) {
        return new RuleVerdict(OK, rule, grade);
    }

    public int getStatus() {
        return status;
    }

    public TItemAlertRule getRule() {
        return rule;
    }

    public Integer getGrade() {
        return grade;
    }

    public boolean isOk() {
        return status == OK;
    }

    public boolean isRejected() {
        return status == REJECTED;
    }

    public boolean isNoRule() {
        return status == NO_RULE;
    }
}
