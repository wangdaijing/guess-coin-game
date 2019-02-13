package io.nuls.contract.guess.coin.model;

/**
 * @Author: wangdaijing
 * @Time: 2019-01-16 16:40
 */
public final class GameStatus {

    /**
     * 等待玩家加入
     */
    public static final Integer WATING_JOIN = 1;

    /**
     * 已结束
     */
    public static final Integer DONE = 2;

    /**
     * 庄家出局
     */
    public static final Integer BANKER_OUT = 3;


}
