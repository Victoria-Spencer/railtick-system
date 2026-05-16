package org.rail.orderservice.model.bo;

import lombok.Data;
import org.rail.api.dto.AvailableSeatRemoteDTO;
import org.rail.api.dto.PassengerRemoteDTO;
import org.rail.orderservice.model.dto.CreatePreOrderDTO;

import java.util.List;

@Data
public class PreOrderContext {

    private CreatePreOrderDTO reqDTO;
    private List<PassengerRemoteDTO> passengerList;
}
