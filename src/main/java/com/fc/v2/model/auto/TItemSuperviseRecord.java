package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 办件监察台账对象 t_item_supervise_record
 * <p>只追加、不改口：评定成笔记一笔，销警另成一笔，两笔各留各的代号与日子。</p>
 *
 * @author fuce
 * @date 2026-09-30
 */
@TableName("t_item_supervise_record")
@ApiModel(value = "TItemSuperviseRecord", description = "办件监察台账（只追加）")
public class TItemSuperviseRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 本笔性质 1评定 2销警 9无从依凭 */
    public static final int KIND_GRADE = 1;
    public static final int KIND_RELIEVE = 2;
    public static final int KIND_NO_RULE = 9;

    /** 警报情形 0在警 1已销 */
    public static final int ALERT_ACTIVE = 0;
    public static final int ALERT_RELIEVED = 1;

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 办件编号 */
    @TableField("case_no")
    @ApiModelProperty(value = "办件编号")
    private String caseNo;

    /** 事由 1即办件 2承诺件 3转报件 */
    @TableField("affair_type")
    @ApiModelProperty(value = "事由 1即办件 2承诺件 3转报件")
    private Integer affairType;

    /** 办件受理那一天（取尺凭它，不凭开屏今日） */
    @TableField("accept_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "办件受理那一天")
    private Date acceptAt;

    /** 本回判定算到今日的逾期率(0..100) */
    @TableField("overdue_rate")
    @ApiModelProperty(value = "本回判定的逾期率(0..100)")
    private BigDecimal overdueRate;

    /** 本笔性质 1评定 2销警 9无从依凭 */
    @TableField("record_kind")
    @ApiModelProperty(value = "本笔性质 1评定 2销警 9无从依凭")
    private Integer recordKind;

    /** 评出的等级 1低段 2中段 3高段 4超高段；无从依凭为空 */
    @TableField("grade_level")
    @ApiModelProperty(value = "评出的等级 1低段 2中段 3高段 4超高段")
    private Integer gradeLevel;

    /** 所依判定线代号（钉死，服务层给回） */
    @TableField("rule_code")
    @ApiModelProperty(value = "所依判定线代号")
    private String ruleCode;

    /** 所依那条尺的上架之日（钉死，服务层给回） */
    @TableField("rule_eff_start")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "所依那条尺的上架之日")
    private Date ruleEffStart;

    /** 警报情形 0在警 1已销 */
    @TableField("alert_status")
    @ApiModelProperty(value = "警报情形 0在警 1已销")
    private Integer alertStatus;

    /** 关联笔：销警笔指向被销的原评定笔 */
    @TableField("link_record_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "销警笔指向被销的原评定笔")
    private Long linkRecordId;

    /** 本回判定落账之时 */
    @TableField("judged_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "本回判定落账之时")
    private Date judgedAt;

    /** 删除标记（台账只追加，正常不用） */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记")
    private Integer delFlag;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCaseNo() {
        return caseNo;
    }

    public void setCaseNo(String caseNo) {
        this.caseNo = caseNo;
    }

    public Integer getAffairType() {
        return affairType;
    }

    public void setAffairType(Integer affairType) {
        this.affairType = affairType;
    }

    public Date getAcceptAt() {
        return acceptAt;
    }

    public void setAcceptAt(Date acceptAt) {
        this.acceptAt = acceptAt;
    }

    public BigDecimal getOverdueRate() {
        return overdueRate;
    }

    public void setOverdueRate(BigDecimal overdueRate) {
        this.overdueRate = overdueRate;
    }

    public Integer getRecordKind() {
        return recordKind;
    }

    public void setRecordKind(Integer recordKind) {
        this.recordKind = recordKind;
    }

    public Integer getGradeLevel() {
        return gradeLevel;
    }

    public void setGradeLevel(Integer gradeLevel) {
        this.gradeLevel = gradeLevel;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public Date getRuleEffStart() {
        return ruleEffStart;
    }

    public void setRuleEffStart(Date ruleEffStart) {
        this.ruleEffStart = ruleEffStart;
    }

    public Integer getAlertStatus() {
        return alertStatus;
    }

    public void setAlertStatus(Integer alertStatus) {
        this.alertStatus = alertStatus;
    }

    public Long getLinkRecordId() {
        return linkRecordId;
    }

    public void setLinkRecordId(Long linkRecordId) {
        this.linkRecordId = linkRecordId;
    }

    public Date getJudgedAt() {
        return judgedAt;
    }

    public void setJudgedAt(Date judgedAt) {
        this.judgedAt = judgedAt;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
