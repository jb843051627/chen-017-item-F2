package com.fc.v2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fc.v2.mapper.auto.TItemAlertRuleMapper;
import com.fc.v2.model.auto.TItemAlertRule;
import com.fc.v2.model.custom.RuleVerdict;
import com.fc.v2.service.impl.TItemAlertRuleServiceImpl;

/**
 * 判定线唯一求值方法口径测试（不起 Spring、不连库，mapper 打桩）。
 * 覆盖：齐平取高、空高段随中段落、越界退回、无尺无从依凭、并列取代号靠后、
 * 换手半开区间 eff_end 不含、旧尺历史回请、收档不挤当下、事由各管各的。
 *
 * @author fuce
 * @date 2026-09-30
 */
@ExtendWith(MockitoExtension.class)
class TItemAlertRuleServiceImplTest {

    @Mock
    private TItemAlertRuleMapper mapper;

    @InjectMocks
    private TItemAlertRuleServiceImpl service;

    private static final String IMMEDIATE = "IMMEDIATE";
    private static final String PROMISE = "PROMISE";

    private final SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private Date d(String s) throws Exception {
        return f.parse(s);
    }

    private TItemAlertRule rule(String code, String biz, String t1, String t2, String t3,
                                String start, String end, int priority, int status) {
        TItemAlertRule r = new TItemAlertRule();
        r.setId((long) code.hashCode());
        r.setRuleCode(code);
        r.setRuleName(code + "-name");
        r.setBizType(biz);
        r.setTh1Max(t1 == null ? null : new BigDecimal(t1));
        r.setTh2Max(t2 == null ? null : new BigDecimal(t2));
        r.setTh3Max(t3 == null ? null : new BigDecimal(t3));
        try {
            r.setEffStart(start == null ? null : d(start));
            r.setEffEnd(end == null ? null : d(end));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        r.setPriority(priority);
        r.setStatus(status);
        r.setDelFlag(0);
        return r;
    }

    private void feed(TItemAlertRule... rs) {
        List<TItemAlertRule> list = new ArrayList<TItemAlertRule>(Arrays.asList(rs));
        lenient().when(mapper.selectList(any())).thenReturn(list);
    }

    private RuleVerdict eval(String rate, String at, String biz) throws Exception {
        return service.evaluate(rate == null ? null : new BigDecimal(rate), d(at), biz);
    }

    private TItemAlertRule immediateActive() {
        return rule("R002", IMMEDIATE, "6", "12", "25", "2025-10-01 00:00:00", null, 100, 0);
    }

    @BeforeEach
    void setUp() {
        lenient().when(mapper.selectById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            TItemAlertRule r = immediateActive();
            r.setId(id);
            return r;
        });
    }

    // ---------- 定层：齐平取高 ----------

    @Test
    void level_belowLow_isLow() throws Exception {
        feed(rule("R002", IMMEDIATE, "6", "12", "25", "2025-10-01 00:00:00", null, 100, 0));
        RuleVerdict v = eval("5.99", "2026-01-01 00:00:00", IMMEDIATE);
        assertTrue(v.isOk());
        assertEquals(1, v.getLevel());
    }

    @Test
    void level_equalEachThreshold_roundsUp() throws Exception {
        feed(rule("R002", IMMEDIATE, "6", "12", "25", "2025-10-01 00:00:00", null, 100, 0));
        assertEquals(2, eval("6", "2026-01-01 00:00:00", IMMEDIATE).getLevel());   // 齐平低界→中段
        assertEquals(3, eval("12", "2026-01-01 00:00:00", IMMEDIATE).getLevel());  // 齐平中界→高段
        assertEquals(4, eval("25", "2026-01-01 00:00:00", IMMEDIATE).getLevel());  // 齐平高界→顶段
    }

    @Test
    void level_aboveHigh_isTop() throws Exception {
        feed(rule("R002", IMMEDIATE, "6", "12", "25", "2025-10-01 00:00:00", null, 100, 0));
        assertEquals(4, eval("99.99", "2026-01-01 00:00:00", IMMEDIATE).getLevel());
    }

    // ---------- 空高段：不设高段，随中段落，不断档不报错 ----------

    @Test
    void level_missingHigh_neverReachesTop() throws Exception {
        // R003 早年纸质簿录入：高段原本没填
        feed(rule("R003", PROMISE, "10", "30", null, "2025-01-01 00:00:00", null, 50, 0));
        assertEquals(1, eval("5", "2026-01-01 00:00:00", PROMISE).getLevel());
        assertEquals(2, eval("10", "2026-01-01 00:00:00", PROMISE).getLevel());   // 齐平低界→中段
        assertEquals(2, eval("29.99", "2026-01-01 00:00:00", PROMISE).getLevel());
        assertEquals(3, eval("30", "2026-01-01 00:00:00", PROMISE).getLevel());   // 齐平中界→高段(顶段不设)
        assertEquals(3, eval("31", "2026-01-01 00:00:00", PROMISE).getLevel());
        assertEquals(3, eval("100", "2026-01-01 00:00:00", PROMISE).getLevel());
    }

    // ---------- 递数越界 / 缺失：退回重录 ----------

    @Test
    void input_outOfRangeOrMissing_rejected() throws Exception {
        feed(immediateActive());
        assertTrue(eval("-0.01", "2026-01-01 00:00:00", IMMEDIATE).isRejected());
        assertTrue(eval("100.01", "2026-01-01 00:00:00", IMMEDIATE).isRejected());
        assertTrue(eval("120", "2026-01-01 00:00:00", IMMEDIATE).isRejected());
        assertTrue(eval(null, "2026-01-01 00:00:00", IMMEDIATE).isRejected());
    }

    @Test
    void input_zeroAndHundred_areValid() throws Exception {
        feed(rule("R", IMMEDIATE, "6", "12", "25", "2025-10-01 00:00:00", null, 100, 0));
        assertEquals(1, eval("0", "2026-01-01 00:00:00", IMMEDIATE).getLevel());
        assertEquals(4, eval("100", "2026-01-01 00:00:00", IMMEDIATE).getLevel());
    }

    @Test
    void missingAcceptAtOrBizType_rejected() throws Exception {
        feed(immediateActive());
        RuleVerdict noDate = service.evaluate(new BigDecimal("10"), null, IMMEDIATE);
        RuleVerdict noBiz = service.evaluate(new BigDecimal("10"), d("2026-01-01 00:00:00"), "  ");
        assertTrue(noDate.isRejected());
        assertTrue(noBiz.isRejected());
    }

    // ---------- 无尺：无从依凭，不凑邻近尺 ----------

    @Test
    void noRule_acceptBeforeEarliest_noRule() throws Exception {
        feed(rule("R002", IMMEDIATE, "6", "12", "25", "2025-10-01 00:00:00", null, 100, 0));
        RuleVerdict v = eval("10", "2024-12-31 23:59:59", IMMEDIATE);
        assertTrue(v.isNoRule());
        assertEquals(0, v.getLevel());
        assertNull(v.getRuleCode());
    }

    @Test
    void noRule_bizTypeMismatch_noRule() throws Exception {
        feed(rule("R002", IMMEDIATE, "6", "12", "25", "2025-10-01 00:00:00", null, 100, 0));
        RuleVerdict v = eval("10", "2026-01-01 00:00:00", PROMISE); // 即办件的尺不管承诺件
        assertTrue(v.isNoRule());
    }

    // ---------- 尊卑次序：priority 高者先开口；并列认代号排在后头，季季一样 ----------

    @Test
    void tiePriority_prefersLaterCode_andIsDeterministic() throws Exception {
        // 同窗口、同优先级、同事由；input=11：R001 落 3 段，R002 落 2 段，借此辨认选了谁
        feed(rule("R001", IMMEDIATE, "5", "10", "20", "2025-10-01 00:00:00", null, 100, 0),
             rule("R002", IMMEDIATE, "6", "12", "25", "2025-10-01 00:00:00", null, 100, 0));
        RuleVerdict first = eval("11", "2026-01-01 00:00:00", IMMEDIATE);
        assertEquals("R002", first.getRuleCode());
        assertEquals(2, first.getLevel());
        // 季季同此一认法：再判一回仍是代号靠后的 R002
        RuleVerdict second = eval("11", "2026-04-01 00:00:00", IMMEDIATE);
        assertEquals("R002", second.getRuleCode());
    }

    @Test
    void higherPriority_speaksFirst() throws Exception {
        feed(rule("RLOW", IMMEDIATE, "5", "10", "20", "2025-10-01 00:00:00", null, 100, 0),
             rule("RHIGH", IMMEDIATE, "6", "12", "25", "2025-10-01 00:00:00", null, 200, 0));
        RuleVerdict v = eval("11", "2026-01-01 00:00:00", IMMEDIATE);
        assertEquals("RHIGH", v.getRuleCode());
        assertEquals(2, v.getLevel());
    }

    // ---------- 换手半开区间 + 旧尺历史回请 + 收档不挤当下 ----------

    private TItemAlertRule oldPaper() {
        // 年初纸刻 R001：管到 2025-10-01 前一刻，已收档(status=1)
        return rule("R001", IMMEDIATE, "5", "10", "20",
                "2025-01-01 00:00:00", "2025-10-01 00:00:00", 100, 1);
    }

    @Test
    void handover_endExclusive_oldBeforeNewOnTheDay() throws Exception {
        feed(oldPaper(), immediateActive());
        // 换手前一刻仍是旧尺；input=22：旧尺顶段(4)、新尺高段(3)
        RuleVerdict before = eval("22", "2025-09-30 23:59:59", IMMEDIATE);
        assertEquals("R001", before.getRuleCode());
        assertEquals(4, before.getLevel());
        // 换手当天零点整起由新尺开口（eff_end 不含）
        RuleVerdict onTheDay = eval("22", "2025-10-01 00:00:00", IMMEDIATE);
        assertEquals("R002", onTheDay.getRuleCode());
        assertEquals(3, onTheDay.getLevel());
    }

    @Test
    void oldCaseAcceptedLastAutumn_judgedNow_summonsArchivedRuleOfThatDay() throws Exception {
        // 去年秋天受理、今年开春补结论：凭受理日把当时在档、现已收档的旧尺请出来
        feed(oldPaper(), immediateActive());
        RuleVerdict v = eval("8", "2025-09-15 10:00:00", IMMEDIATE);
        assertEquals("R001", v.getRuleCode());
        assertEquals(2, v.getLevel());
    }

    @Test
    void archivedRuleWithoutEnd_neverSqueezesIntoCurrent() throws Exception {
        // 收了档又没交棒日的尺，不许挤进当下判定 → 无从依凭
        feed(rule("RARCH", IMMEDIATE, "5", "10", "20", "2025-01-01 00:00:00", null, 100, 1));
        RuleVerdict v = eval("10", "2026-01-01 00:00:00", IMMEDIATE);
        assertTrue(v.isNoRule());
    }

    // ---------- 回证带齐代号与上架之日 ----------

    @Test
    void verdictCarriesCodeAndEffStart() throws Exception {
        feed(immediateActive());
        RuleVerdict v = eval("10", "2026-01-01 00:00:00", IMMEDIATE);
        assertEquals("R002", v.getRuleCode());
        assertEquals(d("2025-10-01 00:00:00"), v.getRuleEffStart());
        assertEquals(0, new BigDecimal("10").compareTo(v.getOverdueRate()));
    }

    // ---------- 旁路口径：usable / countAvailable ----------

    @Test
    void usable_honorsWindowAndStatus() throws Exception {
        TItemAlertRule old = oldPaper();
        old.setId(7L);
        when(mapper.selectById(7L)).thenReturn(old);
        assertTrue(service.usable(7L, d("2025-09-30 12:00:00")));
        assertFalse(service.usable(7L, d("2025-10-01 00:00:00")));
    }

    @Test
    void countAvailable_onlyBizInWindow() throws Exception {
        feed(oldPaper(), immediateActive(),
             rule("R003", PROMISE, "10", "30", null, "2025-01-01 00:00:00", null, 50, 0));
        assertEquals(1, service.countAvailable(d("2026-01-01 00:00:00"), IMMEDIATE)); // 只剩秋刻
        assertEquals(1, service.countAvailable(d("2026-01-01 00:00:00"), PROMISE));
        assertEquals(2, service.countAvailable(d("2026-01-01 00:00:00"), null));     // 不限事由:秋刻+承诺
        // 换手前：即办件只有当时在档的旧纸刻 R001（秋刻尚未开口）
        assertEquals(1, service.countAvailable(d("2025-09-30 00:00:00"), IMMEDIATE));
        assertEquals(2, service.countAvailable(d("2025-09-30 00:00:00"), null));     // 旧纸刻+承诺
    }
}
