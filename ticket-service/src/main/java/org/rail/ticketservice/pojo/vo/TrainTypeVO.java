package org.rail.ticketservice.pojo.vo;

import lombok.Data;

@Data
public class TrainTypeVO {

    // 类型ID
    private Integer typeId;
    // 类型名称
    private String typeName;
    // 类型编码
    private String typeCode;
}
