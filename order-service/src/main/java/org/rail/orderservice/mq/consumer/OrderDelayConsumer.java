package org.rail.orderservice.mq.consumer;

import cn.hutool.core.util.ObjectUtil;
import org.rail.api.client.TicketFeignClient;
import org.rail.api.constant.OrderTypeConstants;
import org.rail.api.constant.SeatIntervalStatusConstants;
import org.rail.api.dto.BatchSeatIntervalInsertDTO;
import org.rail.api.dto.SeatBaseDTO;
import org.rail.orderservice.constant.OrderRedisConstants;
import org.rail.common.core.exception.MqException;
import org.rail.common.core.exception.OpenFeignException;
import org.rail.common.core.model.result.Result;
import org.rail.common.redis.api.ICacheClient;
import org.rail.orderservice.constant.OrderPaymentStatusConstants;
import org.rail.orderservice.mapper.OrderMapper;
import org.rail.orderservice.model.entity.Order;
import org.rail.orderservice.model.entity.OrderDetails;
import org.rail.orderservice.mq.config.RabbitMQConfig;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OrderDelayConsumer {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private TicketFeignClient ticketFeignClient;
    @Autowired
    private ICacheClient cacheClient;
    @Autowired
    private RedissonClient redissonClient;

    private static final String LOCK_KEY_PREFIX = OrderRedisConstants.RAIL_LOCK_ORDER_DELAY_PREFIX;

    @RabbitListener(queues = RabbitMQConfig.ORDER_DELAY_QUEUE)
    public void consume(String orderSn) {
        String convertFlagKey = OrderRedisConstants.RAIL_ORDER_CONVERTED + orderSn;

        // 分布式锁：防重复消费
        String lockKey = LOCK_KEY_PREFIX + orderSn;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(0, 30, TimeUnit.SECONDS)) {
                return;
            }

            // 已支付直接跳过
            if (cacheClient.exists(convertFlagKey)) {
                cacheClient.delete(convertFlagKey);
                return;
            }

            String orderKey = buildOrderKey(orderSn);
            Order order = cacheClient.get(orderKey, Order.class);
            if (ObjectUtil.isNull(order)) {
                return;
            }

            String detailsKey = buildOrderDetailsKey(order.getId());
            List<OrderDetails> detailsList = new ArrayList<>(cacheClient.getSetMembers(detailsKey));

            // 取消订单
            orderMapper.updateOrder(orderSn, OrderPaymentStatusConstants.CANCELED);

            if (ObjectUtil.isNotEmpty(detailsList)) {
                BatchSeatIntervalInsertDTO batchSeatDTO = buildSeatReleaseDto(order, detailsList);
                Result<Void> feignResult = ticketFeignClient.updateSeatStatus(batchSeatDTO);
                if (!feignResult.isSuccess()) {
                    throw new OpenFeignException("订单超时释放座位失败：" + feignResult.getMessage());
                }
            }

            clearOrderCache(orderKey, detailsKey);

        } catch (OpenFeignException e) {
            throw e;
        } catch (Exception e) {
            throw new MqException("订单超时消息消费失败", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 构建订单主表Redis Key
     */
    private String buildOrderKey(String orderSn) {
        return String.format("%s%s",
                OrderRedisConstants.RAIL_ORDER_PREFIX, orderSn);
    }

    /**
     * 构建订单明细Redis Key
     */
    private String buildOrderDetailsKey(Long orderId) {
        return String.format("%sorderId:%d",
                OrderRedisConstants.RAIL_ORDER_DETAILS_PREFIX, orderId);
    }

    /**
     * 清理订单所有缓存
     */
    private void clearOrderCache(String orderKey, String detailsKey) {
        cacheClient.delete(orderKey);
        cacheClient.delete(detailsKey);
        cacheClient.autoClearAggCache(orderKey);
    }

    /**
     * 构建座位释放DTO
     */
    private BatchSeatIntervalInsertDTO buildSeatReleaseDto(Order order, List<OrderDetails> detailsList) {
        OrderDetails listFirst = detailsList.getFirst();

        BatchSeatIntervalInsertDTO batchSeatDTO = new BatchSeatIntervalInsertDTO();
        // 公共字段赋值
        batchSeatDTO.setTrainId(order.getTrainId());
        batchSeatDTO.setOrderId(order.getId());
        batchSeatDTO.setOrderType(OrderTypeConstants.ORDER);
        batchSeatDTO.setDepartureCode(listFirst.getDepartureCode());
        batchSeatDTO.setArrivalCode(listFirst.getArrivalCode());
        batchSeatDTO.setStatus(SeatIntervalStatusConstants.RELEASED);
        batchSeatDTO.setSeatList(new ArrayList<>());

        // 组装座位信息
        for (OrderDetails detail : detailsList) {
            SeatBaseDTO seatBaseDTO = new SeatBaseDTO();
            seatBaseDTO.setSeatType(detail.getSeatType());
            seatBaseDTO.setCarriageNumber(detail.getCarriageNumber());
            seatBaseDTO.setSeatNo(detail.getSeatNo());
            batchSeatDTO.getSeatList().add(seatBaseDTO);
        }
        return batchSeatDTO;
    }
}