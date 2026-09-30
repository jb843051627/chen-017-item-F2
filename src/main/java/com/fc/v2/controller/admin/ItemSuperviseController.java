package com.fc.v2.controller.admin;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.custom.JudgeResult;
import com.fc.v2.service.ITItemSuperviseRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 办件监察 Controller。
 * <p>窗口自核那一屏与局里监察那一屏，判定都走 {@link #judge} 这一个口子，背后是服务层同一个求值方法：
 * 界取哪一册、并排几条听谁的、分界数空着怎么落、钉进台账的代号与启用之日，全由服务现算现给，
 * 页面自己请一条线再贴结论的做法一概不认。</p>
 *
 * @author fuce
 * @date 2026-09-30
 */
@Api(value = "办件监察")
@Controller
@RequestMapping("/ItemSuperviseController")
public class ItemSuperviseController extends BaseController {

    private final String prefix = "admin/itemSupervise";

    @Autowired
    private ITItemSuperviseRecordService itemSuperviseRecordService;

    /** 局里监察屏入口（窗口自核屏也是同一页面、同一判定口子） */
    @ApiOperation(value = "监察判定屏", notes = "窗口自核与局监察共用这一屏同一套口径")
    @GetMapping("/view")
    @RequiresPermissions("item:itemSupervise:view")
    public String view(ModelMap model) {
        return prefix + "/judge";
    }

    @Log(title = "办件逾期判定落账", action = "judge")
    @ApiOperation(value = "单办件判定", notes = "凭受理日取在档线，一回给出线与等级并钉台账；越界退回、无尺记无从依凭")
    @PostMapping("/judge")
    @RequiresPermissions("item:itemSupervise:judge")
    @ResponseBody
    public AjaxResult judge(@RequestParam String caseNo,
                            @RequestParam Integer affairType,
                            @RequestParam String acceptAt,
                            @RequestParam java.math.BigDecimal overdueRate) {
        Date accept;
        try {
            accept = parseDay(acceptAt);
        } catch (ParseException e) {
            return AjaxResult.error("受理之日格式应是 yyyy-MM-dd");
        }
        JudgeResult result = itemSuperviseRecordService.judge(caseNo, affairType, accept, overdueRate);
        if (result.getStatus() == JudgeResult.REJECTED) {
            // 屏上不摆结论，只把话退回监察员重录
            return AjaxResult.error(result.getMessage());
        }
        // 线与等级、代号、上架之日、落账各笔都以服务这一回给回的为准
        return AjaxResult.success(200, result.getMessage(), result);
    }

    @Log(title = "在警办件查询", action = "alerts")
    @ApiOperation(value = "当下在警清单", notes = "已被销警笔勾掉的不再出现")
    @GetMapping("/alerts")
    @RequiresPermissions("item:itemSupervise:list")
    @ResponseBody
    public ResultTable alerts() {
        startPage();
        java.util.List<?> list = itemSuperviseRecordService.listActiveAlerts();
        com.github.pagehelper.PageInfo<?> page = new com.github.pagehelper.PageInfo<Object>(list);
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "办件台账倒查", action = "history")
    @ApiOperation(value = "单件台账", notes = "评定/销警/无从依凭各笔按落账之时排列，钉过不改口")
    @GetMapping("/history")
    @RequiresPermissions("item:itemSupervise:list")
    @ResponseBody
    public ResultTable history(@RequestParam String caseNo) {
        startPage();
        java.util.List<?> list = itemSuperviseRecordService.listByCaseNo(caseNo);
        com.github.pagehelper.PageInfo<?> page = new com.github.pagehelper.PageInfo<Object>(list);
        return pageTable(page.getList(), page.getTotal());
    }

    private static Date parseDay(String s) throws ParseException {
        String v = s.trim();
        String pattern = v.length() > 10 ? "yyyy-MM-dd HH:mm:ss" : "yyyy-MM-dd";
        SimpleDateFormat f = new SimpleDateFormat(pattern);
        f.setLenient(false);
        return f.parse(v);
    }
}
