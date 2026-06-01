package org.rail.orderservice.mq.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.rail.orderservice.model.entity.Order;
import org.rail.orderservice.model.entity.OrderDetails;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreateMessage implements Serializable {
    private Order order;
    private List<OrderDetails> orderDetailsList;
}