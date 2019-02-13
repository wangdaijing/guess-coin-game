package io.nuls.contract.guess.coin.model;

import io.nuls.contract.sdk.Address;
import io.nuls.contract.sdk.Event;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * @Author: wangdaijing
 * @Time: 2019-02-12 17:30
 */
public class TransferEvent implements Event {

    private BigInteger systemCompensation = BigInteger.ZERO;

    private BigInteger contractFee = BigInteger.ZERO;

    private BigInteger bankerCompensation = BigInteger.ZERO;

    private Map<Address,BigInteger> playerEarnings = new HashMap<>();

    public TransferEvent(){
    }

    public void putPlayer(Address player,BigInteger earnings){
        playerEarnings.put(player,earnings);
    }

    @Override
    public String toString() {
        return new StringBuilder("{")
                .append("\"systemCompensation\":")
                .append(systemCompensation)
                .append(",\"contractFee\":")
                .append(contractFee)
                .append(",\"bankerCompensation\":")
                .append(bankerCompensation)
                .append(",\"playerEarnings\":")
                .append(playerEarnings)
                .append('}').toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransferEvent)) return false;

        TransferEvent that = (TransferEvent) o;

        if (systemCompensation != null ? !systemCompensation.equals(that.systemCompensation) : that.systemCompensation != null)
            return false;
        if (contractFee != null ? !contractFee.equals(that.contractFee) : that.contractFee != null) return false;
        if (bankerCompensation != null ? !bankerCompensation.equals(that.bankerCompensation) : that.bankerCompensation != null)
            return false;
        return playerEarnings != null ? playerEarnings.equals(that.playerEarnings) : that.playerEarnings == null;
    }

    @Override
    public int hashCode() {
        int result = systemCompensation != null ? systemCompensation.hashCode() : 0;
        result = 31 * result + (contractFee != null ? contractFee.hashCode() : 0);
        result = 31 * result + (bankerCompensation != null ? bankerCompensation.hashCode() : 0);
        result = 31 * result + (playerEarnings != null ? playerEarnings.hashCode() : 0);
        return result;
    }

    public BigInteger getSystemCompensation() {
        return systemCompensation;
    }

    public void setSystemCompensation(BigInteger systemCompensation) {
        this.systemCompensation = systemCompensation;
    }

    public BigInteger getContractFee() {
        return contractFee;
    }

    public void setContractFee(BigInteger contractFee) {
        this.contractFee = contractFee;
    }

    public BigInteger getBankerCompensation() {
        return bankerCompensation;
    }

    public void setBankerCompensation(BigInteger bankerCompensation) {
        this.bankerCompensation = bankerCompensation;
    }

    public Map<Address, BigInteger> getPlayerEarnings() {
        return playerEarnings;
    }

    public void setPlayerEarnings(Map<Address, BigInteger> playerEarnings) {
        this.playerEarnings = playerEarnings;
    }
}
