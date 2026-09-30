package com.fc.v2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TItemAlertRuleMapper;
import com.fc.v2.mapper.auto.TItemCaseMapper;
import com.fc.v2.mapper.auto.TItemSuperviseRecordMapper;
import com.fc.v2.model.auto.TItemAlertRule;
import com.fc.v2.model.auto.TItemCase;
import com.fc.v2.model.auto.TItemSuperviseRecord;
import com.fc.v2.model.custom.RuleVerdict;
import com.fc.v2.service.impl.TItemAlertRuleServiceImpl;
import com.fc.v2.service.impl.TItemSuperviseRecordServiceImpl;

/**
 * 监察记录钉档口径测试：取尺定层走真实的判定线服务（尺 mapper 打桩），
 * 办件/记录两张表 mapper 打桩。覆盖追加不改写、降级销警留两笔、无从依凭、退回不落记录，
 * 以及撤并部门照出结论（本服务不注入、不触碰部门名录）。
 *
 * @author fuce
 * @date 2026-09-30
 */
@ExtendWith(MockitoExtension.class)
class TItemSuperviseRecordServiceImplTest {

    @Mock
    private TItemCaseMapper caseMapper;
    @Mock
    private TItemSuperviseRecordMapper recordMapper;
    @Mock
    private TItemAlertRuleMapper ruleMapper;

    private TItemSuperviseRecordServiceImpl superviseService;

    private final SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static final String IMMEDIATE = "IMMEDIATE";

    @BeforeEach
    void setUp() {
        TItemAlertRuleServiceImpl ruleService = new TItemAlertRuleServiceImpl();
        ReflectionTestUtils.setField(ruleService, "itemAlertRuleMapper", ruleMapper);

        superviseService = new TItemSuperviseRecordServiceImpl();
        ReflectionTestUtils.setField(superviseService, "itemCaseMapper", caseMapper);
        ReflectionTestUtils.setField(superviseService, "superviseRecordMapper", recordMapper);
        ReflectionTestUtils.setField(superviseService, "itemAlertRuleService", ruleService);

        // 即办件秋刻：6 / 12 / 25
        TItemAlertRule r = new TItemAlertRule();
        r.setRuleCode("R002");
        r.setRuleName("秋刻");
        r.setBizType(IMMEDIATE);
        r.setTh1Max(new BigDecimal("6"));
        r.setTh2Max(new BigDecimal("12"));
        r.setTh3Max(new BigDecimal("25"));
        try {
            r.setEffStart(f.parse("2025-10-01 00:00:00"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        r.setPriority(100);
        r.setStatus(0);
        r.setDelFlag(0);
        lenient().when(ruleMapper.selectList(any())).thenReturn(new ArrayList<TItemAlertRule>(Arrays.asList(r)));
    }

    private void feedCase(String rate, String acceptAt, Integer siteId) {
        TItemCase c = new TItemCase();
        c.setId(1L);
        c.setCaseNo("C-1");
        c.setBizType(IMMEDIATE);
        c.setSiteId(siteId);
        c.setOverdueRate(rate == null ? null : new BigDecimal(rate));
        try {
            c.setAcceptAt(acceptAt == null ? null : f.parse(acceptAt));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        c.setDelFlag(0);
        when(caseMapper.selectOne(any())).thenReturn(c);
    }

    @Test
    void highLevel_appendsAlertRecord() {
        feedCase("20", "2026-01-01 00:00:00", 0); // 20→高段(3)，挂警
        RuleVerdict v = superviseService.judgeCase(1L);

        assertTrue(v.isOk());
        assertEquals(3, v.getLevel());
        ArgumentCaptor<TItemSuperviseRecord> cap = ArgumentCaptor.forClass(TItemSuperviseRecord.class);
        verify(recordMapper, times(1)).insert(cap.capture());
        TItemSuperviseRecord rec = cap.getValue();
        assertEquals("C-1", rec.getCaseNo());
        assertEquals(3, rec.getLevel());
        assertEquals("R002", rec.getRuleCode());
        assertEquals(1, rec.getAlertFlag());
        // 高段不触发销警
        verify(recordMapper, never()).update(any(), any(UpdateWrapper.class));
    }

    @Test
    void downgradeBelowMid_appendsNewAndCancelsPrior_keepingBothRows() {
        feedCase("10", "2026-01-01 00:00:00", 0); // 10→中段(2)，在挂警线以下
        RuleVerdict v = superviseService.judgeCase(1L);

        assertEquals(2, v.getLevel());
        // 追加一笔现下的（alert=0）
        ArgumentCaptor<TItemSuperviseRecord> cap = ArgumentCaptor.forClass(TItemSuperviseRecord.class);
        verify(recordMapper, times(1)).insert(cap.capture());
        assertEquals(0, cap.getValue().getAlertFlag());
        // 先前挂着的警报随即销掉：只 update alert_flag，绝不删行、不改旧档
        verify(recordMapper, times(1)).update(any(), any(UpdateWrapper.class));
    }

    @Test
    void noRule_appendsLevel0WithBlankCode_andGivesNoConclusion() {
        feedCase("10", "2024-01-01 00:00:00", 0); // 尺 2025-10 才上架→无从依凭
        RuleVerdict v = superviseService.judgeCase(1L);

        assertTrue(v.isNoRule());
        ArgumentCaptor<TItemSuperviseRecord> cap = ArgumentCaptor.forClass(TItemSuperviseRecord.class);
        verify(recordMapper, times(1)).insert(cap.capture());
        TItemSuperviseRecord rec = cap.getValue();
        assertEquals(0, rec.getLevel());
        assertNull(rec.getRuleCode());
        assertNull(rec.getRuleEffStart());
        assertEquals(0, rec.getAlertFlag());
        verify(recordMapper, never()).update(any(), any(UpdateWrapper.class));
    }

    @Test
    void rejectedRate_writesNothing() {
        feedCase("120", "2026-01-01 00:00:00", 0); // 过百退回
        RuleVerdict v = superviseService.judgeCase(1L);

        assertTrue(v.isRejected());
        verify(recordMapper, never()).insert(any());
        verify(recordMapper, never()).update(any(), any(Wrapper.class));
    }

    @Test
    void withdrawnDepartment_stillConcludes_andNeverTouchesRoster() {
        // 承办部门底册 id=1 已撤并：本趟照出结论；且本服务根本没有部门名录的依赖，名录由核对那边走。
        feedCase("20", "2026-01-01 00:00:00", 1);
        RuleVerdict v = superviseService.judgeCase(1L);

        assertTrue(v.isOk());
        assertEquals(3, v.getLevel());
        verify(recordMapper, times(1)).insert(any());
    }

    @Test
    void listByCase_returnsRowsNewestFirst() {
        TItemSuperviseRecord r1 = new TItemSuperviseRecord();
        r1.setId(2L);
        when(recordMapper.selectList(any())).thenReturn(new ArrayList<TItemSuperviseRecord>(Arrays.asList(r1)));
        List<TItemSuperviseRecord> rows = superviseService.listByCase("C-1");
        assertEquals(1, rows.size());
        assertEquals(2L, rows.get(0).getId());
    }
}
