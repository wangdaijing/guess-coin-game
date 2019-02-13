package io.nuls.contract.guess.coin;


import io.nuls.contract.sdk.Utils;

/**
 * @Author: wangdaijing
 * @Time: 2019-01-31 17:32
 * @Description: 功能描述
 */
public class Tools {

    public static void requireNonNull(Object obj,String msg){
        Utils.require(obj != null,msg);
    }

}
