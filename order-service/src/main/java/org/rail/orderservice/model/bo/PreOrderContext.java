package org.rail.orderservice.model.bo;

import lombok.Data;
import org.rail.api.dto.AvailableSeatRemoteDTO;
import org.rail.api.dto.PassengerRemoteDTO;
import org.rail.orderservice.model.dto.CreatePreOrderDTO;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PreOrderContext {

    private CreatePreOrderDTO reqDTO;
    private List<PassengerRemoteDTO> passengerList;
    // 统一的过期时间
    private LocalDateTime expireTime;
}
