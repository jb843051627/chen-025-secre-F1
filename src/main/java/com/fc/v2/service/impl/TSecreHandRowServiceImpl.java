package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TSecreHandRowMapper;
import com.fc.v2.model.auto.TSecreHandRow;
import com.fc.v2.service.ITSecreHandRowService;

/**
 * 清退移交行 Service业务层处理（batch-process 形状：整批提交）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TSecreHandRowServiceImpl implements ITSecreHandRowService {

    private static final int MAX_ROWS = 500;
    private static final int STATUS_OK = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TSecreHandRowMapper secreHandRowMapper;

    @Override
    public TSecreHandRow selectTSecreHandRowById(Long id) {
        return this.secreHandRowMapper.selectById(id);
    }

    @Override
    public int submitBatch(String batchNo, List<TSecreHandRow> rows) {
        String no = rows.get(0).getBatchNo();
        java.util.List<TSecreHandRow> errors = new java.util.ArrayList<TSecreHandRow>();
        int seq = 0;
        for (TSecreHandRow r : rows) {
            if (r.getItemCode() == null || r.getItemCode().trim().isEmpty()
                    || r.getQty() == null
                    || r.getQty().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                seq++;
                r.setRowNo(Integer.valueOf(seq));
                r.setBatchNo(no);
                r.setStatus(STATUS_FAIL);
                this.secreHandRowMapper.insert(r);
                errors.add(r);
            }
        }
        if (!errors.isEmpty()) {
            return 0;
        }
        int ok = 0;
        for (TSecreHandRow r : rows) {
            r.setBatchNo(no);
            r.setStatus(STATUS_OK);
            this.secreHandRowMapper.insert(r);
            ok++;
        }
        return ok;
    }

    @Override
    public List<TSecreHandRow> listErrors(String batchNo) {
        return this.secreHandRowMapper.selectList(new QueryWrapper<TSecreHandRow>()
                .eq("batch_no", batchNo).eq("status", STATUS_FAIL));
    }
}
