package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TSecreDispFlow;
import com.fc.v2.service.ITSecreDispFlowService;
import com.fc.v2.util.StringUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 载体处置流转单 Controller（state-machine 形状：流转入口）
 *
 * 一条进度管一件载体，从核件起、到监销止。往前的把手只有一处（advance），
 * 报送里捎来的档位号只当一句转述——本组入口一概不收档位号，
 * 写库读库都按系统数出来的那一个。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Api(value = "载体处置流转单")
@Controller
@RequestMapping("/secreDispFlow")
public class SecreDispFlowController extends BaseController {

    private final String prefix = "admin/secreDispFlow";

    @Autowired
    private ITSecreDispFlowService secreDispFlowService;

    @ApiOperation(value = "流转台账跳转", notes = "流转台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("secreDispFlow:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "载体处置流转单流转台账", action = "list")
    @ApiOperation(value = "流转台账", notes = "流转台账")
    @GetMapping("/list")
    @RequiresPermissions("secreDispFlow:list")
    @ResponseBody
    public ResultTable list(TSecreDispFlow record) {
        QueryWrapper<TSecreDispFlow> queryWrapper = new QueryWrapper<TSecreDispFlow>();
        queryWrapper.like(StringUtils.isNotEmpty(record.getCarrierNo()), "carrier_no", record.getCarrierNo());
        queryWrapper.eq(StringUtils.isNotEmpty(record.getBookNo()), "book_no", record.getBookNo());
        queryWrapper.eq(record.getStatus() != null, "status", record.getStatus());
        queryWrapper.eq(record.getStage() != null, "stage", record.getStage());
        startPage();
        com.github.pagehelper.PageInfo<TSecreDispFlow> page =
                new com.github.pagehelper.PageInfo<TSecreDispFlow>(secreDispFlowService.selectTSecreDispFlowList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "载体处置流转单起行", action = "add")
    @ApiOperation(value = "起行", notes = "凑齐四样才起得来：载体件码、所属名录代号、件数、拟密级")
    @PostMapping("/add")
    @RequiresPermissions("secreDispFlow:add")
    @ResponseBody
    public AjaxResult add(TSecreDispFlow record) {
        secreDispFlowService.add(record);
        return success("起行成了");
    }

    @Log(title = "载体处置流转单推进", action = "advance")
    @ApiOperation(value = "推进一档", notes = "每回只挪紧邻的下一档；到监销走满即封住")
    @PostMapping("/advance")
    @RequiresPermissions("secreDispFlow:advance")
    @ResponseBody
    public AjaxResult advance(Long id, String remark, String witnessOne, String witnessTwo, String pulpNo) {
        secreDispFlowService.advance(id, remark, witnessOne, witnessTwo, pulpNo);
        return success("推进成了");
    }

    @Log(title = "载体处置流转单回退", action = "rollback")
    @ApiOperation(value = "回退一档", notes = "一回只收一档；被收档的按语沉到末位")
    @PostMapping("/rollback")
    @RequiresPermissions("secreDispFlow:rollback")
    @ResponseBody
    public AjaxResult rollback(Long id, String remark) {
        secreDispFlowService.rollback(id, remark);
        return success("收回了一档");
    }

    @Log(title = "载体处置流转单记按语", action = "record")
    @ApiOperation(value = "记按语", notes = "落在当前这一档；同一档第二遍递上来不另叠一层")
    @PostMapping("/record")
    @RequiresPermissions("secreDispFlow:record")
    @ResponseBody
    public AjaxResult record(Long id, String note) {
        boolean ok = secreDispFlowService.updateContent(id, note);
        return ok ? success("记上了") : error("同一档第二遍递上来，这一行只留头一遍那句，不另叠一层");
    }

    @Log(title = "载体处置流转单叫停", action = "halt")
    @ApiOperation(value = "叫停", notes = "中途叫停；仍占着未走完那一格")
    @PostMapping("/halt")
    @RequiresPermissions("secreDispFlow:halt")
    @ResponseBody
    public AjaxResult halt(Long id, String remark) {
        secreDispFlowService.halt(id, remark);
        return success("已叫停");
    }

    @Log(title = "载体处置流转单作废", action = "annul")
    @ApiOperation(value = "作废", notes = "作废只从末一档走，作废即封住")
    @PostMapping("/annul")
    @RequiresPermissions("secreDispFlow:annul")
    @ResponseBody
    public AjaxResult annul(Long id, String remark) {
        secreDispFlowService.annul(id, remark);
        return success("已作废封住");
    }

    @Log(title = "载体处置流转单撤销", action = "revoke")
    @ApiOperation(value = "撤销", notes = "只在头一档起来之前那一小段；撤掉的不留进册面")
    @PostMapping("/revoke")
    @RequiresPermissions("secreDispFlow:revoke")
    @ResponseBody
    public AjaxResult revoke(Long id) {
        return toAjax(secreDispFlowService.revoke(id) ? 1 : 0);
    }

    @Log(title = "载体处置流转单收尾", action = "seal")
    @ApiOperation(value = "收尾", notes = "中途撤了申请、或所属单位从名录上并走，也算把尾收了")
    @PostMapping("/seal")
    @RequiresPermissions("secreDispFlow:seal")
    @ResponseBody
    public AjaxResult seal(Long id, String cause, String remark) {
        secreDispFlowService.seal(id, cause, remark);
        return success("尾收了，已封住");
    }

    @Log(title = "载体处置流转单删除", action = "remove")
    @ApiOperation(value = "删除", notes = "已封住的抽不走")
    @DeleteMapping("/remove")
    @RequiresPermissions("secreDispFlow:remove")
    @ResponseBody
    public AjaxResult remove(Long id) {
        return toAjax(secreDispFlowService.remove(id) ? 1 : 0);
    }

    @ApiOperation(value = "未走完条数", notes = "随起行加一、随封行减一；与回库逐档点出来的同一回算的")
    @GetMapping("/unfinishedCount")
    @RequiresPermissions("secreDispFlow:list")
    @ResponseBody
    public AjaxResult unfinishedCount() {
        return retobject(200, secreDispFlowService.countUnfinished());
    }
}
