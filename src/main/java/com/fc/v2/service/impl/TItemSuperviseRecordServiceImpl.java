package com.fc.v2.service.impl;

import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TItemCaseMapper;
import com.fc.v2.mapper.auto.TItemSuperviseRecordMapper;
import com.fc.v2.model.auto.TItemCase;
import com.fc.v2.model.auto.TItemSuperviseRecord;
import com.fc.v2.model.custom.RuleVerdict;
import com.fc.v2.service.ITItemAlertRuleService;
import com.fc.v2.service.ITItemSuperviseRecordService;

/**
 * 办件监察记录 Service实现：只追加、钉过不改口，降级只销警。
 *
 * 取尺与定层仍由 {@link ITItemAlertRuleService#evaluate} 一回给回，本类只负责落记录，
 * 不另算档位、不自取代号。承办部门底册（名录）在本类里一个字都不碰——撤并与否只影响
 * 名录那头（核对那边走），本趟照样出结论。
 *
 * @author fuce
 * @date 2026-09-30
 */
@Service
public class TItemSuperviseRecordServiceImpl implements ITItemSuperviseRecordService {

    @javax.annotation.Resource
    private TItemCaseMapper itemCaseMapper;

    @javax.annotation.Resource
    private TItemSuperviseRecordMapper superviseRecordMapper;

    @javax.annotation.Resource
    private ITItemAlertRuleService itemAlertRuleService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RuleVerdict judgeCase(Long caseId) {
        if (caseId == null) {
            return RuleVerdict.rejected(null, null, null);
        }
        TItemCase caze = this.itemCaseMapper.selectOne(new QueryWrapper<TItemCase>()
                .eq("id", caseId).eq("del_flag", 0));
        if (caze == null) {
            return RuleVerdict.rejected(null, null, null);
        }

        // 取尺（凭受理日）与定层同一回判定，中间不夹第二回计算。
        RuleVerdict verdict = this.itemAlertRuleService.evaluate(
                caze.getOverdueRate(), caze.getAcceptAt(), caze.getBizType());

        Date now = new Date();

        if (verdict.isRejected()) {
            // 数不在 0..100 / 空着：不进判定、不落记录，退回监察员重录。
            return verdict;
        }

        // OK 与 NO_RULE 都追加一笔；NO_RULE 为 level=0、代号留空的"无从依凭"，不硬给结论。
        TItemSuperviseRecord rec = new TItemSuperviseRecord();
        rec.setCaseNo(caze.getCaseNo());
        rec.setLevel(verdict.getLevel());
        rec.setRuleCode(verdict.isOk() ? verdict.getRuleCode() : null);
        rec.setRuleEffStart(verdict.isOk() ? verdict.getRuleEffStart() : null);
        rec.setOverdueRate(caze.getOverdueRate());
        rec.setJudgedAt(now);
        rec.setAlertFlag(verdict.isOk() && verdict.getLevel() >= ALERT_MIN_LEVEL ? 1 : 0);
        rec.setDelFlag(0);
        this.superviseRecordMapper.insert(rec);

        if (verdict.isOk() && verdict.getLevel() < ALERT_MIN_LEVEL) {
            // 落回中段以下：先前那一层的警报随即销掉。只置 alert_flag=0，旧行保留——
            // 销掉的与现下的两笔各留各的代号与日子，合成一条就算错。
            this.superviseRecordMapper.update(null, new UpdateWrapper<TItemSuperviseRecord>()
                    .eq("case_no", caze.getCaseNo())
                    .eq("alert_flag", 1)
                    .eq("del_flag", 0)
                    .set("alert_flag", 0)
                    .set("update_time", now));
        }
        // 名录（t_item_dept_arch）即便已撤并也不动，由核对那边走。

        return verdict;
    }

    @Override
    public List<TItemSuperviseRecord> listByCase(String caseNo) {
        if (caseNo == null || caseNo.trim().isEmpty()) {
            return new java.util.ArrayList<TItemSuperviseRecord>();
        }
        return this.superviseRecordMapper.selectList(new QueryWrapper<TItemSuperviseRecord>()
                .eq("case_no", caseNo).eq("del_flag", 0).orderByDesc("judged_at").orderByDesc("id"));
    }
}
