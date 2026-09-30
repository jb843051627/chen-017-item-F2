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
 * <p>全市一册、一处取刻度：在档过滤、换手日界、尊卑次序、等档取高、空刻度回落、0..100 越界退回，
 * 口径全部收在本类，窗口自核与局监察两屏共用，不许各算各的。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TItemAlertRuleServiceImpl implements ITItemAlertRuleService {

    /** 在档旗：0 在档（进当下判定）；1 收档（只许倒查来历） */
    private static final int STATUS_ACTIVE = 0;

    private static final int NORMAL = 0;

    /** 逾期率允许的上下限（含）：0..100，之外的数一律退回重录 */
    private static final BigDecimal RATE_MIN = BigDecimal.ZERO;
    private static final BigDecimal RATE_MAX = new BigDecimal("100");

    /**
     * 尊卑次序降序（数值大的先开口）；次序并列认代号排在后头者（代号降序）。
     * 纯字符串/整数比较，不随时辰走——季季同一认法，不许这季认前一条下季认后一条。
     */
    private static final Comparator<TItemAlertRule> PRIORITY_THEN_CODE_DESC = new Comparator<TItemAlertRule>() {
        @Override
        public int compare(TItemAlertRule a, TItemAlertRule b) {
            int pa = a.getPriority() == null ? Integer.MIN_VALUE : a.getPriority();
            int pb = b.getPriority() == null ? Integer.MIN_VALUE : b.getPriority();
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
    public RuleVerdict evaluate(Integer affairType, BigDecimal overdueRate, Date acceptAt) {
        // 递进来的数不落在 0..100：负数、120、空着——不进判定，退回监察员重录
        if (!rateInRange(overdueRate) || affairType == null || acceptAt == null) {
            return RuleVerdict.rejected();
        }
        List<TItemAlertRule> avail = listAvailable(affairType, acceptAt);
        if (avail.isEmpty()) {
            // 翻不出受理日在档的尺：写明无从依凭，不拿邻近日子的某条凑数
            return RuleVerdict.noRule();
        }
        // 取尺（排在最前那条开口）与定层同一回给出，中间不夹第二回计算
        TItemAlertRule speaker = avail.get(0);
        return RuleVerdict.of(speaker, levelOf(speaker, overdueRate));
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal overdueRate, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || at == null || !rateInRange(overdueRate)) {
            return 0;
        }
        List<TItemAlertRule> hit = itemAlertRuleMapper.selectList(new QueryWrapper<TItemAlertRule>()
                .eq("del_flag", NORMAL)
                .eq("rule_code", ruleCode.trim()));
        TItemAlertRule active = null;
        for (TItemAlertRule r : hit) {
            if (inForceAt(r, at)) {
                active = r;
                break;
            }
        }
        // 收了档的代号不许进当下判定；同代号多版时取受理日在档的那一版
        return active == null ? 0 : levelOf(active, overdueRate);
    }

    @Override
    public TItemAlertRule selectTItemAlertRuleById(Long id) {
        return id == null ? null : itemAlertRuleMapper.selectById(id);
    }

    @Override
    public List<TItemAlertRule> listAvailable(Integer affairType, Date at) {
        if (affairType == null || at == null) {
            return new ArrayList<TItemAlertRule>();
        }
        List<TItemAlertRule> all = itemAlertRuleMapper.selectList(new QueryWrapper<TItemAlertRule>()
                .eq("del_flag", NORMAL)
                .eq("affair_type", affairType));
        List<TItemAlertRule> avail = new ArrayList<TItemAlertRule>();
        for (TItemAlertRule r : all) {
            if (inForceAt(r, at)) {
                avail.add(r);
            }
        }
        Collections.sort(avail, PRIORITY_THEN_CODE_DESC);
        return avail;
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        TItemAlertRule r = itemAlertRuleMapper.selectById(id);
        return r != null && inForceAt(r, at);
    }

    @Override
    public synchronized TItemAlertRule register(TItemAlertRule rule) {
        if (rule == null || rule.getRuleCode() == null || rule.getRuleCode().trim().isEmpty()) {
            throw new IllegalArgumentException("代号不得空");
        }
        if (rule.getAffairType() == null) {
            throw new IllegalArgumentException("管的事由必填：1即办件 2承诺件 3转报件");
        }
        if (rule.getEffStart() == null) {
            throw new IllegalArgumentException("上架那一天必填");
        }
        if (rule.getEffEnd() != null && rule.getEffEnd().before(rule.getEffStart())) {
            throw new IllegalArgumentException("预定收档之日不得早于上架之日");
        }
        String code = rule.getRuleCode().trim();
        Integer dup = itemAlertRuleMapper.selectCount(new QueryWrapper<TItemAlertRule>()
                .eq("del_flag", NORMAL)
                .eq("rule_code", code));
        if (dup != null && dup > 0) {
            throw new IllegalStateException("代号已在册：" + code + "（一个代号独占一行，不另开第二行）");
        }
        rule.setRuleCode(code);
        rule.setDelFlag(NORMAL);
        if (rule.getStatus() == null) {
            // 新尺默认在档；上架之日在将来时，[start,end) 判定自会拦住，到点才开口
            rule.setStatus(STATUS_ACTIVE);
        }
        itemAlertRuleMapper.insert(rule);
        return rule;
    }

    @Override
    public int archive(Long id, Date effEnd) {
        if (id == null) {
            return 0;
        }
        TItemAlertRule r = itemAlertRuleMapper.selectById(id);
        if (r == null || (r.getDelFlag() != null && r.getDelFlag() != NORMAL)) {
            return 0;
        }
        if (effEnd != null && r.getEffStart() != null && effEnd.before(r.getEffStart())) {
            throw new IllegalArgumentException("收档之日不得早于上架之日");
        }
        if (effEnd != null && effEnd.after(new Date())) {
            // 将来才收口属于"预定换手"：那应另立接手新尺，而不是提前放倒这面旗
            throw new IllegalArgumentException("收档之日不得晚于今日");
        }
        // 只放倒旗与记收档日，行次留在册里供倒查来历，绝不删
        r.setStatus(1);
        r.setEffEnd(effEnd == null ? new Date() : effEnd);
        return itemAlertRuleMapper.updateById(r);
    }

    @Override
    public List<TItemAlertRule> listLedger(Integer affairType, Integer status) {
        QueryWrapper<TItemAlertRule> w = new QueryWrapper<TItemAlertRule>().eq("del_flag", NORMAL);
        if (affairType != null) {
            w.eq("affair_type", affairType);
        }
        if (status != null) {
            w.eq("status", status);
        }
        w.orderBy(true, true, "affair_type")
                .orderBy(true, false, "eff_start")
                .orderBy(true, false, "priority");
        return itemAlertRuleMapper.selectList(w);
    }

    /**
     * 在档判定（两屏共用的同一种日子讲法）：at 落在半开区间 [eff_start, eff_end) 之内即在档开口。
     * 旧线管到交棒日 00:00:00 为止（不含），新线自同一刻起开口——换手那天全市只有一套算法，
     * 不会两条都开口、也不会两条都不开口。
     * <p>只认窗口、不另认当下那面旗：去年秋天受理、今年开春补结论时，旧尺的旗虽已放倒，
     * 其历史窗口仍在，须凭受理日把它请出来；收了档"不进当下判定"由收档日（不晚于今日）在窗口上
     * 自然排除——{@link #archive} 不允许把收档日定在将来。旗只用于册面展示与倒查来历。</p>
     * <p>日期一律在 Java 侧比较：JDBC serverTimezone 与本机不对称，把 Date 直接交给 SQL 会整体偏移，
     * 切换当日会随机错档（实测踩到）。</p>
     */
    private boolean inForceAt(TItemAlertRule r, Date at) {
        if (r == null || r.getEffStart() == null || at.before(r.getEffStart())) {
            return false;
        }
        // 收档日为空 = 一直开口；但旗已放倒又漏记收档日的异常行，按收档处理，不许漏进当下判定
        if (r.getEffEnd() == null) {
            return r.getStatus() == null || r.getStatus() == STATUS_ACTIVE;
        }
        // 非空则交棒那一刻（不含）收口
        return at.before(r.getEffEnd());
    }

    private boolean rateInRange(BigDecimal rate) {
        return rate != null
                && rate.compareTo(RATE_MIN) >= 0
                && rate.compareTo(RATE_MAX) <= 0;
    }

    /**
     * 定层：拿逾期率从头依次撞三道刻度，与刻度齐平算撞进高的那一段（不严格小于即上靠，往高里取）。
     * 某道刻度空着 = 它封顶的那一段不设立，不报错、不断档，从上一段往下落：
     * 早年纸质录入的尺高界没填，越过中界也进不了高段，跟着中段落回中段等级，
     * 绝不会因高界空着飞到第四段。低界空着则低段不封口，整尺只给低段。
     * 界齐时的等级：撞低界进中段(2)、撞中界且高界在则进高段(3)、撞高界进超高段(4)。
     */
    private int levelOf(TItemAlertRule rule, BigDecimal rate) {
        BigDecimal th1 = rule.getTh1Max();
        BigDecimal th2 = rule.getTh2Max();
        BigDecimal th3 = rule.getTh3Max();
        if (th1 == null || rate.compareTo(th1) < 0) {
            return 1;
        }
        if (th2 == null) {
            // 中界空：中段不设，跟着低段落
            return 1;
        }
        if (rate.compareTo(th2) < 0) {
            return 2;
        }
        if (th3 == null) {
            // 高界空：高段不设，跟着中段往下落
            return 2;
        }
        if (rate.compareTo(th3) < 0) {
            return 3;
        }
        return 4;
    }
}
