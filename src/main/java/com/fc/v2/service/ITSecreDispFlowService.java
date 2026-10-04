package com.fc.v2.service;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.exception.secre.SecreFlowException;
import com.fc.v2.model.auto.TSecreDispFlow;

/**
 * 载体处置流转单 Service接口（state-machine 形状：单据流转，不提供增删改查全套）
 *
 * 总规矩：一条进度管一件载体，从核件起、到监销止；四档按次序排——核件、定级、清退、监销。
 * 违规一律抛 {@link SecreFlowException}，话里点名挡在哪一条。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITSecreDispFlowService {

    /** 按主键查询单据 */
    TSecreDispFlow selectTSecreDispFlowById(Long id);

    /** 列表查询（流转台账）：撤掉的不留进册面；封住的照旧列得出，只是没有把手 */
    List<TSecreDispFlow> selectTSecreDispFlowList(QueryWrapper<TSecreDispFlow> queryWrapper);

    /**
     * 未走完条数：随起行加一、随封行减一。
     * 屏上那个数与回库逐档点出来的是同一回算的——同一趟查回来逐档点，不另起一套口径。
     */
    long countUnfinished();

    /**
     * 起行（第一条、第六条）：凑齐四样——载体件码、所属名录代号、件数、拟密级——才起得来；
     * 缺哪一样点名哪一样。一件载体同时只许一条没走到尾的进度在场，占着就起不来。
     */
    TSecreDispFlow add(TSecreDispFlow record);

    /**
     * 推进一档（第二条、第三条）：往前的把手只有这一处，每回只挪紧邻的下一档；
     * 档位号由系统数出来，报送里捎来的号只当转述、不作数。
     * 定级往清退看密级变更走没走满；清退往监销看核收落没落「可」；
     * 监销走满要两名见证的岗当场落笔、化浆凭证的码挂回本行，走完即封住。
     */
    TSecreDispFlow advance(Long id, String remark, String witnessOne, String witnessTwo, String pulpNo);

    /**
     * 回退一档（第五条）：一回只收一档；被收那一档早先落下的按语沉到末位，
     * 重走这一档从这一档重新攒起；已经停在头一档的，不再往回退。
     */
    TSecreDispFlow rollback(Long id, String remark);

    /**
     * 记按语（第五条、第七条）：落在当前这一档上，一层层叠在这一行。
     * 同一档第二遍递上来，只留头一遍那句，不另叠一层（回头递返回 false）。
     */
    boolean updateContent(Long id, String note);

    /** 叫停（第六条）：只有在办的叫得停；叫停的仍占着「未走完」那一格，后一件起不来 */
    TSecreDispFlow halt(Long id, String remark);

    /** 作废（第六条）：只从末一档走；作废即收尾封住 */
    TSecreDispFlow annul(Long id, String remark);

    /** 撤销（第六条）：只在头一档起来之前那一小段；撤掉的不留进册面，也不占「未走完」那一格 */
    boolean revoke(Long id);

    /**
     * 收尾（第三条）：中途撤了申请（cause=撤申请）、或所属那一处单位从名录上并走
     * （cause=单位并走，须名册上确已撤出），两种情形也算把尾收了，收了尾就封住。
     */
    TSecreDispFlow seal(Long id, String cause, String remark);

    /** 删除单据（第四条）：已封住的抽不走 */
    boolean remove(Long id);
}
