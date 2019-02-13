package io.nuls.contract.guess.coin.model;


import io.nuls.contract.sdk.Address;
import java.math.BigInteger;

/**
 * @Author: wangdaijing
 * @Time: 2019-01-16 17:14
 * @Description: 玩家
 */
public class Player {

    private Address addres;

    private BigInteger wager;

    private int answer;

    public Player(Address address, BigInteger wager, int answer){
        this.addres = address;
        this.wager = wager;
        this.answer = answer;
    }

    public Address getAddres() {
        return addres;
    }

    public void setAddres(Address addres) {
        this.addres = addres;
    }

    public BigInteger getWager() {
        return wager;
    }

    public void setWager(BigInteger wager) {
        this.wager = wager;
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
        if (!(o instanceof Player)) return false;

        Player player = (Player) o;

        if (answer != player.answer) return false;
        if (addres != null ? !addres.equals(player.addres) : player.addres != null) return false;
        return wager != null ? wager.equals(player.wager) : player.wager == null;
    }

    @Override
    public int hashCode() {
        int result = addres != null ? addres.hashCode() : 0;
        result = 31 * result + (wager != null ? wager.hashCode() : 0);
        result = 31 * result + (int) answer;
        return result;
    }

    @Override
    public String toString() {
        return new StringBuilder("{")
                .append("\"addres\":")
                .append(addres)
                .append(",\"wager\":")
                .append(wager)
                .append(",\"answer\":")
                .append(answer)
                .append('}').toString();
    }
}
