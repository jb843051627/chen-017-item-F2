package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TItemSuperviseRecordMapper;
import com.fc.v2.model.auto.TItemAlertRule;
import com.fc.v2.model.auto.TItemSuperviseRecord;
import com.fc.v2.model.custom.JudgeResult;
import com.fc.v2.model.custom.RuleVerdict;
import com.fc.v2.service.ITItemAlertRuleService;
import com.fc.v2.service.ITItemSuperviseRecordService;

/**
 * 办件监察台账 Service实现（只追加、不改口）。
 *
 * @author fuce
 * @date 2026-09-30
 */
@Service
public class TItemSuperviseRecordServiceImpl implements ITItemSuperviseRecordService {

    private static final int NORMAL = 0;

    /** 高段起警：等级到高段(3)及以上才在警；落回中段以下即销 */
    private static final int ALERT_GRADE_LINE = 3;

    @javax.annotation.Resource
    private TItemSuperviseRecordMapper superviseRecordMapper;

    /** 取尺与定层只向判定线服务求这一回，本类绝不再算第二回 */
    @javax.annotation.Resource
    private ITItemAlertRuleService itemAlertRuleService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JudgeResult judge(String caseNo, Integer affairType, Date acceptAt, BigDecimal overdueRate) {
        if (caseNo == null || caseNo.trim().isEmpty()) {
            return JudgeResult.rejected("办件编号空着，不进判定，退回重录");
        }
        // 唯一求值门：取哪条尺、落在哪一档，连同代号与上架之日都由它这一回给回
        RuleVerdict verdict = itemAlertRuleService.evaluate(affairType, overdueRate, acceptAt);
        if (verdict.isRejected()) {
            // 负数、120、空着之类：不进判定，台账上一个字也不落，退回监察员重录
            return JudgeResult.rejected("逾期率只收 0..100，事由与受理日也不得空，退回重录");
        }

        Date now = new Date();
        if (verdict.isNoRule()) {
            // 受理当日翻不出在档的尺：写明无从依凭落一笔，不拿邻近日子的尺凑、不硬给结论
            TItemSuperviseRecord note = baseRecord(caseNo.trim(), affairType, acceptAt, overdueRate, now);
            note.setRecordKind(TItemSuperviseRecord.KIND_NO_RULE);
            note.setRemark("受理当日无在档可用判定线，本趟无从依凭");
            superviseRecordMapper.insert(note);
            return JudgeResult.noRule(note);
        }

        TItemAlertRule rule = verdict.getRule();
        int grade = verdict.getGrade();
        List<TItemSuperviseRecord> written = new ArrayList<TItemSuperviseRecord>();

        // 回落销警：只在落回中段以下(高段那一层之下)时触发，把本件尚在警上的旧警报各销一笔；
        // 仍在高段以内的上下浮动不销。旧笔一个字段不改，销警另成一笔。
        if (grade < ALERT_GRADE_LINE) {
            List<TItemSuperviseRecord> openAlerts = listOpenAlertsOfCase(caseNo.trim());
            for (TItemSuperviseRecord old : openAlerts) {
                written.add(relieve(old, overdueRate, now));
            }
        }

        // 评定笔：等级连同所依代号、上架之日钉死；钉过不再改口，故只追加新笔
        TItemSuperviseRecord gradeRecord = baseRecord(caseNo.trim(), affairType, acceptAt, overdueRate, now);
        gradeRecord.setRecordKind(TItemSuperviseRecord.KIND_GRADE);
        gradeRecord.setGradeLevel(grade);
        gradeRecord.setRuleCode(rule.getRuleCode());
        gradeRecord.setRuleEffStart(rule.getEffStart());
        gradeRecord.setAlertStatus(grade >= ALERT_GRADE_LINE
                ? TItemSuperviseRecord.ALERT_ACTIVE : TItemSuperviseRecord.ALERT_RELIEVED);
        superviseRecordMapper.insert(gradeRecord);
        written.add(gradeRecord);

        // 承办部门底册即便已撤并，本趟也只出结论：此处只碰监察台账，名录那头由核对那边走。
        return JudgeResult.graded(written, written.size() > 1
                ? "逾期率回落，原警报已另笔销掉，本回评定另行钉账" : "已按所依判定线评定并钉账");
    }

    @Override
    public List<TItemSuperviseRecord> listByCaseNo(String caseNo) {
        if (caseNo == null || caseNo.trim().isEmpty()) {
            return new ArrayList<TItemSuperviseRecord>();
        }
        return superviseRecordMapper.selectList(new QueryWrapper<TItemSuperviseRecord>()
                .eq("del_flag", NORMAL)
                .eq("case_no", caseNo.trim())
                .orderBy(true, true, "judged_at")
                .orderBy(true, true, "id"));
    }

    @Override
    public List<TItemSuperviseRecord> listActiveAlerts() {
        List<TItemSuperviseRecord> candidates = superviseRecordMapper.selectList(
                new QueryWrapper<TItemSuperviseRecord>()
                        .eq("del_flag", NORMAL)
                        .eq("record_kind", TItemSuperviseRecord.KIND_GRADE)
                        .eq("alert_status", TItemSuperviseRecord.ALERT_ACTIVE)
                        .ge("grade_level", ALERT_GRADE_LINE));
        List<TItemSuperviseRecord> open = filterRelieved(candidates);
        // 同一件连日钉在高段会有多笔未销评定；当下清单只露该件最新那一笔（历史笔仍在台账可倒查）
        java.util.Map<String, TItemSuperviseRecord> latestByCase =
                new java.util.LinkedHashMap<String, TItemSuperviseRecord>();
        for (TItemSuperviseRecord r : open) {
            TItemSuperviseRecord cur = latestByCase.get(r.getCaseNo());
            if (cur == null || laterThan(r, cur)) {
                latestByCase.put(r.getCaseNo(), r);
            }
        }
        return new ArrayList<TItemSuperviseRecord>(latestByCase.values());
    }

    private boolean laterThan(TItemSuperviseRecord a, TItemSuperviseRecord b) {
        Date ta = a.getJudgedAt() == null ? new Date(0) : a.getJudgedAt();
        Date tb = b.getJudgedAt() == null ? new Date(0) : b.getJudgedAt();
        if (!ta.equals(tb)) {
            return ta.after(tb);
        }
        long ia = a.getId() == null ? 0L : a.getId();
        long ib = b.getId() == null ? 0L : b.getId();
        return ia > ib;
    }

    @Override
    public TItemSuperviseRecord selectById(Long id) {
        return id == null ? null : superviseRecordMapper.selectById(id);
    }

    /**
     * 某件尚在警上的评定笔：候选在警笔里，剔除已被销警笔指向的。
     * 旧笔一个字段都不改——销与留全靠追加的销警笔说话。
     */
    private List<TItemSuperviseRecord> listOpenAlertsOfCase(String caseNo) {
        List<TItemSuperviseRecord> candidates = superviseRecordMapper.selectList(
                new QueryWrapper<TItemSuperviseRecord>()
                        .eq("del_flag", NORMAL)
                        .eq("case_no", caseNo)
                        .eq("record_kind", TItemSuperviseRecord.KIND_GRADE)
                        .eq("alert_status", TItemSuperviseRecord.ALERT_ACTIVE)
                        .ge("grade_level", ALERT_GRADE_LINE));
        return filterRelieved(candidates);
    }

    private List<TItemSuperviseRecord> filterRelieved(List<TItemSuperviseRecord> candidates) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        List<Long> ids = new ArrayList<Long>();
        for (TItemSuperviseRecord r : candidates) {
            ids.add(r.getId());
        }
        List<TItemSuperviseRecord> reliefs = superviseRecordMapper.selectList(
                new QueryWrapper<TItemSuperviseRecord>()
                        .eq("del_flag", NORMAL)
                        .eq("record_kind", TItemSuperviseRecord.KIND_RELIEVE)
                        .in("link_record_id", ids));
        Set<Long> relievedIds = new HashSet<Long>();
        for (TItemSuperviseRecord r : reliefs) {
            relievedIds.add(r.getLinkRecordId());
        }
        List<TItemSuperviseRecord> open = new ArrayList<TItemSuperviseRecord>();
        for (TItemSuperviseRecord r : candidates) {
            if (!relievedIds.contains(r.getId())) {
                open.add(r);
            }
        }
        return open;
    }

    /**
     * 销警笔：抄的是<b>旧评定笔</b>那一层的等级、代号与日子，指向旧笔；
     * 与现下评定笔各成一条，合成一条就算错。
     */
    private TItemSuperviseRecord relieve(TItemSuperviseRecord old, BigDecimal currentRate, Date now) {
        TItemSuperviseRecord rel = baseRecord(old.getCaseNo(), old.getAffairType(), old.getAcceptAt(),
                currentRate, now);
        rel.setRecordKind(TItemSuperviseRecord.KIND_RELIEVE);
        rel.setGradeLevel(old.getGradeLevel());
        rel.setRuleCode(old.getRuleCode());
        rel.setRuleEffStart(old.getRuleEffStart());
        rel.setAlertStatus(TItemSuperviseRecord.ALERT_RELIEVED);
        rel.setLinkRecordId(old.getId());
        rel.setRemark("逾期率落回中段以下，先前那一层警报随即销掉");
        superviseRecordMapper.insert(rel);
        return rel;
    }

    private TItemSuperviseRecord baseRecord(String caseNo, Integer affairType, Date acceptAt,
                                            BigDecimal overdueRate, Date now) {
        TItemSuperviseRecord r = new TItemSuperviseRecord();
        r.setCaseNo(caseNo);
        r.setAffairType(affairType);
        r.setAcceptAt(acceptAt);
        r.setOverdueRate(overdueRate);
        r.setJudgedAt(now);
        r.setDelFlag(NORMAL);
        return r;
    }
}
