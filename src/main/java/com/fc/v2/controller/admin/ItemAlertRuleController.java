package com.fc.v2.controller.admin;

import java.math.BigDecimal;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.custom.RuleVerdict;
import com.fc.v2.service.ITItemAlertRuleService;
import com.fc.v2.service.ITItemSuperviseRecordService;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/**
 * 办件逾期风险判定线 Controller。
 *
 * 窗口自核那一屏与局里监察那一屏，分界日子、并排几条听谁、空刻度怎么落，
 * 都由服务层那一个求值方法现算现给。屏上不许先摆结论等人录，也不许页面自取一条线
 * 再自贴结论；代号与上架之日以回证给回的为准。
 *
 * @author fuce
 * @date 2026-09-30
 */
@Api(value = "办件逾期风险判定线")
@Controller
@RequestMapping("/itemAlertRule")
public class ItemAlertRuleController extends BaseController {

    @Autowired
    private ITItemAlertRuleService itemAlertRuleService;

    @Autowired
    private ITItemSuperviseRecordService itemSuperviseRecordService;

    /**
     * 窗口自核屏：受理日 + 事由 + 逾期率 → 一回判定回证。不落记录，只现算现看；
     * 与监察屏同走 {@link ITItemAlertRuleService#evaluate}，日子界限是同一种讲法。
     */
    @Log(title = "判定线窗口自核", action = "eval")
    @ApiOperation(value = "窗口自核求值", notes = "凭受理日取尺并定层，不落监察记录")
    @PostMapping("/eval")
    @ResponseBody
    public AjaxResult eval(@RequestParam("overdueRate") BigDecimal overdueRate,
                           @RequestParam("acceptAt") Date acceptAt,
                           @RequestParam("bizType") String bizType) {
        RuleVerdict verdict = itemAlertRuleService.evaluate(overdueRate, acceptAt, bizType);
        // 不替页面下结论：三态（定档/无从依凭/退回重录）原样给回，由页面按回证展看。
        return AjaxResult.successData(200, verdict);
    }

    /**
     * 局里监察屏：对一桩已受理办件走完整趟——取尺定层并钉进监察记录，降级随带销警。
     */
    @Log(title = "办件监察定层", action = "judge")
    @ApiOperation(value = "监察定层并钉档", notes = "对办件走一趟判定并追加监察记录")
    @PostMapping("/judge/{caseId}")
    @ResponseBody
    public AjaxResult judge(@PathVariable("caseId") Long caseId) {
        RuleVerdict verdict = itemSuperviseRecordService.judgeCase(caseId);
        if (verdict.isRejected()) {
            return AjaxResult.error("逾期率不合法或办件缺失，已退回，请重录后再判");
        }
        if (verdict.isNoRule()) {
            // 无从依凭也照实回，不硬给结论、不拿邻近日子的尺凑。
            return AjaxResult.successData(202, verdict);
        }
        return AjaxResult.successData(200, verdict);
    }

    /** 查一桩办件历次监察记录（含已销警行，新的在前） */
    @Log(title = "办件监察记录", action = "records")
    @ApiOperation(value = "监察记录来历", notes = "按办件单号倒查历次记录")
    @GetMapping("/records/{caseNo}")
    @ResponseBody
    public AjaxResult records(@PathVariable("caseNo") String caseNo) {
        return AjaxResult.successData(200, itemSuperviseRecordService.listByCase(caseNo));
    }
}
