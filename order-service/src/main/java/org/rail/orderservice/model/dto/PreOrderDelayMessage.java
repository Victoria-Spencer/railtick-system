package org.rail.orderservice.model.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class PreOrderDelayMessage implements Serializable {
    private Long userId;
    private Long trainId;
}