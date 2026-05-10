package org.rail.ticketservice.model.entity;

import lombok.Data;

@Data
public class TrainTypeDict {

    private Long id;
    private String typeName;
    private String typeCode;
    private String description;
}
