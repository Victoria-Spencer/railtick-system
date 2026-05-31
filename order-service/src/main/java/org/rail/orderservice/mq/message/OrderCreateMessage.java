package org.rail.orderservice.mq.message;

import lombok.Data;
import org.rail.orderservice.model.entity.Order;
import org.rail.orderservice.model.entity.OrderDetails;

import java.io.Serializable;
import java.util.List;

@Data
public class OrderCreateMessage implements Serializable {
    private Order order;
    private List<OrderDetails> orderDetailsList;
}