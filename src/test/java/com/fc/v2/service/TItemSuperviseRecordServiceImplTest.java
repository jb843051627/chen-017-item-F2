package com.fc.v2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import org.mockito.ArgumentCaptor;

import com.fc.v2.mapper.auto.TItemSuperviseRecordMapper;
import com.fc.v2.model.auto.TItemAlertRule;
import com.fc.v2.model.auto.TItemSuperviseRecord;
import com.fc.v2.model.custom.JudgeResult;
import com.fc.v2.model.custom.RuleVerdict;
import com.fc.v2.service.impl.TItemSuperviseRecordServiceImpl;

/**
 * 监察台账口径测试：钉账不改口、越界不落、无从依凭落笔记、回落销警另成一笔且新旧各留代号与日子。
 */
class TItemSuperviseRecordServiceImplTest {

    private TItemSuperviseRecordMapper recordMapper;
    private ITItemAlertRuleService alertRuleService;
    private TItemSuperviseRecordServiceImpl svc;
    private final SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @BeforeEach
    void setUp() throws Exception {
        recordMapper = mock(TItemSuperviseRecordMapper.class);
        alertRuleService = mock(ITItemAlertRuleService.class);
        svc = new TItemSuperviseRecordServiceImpl();
        inject(svc, "superviseRecordMapper", recordMapper);
        inject(svc, "itemAlertRuleService", alertRuleService);
    }

    @Test
    void rejected_input_writes_nothing() {
        when(alertRuleService.evaluate(any(Integer.class), any(BigDecimal.class), any(Date.class))).thenReturn(RuleVerdict.rejected());
        JudgeResult r = svc.judge("C1", 2, t("2025-12-01 00:00:00"), new BigDecimal("120"));
        assertEquals(JudgeResult.REJECTED, r.getStatus());
        verify(recordMapper, never()).insert(any());
    }

    @Test
    void blank_case_no_rejected() {
        JudgeResult r = svc.judge("  ", 2, t("2025-12-01 00:00:00"), new BigDecimal("5"));
        assertEquals(JudgeResult.REJECTED, r.getStatus());
        verify(alertRuleService, never()).evaluate(any(Integer.class), any(BigDecimal.class), any(Date.class));
        verify(recordMapper, never()).insert(any());
    }

    @Test
    void no_rule_writes_a_note_without_grade_or_code() {
        when(alertRuleService.evaluate(any(Integer.class), any(BigDecimal.class), any(Date.class))).thenReturn(RuleVerdict.noRule());
        JudgeResult r = svc.judge("C1", 2, t("2024-01-01 00:00:00"), new BigDecimal("5"));
        assertEquals(JudgeResult.NO_RULE, r.getStatus());

        ArgumentCaptor<TItemSuperviseRecord> cap = ArgumentCaptor.forClass(TItemSuperviseRecord.class);
        verify(recordMapper).insert(cap.capture());
        TItemSuperviseRecord rec = cap.getValue();
        assertEquals(TItemSuperviseRecord.KIND_NO_RULE, rec.getRecordKind());
        assertNull(rec.getGradeLevel(), "无从依凭不硬给等级");
        assertNull(rec.getRuleCode(), "无从依凭不贴线代号");
    }

    @Test
    void high_grade_is_pinned_with_rule_code_and_eff_start() {
        TItemAlertRule rule = rule("P-SYS-02", t("2025-10-01 00:00:00"));
        when(alertRuleService.evaluate(any(Integer.class), any(BigDecimal.class), any(Date.class)))
                .thenReturn(RuleVerdict.of(rule, 3));
        // 无在警旧账
        when(recordMapper.selectList(any())).thenReturn(Collections.<TItemSuperviseRecord>emptyList());

        JudgeResult r = svc.judge("C9", 2, t("2025-12-01 00:00:00"), new BigDecimal("20"));
        assertEquals(JudgeResult.GRADED, r.getStatus());

        ArgumentCaptor<TItemSuperviseRecord> cap = ArgumentCaptor.forClass(TItemSuperviseRecord.class);
        verify(recordMapper).insert(cap.capture());
        TItemSuperviseRecord rec = cap.getValue();
        assertEquals(TItemSuperviseRecord.KIND_GRADE, rec.getRecordKind());
        assertEquals(3, rec.getGradeLevel());
        assertEquals("P-SYS-02", rec.getRuleCode());
        assertEquals(t("2025-10-01 00:00:00"), rec.getRuleEffStart(), "上架之日随评定一道钉死");
        assertEquals(TItemSuperviseRecord.ALERT_ACTIVE, rec.getAlertStatus());
        assertEquals(0, new BigDecimal("20").compareTo(rec.getOverdueRate()));
    }

    @Test
    void rate_falling_below_mid_relieves_old_alert_in_separate_record() {
        Date accept = t("2025-12-01 00:00:00");
        TItemAlertRule oldRule = rule("P-PAPER-01", t("2025-01-01 00:00:00"));
        TItemAlertRule newRule = rule("P-SYS-02", t("2025-10-01 00:00:00"));

        // 头回高段(3)起警：等级未落回中段以下，不查在警、不销
        when(alertRuleService.evaluate(any(Integer.class), any(BigDecimal.class), any(Date.class)))
                .thenReturn(RuleVerdict.of(oldRule, 3));
        TItemSuperviseRecord oldGrade = gradeRecord(7001L, "C2", accept, 3,
                "P-PAPER-01", t("2025-01-01 00:00:00"));

        JudgeResult first = svc.judge("C2", 2, accept, new BigDecimal("20"));
        assertEquals(JudgeResult.GRADED, first.getStatus());

        // 第二回落回中段(2)：先查在警候选→[oldGrade]，再查已销笔→[]
        when(alertRuleService.evaluate(any(Integer.class), any(BigDecimal.class), any(Date.class)))
                .thenReturn(RuleVerdict.of(newRule, 2));
        when(recordMapper.selectList(any()))
                .thenReturn(Collections.singletonList(oldGrade))
                .thenReturn(Collections.<TItemSuperviseRecord>emptyList());
        JudgeResult second = svc.judge("C2", 2, accept, new BigDecimal("10"));
        assertEquals(JudgeResult.GRADED, second.getStatus());

        ArgumentCaptor<TItemSuperviseRecord> cap = ArgumentCaptor.forClass(TItemSuperviseRecord.class);
        verify(recordMapper, times(3)).insert(cap.capture());
        List<TItemSuperviseRecord> all = cap.getAllValues();

        TItemSuperviseRecord firstRec = all.get(0);
        assertEquals(3, firstRec.getGradeLevel());

        TItemSuperviseRecord relief = all.get(1);
        TItemSuperviseRecord secondRec = all.get(2);

        // 销警笔抄旧笔的层级/代号/日子，指向旧笔，绝不与新笔合成一条
        assertEquals(TItemSuperviseRecord.KIND_RELIEVE, relief.getRecordKind());
        assertEquals(3, relief.getGradeLevel(), "销警笔录的是先前那一层");
        assertEquals("P-PAPER-01", relief.getRuleCode(), "销警笔留旧代号");
        assertEquals(t("2025-01-01 00:00:00"), relief.getRuleEffStart(), "销警笔留旧尺上架之日");
        assertEquals(Long.valueOf(7001L), relief.getLinkRecordId());
        assertEquals(TItemSuperviseRecord.ALERT_RELIEVED, relief.getAlertStatus());
        assertEquals(0, new BigDecimal("10").compareTo(relief.getOverdueRate()));

        // 现下评定笔另留自己的代号与日子
        assertEquals(TItemSuperviseRecord.KIND_GRADE, secondRec.getRecordKind());
        assertEquals(2, secondRec.getGradeLevel());
        assertEquals("P-SYS-02", secondRec.getRuleCode());
        assertEquals(t("2025-10-01 00:00:00"), secondRec.getRuleEffStart());
        assertNull(secondRec.getLinkRecordId());
    }

    @Test
    void moving_within_high_band_does_not_relieve() {
        TItemAlertRule rule = rule("P-SYS-02", t("2025-10-01 00:00:00"));
        when(alertRuleService.evaluate(any(Integer.class), any(BigDecimal.class), any(Date.class)))
                .thenReturn(RuleVerdict.of(rule, 4))
                .thenReturn(RuleVerdict.of(rule, 3));
        when(recordMapper.selectList(any())).thenReturn(Collections.<TItemSuperviseRecord>emptyList());

        svc.judge("C3", 2, t("2025-12-01 00:00:00"), new BigDecimal("40"));
        JudgeResult again = svc.judge("C3", 2, t("2025-12-01 00:00:00"), new BigDecimal("20"));
        assertEquals(JudgeResult.GRADED, again.getStatus());
        // 两回都在高段以内：只各落评定笔，不查在警、不销警
        verify(recordMapper, times(2)).insert(any());
        for (TItemSuperviseRecord r : again.getRecords()) {
            assertEquals(TItemSuperviseRecord.KIND_GRADE, r.getRecordKind());
        }
    }

    @Test
    void active_alerts_exclude_already_relieved() {
        TItemSuperviseRecord openOne = gradeRecord(8001L, "A", t("2025-12-01 00:00:00"), 3,
                "R1", t("2025-01-01 00:00:00"));
        TItemSuperviseRecord relievedOne = gradeRecord(8002L, "B", t("2025-12-01 00:00:00"), 3,
                "R1", t("2025-01-01 00:00:00"));
        TItemSuperviseRecord relief = new TItemSuperviseRecord();
        relief.setLinkRecordId(8002L);

        when(recordMapper.selectList(any()))
                .thenReturn(new ArrayList<TItemSuperviseRecord>(Arrays.asList(openOne, relievedOne)))
                .thenReturn(Collections.singletonList(relief));

        List<TItemSuperviseRecord> active = svc.listActiveAlerts();
        assertEquals(1, active.size());
        assertEquals(Long.valueOf(8001L), active.get(0).getId(), "被销警笔指向的旧警不再出现");
    }

    // ---------- helpers ----------

    private TItemAlertRule rule(String code, Date start) {
        TItemAlertRule r = new TItemAlertRule();
        r.setRuleCode(code);
        r.setEffStart(start);
        r.setStatus(0);
        return r;
    }

    private TItemSuperviseRecord gradeRecord(Long id, String caseNo, Date accept, int grade,
                                             String code, Date effStart) {
        TItemSuperviseRecord r = new TItemSuperviseRecord();
        r.setId(id);
        r.setCaseNo(caseNo);
        r.setAffairType(2);
        r.setAcceptAt(accept);
        r.setRecordKind(TItemSuperviseRecord.KIND_GRADE);
        r.setGradeLevel(grade);
        r.setRuleCode(code);
        r.setRuleEffStart(effStart);
        r.setAlertStatus(TItemSuperviseRecord.ALERT_ACTIVE);
        r.setDelFlag(0);
        return r;
    }

    private static void inject(Object target, String field, Object value) throws Exception {
        Field mf = target.getClass().getDeclaredField(field);
        mf.setAccessible(true);
        mf.set(target, value);
    }

    private Date t(String s) {
        try {
            return f.parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
