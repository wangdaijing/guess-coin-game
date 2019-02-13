package io.nuls.contract.guess.coin;

import io.nuls.contract.guess.coin.model.GameStatus;
import io.nuls.contract.guess.coin.model.GameTable;
import io.nuls.contract.guess.coin.model.Player;
import io.nuls.contract.guess.coin.model.TransferEvent;
import io.nuls.contract.sdk.*;
import io.nuls.contract.sdk.annotation.Payable;
import io.nuls.contract.sdk.annotation.Required;
import io.nuls.contract.sdk.annotation.View;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/**
 * @Author: wangdaijing
 * @Time: 2019-01-16 16:14
 * @Description: 坐庄猜硬币游戏
 * 规则
 * 1.玩家和庄家对赌，庄家可由任意玩家扮演
 * 2.庄家生成随机数，通过随机数的奇偶性来作为比对的标准，奇数为正面，偶数为背面，玩家通过调用不同的函数确定下注的方向。
 * 3.首先庄家开始游戏，线下生成随机数、通过SHA3-256算出随机数的散列值，决定本次的赌本大小及本轮截止块数，将随机数的散列值和赌本资产、截止下注的块数上传到合约中。赌本大小决定本次可接受多少赌注下注。
 * 4.等待玩家参与。
 * 5.玩家加入游戏，调用正反面对应的函数，并转入赌注资产到合约中。
 * 6.等待截止块数。
 * 7.庄家上传随机数明文，合约计算明文的SHA3-256的散列值，与3中的散列值进行比对，若比对一致触发开奖，比对玩家是否猜中。
 * 8.若超过截止块数N个块数后，还未触发开奖，任一玩家触发瓜分庄家押金接口将庄家押金按照各个玩家下注比例进行瓜分。
 */
public class GuessCoinContract implements Contract {

    /**
     * 系统提成地址
     */
    private Address systemAddress = new Address("TTavg5s8g7aQfG7iBk5MhdPkM8t5fw2N");

    /**
     * 合约创建人
     */
    private final Address creatorAddress;

    /**
     * 同时最大可以进行的游戏数量
     */
    private final Integer maxBanker;

    /**
     * 最小下注金额，0.1 NULS
     * 合约不接受小于此金额的下注
     */
    private static final BigInteger MIN_WAGER = BigInteger.valueOf(1L);

    /**
     * 当赌局出现和局(庄家不输不赢）时，触发庄家补偿机制，庄家从赢家收益从抽取10%的利润
     */
    public static final BigDecimal BANKER_COMPENSATION = BigDecimal.valueOf(0.1);

    /**
     * 系统抽成
     */
    public static final BigDecimal SYSTEM_COMPENSATION = BigDecimal.valueOf(0.01);


    /**
     * 默认收盘超时块数
     */
    public static final Integer DEFAULT_WAITING_END_BLOCK_COUNT = 360;


    /**
     * 手续费
     * 合约创建人收取
     */
    private final BigDecimal contractFee;

    /**
     * 等待庄家收盘的区块数量
     * 超过（收盘区块高度+等待庄家收盘的区块数量）的区块高度后，玩家可举报庄家违规，判定成功后所有玩家瓜分庄家赌注
     */
    private final Integer waitingEndBlockCount;

    Map<Long, GameTable> tableList = new LinkedHashMap<>();

    /**
     * 构建一个猜硬币正反的合约
     *
     * @param contractFee         合约创建者收取的手续费 百分比，0.1为10%
     * @param maxBanker           同时可以有多少个庄家
     * @param watingEndBlockCount 等待庄家收盘的区块数量
     */
    public GuessCoinContract(@Required Float contractFee, final Integer maxBanker, final Integer watingEndBlockCount) {
        if (null == contractFee) {
            this.contractFee = BigDecimal.ZERO;
        } else {
            Utils.require(contractFee >= 0, "fee not bee minus");
            this.contractFee = BigDecimal.valueOf(contractFee);
        }
        if (null == maxBanker) {
            this.maxBanker = Integer.MAX_VALUE;
        } else {
            Utils.require(maxBanker >= 1, "maxBanker not bee minus");
            this.maxBanker = maxBanker;
        }
        if (null == watingEndBlockCount) {
            this.waitingEndBlockCount = DEFAULT_WAITING_END_BLOCK_COUNT;
        } else {
            Utils.require(watingEndBlockCount >= 1, "waitingEndBlockCount not bee minus");
            this.waitingEndBlockCount = watingEndBlockCount;
        }
        this.creatorAddress = Msg.sender();
    }

    /**
     * 扮演庄家开盘口
     *
     * @param riddleHash      谜底hash值 SHA3(明文) 明文应该为一个数字，最终通过判断数字的奇偶性来确定是硬币的正面还是背面
     *                        当<gameBlockNumber>个区块后，庄家必须公布谜底的明文，合约会将明文进行SHA3(明文)运算，然后和riddleHash进行比对。
     *                        比对结果一致将对玩家的赌注进行清算，比对不一致将无法结束游戏。
     *                        如果N块后庄家任未完成清算，玩家可举报庄家作弊，将按照下注比例瓜分庄家的押金，强制结束游戏
     * @param gameBlockNumber 游戏开始到谜底揭晓持续的区块数
     *                        如果当前块高度是10，gameBlockNumber是5，那么在区块高度15时，庄家将公布谜底
     * @return
     */
    @Payable
    public String createGameTable(@Required String riddleHash,@Required Integer gameBlockNumber) {
        Long id = tableList.size() + 1L;
        Tools.requireNonNull(riddleHash, "riddle hash can't null");
        Tools.requireNonNull(gameBlockNumber, "gameBlockNumber can't null");
        int activeTableCount = 0;
        for (GameTable gt : tableList.values()){
            if(gt.getStatus().equals(GameStatus.WATING_JOIN)){
                activeTableCount++;
            }
        }
        Utils.require(activeTableCount < maxBanker,"game table number to limit");
        //庄家支付的押金为总的可接受玩家的赌注金额
        BigInteger maxWagerTotal = getWagerForPaying();
        Utils.require(maxWagerTotal != null && maxWagerTotal.max(BigInteger.ZERO).equals(maxWagerTotal), "must paying wager");
        //从最新的高度开始计算庄家公布谜底的高度
        Long endBlockHeight = Block.newestBlockHeader().getHeight() + gameBlockNumber;
        GameTable gt = new GameTable(id, Msg.sender(), riddleHash, maxWagerTotal, endBlockHeight);
        tableList.put(id, gt);
        return gt.toString();
    }

    /**
     * 获取等待加入的游戏桌列表
     *
     * @return
     */
    @View
    public String getGameTableList() {
        StringBuilder res = new StringBuilder();
        for (Map.Entry<Long, GameTable> entry : tableList.entrySet()) {
            if (res.length() > 0) {
                res.append(",");
            }
            if (entry.getValue().getStatus().equals(GameStatus.WATING_JOIN)) {
                res.append(entry.getValue().toString());
            }
        }
        return res.insert(0, "[").append("]").toString();
    }

    /**
     * 猜正面
     *
     * @param tableId
     * @return
     */
    @Payable
    public String guessFront(@Required Long tableId) {
        return join(tableId, 1).toString();
    }

    /**
     * 猜背面
     *
     * @param tableId
     * @return
     */
    @Payable
    public String guessBack(@Required Long tableId) {
        return join(tableId, 0).toString();
    }

    /**
     * 加入游戏
     * 加入游戏者必须押一定的赌金，赌金必须小于等于 剩余可接受赌注金额
     *
     * @param tableId
     * @param answer
     * @return
     */
    private GameTable join(Long tableId, int answer) {
        GameTable gt = checkGameTableAndGet(tableId, GameStatus.WATING_JOIN);
        BigInteger wager = getWagerForPaying();
        Utils.require(wager != null && wager.max(BigInteger.ZERO).equals(wager), "must paying wager");
        Utils.require(gt.getEndBlockHeight() - 6 > Block.number(), "游戏已进入开奖环节，不能参加");
        gt.join(new Player(Msg.sender(), getWagerForPaying(), answer));
        return gt;
    }


    /**
     * 举报庄家作弊
     * 判定庄家作弊后，将自动按照投注比例瓜分庄家押金
     * 判定标准为 达到开奖高度后的<waitingEndBlockCount>块后，庄家任未调用done方法公开谜底
     * @param tableId
     * @return
     */
    public String bankerOut(@Required Long tableId){
        Tools.requireNonNull(tableId, "table id can't null");
        GameTable gt = checkGameTableAndGet(tableId,GameStatus.WATING_JOIN);
        //判定是否达到可以举报的块高度
        Utils.require(gt.getEndBlockHeight() + waitingEndBlockCount <= Block.number(), "还没有到达庄家公开谜底的逾期高度");
        TransferEvent event = new TransferEvent();
        Address banker = gt.getBanker();
        BigInteger maxWagerTotal = gt.getMaxWagerTotal();
        //判定如果本轮没有玩家参与，则把所有押金退回给庄家
        if(gt.getPlayerList().isEmpty()){
            banker.transfer(maxWagerTotal);
            gt.setStatus(GameStatus.DONE);
            event.putPlayer(banker, BigInteger.ZERO);
            Utils.emit(event);
            return gt.toString();
        }
        //玩家收益百分比
        BigDecimal winEarningsPer = BigDecimal.ONE
                //减掉系统提成
                .subtract(GuessCoinContract.SYSTEM_COMPENSATION)
                //减掉合约手续费
                .subtract(contractFee);
        //合约创建者手续费收益
        BigInteger contractFeeTotal = BigInteger.ZERO;
        //系统提成收益
        BigInteger systemCompensation = BigInteger.ZERO;
        //庄家支付的押金
        BigInteger bankerWager = maxWagerTotal;
        for (Player player : gt.getPlayerList()){
            //从庄家支付的押金中减掉赔偿给玩家的数量
            bankerWager = bankerWager.subtract(player.getWager());
            //然后将这部分赌注分配给玩家、合约手续费、系统提成
            BigDecimal playerWager = new BigDecimal(player.getWager());
            //玩家应得收益
            BigInteger winAmount = playerWager.multiply(winEarningsPer).toBigInteger();
            event.putPlayer(player.getAddres(), winAmount);
            player.setWager(player.getWager().add(winAmount));
            //合约手续费
            BigInteger contractFeeNumber = BigInteger.ZERO;
            if (!contractFee.equals(BigDecimal.ZERO)) {
                contractFeeNumber = playerWager.multiply(contractFee).toBigInteger();
                contractFeeTotal = contractFeeTotal.add(contractFeeNumber);
            }
            //系统收益
            systemCompensation = systemCompensation.add(playerWager.toBigInteger().subtract(winAmount).subtract(contractFeeNumber));
            //转账到玩家账户 押金+赢的数量
            player.getAddres().transfer(player.getWager());
        }
        //判定赔给玩家后押金是否有剩余，如果有剩余退回给庄家
        if (bankerWager.compareTo(BigInteger.ZERO) == 1){
            banker.transfer(bankerWager);
        }
        if (systemCompensation.compareTo(BigInteger.ZERO) == 1) {
            systemAddress.transfer(systemCompensation);
            event.setSystemCompensation(systemCompensation);
        }
//        //支付合约手续费
        if (contractFeeTotal.compareTo(BigInteger.ZERO) == 1) {
            creatorAddress.transfer(contractFeeTotal);
            event.setContractFee(contractFeeTotal);
        }
        Utils.emit(event);
        gt.setStatus(GameStatus.BANKER_OUT);
        return gt.toString();
    }

    /**
     * 庄家公开谜底
     *
     * @param tableId
     * @param riddle  谜底的明文
     */
    public String done(@Required  Long tableId,@Required Long riddle) {
        Tools.requireNonNull(tableId, "table id can't null");
        Tools.requireNonNull(riddle, "riddle can't null");
        GameTable gt = checkGameTableAndGet(tableId, GameStatus.WATING_JOIN);
        Utils.require(gt.getEndBlockHeight() <= Block.number(), "还没有到达开奖环节的块高度");
        String hash = Utils.sha3(String.valueOf(riddle));
        //比对庄家提供的谜底与开盘时提供的是否一致
        Utils.require(hash.equals(gt.getRiddleHash()), "riddle and riddleHash not match");
        TransferEvent event = new TransferEvent();
        List<Player> playerList = gt.getPlayerList();
        Address banker = gt.getBanker();
        BigInteger maxWagerTotal = gt.getMaxWagerTotal();
        //如果没有玩家参与，退回庄家押金，结束本轮游戏
        if (playerList.isEmpty()) {
            banker.transfer(maxWagerTotal);
            gt.setStatus(GameStatus.DONE);
            event.putPlayer(banker, BigInteger.ZERO);
            Utils.emit(event);
            return gt.toString();
        }

        //通过判断riddle的奇偶性，转换为正面还背面 奇数为正面，偶数为背面
        //通过取2的模，0为偶数，1为奇数
        int anwser = (int) (riddle % 2);
        //先预计算一次本轮庄家的收益情况
        //存储庄家赢的数量
        BigInteger bankerWinNumber = BigInteger.ZERO;
        //玩家总下注数量
        BigInteger playerWagerTotal = BigInteger.ZERO;
        for (Player player : playerList) {
            playerWagerTotal = playerWagerTotal.add(player.getWager());
            //如果玩家猜中了，庄家收益减少相应数量
            if (player.getAnswer() == anwser) {
                bankerWinNumber = bankerWinNumber.subtract(player.getWager());
            } else {
                //玩家猜错了，庄家收益增加相应数量
                bankerWinNumber = bankerWinNumber.add(player.getWager());
            }
        }
        //玩家收益百分比
        BigDecimal winEarningsPer = BigDecimal.ONE
                //减掉系统提成
                .subtract(GuessCoinContract.SYSTEM_COMPENSATION)
                //减掉合约手续费
                .subtract(contractFee);
        //保存是否触发庄家补偿
        boolean bankerCompensationFlag = false;
        //如果本轮庄家收益为0，触发庄家补偿机制，从赢家收益中提成10%
        if (bankerWinNumber.equals(BigInteger.ZERO)) {
            winEarningsPer = winEarningsPer.subtract(GuessCoinContract.BANKER_COMPENSATION);
            bankerCompensationFlag = true;
        }
        //开始清算赌资
        //1.遍历玩家列表，猜中的，首先庄家资产中划转对应数量出来，分别计算玩家赢的数量转入到玩家资产中，计算出合约手续费到累计变量中，剩余数量累计到系统提成变量中
        //1.1.如果本轮庄家收益为0，需要额外保存庄家补偿
        //2.猜错的，从玩家赌注中转入庄家赌注
        //3.从庄家赌注中减去庄家押金部分，如果大于0则证明庄家由盈利，盈利部分再计算一次合约手续费提成和系统提成，剩余部分转入庄家地址
        //4.将合约收付费和系统提成转入对应地址
        //庄家支付的押金总额
        BigInteger bankerWager = maxWagerTotal;
        //合约创建者手续费收益
        BigInteger contractFeeTotal = BigInteger.ZERO;
        //系统提成收益
        BigInteger systemCompensation = BigInteger.ZERO;
        //庄家补偿金额
        BigInteger bankerCompensation = BigInteger.ZERO;
        for (Player player : playerList) {
            //玩家获胜
            if (player.getAnswer() == anwser) {
                //首先从庄家押金中减去对应数量的赌注
                bankerWager = bankerWager.subtract(player.getWager());
                //然后将这部分赌注分配给玩家、合约手续费、系统提成、庄家补偿
                BigDecimal playerWager = new BigDecimal(player.getWager());
                //玩家应得收益
                BigInteger winAmount = playerWager.multiply(winEarningsPer).toBigInteger();
                event.putPlayer(player.getAddres(), winAmount);
                player.setWager(player.getWager().add(winAmount));
                //合约手续费
                BigInteger contractFeeNumber = BigInteger.ZERO;
                if (!contractFee.equals(BigDecimal.ZERO)) {
                    contractFeeNumber = playerWager.multiply(contractFee).toBigInteger();
                    contractFeeTotal = contractFeeTotal.add(contractFeeNumber);
                }
                //庄家补偿数量
                BigInteger bankerCompensationNumber = BigInteger.ZERO;
                //判断是否触发了庄家补偿策略
                if (bankerCompensationFlag) {
                    bankerCompensationNumber = playerWager.multiply(GuessCoinContract.BANKER_COMPENSATION).toBigInteger();
                    bankerCompensation = bankerCompensation.add(bankerCompensationNumber);
                }
                //系统收益
                systemCompensation = systemCompensation.add(playerWager.toBigInteger().subtract(winAmount).subtract(contractFeeNumber).subtract(bankerCompensationNumber));
                //转账到玩家账户 押金+赢的数量
                player.getAddres().transfer(player.getWager());
            } else {
                //庄家获胜
                //将玩家赌注划转到庄家赌注中
                bankerWager = bankerWager.add(player.getWager());
                event.putPlayer(player.getAddres(), player.getWager().negate());
                player.setWager(BigInteger.ZERO);
            }
        }
        //计算庄家应该支付的各种手续费，庄家的金额 = 庄家赌注 - （庄家赌注 - 庄家押金） * 系统提成 - （庄家赌注 - 庄家押金） * 合约手续费 + 庄家补偿
        //判断庄家在本轮是否赔钱
        //小于maxWagerTotal时，庄家赔钱
        //等于maxWagerTotal时，庄家和局，触发庄家提成
        //大于maxWagerTotal时，庄家有收益，需要对收益计算手续费
        switch (bankerWager.compareTo(maxWagerTotal)) {
            case -1: {
                //判断押金是否全部赔完
                if (bankerWager.compareTo(BigInteger.ZERO) == 1) {
                    banker.transfer(bankerWager);
                    event.putPlayer(banker,bankerWager.subtract(maxWagerTotal));
                }
                break;
            }
            case 0: {
                //支付庄家补偿和退回押金
                banker.transfer(bankerCompensation.add(maxWagerTotal));
                event.setBankerCompensation(bankerCompensation);
                event.putPlayer(banker,BigInteger.ZERO);
                break;
            }
            case 1: {
                //计算出庄家从玩家手上赢得的金额
                BigDecimal bankerEarnings = new BigDecimal(bankerWager.subtract(maxWagerTotal));
                //减去相关手续费后，庄家能获取的资金百分比
                BigDecimal bankerEarningsPer = BigDecimal.ONE.subtract(contractFee).subtract(GuessCoinContract.SYSTEM_COMPENSATION);
                //实际需要转给庄家的金额
                BigInteger bankerRealEarnings = bankerEarnings.multiply(bankerEarningsPer).toBigInteger();
                BigInteger contractFeeNumber = BigInteger.ZERO;
                if (!contractFee.equals(BigDecimal.ZERO)) {
                    contractFeeNumber = bankerEarnings.multiply(contractFee).toBigInteger();
                    contractFeeTotal = contractFeeTotal.add(contractFeeNumber);
                }
                systemCompensation = systemCompensation.add(bankerEarnings.toBigInteger().subtract(bankerRealEarnings).subtract(contractFeeNumber));
                //将庄家的押金及收益转到庄家账户中
                banker.transfer(maxWagerTotal.add(bankerRealEarnings));
                event.putPlayer(banker,bankerRealEarnings);
            }
            default:
        }
//        if( == -1){
//            //如果赔完玩家的赌注以后押金还有剩余，需要退回到庄家账户
//
//        }else{
//            //计算出庄家从玩家手上赢得的金额
//            BigDecimal bankerEarnings = new BigDecimal(bankerWager.subtract(maxWagerTotal));
//            //减去相关手续费后，庄家能获取的资金百分比
//            BigDecimal bankerEarningsPer = BigDecimal.ONE.subtract(contractFee).subtract(GuessCoinContract.SYSTEM_COMPENSATION);
//            //实际需要转给庄家的金额
//            BigInteger bankerRealEarnings = bankerEarnings.multiply(bankerEarningsPer).toBigInteger();
//            BigInteger contractFeeNumber = BigInteger.ZERO;
//            if(!contractFee.equals(BigDecimal.ZERO)){
//                contractFeeNumber = bankerEarnings.multiply(contractFee).toBigInteger();
//                contractFeeTotal = contractFeeTotal.add(contractFeeNumber);
//            }
//            systemCompensation = systemCompensation.add(bankerEarnings.toBigInteger().subtract(bankerRealEarnings).subtract(contractFeeNumber));
//            //将庄家的押金及收益转到庄家账户中
//            banker.transfer(maxWagerTotal.add(bankerRealEarnings));
//        }
        //支付系统提成
//        Utils.emit(new TestEvent(systemCompensation.intValue()));
//        Utils.emit(new TestEvent(contractFeeTotal.intValue()));
        if (systemCompensation.compareTo(BigInteger.ZERO) == 1) {
            systemAddress.transfer(systemCompensation);
            event.setSystemCompensation(systemCompensation);
        }
//        //支付合约手续费
        if (contractFeeTotal.compareTo(BigInteger.ZERO) == 1) {
            creatorAddress.transfer(contractFeeTotal);
            event.setContractFee(contractFeeTotal);
        }
        Utils.emit(event);
        //更新合约状态
        gt.setStatus(GameStatus.DONE);
        gt.setRiddle(riddle);
        gt.setAnswer(anwser);
        return gt.toString();
    }

    /**
     * 通过table id 获取 玩桌 并检查玩桌状态是否是预期状态
     *
     * @param tableId
     * @param matchStatus
     * @return
     */
    private GameTable checkGameTableAndGet(Long tableId, Integer matchStatus) {
        Utils.require(tableList.containsKey(tableId), "table id error");
        GameTable gt = tableList.get(tableId);
        Utils.require(gt.getStatus().equals(matchStatus), "table status error");
        return gt;
    }

    private BigInteger getWagerForPaying() {
        BigInteger wager = Msg.value();
        Tools.requireNonNull(wager, "wager can't null");
        Utils.require(wager.min(MIN_WAGER).equals(MIN_WAGER), "wager not be less than " + (MIN_WAGER.divide(BigInteger.valueOf(100000000L)) + " NULS"));
        return wager;
    }




}
