package org.rail.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RandomSeatQueryDTO {

    private Long trainId;
    private Integer seatType;
    private String departureCode;
    private String arrivalCode;
    private Integer passengerCount;
    // 用户偏好的座位序号（A/B/C/D/F）
    private List<String> preferredSeatSymbols;

    private Long orderId;
    private Integer orderType;
    private Integer status;
}
