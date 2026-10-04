package com.fc.v2.service.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TSecreDispFlowMapper;
import com.fc.v2.model.auto.TSecreDispFlow;
import com.fc.v2.service.ITSecreDispFlowService;

/**
 * 载体处置流转单 Service业务层处理（state-machine 形状：单据流转）
 *
 * <p>进度四档：0核件 → 1定级 → 2清退 → 3监销；单子情形：0未起 1在办 2已封住。照条办：
 * <ul>
 *   <li>第二条：往前的把手只有 advance 一处，每回只挪紧邻的下一档；档位以库内数出来的为准，
 *       报送里捎来的档位号只当转述，不作数。</li>
 *   <li>第三、四条：监销走满（见证落笔、化浆凭证码随 remark 挂回这一行）即收尾封住；
 *       封住的只读——改不进来、抽不走、也往回挪不了半档，但台账里照旧列得出。</li>
 *   <li>第五条：回退一回只收一档；被收那一格里早先落下的按语沉到末位（改挂「沉·」牌），
 *       重走这一档从这一档重新攒起；已停在头一档的不再往回退。同一档同一句话第二遍
 *       递上来，只留头一遍那句，不另叠一层。</li>
 *   <li>第六条：未起的可以撤掉（remove），撤掉的不留册面、不占「未走完」那一格；
 *       已封住的抽不走。</li>
 *   <li>第七条：每一档写过的话连同落在哪一刻，一层层叠在 content 上（一层一行：
 *       【牌】yyyy-MM-dd HH:mm:ss｜话）。两个入口同一瞬间各递一笔，走同一把锁各自
 *       记各自的先后，不许并作一条，也不许吞掉一笔。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TSecreDispFlowServiceImpl implements ITSecreDispFlowService {

    /** 四档牌名，按下标取：0核件 1定级 2清退 3监销 */
    private static final String[] STAGE_NAMES = {"核件", "定级", "清退", "监销"};
    private static final int STAGE_FIRST = 0;
    private static final int STAGE_MAX = 3;

    /** 单子情形：0未起 1在办 2已封住 */
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_SEALED = 2;

    /** 层记版式：【牌】yyyy-MM-dd HH:mm:ss｜话（版式照旧的走，一层一行，单拎一层也站得住） */
    private static final String LAYER_TIME_FMT = "yyyy-MM-dd HH:mm:ss";
    private static final char LAYER_L = '【';
    private static final char LAYER_R = '】';
    private static final String LAYER_SEP = "｜";
    private static final String LINE_SEP = "\n";
    /** 监销走满那一层的牌 */
    private static final String TAG_SEAL = "封";
    /** 回退记号的牌头：【回退·清退→定级】 */
    private static final String TAG_BACK_PREFIX = "回退·";
    /** 被收那一格沉到末位的按语改挂的牌头：【沉·清退】 */
    private static final String TAG_SUNK_PREFIX = "沉·";

    /** 挪档、回退、改、删的读-改-写走同一把锁：同一瞬间各递一笔的各自记各自的，不并不吞 */
    private final Object flowLock = new Object();

    @javax.annotation.Resource
    private TSecreDispFlowMapper secreDispFlowMapper;

    @Override
    public TSecreDispFlow selectTSecreDispFlowById(Long id) {
        return this.secreDispFlowMapper.selectById(id);
    }

    @Override
    public List<TSecreDispFlow> selectTSecreDispFlowList(QueryWrapper<TSecreDispFlow> queryWrapper) {
        // 第四条：封住的那一类在屏上照旧列得出来，这里不加任何过滤
        return this.secreDispFlowMapper.selectList(queryWrapper);
    }

    @Override
    public TSecreDispFlow advance(Long id, String remark) {
        if (id == null) {
            return null;
        }
        synchronized (flowLock) {
            TSecreDispFlow r = this.secreDispFlowMapper.selectById(id);
            if (r == null) {
                return null;
            }
            int status = statusOf(r);
            if (status == STATUS_SEALED) {
                return null; // 第四条：封住的动不得
            }
            int stage = stageOf(r);
            String tag;
            if (status == STATUS_PENDING) {
                // 起行：进入当前这一档（寻常为头一档核件），未走完的条数随起行加一
                r.setStatus(STATUS_ACTIVE);
                tag = STAGE_NAMES[stage];
            } else if (stage < STAGE_MAX) {
                // 第二条：每回只挪紧邻的下一档，档位按系统数出来的那一个写库
                stage = stage + 1;
                tag = STAGE_NAMES[stage];
            } else {
                // 第三条：监销走满——两名见证落笔、化浆凭证的码随 remark 挂回这一行，收尾封住
                r.setStatus(STATUS_SEALED);
                tag = TAG_SEAL;
            }
            r.setStage(stage);
            appendLayer(r, tag, remark);
            r.setLastAction(remark);
            r.setUpdateTime(new Date());
            this.secreDispFlowMapper.updateById(r);
            return r;
        }
    }

    @Override
    public TSecreDispFlow rollback(Long id, String remark) {
        if (id == null) {
            return null;
        }
        synchronized (flowLock) {
            TSecreDispFlow r = this.secreDispFlowMapper.selectById(id);
            if (r == null) {
                return null;
            }
            if (statusOf(r) != STATUS_ACTIVE) {
                return null; // 未起的没有东西可收；已封住的往回挪不了半档（第四条）
            }
            int stage = stageOf(r);
            if (stage <= STAGE_FIRST) {
                return null; // 第五条：已停在头一档的，不再往回退
            }
            // 第五条：往回一回只收一档
            r.setStage(stage - 1);
            // 被收那一格里早先落下的按语取出来，改挂「沉·」牌
            List<String> sunk = detachStageLayers(r, stage);
            appendLine(r, LAYER_L + TAG_BACK_PREFIX + STAGE_NAMES[stage] + "→" + STAGE_NAMES[stage - 1]
                    + LAYER_R + now() + LAYER_SEP + normNote(remark));
            for (String line : sunk) {
                appendLine(r, line); // 沉到末位去；重走这一档从这一档重新攒起
            }
            r.setLastAction(remark);
            r.setUpdateTime(new Date());
            this.secreDispFlowMapper.updateById(r);
            return r;
        }
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        if (id == null) {
            return false;
        }
        synchronized (flowLock) {
            TSecreDispFlow r = this.secreDispFlowMapper.selectById(id);
            if (r == null || statusOf(r) == STATUS_SEALED) {
                return false; // 第四条：封住的改不进来
            }
            r.setContent(remark);
            r.setUpdateTime(new Date());
            return this.secreDispFlowMapper.updateById(r) > 0;
        }
    }

    @Override
    public boolean remove(Long id) {
        if (id == null) {
            return false;
        }
        synchronized (flowLock) {
            TSecreDispFlow r = this.secreDispFlowMapper.selectById(id);
            if (r == null || statusOf(r) == STATUS_SEALED) {
                return false; // 第四条：封住的抽不走
            }
            // 第六条：未起的撤掉不留册面、不占「未走完」那一格
            return this.secreDispFlowMapper.deleteById(id) > 0;
        }
    }

    /* ------------------------------------------------------------------ */
    /* 层记：每一档写过的话连同时刻一层层叠在 content 上，一层一行               */
    /* ------------------------------------------------------------------ */

    /** 叠一层；与末层同牌同话的视为同一笔重递，只留头一遍那句，不另叠一层（第五条） */
    private void appendLayer(TSecreDispFlow r, String tag, String remark) {
        String note = normNote(remark);
        String tail = tailLine(r.getContent());
        if (tail != null && tag.equals(tagOf(tail)) && note.equals(noteOf(tail))) {
            return;
        }
        appendLine(r, LAYER_L + tag + LAYER_R + now() + LAYER_SEP + note);
    }

    private void appendLine(TSecreDispFlow r, String line) {
        String content = r.getContent();
        r.setContent(content == null || content.isEmpty() ? line : content + LINE_SEP + line);
    }

    /** 把某一档还「活」着的层记从原位取出，改挂「沉·」牌交回（由调用方沉到末位） */
    private List<String> detachStageLayers(TSecreDispFlow r, int stage) {
        List<String> sunk = new ArrayList<String>();
        String content = r.getContent();
        if (content == null || content.isEmpty()) {
            return sunk;
        }
        String liveTag = STAGE_NAMES[stage];
        StringBuilder keep = new StringBuilder();
        for (String line : content.split(LINE_SEP)) {
            if (line.trim().isEmpty()) {
                continue;
            }
            if (liveTag.equals(tagOf(line))) {
                sunk.add(LAYER_L + TAG_SUNK_PREFIX + liveTag + LAYER_R + line.substring(line.indexOf(LAYER_R) + 1));
            } else {
                if (keep.length() > 0) {
                    keep.append(LINE_SEP);
                }
                keep.append(line);
            }
        }
        r.setContent(keep.toString());
        return sunk;
    }

    private static String now() {
        return new SimpleDateFormat(LAYER_TIME_FMT).format(new Date());
    }

    /** 一句话进层记：换行压成空格，保证一层一行，段数与受理簿一笔一笔点得拢 */
    private static String normNote(String remark) {
        if (remark == null) {
            return "";
        }
        return remark.replace('\r', ' ').replace('\n', ' ').trim();
    }

    /** 末一个非空行；没有则 null */
    private static String tailLine(String content) {
        if (content == null) {
            return null;
        }
        String[] lines = content.split(LINE_SEP);
        for (int i = lines.length - 1; i >= 0; i--) {
            if (!lines[i].trim().isEmpty()) {
                return lines[i];
            }
        }
        return null;
    }

    /** 层记行的牌（【】之间那一截）；不是层记行返回 null */
    private static String tagOf(String line) {
        if (line == null || line.length() < 2 || line.charAt(0) != LAYER_L) {
            return null;
        }
        int r = line.indexOf(LAYER_R);
        return r < 0 ? null : line.substring(1, r);
    }

    /** 层记行的话（｜之后那一截）；没有则 null */
    private static String noteOf(String line) {
        int p = line == null ? -1 : line.indexOf(LAYER_SEP);
        return p < 0 ? null : line.substring(p + LAYER_SEP.length());
    }

    private static int stageOf(TSecreDispFlow r) {
        Integer s = r.getStage();
        if (s == null) {
            return STAGE_FIRST;
        }
        return Math.max(STAGE_FIRST, Math.min(STAGE_MAX, s));
    }

    private static int statusOf(TSecreDispFlow r) {
        Integer s = r.getStatus();
        return s == null ? STATUS_PENDING : s;
    }

}
