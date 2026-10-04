package com.fc.v2.service.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.exception.secre.SecreFlowException;
import com.fc.v2.mapper.auto.TSecreDispFlowMapper;
import com.fc.v2.mapper.auto.TSecreHandRowMapper;
import com.fc.v2.mapper.auto.TSecreLevelBillMapper;
import com.fc.v2.mapper.auto.TSecreOrgBookMapper;
import com.fc.v2.mapper.custom.SecreDispFlowDao;
import com.fc.v2.model.auto.TSecreDispFlow;
import com.fc.v2.model.auto.TSecreHandRow;
import com.fc.v2.model.auto.TSecreLevelBill;
import com.fc.v2.model.auto.TSecreOrgBook;
import com.fc.v2.service.ITSecreDispFlowService;
import com.fc.v2.shiro.util.ShiroUtils;

/**
 * 载体处置流转单 Service业务层处理（state-machine 形状：单据流转）
 *
 * 一条进度管一件载体，四档按次序排：核件(0)、定级(1)、清退(2)、监销(3)。
 * 情形：0未起 1在办 2已封住 3已叫停。规矩七条，逐条落在下面的把手上。
 *
 * 落库的写法：每笔都带哨兵（当时那档那情形＋底稿长度）一次落。两笔同时递到，
 * 先落的算数；后到的回去看——档位情形没动过，只是底稿被叠了层，重算重递；
 * 档位情形动过了，原样打回，决不许一笔递进来推着多走几档（第二条）。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TSecreDispFlowServiceImpl implements ITSecreDispFlowService {

    /** 四档：核件 / 定级 / 清退 / 监销 */
    private static final int STAGE_FIRST = 0;
    private static final int STAGE_GRADE = 1;
    private static final int STAGE_CLEAR = 2;
    private static final int STAGE_LAST = 3;

    /** 情形：0未起 1在办 2已封住 3已叫停 */
    private static final int STATUS_NEW = 0;
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_SEALED = 2;
    private static final int STATUS_HALTED = 3;

    /** 层上落的动作名 */
    private static final String ACT_CREATE = "起行";
    private static final String ACT_ADVANCE = "推进";
    private static final String ACT_NOTE = "按语";
    private static final String ACT_ROLLBACK = "回退";
    private static final String ACT_HALT = "叫停";
    private static final String ACT_ANNUL = "作废";
    private static final String ACT_DESTROY = "监销";

    /** 收尾的两种情形（第三条） */
    private static final String CAUSE_WITHDRAW = "撤申请";
    private static final String CAUSE_MERGE = "单位并走";

    /** 与另一笔撞上时，重算重递的上限 */
    private static final int TRANSIT_RETRY = 3;

    @javax.annotation.Resource
    private TSecreDispFlowMapper secreDispFlowMapper;

    @javax.annotation.Resource
    private TSecreLevelBillMapper secreLevelBillMapper;

    @javax.annotation.Resource
    private TSecreHandRowMapper secreHandRowMapper;

    @javax.annotation.Resource
    private TSecreOrgBookMapper secreOrgBookMapper;

    @javax.annotation.Resource
    private SecreDispFlowDao secreDispFlowDao;

    /**
     * 时刻统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）：与库里 datetime 对得上，
     * 层上落的那一刻就是受理簿上的那一刻。
     */
    private static String ts(Date d) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String contentOf(TSecreDispFlow r) {
        return r.getContent() == null ? "" : r.getContent();
    }

    /** 当前值班岗；没带会话进来（任务、单测）落 system */
    private static String currentUser() {
        try {
            return ShiroUtils.getLoginName();
        } catch (Exception e) {
            return "system";
        }
    }

    @Override
    public TSecreDispFlow selectTSecreDispFlowById(Long id) {
        return this.secreDispFlowMapper.selectById(id);
    }

    @Override
    public List<TSecreDispFlow> selectTSecreDispFlowList(QueryWrapper<TSecreDispFlow> queryWrapper) {
        // 撤掉的不留进册面；封住的照旧列得出（第四条）
        queryWrapper.eq("del_flag", 0);
        return this.secreDispFlowMapper.selectList(queryWrapper);
    }

    /**
     * 第七条：没走到尾的那类条数随起行加一、随封行减一。
     * 屏上那个数与回库逐档点出来的是同一回算的——这里就按同一口径逐档点：
     * 册面上（未删）且未封住（未起/在办/已叫停）的一行算一格。
     */
    @Override
    public long countUnfinished() {
        List<TSecreDispFlow> rows = this.secreDispFlowMapper.selectList(new QueryWrapper<TSecreDispFlow>()
                .select("id", "stage", "status")
                .eq("del_flag", 0)
                .in("status", STATUS_NEW, STATUS_ACTIVE, STATUS_HALTED));
        return rows.size();
    }

    /**
     * 第一条：凑齐四样——载体件码、所属名录代号、件数、拟密级——才起得来；
     * 缺哪一样点名哪一样。第六条：一件载体同时只许一条没走到尾的进度在场。
     */
    @Override
    public TSecreDispFlow add(TSecreDispFlow record) {
        if (record == null) {
            throw new SecreFlowException("单子是空的，起不来");
        }
        List<String> missing = new ArrayList<String>();
        if (isBlank(record.getCarrierNo())) {
            missing.add("载体件码");
        }
        if (isBlank(record.getBookNo())) {
            missing.add("所属名录代号");
        }
        if (record.getQty() == null) {
            missing.add("件数");
        }
        if (isBlank(record.getPlanLevel())) {
            missing.add("拟密级");
        }
        if (!missing.isEmpty()) {
            throw new SecreFlowException("缺" + String.join("、", missing) + "，起不来");
        }
        if (record.getQty().intValue() <= 0) {
            throw new SecreFlowException("件数得是正数，起不来");
        }
        // 名录得在册；已从名录上并走的，挡在册外
        TSecreOrgBook book = this.secreOrgBookMapper.selectOne(new QueryWrapper<TSecreOrgBook>()
                .eq("book_no", record.getBookNo()).eq("del_flag", 0));
        if (book == null) {
            throw new SecreFlowException("名录代号「" + record.getBookNo() + "」不在册，起不来");
        }
        if (book.getStatus() != null && book.getStatus().intValue() == 1) {
            throw new SecreFlowException("名录代号「" + record.getBookNo() + "」已从名录上并走，起不来");
        }
        // 第六条：前一条还卡在半途（未起/在办/已叫停），后一件起不来
        Integer occupying = this.secreDispFlowMapper.selectCount(new QueryWrapper<TSecreDispFlow>()
                .eq("carrier_no", record.getCarrierNo())
                .eq("del_flag", 0)
                .in("status", STATUS_NEW, STATUS_ACTIVE, STATUS_HALTED));
        if (occupying != null && occupying.intValue() > 0) {
            throw new SecreFlowException("这件载体还有一条没走到尾的进度在场，第二条起不来");
        }

        Date now = new Date();
        record.setId(null);
        if (isBlank(record.getBizNo())) {
            record.setBizNo("LC" + UUID.randomUUID().toString().replace("-", ""));
        }
        record.setStage(Integer.valueOf(STAGE_FIRST));
        record.setStatus(Integer.valueOf(STATUS_NEW));
        record.setDelFlag(Integer.valueOf(0));
        // 见证两岗与化浆凭证码是监销走满那一笔落的，起行时不许捎进来
        record.setWitnessOne(null);
        record.setWitnessTwo(null);
        record.setPulpNo(null);
        record.setLastAction(ACT_CREATE);
        String note = "件码「" + record.getCarrierNo().trim() + "」名录「" + record.getBookNo().trim()
                + "」件数" + record.getQty() + " 拟密级「" + record.getPlanLevel().trim() + "」"
                + (isBlank(record.getRemark()) ? "" : "；" + record.getRemark().trim());
        record.setContent(SecreFlowLayers.layer(STAGE_FIRST, ts(now), ACT_CREATE, note));
        this.secreDispFlowMapper.insert(record);
        return record;
    }

    /**
     * 第二条：往前的把手只有这一处，每回只挪紧邻的下一档；档位号系统数出来，
     * 报送里捎来的号只当转述、不作数（这里根本不收档位号，读库写库都按数出来的那个）。
     * 第三条：定级往清退看密级变更走没走满；清退往监销看核收落没落「可」；
     * 监销走满要两名见证的岗当场落笔、化浆凭证的码挂回本行。
     */
    @Override
    public TSecreDispFlow advance(Long id, String remark, String witnessOne, String witnessTwo, String pulpNo) {
        for (int attempt = 0; attempt < TRANSIT_RETRY; attempt++) {
            TSecreDispFlow r = mustRow(id);
            mustMovable(r);
            Date now = new Date();
            int status = r.getStatus().intValue();
            int stage = r.getStage().intValue();

            int newStage;
            int newStatus;
            String action = ACT_ADVANCE;
            String layer;
            String w1 = null;
            String w2 = null;
            String pp = null;

            if (status == STATUS_NEW) {
                // 未起 → 头一档（核件）起来
                newStage = STAGE_FIRST;
                newStatus = STATUS_ACTIVE;
                layer = SecreFlowLayers.layer(STAGE_FIRST, ts(now), ACT_ADVANCE, remark);
            } else if (stage < STAGE_LAST) {
                // 第三条闸门：定级→清退，密级变更那一步得走满（签批单已点齐）
                if (stage == STAGE_GRADE) {
                    Integer bills = this.secreLevelBillMapper.selectCount(new QueryWrapper<TSecreLevelBill>()
                            .eq("bill_no", r.getBizNo())
                            .eq("status", 1)
                            .eq("del_flag", 0));
                    if (bills == null || bills.intValue() == 0) {
                        throw new SecreFlowException("密级变更那一步还没走满，不许往清退挪");
                    }
                }
                // 第三条闸门：清退→监销，核收得落「可」（移交行已收下）
                if (stage == STAGE_CLEAR) {
                    Integer accepted = this.secreHandRowMapper.selectCount(new QueryWrapper<TSecreHandRow>()
                            .eq("item_code", r.getCarrierNo())
                            .eq("status", 1)
                            .eq("del_flag", 0));
                    if (accepted == null || accepted.intValue() == 0) {
                        throw new SecreFlowException("清退这一档的核收还没落「可」，监销不许开口");
                    }
                }
                newStage = stage + 1;
                newStatus = STATUS_ACTIVE;
                layer = SecreFlowLayers.layer(stage + 1, ts(now), ACT_ADVANCE, remark);
            } else {
                // 末一档（监销）：两名见证的岗当场落笔、化浆凭证的码挂回本行，才算走满，走完封住
                if (isBlank(witnessOne) || isBlank(witnessTwo)) {
                    throw new SecreFlowException("监销要两名见证的岗当场落笔，人不够，走不满");
                }
                if (witnessOne.trim().equals(witnessTwo.trim())) {
                    throw new SecreFlowException("两名见证的岗得是两个人，不能是同一岗");
                }
                if (isBlank(pulpNo)) {
                    throw new SecreFlowException("化浆凭证的码没挂回这一行，监销走不满");
                }
                String note = "见证「" + witnessOne.trim() + "」「" + witnessTwo.trim() + "」化浆凭证「" + pulpNo.trim() + "」"
                        + (isBlank(remark) ? "" : "；" + remark.trim());
                newStage = STAGE_LAST;
                newStatus = STATUS_SEALED;
                action = ACT_DESTROY;
                w1 = witnessOne.trim();
                w2 = witnessTwo.trim();
                pp = pulpNo.trim();
                layer = SecreFlowLayers.layer(STAGE_LAST, ts(now), ACT_DESTROY, note);
            }

            String content = contentOf(r);
            String newContent = SecreFlowLayers.appendLive(content, layer);
            int rows = this.secreDispFlowDao.rewriteAndTransit(id, status, stage, content.length(),
                    newContent, newStage, newStatus, action, w1, w2, pp, currentUser(), now);
            if (rows > 0) {
                return this.secreDispFlowMapper.selectById(id);
            }
            throwIfMoved(id, status, stage);
        }
        throw busy();
    }

    /**
     * 第五条：往回一回只收一档；被收那一档早先落下的按语沉到末位，
     * 重走这一档从这一档重新攒起；已经停在头一档的，不再往回退。
     */
    @Override
    public TSecreDispFlow rollback(Long id, String remark) {
        for (int attempt = 0; attempt < TRANSIT_RETRY; attempt++) {
            TSecreDispFlow r = mustRow(id);
            mustMovable(r);
            int status = r.getStatus().intValue();
            int stage = r.getStage().intValue();
            if (status == STATUS_NEW) {
                throw new SecreFlowException("还没起来，无档可退");
            }
            if (stage <= STAGE_FIRST) {
                throw new SecreFlowException("已经停在头一档，不再往回退");
            }
            Date now = new Date();
            String content = contentOf(r);
            // 被收那一档的活层沉到末位；回退这一层叠在活层段尾、沉层段前
            String[] parts = SecreFlowLayers.sink(content, stage);
            String newContent = parts[0]
                    + SecreFlowLayers.layer(stage - 1, ts(now), ACT_ROLLBACK, remark)
                    + parts[1];
            int rows = this.secreDispFlowDao.rewriteAndTransit(id, status, stage, content.length(),
                    newContent, stage - 1, STATUS_ACTIVE, ACT_ROLLBACK, null, null, null, currentUser(), now);
            if (rows > 0) {
                return this.secreDispFlowMapper.selectById(id);
            }
            throwIfMoved(id, status, stage);
        }
        throw busy();
    }

    /**
     * 第七条：每一档写过的话一层层叠在这一行上。第五条：同一档第二遍递上来，
     * 这一行只留头一遍那句，不另叠一层（回头递返回 false）。
     */
    @Override
    public boolean updateContent(Long id, String note) {
        if (isBlank(note)) {
            throw new SecreFlowException("按语是空的，记不上");
        }
        for (int attempt = 0; attempt < TRANSIT_RETRY; attempt++) {
            TSecreDispFlow r = mustRow(id);
            mustMovable(r);
            int status = r.getStatus().intValue();
            int stage = r.getStage().intValue();
            if (status == STATUS_NEW) {
                throw new SecreFlowException("还没起来，没档可记");
            }
            if (SecreFlowLayers.isDuplicate(r.getContent(), stage, ACT_NOTE, note.trim())) {
                return false;
            }
            Date now = new Date();
            String content = contentOf(r);
            String newContent = SecreFlowLayers.appendLive(content,
                    SecreFlowLayers.layer(stage, ts(now), ACT_NOTE, note));
            int rows = this.secreDispFlowDao.rewriteAndTransit(id, status, stage, content.length(),
                    newContent, stage, status, ACT_NOTE, null, null, null, currentUser(), now);
            if (rows > 0) {
                return true;
            }
            throwIfMoved(id, status, stage);
        }
        throw busy();
    }

    /** 第六条：中途叫停。叫停的仍占着「未走完」那一格，后一件起不来。 */
    @Override
    public TSecreDispFlow halt(Long id, String remark) {
        for (int attempt = 0; attempt < TRANSIT_RETRY; attempt++) {
            TSecreDispFlow r = mustRow(id);
            mustNotRevoked(r);
            mustLanded(r);
            int status = r.getStatus().intValue();
            int stage = r.getStage().intValue();
            if (status == STATUS_SEALED) {
                throw new SecreFlowException("这条已封住，只读得、动不得");
            }
            if (status == STATUS_HALTED) {
                throw new SecreFlowException("这条已经叫停了");
            }
            if (status != STATUS_ACTIVE) {
                throw new SecreFlowException("只有在办的叫得停");
            }
            Date now = new Date();
            String content = contentOf(r);
            String newContent = SecreFlowLayers.appendLive(content,
                    SecreFlowLayers.layer(stage, ts(now), ACT_HALT, remark));
            int rows = this.secreDispFlowDao.rewriteAndTransit(id, status, stage, content.length(),
                    newContent, stage, STATUS_HALTED, ACT_HALT, null, null, null, currentUser(), now);
            if (rows > 0) {
                return this.secreDispFlowMapper.selectById(id);
            }
            throwIfMoved(id, status, stage);
        }
        throw busy();
    }

    /** 第六条：作废只从末一档走；作废即收尾封住。 */
    @Override
    public TSecreDispFlow annul(Long id, String remark) {
        for (int attempt = 0; attempt < TRANSIT_RETRY; attempt++) {
            TSecreDispFlow r = mustRow(id);
            mustNotRevoked(r);
            mustLanded(r);
            int status = r.getStatus().intValue();
            int stage = r.getStage().intValue();
            if (status == STATUS_SEALED) {
                throw new SecreFlowException("这条已封住，只读得、动不得");
            }
            if (status != STATUS_ACTIVE || stage != STAGE_LAST) {
                throw new SecreFlowException("作废只从末一档走");
            }
            Date now = new Date();
            String content = contentOf(r);
            String newContent = SecreFlowLayers.appendLive(content,
                    SecreFlowLayers.layer(STAGE_LAST, ts(now), ACT_ANNUL, remark));
            int rows = this.secreDispFlowDao.rewriteAndTransit(id, status, stage, content.length(),
                    newContent, STAGE_LAST, STATUS_SEALED, ACT_ANNUL, null, null, null, currentUser(), now);
            if (rows > 0) {
                return this.secreDispFlowMapper.selectById(id);
            }
            throwIfMoved(id, status, stage);
        }
        throw busy();
    }

    /** 第六条：撤销只发生在头一档起来之前那一小段；撤掉的不留进册面，也不占「未走完」那一格。 */
    @Override
    public boolean revoke(Long id) {
        TSecreDispFlow r = mustRow(id);
        if (r.getDelFlag() != null && r.getDelFlag().intValue() == 1) {
            throw new SecreFlowException("这条已撤销，册面上没有了");
        }
        if (r.getStatus() == null || r.getStatus().intValue() != STATUS_NEW) {
            throw new SecreFlowException("撤销只在头一档起来之前那一小段，起来了就撤不了");
        }
        int rows = this.secreDispFlowDao.markRevoked(id, currentUser(), new Date());
        if (rows == 0) {
            throw race();
        }
        return true;
    }

    /** 第三条：中途撤了申请、或所属那一处单位从名录上并走，两种情形也算把尾收了。 */
    @Override
    public TSecreDispFlow seal(Long id, String cause, String remark) {
        for (int attempt = 0; attempt < TRANSIT_RETRY; attempt++) {
            TSecreDispFlow r = mustRow(id);
            mustNotRevoked(r);
            mustLanded(r);
            int status = r.getStatus().intValue();
            int stage = r.getStage().intValue();
            if (status == STATUS_SEALED) {
                throw new SecreFlowException("这条已封住，只读得、动不得");
            }
            if (status == STATUS_NEW) {
                throw new SecreFlowException("还没起来的单子走撤销，不走收尾");
            }
            if (!CAUSE_WITHDRAW.equals(cause) && !CAUSE_MERGE.equals(cause)) {
                throw new SecreFlowException("收尾只有两种情形：中途撤了申请、所属单位从名录上并走");
            }
            if (CAUSE_MERGE.equals(cause)) {
                TSecreOrgBook book = this.secreOrgBookMapper.selectOne(new QueryWrapper<TSecreOrgBook>()
                        .eq("book_no", r.getBookNo()).eq("del_flag", 0));
                if (book == null || book.getStatus() == null || book.getStatus().intValue() != 1) {
                    throw new SecreFlowException("所属单位还在册上，算不上并走，尾收不了");
                }
            }
            Date now = new Date();
            String content = contentOf(r);
            String newContent = SecreFlowLayers.appendLive(content,
                    SecreFlowLayers.layer(stage, ts(now), cause, remark));
            int rows = this.secreDispFlowDao.rewriteAndTransit(id, status, stage, content.length(),
                    newContent, stage, STATUS_SEALED, cause, null, null, null, currentUser(), now);
            if (rows > 0) {
                return this.secreDispFlowMapper.selectById(id);
            }
            throwIfMoved(id, status, stage);
        }
        throw busy();
    }

    /** 第四条：已封住的抽不走。 */
    @Override
    public boolean remove(Long id) {
        TSecreDispFlow r = this.secreDispFlowMapper.selectById(id);
        if (r == null) {
            return false;
        }
        if (r.getStatus() != null && r.getStatus().intValue() == STATUS_SEALED) {
            throw new SecreFlowException("这条已封住，抽不走");
        }
        return this.secreDispFlowMapper.deleteById(id) > 0;
    }

    /** 行不在，话照直说 */
    private TSecreDispFlow mustRow(Long id) {
        if (id == null) {
            throw new SecreFlowException("没点哪一条，办不了");
        }
        TSecreDispFlow r = this.secreDispFlowMapper.selectById(id);
        if (r == null) {
            throw new SecreFlowException("这条进度不在册");
        }
        return r;
    }

    private void mustNotRevoked(TSecreDispFlow r) {
        if (r.getDelFlag() != null && r.getDelFlag().intValue() == 1) {
            throw new SecreFlowException("这条已撤销，册面上没有了");
        }
    }

    /** 情形、关次都得落上了才办得动 */
    private void mustLanded(TSecreDispFlow r) {
        if (r.getStatus() == null) {
            throw new SecreFlowException("这条情形没落上，办不了");
        }
        if (r.getStage() == null) {
            throw new SecreFlowException("这条关次没落上，办不了");
        }
    }

    /** 第四条：封住的只读得、动不得；叫停的也挪不动 */
    private void mustMovable(TSecreDispFlow r) {
        mustNotRevoked(r);
        mustLanded(r);
        if (r.getStatus().intValue() == STATUS_SEALED) {
            throw new SecreFlowException("这条已封住，只读得、动不得");
        }
        if (r.getStatus().intValue() == STATUS_HALTED) {
            throw new SecreFlowException("这条中途叫停了，挪不动");
        }
    }

    /**
     * 哨兵没对上时看一眼：档位情形动过了（或行没了、撤了），是另一笔先挪了档，
     * 这一笔原样打回；没动过只是底稿被叠了层，才放回去重算重递。
     */
    private void throwIfMoved(Long id, int expectStatus, int expectStage) {
        TSecreDispFlow now = this.secreDispFlowMapper.selectById(id);
        if (now == null || now.getStatus() == null || now.getStage() == null
                || now.getStatus().intValue() != expectStatus
                || now.getStage().intValue() != expectStage
                || (now.getDelFlag() != null && now.getDelFlag().intValue() == 1)) {
            throw race();
        }
    }

    /** 两笔同时递到，先落的算数，后到的按库里的数重来（第七条） */
    private SecreFlowException race() {
        return new SecreFlowException("另有一笔先落了，按库里的数重来");
    }

    /** 连着几回都撞在另一笔上，稍候再递 */
    private SecreFlowException busy() {
        return new SecreFlowException("另有一笔正在落，这一笔没落成，稍候再递");
    }
}
