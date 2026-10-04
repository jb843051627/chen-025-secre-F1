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
import java.util.Date;

/**
 * 载体处置流转单对象 t_secre_disp_flow
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_secre_disp_flow")
@ApiModel(value = "TSecreDispFlow", description = "载体处置流转单")
public class TSecreDispFlow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 载体处置流转单号 */
    @TableField("biz_no")
    @ApiModelProperty(value = "载体处置流转单号")
    private String bizNo;

    /** 载体件码（起行四样之一，缺一起不来） */
    @TableField("carrier_no")
    @ApiModelProperty(value = "载体件码")
    private String carrierNo;

    /** 所属名录代号（起行四样之一） */
    @TableField("book_no")
    @ApiModelProperty(value = "所属名录代号")
    private String bookNo;

    /** 件数（起行四样之一） */
    @TableField("qty")
    @ApiModelProperty(value = "件数")
    private Integer qty;

    /** 拟上去的那一级密级（起行四样之一） */
    @TableField("plan_level")
    @ApiModelProperty(value = "拟密级")
    private String planLevel;

    /** 当前关次 0..3（核件/定级/清退/监销） */
    @TableField("stage")
    @ApiModelProperty(value = "当前关次 0..3（核件/定级/清退/监销）")
    private Integer stage;

    /** 单子走到哪一关 0未起 1在办 2已封住 3已叫停 */
    @TableField("status")
    @ApiModelProperty(value = "单子走到哪一关 0未起 1在办 2已封住 3已叫停")
    private Integer status;

    /** 监销见证岗一（当场落笔） */
    @TableField("witness_one")
    @ApiModelProperty(value = "监销见证岗一")
    private String witnessOne;

    /** 监销见证岗二（当场落笔） */
    @TableField("witness_two")
    @ApiModelProperty(value = "监销见证岗二")
    private String witnessTwo;

    /** 化浆凭证的码（监销走满时挂回本行） */
    @TableField("pulp_no")
    @ApiModelProperty(value = "化浆凭证的码")
    private String pulpNo;

    /** 一关一记：每层一行【档k】时刻｜动作｜按语；被收那一档的按语冠【沉】沉到末位 */
    @TableField("content")
    @ApiModelProperty(value = "一关一记")
    private String content;

    /** 最近一次挪关动作 */
    @TableField("last_action")
    @ApiModelProperty(value = "最近一次挪关动作")
    private String lastAction;

    /** 删除标记 0正常 1删除（撤销不留进册面走这里） */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public String getCarrierNo() {
        return carrierNo;
    }

    public void setCarrierNo(String carrierNo) {
        this.carrierNo = carrierNo;
    }

    public String getBookNo() {
        return bookNo;
    }

    public void setBookNo(String bookNo) {
        this.bookNo = bookNo;
    }

    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }

    public String getPlanLevel() {
        return planLevel;
    }

    public void setPlanLevel(String planLevel) {
        this.planLevel = planLevel;
    }

    public Integer getStage() {
        return stage;
    }

    public void setStage(Integer stage) {
        this.stage = stage;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getWitnessOne() {
        return witnessOne;
    }

    public void setWitnessOne(String witnessOne) {
        this.witnessOne = witnessOne;
    }

    public String getWitnessTwo() {
        return witnessTwo;
    }

    public void setWitnessTwo(String witnessTwo) {
        this.witnessTwo = witnessTwo;
    }

    public String getPulpNo() {
        return pulpNo;
    }

    public void setPulpNo(String pulpNo) {
        this.pulpNo = pulpNo;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getLastAction() {
        return lastAction;
    }

    public void setLastAction(String lastAction) {
        this.lastAction = lastAction;
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
