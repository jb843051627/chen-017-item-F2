package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TItemAlertRuleMapper;
import com.fc.v2.model.auto.TItemAlertRule;
import com.fc.v2.model.custom.RuleVerdict;
import com.fc.v2.service.ITItemAlertRuleService;

/**
 * 办件逾期风险判定线 Service业务层处理。
 *
 * 全市一套说法、取刻度只走一处：取尺与定层同在 {@link #evaluate(BigDecimal, Date, String)}
 * 这一回判定里完成，中间不夹第二回计算。
 *
 * 换手口径：窗口半开区间 [effStart, effEnd)，旧尺管到 effEnd 前一刻、新尺自 effEnd 开口，
 * 窗口自核屏与局里监察屏都走这里，日子界限只有这一种讲法。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TItemAlertRuleServiceImpl implements ITItemAlertRuleService {

    /** 在档 */
    private static final int STATUS_ACTIVE = 0;
    /** 收档 */
    private static final int STATUS_ARCHIVED = 1;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** 尊卑次序：priority 降序（数值大先开口）；并列认代号排在后头（ruleCode 降序），季季同此一认法 */
    private static final Comparator<TItemAlertRule> PRIORITY_THEN_CODE_DESC = new Comparator<TItemAlertRule>() {
        @Override
        public int compare(TItemAlertRule a, TItemAlertRule b) {
            int pa = a.getPriority() == null ? 0 : a.getPriority();
            int pb = b.getPriority() == null ? 0 : b.getPriority();
            if (pa != pb) {
                return pb - pa;
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            return cb.compareTo(ca);
        }
    };

    @javax.annotation.Resource
    private TItemAlertRuleMapper itemAlertRuleMapper;

    @Override
    public TItemAlertRule selectTItemAlertRuleById(Long id) {
        return this.itemAlertRuleMapper.selectById(id);
    }

    @Override
    public RuleVerdict evaluate(BigDecimal input, Date acceptAt, String bizType) {
        if (!validInput(input) || acceptAt == null || bizType == null || bizType.trim().isEmpty()) {
            return RuleVerdict.rejected(input, acceptAt, bizType);
        }
        TItemAlertRule rule = pickRule(acceptAt, bizType);
        if (rule == null) {
            // 翻不出可用尺：写明无从依凭，不拿邻近日子的尺凑数
            return RuleVerdict.noRule(input, acceptAt, bizType);
        }
        return RuleVerdict.of(levelOf(rule, input), rule.getRuleCode(), rule.getEffStart(),
                rule.getRuleName(), input, acceptAt, bizType);
    }

    @Override
    public RuleVerdict evaluate(String ruleCode, BigDecimal input, Date acceptAt, String bizType) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || !validInput(input) || acceptAt == null) {
            return RuleVerdict.rejected(input, acceptAt, bizType);
        }
        TItemAlertRule rule = findByCodeAt(ruleCode, acceptAt, bizType);
        if (rule == null) {
            return RuleVerdict.noRule(input, acceptAt, bizType);
        }
        return RuleVerdict.of(levelOf(rule, input), rule.getRuleCode(), rule.getEffStart(),
                rule.getRuleName(), input, acceptAt, bizType);
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || !validInput(input) || at == null) {
            return 0;
        }
        TItemAlertRule rule = findByCodeAt(ruleCode, at, null);
        return rule == null ? 0 : levelOf(rule, input);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        return evaluateTop(input, at, null);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at, String bizType) {
        if (!validInput(input) || at == null) {
            return 0;
        }
        List<TItemAlertRule> avail = listAvailable(at, bizType);
        return avail.isEmpty() ? 0 : levelOf(avail.get(0), input);
    }

    @Override
    public List<TItemAlertRule> listAvailable(Date at) {
        return listAvailable(at, null);
    }

    @Override
    public List<TItemAlertRule> listAvailable(Date at, String bizType) {
        if (at == null) {
            return new ArrayList<TItemAlertRule>();
        }
        String type = bizType == null || bizType.trim().isEmpty() ? null : bizType.trim();
        // 只按 del_flag 取，事由与窗口都在 Java 侧判：日期判定绕开 JDBC 时区错位，
        // 事由与窗口收在同一处，保证无库旁路/单测与线上是同一套口径。
        List<TItemAlertRule> all = this.itemAlertRuleMapper.selectList(
                new QueryWrapper<TItemAlertRule>().eq("del_flag", 0));
        List<TItemAlertRule> hit = new ArrayList<TItemAlertRule>();
        for (TItemAlertRule r : all) {
            if ((type == null || type.equals(r.getBizType())) && eligibleAt(r, at)) {
                hit.add(r);
            }
        }
        Collections.sort(hit, PRIORITY_THEN_CODE_DESC);
        return hit;
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        TItemAlertRule r = this.itemAlertRuleMapper.selectById(id);
        return r != null && eligibleAt(r, at);
    }

    @Override
    public int countAvailable(Date at) {
        return countAvailable(at, null);
    }

    @Override
    public int countAvailable(Date at, String bizType) {
        return listAvailable(at, bizType).size();
    }

    /** 取尺：该事由、受理日窗口内请尊卑最高者 */
    private TItemAlertRule pickRule(Date acceptAt, String bizType) {
        List<TItemAlertRule> avail = listAvailable(acceptAt, bizType);
        return avail.isEmpty() ? null : avail.get(0);
    }

    /** 按代号取其在 at 窗口内的那一条（代号独占一个，换手后新旧各是各的代号）；bizType 非空时须同事由 */
    private TItemAlertRule findByCodeAt(String ruleCode, Date at, String bizType) {
        String type = bizType == null || bizType.trim().isEmpty() ? null : bizType.trim();
        List<TItemAlertRule> hit = this.itemAlertRuleMapper.selectList(new QueryWrapper<TItemAlertRule>()
                .eq("del_flag", 0).eq("rule_code", ruleCode));
        for (TItemAlertRule r : hit) {
            if ((type == null || type.equals(r.getBizType())) && eligibleAt(r, at)) {
                return r;
            }
        }
        return null;
    }

    /**
     * 是否在 at 时刻开口。窗口半开 [effStart, effEnd)，统一在 Java 侧按毫秒比：
     * JDBC 的 serverTimezone 与本机时区不对称，把 Date 直接绑进 SQL 会在换手当天整体错档。
     * 另：收了档（status=1）又无 effEnd 的尺不许挤进当下判定；有 effEnd 的旧尺只在其
     * 历史窗口内为旧案开口（去年秋受理、今春补结论请的就是它）。
     */
    private boolean eligibleAt(TItemAlertRule r, Date at) {
        if (r.getEffStart() == null) {
            return false;
        }
        long t = at.getTime();
        boolean inWindow = t >= r.getEffStart().getTime()
                && (r.getEffEnd() == null || t < r.getEffEnd().getTime());
        if (!inWindow) {
            return false;
        }
        boolean archivedOpen = r.getStatus() != null && r.getStatus() == STATUS_ARCHIVED
                && r.getEffEnd() == null;
        return !archivedOpen;
    }

    /** 合法递数：空、负数、过百一律退回重录 */
    private boolean validInput(BigDecimal input) {
        return input != null
                && input.compareTo(BigDecimal.ZERO) >= 0
                && input.compareTo(HUNDRED) <= 0;
    }

    /**
     * 定层：从低段起，每"撞上或越过"一道设着的刻度升一级。
     * 用"&gt;="即与刻度齐平也算撞上高段（往高里取）。
     * 某道刻度空着＝不设这一道：计数时自然跳过——高段界(th3)空着时，越过中段最高只到
     * 高段(3)，顶段(4)不设，即"跟着中段往下落"，不断档、不报错。
     */
    private int levelOf(TItemAlertRule rule, BigDecimal input) {
        int level = 1;
        if (rule.getTh1Max() != null && input.compareTo(rule.getTh1Max()) >= 0) {
            level++;
        }
        if (rule.getTh2Max() != null && input.compareTo(rule.getTh2Max()) >= 0) {
            level++;
        }
        if (rule.getTh3Max() != null && input.compareTo(rule.getTh3Max()) >= 0) {
            level++;
        }
        return level;
    }
}
