package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.List;

/**
 * 流转单「一关一记」的层叠底稿（纯函数，不碰库）。
 *
 * 行式：每层一行，形如  【档2】2026-10-03 09:30:00｜推进｜按语
 * 整段分上下两截：上截是活层（这一回走的），下截是沉层（被收档落下的按语，
 * 冠【沉】，沉到末位）。新层一律叠在活层尾、沉层前，沉层永远压在末位。
 * 从末一档倒着数回头一档，行数就是段数，要与当日受理簿一笔一笔合得拢；
 * 单拎一行出来也站得住（档次、时刻、动作、按语都在这一行里）。
 *
 * @author fuce
 * @date 2026-10-03
 */
final class SecreFlowLayers {

    /** 沉底层的冠头 */
    static final String SUNK_MARK = "【沉】";

    private static final String HEAD_OPEN = "【档";
    private static final String HEAD_CLOSE = "】";
    private static final String SEP = "｜";

    private SecreFlowLayers() {
    }

    /** 攒一层：【档k】时刻｜动作｜按语，行尾带换行；按语里的换行先抹平，一层就是一行 */
    static String layer(int stage, String ts, String action, String note) {
        return HEAD_OPEN + stage + HEAD_CLOSE + ts + SEP + action + SEP + (note == null ? "" : flat(note)) + "\n";
    }

    /** 新层叠上来：活层段尾、沉层段前，沉层始终压在末位 */
    static String appendLive(String content, String layer) {
        String[] parts = sections(content);
        return parts[0] + layer + parts[1];
    }

    /**
     * 收档：把当前这一档（档k）落在末位的活层起出来、冠【沉】挪到沉层段末位。
     * 返回两段：[0] 活层段（重走这一档从这里重新攒起），[1] 沉层段。
     * 回退那一层由调用方叠在活层段尾、沉层段前。
     */
    static String[] sink(String content, int stage) {
        String[] parts = sections(content);
        List<String> live = split(parts[0]);
        List<String> sunk = split(parts[1]);
        List<String> toSink = new ArrayList<String>();
        for (int i = live.size() - 1; i >= 0 && stageOf(live.get(i)) == stage; i--) {
            toSink.add(0, live.remove(i));
        }
        for (String line : toSink) {
            sunk.add(SUNK_MARK + line);
        }
        return new String[]{join(live), join(sunk)};
    }

    /** 拆成活层段、沉层段两截，各自保持原有先后 */
    static String[] sections(String content) {
        List<String> live = new ArrayList<String>();
        List<String> sunk = new ArrayList<String>();
        for (String line : split(content)) {
            (isSunk(line) ? sunk : live).add(line);
        }
        return new String[]{join(live), join(sunk)};
    }

    /** 把整段底稿拆成一行一行（空行不算一层） */
    static List<String> split(String content) {
        List<String> lines = new ArrayList<String>();
        if (content == null || content.isEmpty()) {
            return lines;
        }
        for (String line : content.split("\n")) {
            if (line != null && !line.trim().isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }

    /** 这一行是不是沉底层 */
    static boolean isSunk(String line) {
        return line != null && line.startsWith(SUNK_MARK);
    }

    /** 这一行落在哪一档；认不出返回 -1 */
    static int stageOf(String line) {
        if (line == null) {
            return -1;
        }
        String s = isSunk(line) ? line.substring(SUNK_MARK.length()) : line;
        if (!s.startsWith(HEAD_OPEN)) {
            return -1;
        }
        int end = s.indexOf(HEAD_CLOSE, HEAD_OPEN.length());
        if (end < 0) {
            return -1;
        }
        try {
            return Integer.parseInt(s.substring(HEAD_OPEN.length(), end));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 这一行的动作（第二个字段） */
    static String actionOf(String line) {
        if (line == null) {
            return "";
        }
        int first = line.indexOf(SEP);
        if (first < 0) {
            return "";
        }
        int second = line.indexOf(SEP, first + SEP.length());
        if (second < 0) {
            return "";
        }
        return line.substring(first + SEP.length(), second);
    }

    /** 这一行的按语（第二个分隔符之后的全部，按语里再带分隔符也不拆；行尾的结构换行不算按语） */
    static String noteOf(String line) {
        if (line == null) {
            return "";
        }
        int first = line.indexOf(SEP);
        if (first < 0) {
            return "";
        }
        int second = line.indexOf(SEP, first + SEP.length());
        if (second < 0) {
            return "";
        }
        String note = line.substring(second + SEP.length());
        if (note.endsWith("\n")) {
            note = note.substring(0, note.length() - 1);
        }
        if (note.endsWith("\r")) {
            note = note.substring(0, note.length() - 1);
        }
        return note;
    }

    /** 段数：从末一档倒着数回头一档，一行一段，与受理簿一笔一笔对 */
    static int count(String content) {
        return split(content).size();
    }

    /**
     * 同一档第二遍递上来的辨认：末一层活层与这一笔同档、同动作、同一句，
     * 就算同一笔的回头递，不另叠一层。两个入口各递各的（句不同）不在此列。
     */
    static boolean isDuplicate(String content, int stage, String action, String note) {
        List<String> lines = split(content);
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            if (isSunk(line)) {
                continue;
            }
            return stageOf(line) == stage
                    && actionOf(line).equals(action)
                    && noteOf(line).equals(note == null ? "" : flat(note));
        }
        return false;
    }

    private static String join(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /** 按语抹平：换行抹成空格，一层就是一行 */
    private static String flat(String note) {
        return note.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
