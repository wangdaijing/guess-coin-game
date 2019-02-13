package io.nuls.contract.guess.coin.model;

import io.nuls.contract.sdk.Address;
import io.nuls.contract.sdk.Msg;
import io.nuls.contract.sdk.Utils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author: wangdaijing
 * @Time: 2019-01-16 16:15
 * @Description:
 */
public class GameTable {

    /**
     * 赌桌id
     */
    private Long id;

    /**
     * 庄家
     */
    private Address banker;

    /**
     * 谜底hash值
     */
    private String riddleHash;

    /**
     * 谜底明文
     */
    private Long riddle;


    /**
     * 正确答案
     */
    private Integer answer;

    /**
     * 接受的赌注总额
     */
    private BigInteger maxWagerTotal;


    /**
     * 收盘的区块高度
     * 到达此高度时可调用庄家收盘接口
     */
    private Long endBlockHeight;

    /**
     * 状态
     */
    private Integer status;


    private List<Player> playerList;


    public GameTable(Long id, Address banker, String riddleHash, BigInteger maxWagerTotal, Long endBlockHeight) {
        this.id = id;
        this.banker = banker;
        this.riddleHash = riddleHash;
        this.maxWagerTotal = maxWagerTotal;
        this.endBlockHeight = endBlockHeight;
        this.playerList = new ArrayList<>();
        this.status = GameStatus.WATING_JOIN;
    }


    /**
     * 玩家加入游戏
     * 会判定是否已超过庄家可用赌注上限
     *
     * @param player
     * @return
     */
    public void join(Player player) {
        BigInteger playerTotalWager = Msg.value();
        for (Player otherPlayer : playerList) {
            Utils.require(playerTotalWager.abs().compareTo(maxWagerTotal) < 1, "赌注大于最大可下注数量");
            //计算其他玩家下注的数量，考虑到玩家猜测的结果不一致，会出现有输有赢的情况，所以计算已使用的下注数量时，应将选择对立面的赌注金额抵消掉
            if (otherPlayer.getAnswer() == player.getAnswer()) {
                playerTotalWager = playerTotalWager.add(otherPlayer.getWager());
            } else {
                playerTotalWager = playerTotalWager.subtract(otherPlayer.getWager());
            }
        }
        playerList.add(player);
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Address getBanker() {
        return banker;
    }

    public void setBanker(Address banker) {
        this.banker = banker;
    }

    public String getRiddleHash() {
        return riddleHash;
    }

    public void setRiddleHash(String riddleHash) {
        this.riddleHash = riddleHash;
    }

    public Long getRiddle() {
        return riddle;
    }

    public void setRiddle(Long riddle) {
        this.riddle = riddle;
    }

    public BigInteger getMaxWagerTotal() {
        return maxWagerTotal;
    }

    public void setMaxWagerTotal(BigInteger maxWagerTotal) {
        this.maxWagerTotal = maxWagerTotal;
    }

    public Long getEndBlockHeight() {
        return endBlockHeight;
    }

    public void setEndBlockHeight(Long endBlockHeight) {
        this.endBlockHeight = endBlockHeight;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public List<Player> getPlayerList() {
        return playerList;
    }

    public int getAnswer() {
        return answer;
    }

    public void setAnswer(int answer) {
        this.answer = answer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GameTable)) return false;

        GameTable gameTable = (GameTable) o;

        if (answer != gameTable.answer) return false;
        if (id != null ? !id.equals(gameTable.id) : gameTable.id != null) return false;
        if (banker != null ? !banker.equals(gameTable.banker) : gameTable.banker != null) return false;
        if (riddleHash != null ? !riddleHash.equals(gameTable.riddleHash) : gameTable.riddleHash != null) return false;
        if (riddle != null ? !riddle.equals(gameTable.riddle) : gameTable.riddle != null) return false;
        if (maxWagerTotal != null ? !maxWagerTotal.equals(gameTable.maxWagerTotal) : gameTable.maxWagerTotal != null)
            return false;
        if (endBlockHeight != null ? !endBlockHeight.equals(gameTable.endBlockHeight) : gameTable.endBlockHeight != null)
            return false;
        if (status != null ? !status.equals(gameTable.status) : gameTable.status != null) return false;
        return playerList != null ? playerList.equals(gameTable.playerList) : gameTable.playerList == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (banker != null ? banker.hashCode() : 0);
        result = 31 * result + (riddleHash != null ? riddleHash.hashCode() : 0);
        result = 31 * result + (riddle != null ? riddle.hashCode() : 0);
        result = 31 * result + (int) answer;
        result = 31 * result + (maxWagerTotal != null ? maxWagerTotal.hashCode() : 0);
        result = 31 * result + (endBlockHeight != null ? endBlockHeight.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (playerList != null ? playerList.hashCode() : 0);
        return result;
    }


    @Override
    public String toString() {
        return new StringBuilder("{")
                .append("\"id\":")
                .append(id)
                .append(",\"banker\":")
                .append(banker)
                .append(",\"riddleHash\":\"")
                .append(riddleHash).append('\"')
                .append(",\"riddle\":")
                .append(riddle)
                .append(",\"answer\":")
                .append(answer)
                .append(",\"maxWagerTotal\":")
                .append(maxWagerTotal)
                .append(",\"endBlockHeight\":")
                .append(endBlockHeight)
                .append(",\"status\":")
                .append(status)
                .append(",\"playerList\":")
                .append(playerList)
                .append('}').toString();
    }
}
