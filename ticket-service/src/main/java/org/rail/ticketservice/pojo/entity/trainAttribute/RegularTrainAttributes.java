package org.rail.ticketservice.pojo.entity.trainAttribute;

import org.rail.ticketservice.pojo.entity.TrainAttributes;

public class RegularTrainAttributes implements TrainAttributes {

    // 软卧数量
    private Integer softSleeperQuantity;
    // 软卧候选标识
    private Boolean softSleeperCandidate;
    // 软卧价格
    private Integer softSleeperPrice;
    // 高级软卧数量
    private Integer deluxeSoftSleeperQuantity;
    // 高级软卧候选标识
    private Boolean deluxeSoftSleeperCandidate;
    // 高级软卧价格
    private Integer deluxeSoftSleeperPrice;
    // 硬卧数量
    private Integer hardSleeperQuantity;
    // 硬卧候选标识
    private Boolean hardSleeperCandidate;
    // 硬卧价格
    private Integer hardSleeperPrice;
    // 硬座数量
    private Integer hardSeatQuantity;
    // 硬座候选标识
    private Boolean hardSeatCandidate;
    // 硬座价格
    private Integer hardSeatPrice;
}
