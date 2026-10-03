package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TSecreCarrier;
import com.fc.v2.service.ITSecreCarrierService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 涉密载体册面 Controller
 *
 * @author fuce
 * @date 2026-09-12
 */
@Api(value = "涉密载体册面")
@Controller
@RequestMapping("/SecreCarrierController")
public class SecreCarrierController extends BaseController {

    private final String prefix = "admin/secreCarrier";

    @Autowired
    private ITSecreCarrierService secreCarrierService;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("secre:secreCarrier:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "涉密载体册面集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分页查询")
    @GetMapping("/list")
    @RequiresPermissions("secre:secreCarrier:list")
    @ResponseBody
    public ResultTable list(TSecreCarrier record) {
        QueryWrapper<TSecreCarrier> queryWrapper = new QueryWrapper<TSecreCarrier>();
        startPage();
        com.github.pagehelper.PageInfo<TSecreCarrier> page =
                new com.github.pagehelper.PageInfo<TSecreCarrier>(secreCarrierService.selectTSecreCarrierList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "涉密载体册面新增", action = "add")
    @ApiOperation(value = "新增", notes = "新增")
    @PostMapping("/add")
    @RequiresPermissions("secre:secreCarrier:add")
    @ResponseBody
    public AjaxResult add(TSecreCarrier record) {
        return toAjax(secreCarrierService.insertTSecreCarrier(record));
    }

    @Log(title = "涉密载体册面修改", action = "edit")
    @ApiOperation(value = "修改保存", notes = "修改保存")
    @PostMapping("/edit")
    @RequiresPermissions("secre:secreCarrier:edit")
    @ResponseBody
    public AjaxResult editSave(TSecreCarrier record) {
        return toAjax(secreCarrierService.updateTSecreCarrier(record));
    }

    @Log(title = "涉密载体册面删除", action = "remove")
    @ApiOperation(value = "删除", notes = "删除")
    @DeleteMapping("/remove")
    @RequiresPermissions("secre:secreCarrier:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(secreCarrierService.deleteTSecreCarrierByIds(ids));
    }
}
