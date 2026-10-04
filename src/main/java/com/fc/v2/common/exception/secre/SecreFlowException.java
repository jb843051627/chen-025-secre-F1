package com.fc.v2.common.exception.secre;

/**
 * 载体处置流转违规异常：哪一条规矩挡住了，话里就点名哪一条
 * （缺哪一样、隔档、闸门未过、已封住、占着未走完那一格……）
 *
 * @author fuce
 * @date 2026-10-03
 */
public class SecreFlowException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SecreFlowException() {
    }

    public SecreFlowException(String message) {
        super(message);
    }
}
