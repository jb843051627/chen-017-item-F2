package com.fc.v2.controller.admin;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TItemAlertRule;
import com.fc.v2.service.ITItemAlertRuleService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 办件逾期风险判定线（全市一册）Controller。
 * 只管立尺、收档、翻册倒查；取哪条尺、落哪一档不在此处算，全在服务求值门。
 *
 * @author fuce
 * @date 2026-09-30
 */
@Api(value = "办件逾期风险判定线")
@Controller
@RequestMapping("/ItemAlertRuleController")
public class ItemAlertRuleController extends BaseController {

    private final String prefix = "admin/itemAlertRule";

    @Autowired
    private ITItemAlertRuleService itemAlertRuleService;

    @ApiOperation(value = "分页跳转", notes = "判定线册页面")
    @GetMapping("/view")
    @RequiresPermissions("item:itemAlertRule:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "判定线册查询", action = "list")
    @ApiOperation(value = "翻册查询", notes = "在档与收档历次行次都在册，可按事由/旗过滤")
    @GetMapping("/list")
    @RequiresPermissions("item:itemAlertRule:list")
    @ResponseBody
    public ResultTable list(@RequestParam(required = false) Integer affairType,
                            @RequestParam(required = false) Integer status) {
        startPage();
        java.util.List<TItemAlertRule> list = itemAlertRuleService.listLedger(affairType, status);
        com.github.pagehelper.PageInfo<TItemAlertRule> page =
                new com.github.pagehelper.PageInfo<TItemAlertRule>(list);
        return pageTable(page.getList(), page.getTotal());
    }

    @GetMapping("/add")
    @RequiresPermissions("item:itemAlertRule:add")
    public String add() {
        return prefix + "/add";
    }

    @Log(title = "判定线立新尺", action = "add")
    @ApiOperation(value = "立新尺入册", notes = "代号全局独占；换尺不删旧，新尺另立一行")
    @PostMapping("/add")
    @RequiresPermissions("item:itemAlertRule:add")
    @ResponseBody
    public AjaxResult add(TItemAlertRule record) {
        try {
            itemAlertRuleService.register(record);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return AjaxResult.error(e.getMessage());
        }
        return AjaxResult.success("新尺已入册在架");
    }

    @Log(title = "判定线收档", action = "archive")
    @ApiOperation(value = "收档", notes = "放倒旗、记收档之日；行次留册只许倒查，不进当下判定")
    @PostMapping("/archive")
    @RequiresPermissions("item:itemAlertRule:edit")
    @ResponseBody
    public AjaxResult archive(@RequestParam Long id,
                              @RequestParam(required = false) String effEnd) {
        try {
            Date end = effEnd == null || effEnd.trim().isEmpty() ? null : parseDay(effEnd.trim());
            int rows = itemAlertRuleService.archive(id, end);
            return rows > 0 ? AjaxResult.success("已收档，行次留册备查")
                    : AjaxResult.error("翻无此行，未收档");
        } catch (IllegalArgumentException e) {
            return AjaxResult.error(e.getMessage());
        } catch (java.text.ParseException e) {
            return AjaxResult.error("收档之日格式应是 yyyy-MM-dd");
        }
    }

    @ApiOperation(value = "倒查来历", notes = "收了档的旧尺经此看来历")
    @GetMapping("/detail/{id}")
    @RequiresPermissions("item:itemAlertRule:list")
    @ResponseBody
    public AjaxResult detail(@PathVariable Long id) {
        TItemAlertRule rule = itemAlertRuleService.selectTItemAlertRuleById(id);
        return rule == null ? AjaxResult.error("册中无此行次") : AjaxResult.successData(200, rule);
    }

    /** 兼容 yyyy-MM-dd 与 yyyy-MM-dd HH:mm:ss 两种录法 */
    private static Date parseDay(String s) throws java.text.ParseException {
        String pattern = s.length() > 10 ? "yyyy-MM-dd HH:mm:ss" : "yyyy-MM-dd";
        SimpleDateFormat f = new SimpleDateFormat(pattern);
        f.setLenient(false);
        return f.parse(s);
    }
}
