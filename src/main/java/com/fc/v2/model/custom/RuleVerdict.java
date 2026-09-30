package com.fc.v2.model.custom;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 一回判定的回证：逾期率落到哪一档、凭的是哪一条线，同一回给出，不许另夹第二回计算。
 * 钉进监察记录的代号与上架之日一律以这里给回的为准，页面不得自取尺、自贴结论。
 *
 * status：
 *   {@link #OK}        已定档，level∈1..4，ruleCode/ruleEffStart 有值
 *   {@link #NO_RULE}   受理日翻不出可用尺——无从依凭，level=0、代号留空，不凑邻近尺
 *   {@link #REJECTED}  递进来的数不在 0..100（负数/越百/空）——退回监察员重录，不进判定
 *
 * @author fuce
 * @date 2026-09-30
 */
public class RuleVerdict implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 已定档 */
    public static final int OK = 1;
    /** 无从依凭（受理日无可用尺） */
    public static final int NO_RULE = 0;
    /** 退回重录（输入越界或缺失） */
    public static final int REJECTED = -1;

    /** 判定情形，见类注释三常量 */
    private int status;
    /** 落档 1低段 2中段 3高段 4顶段；非 OK 为 0 */
    private int level;
    /** 所依代号（OK 时有值） */
    private String ruleCode;
    /** 所依那条尺的上架之日 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date ruleEffStart;
    /** 所依那条尺的线名（仅备屏上展看，判定不依它） */
    private String ruleName;
    /** 本回判定的事由 */
    private String bizType;
    /** 本回判定所用逾期率 */
    private BigDecimal overdueRate;
    /** 取尺所凭的受理那一天 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date acceptAt;

    public static RuleVerdict rejected(BigDecimal input, Date acceptAt, String bizType) {
        RuleVerdict v = new RuleVerdict();
        v.status = REJECTED;
        v.level = 0;
        v.overdueRate = input;
        v.acceptAt = acceptAt;
        v.bizType = bizType;
        return v;
    }

    public static RuleVerdict noRule(BigDecimal input, Date acceptAt, String bizType) {
        RuleVerdict v = new RuleVerdict();
        v.status = NO_RULE;
        v.level = 0;
        v.overdueRate = input;
        v.acceptAt = acceptAt;
        v.bizType = bizType;
        return v;
    }

    public static RuleVerdict of(int level, String ruleCode, Date ruleEffStart, String ruleName,
                                 BigDecimal input, Date acceptAt, String bizType) {
        RuleVerdict v = new RuleVerdict();
        v.status = OK;
        v.level = level;
        v.ruleCode = ruleCode;
        v.ruleEffStart = ruleEffStart;
        v.ruleName = ruleName;
        v.overdueRate = input;
        v.acceptAt = acceptAt;
        v.bizType = bizType;
        return v;
    }

    public boolean isOk() {
        return status == OK;
    }

    public boolean isNoRule() {
        return status == NO_RULE;
    }

    public boolean isRejected() {
        return status == REJECTED;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public Date getRuleEffStart() {
        return ruleEffStart;
    }

    public void setRuleEffStart(Date ruleEffStart) {
        this.ruleEffStart = ruleEffStart;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public BigDecimal getOverdueRate() {
        return overdueRate;
    }

    public void setOverdueRate(BigDecimal overdueRate) {
        this.overdueRate = overdueRate;
    }

    public Date getAcceptAt() {
        return acceptAt;
    }

    public void setAcceptAt(Date acceptAt) {
        this.acceptAt = acceptAt;
    }
}
