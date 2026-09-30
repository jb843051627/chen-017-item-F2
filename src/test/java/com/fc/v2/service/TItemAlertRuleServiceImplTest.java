package com.fc.v2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.mapper.auto.TItemAlertRuleMapper;
import com.fc.v2.model.auto.TItemAlertRule;
import com.fc.v2.model.custom.RuleVerdict;
import com.fc.v2.service.impl.TItemAlertRuleServiceImpl;

/**
 * 判定线求值门口径测试：换手日界、尊卑次序、等档取高、空刻度回落、越界退回、按受理日取线、收档不判定。
 */
class TItemAlertRuleServiceImplTest {

    private TItemAlertRuleMapper mapper;
    private TItemAlertRuleServiceImpl svc;
    private final SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /** 秋前纸质尺：高界空 */
    private TItemAlertRule paper;
    /** 秋后系统尺 */
    private TItemAlertRule sysRule;
    /** 即办件省线/局线 */
    private TItemAlertRule jProv, jCity;
    /** 转报件次序并列两条 */
    private TItemAlertRule zProv, zSys;

    @BeforeEach
    void setUp() throws Exception {
        mapper = mock(TItemAlertRuleMapper.class);
        svc = new TItemAlertRuleServiceImpl();
        Field mf = TItemAlertRuleServiceImpl.class.getDeclaredField("itemAlertRuleMapper");
        mf.setAccessible(true);
        mf.set(svc, mapper);

        paper = rule(1001L, "P-PAPER-01", 2, d("5.00"), d("15.00"), null,
                t("2025-01-01 00:00:00"), t("2025-10-01 00:00:00"), 100, 1); // 已收档
        sysRule = rule(1002L, "P-SYS-02", 2, d("5.00"), d("15.00"), d("30.00"),
                t("2025-10-01 00:00:00"), null, 100, 0);
        jProv = rule(2001L, "J-PROV-01", 1, d("2.00"), d("8.00"), d("20.00"),
                t("2025-01-01 00:00:00"), null, 200, 0);
        jCity = rule(2002L, "J-CITY-01", 1, d("3.00"), d("10.00"), d("25.00"),
                t("2025-01-01 00:00:00"), null, 100, 0);
        zProv = rule(3001L, "Z-PROV-01", 3, d("10.00"), d("30.00"), d("50.00"),
                t("2025-01-01 00:00:00"), null, 100, 0);
        zSys = rule(3002L, "Z-SYS-09", 3, d("8.00"), d("25.00"), d("45.00"),
                t("2025-01-01 00:00:00"), null, 100, 0);

        final List<TItemAlertRule> all = Arrays.asList(paper, sysRule, jProv, jCity, zProv, zSys);
        // 按 QueryWrapper 里真正下的条件过滤（del_flag 之外，第二参数即 affair_type 或 rule_code）
        when(mapper.selectList(any())).thenAnswer(new Answer<List<TItemAlertRule>>() {
            @Override
            @SuppressWarnings("unchecked")
            public List<TItemAlertRule> answer(InvocationOnMock inv) {
                com.baomidou.mybatisplus.core.conditions.AbstractWrapper w =
                        (com.baomidou.mybatisplus.core.conditions.AbstractWrapper) inv.getArgument(0);
                if (w == null) {
                    // 重打桩 when(...any()) 时 Mockito 会以 null 参数再调一次旧应答
                    return Collections.emptyList();
                }
                String sql = w.getTargetSql();
                List<TItemAlertRule> out = new ArrayList<TItemAlertRule>();
                for (TItemAlertRule r : all) {
                    if (sql.contains("affair_type")) {
                        // 参数里 del_flag=0 与 affair_type 两个整数，事由取 1..3 那个
                        int affair = 0;
                        for (Object v : w.getParamNameValuePairs().values()) {
                            if (v instanceof Number && ((Number) v).intValue() >= 1) {
                                affair = ((Number) v).intValue();
                            }
                        }
                        if (r.getAffairType() == affair) {
                            out.add(r);
                        }
                    } else if (sql.contains("rule_code")) {
                        for (Object v : w.getParamNameValuePairs().values()) {
                            if (v instanceof String && v.equals(r.getRuleCode())) {
                                out.add(r);
                            }
                        }
                    }
                }
                return out;
            }
        });
        when(mapper.selectCount(any())).thenReturn(0);
    }

    // ---------- 等档取高、各段归属 ----------

    @Test
    void level_boundary_equal_goes_up() {
        Date at = t("2025-12-01 00:00:00"); // 秋后，听 P-SYS-02
        assertEquals(1, svc.evaluate(2, d("0.00"), at).getGrade());
        assertEquals(1, svc.evaluate(2, d("4.99"), at).getGrade());
        assertEquals(2, svc.evaluate(2, d("5.00"), at).getGrade(), "与低界齐平算撞进中段");
        assertEquals(2, svc.evaluate(2, d("14.99"), at).getGrade());
        assertEquals(3, svc.evaluate(2, d("15.00"), at).getGrade(), "与中界齐平算撞进高段");
        assertEquals(3, svc.evaluate(2, d("29.99"), at).getGrade());
        assertEquals(4, svc.evaluate(2, d("30.00"), at).getGrade(), "与高界齐平算撞进超高段");
        assertEquals(4, svc.evaluate(2, d("100.00"), at).getGrade());
    }

    // ---------- 空高段：跟着中段往下落，不报错、不飞到第4档 ----------

    @Test
    void level_null_high_threshold_falls_back_to_mid() {
        Date before = t("2025-09-30 12:00:00"); // 秋前纸质尺在档
        RuleVerdict v = svc.evaluate(2, d("20.00"), before);
        assertTrue(v.isOk());
        assertEquals("P-PAPER-01", v.getRule().getRuleCode());
        assertEquals(2, v.getGrade(), "高段没填，越过中界也跟着中段往下落，不得给3或4");
        assertEquals(2, svc.evaluate(2, d("15.00"), before).getGrade(), "撞中界但无高段，仍是中段");
        assertEquals(1, svc.evaluate(2, d("4.00"), before).getGrade());
    }

    @Test
    void level_null_mid_threshold_falls_back_to_low() {
        TItemAlertRule noMid = rule(5001L, "X-1", 1, d("5.00"), null, d("30.00"),
                t("2025-01-01 00:00:00"), null, 100, 0);
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(noMid));
        assertEquals(1, svc.evaluate(1, d("20.00"), t("2025-02-01 00:00:00")).getGrade(),
                "中段没设，跟着低段落");
    }

    // ---------- 0..100 越界：负数、120、空着，一律退回 ----------

    @Test
    void rate_out_of_range_rejected() {
        Date at = t("2025-12-01 00:00:00");
        assertTrue(svc.evaluate(2, d("-0.01"), at).isRejected());
        assertTrue(svc.evaluate(2, d("100.01"), at).isRejected());
        assertTrue(svc.evaluate(2, d("120"), at).isRejected());
        assertTrue(svc.evaluate(2, null, at).isRejected());
        assertTrue(svc.evaluate((Integer) null, d("5"), at).isRejected());
        assertTrue(svc.evaluate(2, d("5"), null).isRejected());
    }

    // ---------- 换手那天全市一套算法：半开区间 [start,end) ----------

    @Test
    void handoff_day_half_open_old_exclusive_new_inclusive() {
        // 交棒前一刻：旧纸质尺开口
        RuleVerdict before = svc.evaluate(2, d("20.00"), t("2025-09-30 23:59:59"));
        assertEquals("P-PAPER-01", before.getRule().getRuleCode());
        // 交棒那一刻整：旧尺收口（不含）、新尺开口——不会两条都开、也不会空
        RuleVerdict on = svc.evaluate(2, d("20.00"), t("2025-10-01 00:00:00"));
        assertTrue(on.isOk());
        assertEquals("P-SYS-02", on.getRule().getRuleCode());
        assertEquals(3, on.getGrade());
    }

    @Test
    void archived_rule_never_speaks_even_when_date_matches() {
        // 今年开春补去年秋天受理的结论：件受理于 2025-09-15 → 请出当时在档的旧尺
        RuleVerdict backfill = svc.evaluate(2, d("20.00"), t("2025-09-15 09:00:00"));
        assertEquals("P-PAPER-01", backfill.getRule().getRuleCode(), "凭受理日，旧尺当时在档就要请它");
        // 件受理于 2025-12-01：收了档的旧尺不许挤进当下判定
        RuleVerdict now = svc.evaluate(2, d("20.00"), t("2025-12-01 00:00:00"));
        assertEquals("P-SYS-02", now.getRule().getRuleCode());
    }

    @Test
    void no_rule_for_accept_day_returns_no_rule_not_nearby_one() {
        RuleVerdict v = svc.evaluate(2, d("5.00"), t("2024-12-31 23:59:59"));
        assertTrue(v.isNoRule(), "受理日早于所有尺上架：无从依凭，不拿邻近日子凑");
    }

    // ---------- 尊卑次序：数大先开口；并列认代号靠后者，季季一样 ----------

    @Test
    void priority_higher_speaks() {
        RuleVerdict v = svc.evaluate(1, d("9.00"), t("2025-06-01 00:00:00"));
        assertEquals("J-PROV-01", v.getRule().getRuleCode(), "省线次序200压过局线100");
        // 同一输入两条线结论差一层，验证开口权的后果：省线 9% 落中段(2)
        assertEquals(3, v.getGrade());
    }

    @Test
    void priority_tie_goes_to_later_code_deterministically() {
        // 26% 在 Z-PROV-01 是中段(2)，在 Z-SYS-09 是高段(3)——谁开口直接决定结论
        RuleVerdict v = svc.evaluate(3, d("26.00"), t("2025-06-01 00:00:00"));
        assertEquals("Z-SYS-09", v.getRule().getRuleCode(), "次序并列，代号排在后头的开口，确定性");
        assertEquals(3, v.getGrade());
        RuleVerdict again = svc.evaluate(3, d("26.00"), t("2025-06-01 00:00:00"));
        assertEquals(v.getRule().getRuleCode(), again.getRule().getRuleCode(), "季季同一认法");
    }

    // ---------- 旧·按代号方法：签名照旧，收档不判定 ----------

    @Test
    void legacy_evaluate_by_code() {
        final List<TItemAlertRule> both = Arrays.asList(paper, sysRule);
        when(mapper.selectList(any())).thenAnswer(new Answer<List<TItemAlertRule>>() {
            @Override
            @SuppressWarnings("unchecked")
            public List<TItemAlertRule> answer(InvocationOnMock inv) {
                com.baomidou.mybatisplus.core.conditions.AbstractWrapper w =
                        (com.baomidou.mybatisplus.core.conditions.AbstractWrapper) inv.getArgument(0);
                if (w == null) {
                    return Collections.emptyList();
                }
                List<TItemAlertRule> out = new ArrayList<TItemAlertRule>();
                // MP 3.4.2：先取 SQL 片段触发组装，参数对此刻才齐
                w.getTargetSql();
                for (Object v : w.getParamNameValuePairs().values()) {
                    if (v instanceof String) {
                        for (TItemAlertRule r : both) {
                            if (v.equals(r.getRuleCode())) {
                                out.add(r);
                            }
                        }
                    }
                }
                return out;
            }
        });
        assertEquals(0, svc.evaluate("P-PAPER-01", d("20"), t("2026-03-01 00:00:00")),
                "代号已收档（窗口已过），不进当下判定");
        assertEquals(2, svc.evaluate("P-PAPER-01", d("20"), t("2025-09-15 00:00:00")),
                "在档期内按其刻度（空高段回落中段）");
        assertEquals(0, svc.evaluate("P-PAPER-01", d("120"), t("2025-09-15 00:00:00")), "越界返回0");
        assertEquals(0, svc.evaluate(" ", d("5"), t("2025-09-15 00:00:00")), "空代号返回0");
    }

    @Test
    void usable_and_archive() {
        when(mapper.selectById(1001L)).thenReturn(paper);
        assertFalse(svc.usable(1001L, t("2026-01-01 00:00:00")), "收档线不可用");
        when(mapper.selectById(1002L)).thenReturn(sysRule);
        assertTrue(svc.usable(1002L, t("2026-01-01 00:00:00")), "在档线可用");
    }

    @Test
    void archived_flag_without_end_date_never_speaks() {
        // 异常行：旗已放倒却漏记收档日，不得因窗口敞开而漏进当下判定
        TItemAlertRule broken = rule(6001L, "B-1", 2, d("1"), d("2"), d("3"),
                t("2025-01-01 00:00:00"), null, 999, 1);
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(broken));
        RuleVerdict v = svc.evaluate(2, d("1.5"), t("2025-06-01 00:00:00"));
        assertTrue(v.isNoRule());
    }

    @Test
    void archive_rejects_future_end_date() {
        when(mapper.selectById(1002L)).thenReturn(sysRule);
        assertThrows(IllegalArgumentException.class,
                () -> svc.archive(1002L, t("2099-01-01 00:00:00")),
                "即时收档不许把收档日定在将来");
    }

    @Test
    void register_rejects_duplicate_code() {
        when(mapper.selectCount(any())).thenReturn(1);
        TItemAlertRule r = rule(null, "P-SYS-02", 2, d("1"), d("2"), d("3"),
                t("2026-01-01 00:00:00"), null, 10, 0);
        assertThrows(IllegalStateException.class, () -> svc.register(r), "代号独占，重复拒收");
        verify(mapper, never()).insert(any());
    }

    @Test
    void register_requires_affair_and_start() {
        TItemAlertRule r = rule(null, "NEW-1", null, d("1"), d("2"), d("3"),
                t("2026-01-01 00:00:00"), null, 10, 0);
        assertThrows(IllegalArgumentException.class, () -> svc.register(r));
        TItemAlertRule r2 = rule(null, "NEW-2", 1, d("1"), d("2"), d("3"),
                null, null, 10, 0);
        assertThrows(IllegalArgumentException.class, () -> svc.register(r2));
    }

    // ---------- helpers ----------

    private TItemAlertRule rule(Long id, String code, Integer affair, BigDecimal t1, BigDecimal t2,
                                BigDecimal t3, Date start, Date end, int priority, int status) {
        TItemAlertRule r = new TItemAlertRule();
        r.setId(id);
        r.setRuleCode(code);
        r.setAffairType(affair);
        r.setTh1Max(t1);
        r.setTh2Max(t2);
        r.setTh3Max(t3);
        r.setEffStart(start);
        r.setEffEnd(end);
        r.setPriority(priority);
        r.setStatus(status);
        r.setDelFlag(0);
        return r;
    }

    private Date t(String s) {
        try {
            return f.parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private BigDecimal d(String s) {
        return s == null ? null : new BigDecimal(s);
    }
}
