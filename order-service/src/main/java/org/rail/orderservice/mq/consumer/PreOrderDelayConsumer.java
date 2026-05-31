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
import org.rail.orderservice.mq.config.RabbitMQConfig;
import org.rail.orderservice.mq.message.PreOrderDelayMessage;
import org.rail.orderservice.model.entity.PreOrder;
import org.rail.orderservice.model.entity.PreOrderDetails;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class PreOrderDelayConsumer {

    @Autowired
    private ICacheClient cacheClient;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private TicketFeignClient ticketFeignClient;

    private static final String LOCK_KEY_PREFIX = OrderRedisConstants.RAIL_LOCK_PRE_ORDER_DELAY_PREFIX;

    @RabbitListener(queues = RabbitMQConfig.PRE_ORDER_QUEUE)
    public void consume(PreOrderDelayMessage message) {
        Long userId = message.getUserId();
        Long trainId = message.getTrainId();
        String preOrderSn = message.getPreOrderSn();
        String convertFlagKey = OrderRedisConstants.RAIL_PRE_ORDER_CONVERTED + preOrderSn;

        String preOrderKey = buildPreOrderKey(userId, trainId);
        String lockKey = LOCK_KEY_PREFIX + userId + ":" + trainId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 防重复消费
            if (!lock.tryLock(0, 30, TimeUnit.SECONDS)) {
                return;
            }

            // 已转化为正式订单 → 直接跳过
            if (cacheClient.exists(convertFlagKey)) {
                cacheClient.delete(convertFlagKey);
                return;
            }

            // 查询最新预订单
            PreOrder preOrder = cacheClient.get(preOrderKey);

            if (ObjectUtil.isNull(preOrder) || preOrder.getExpireTime().isAfter(LocalDateTime.now())) {
                // 预订单不存在/未超时
                return;
            }

            // 真正超时 → 释放座位
            Long preOrderId = preOrder.getId();
            String detailsKey = buildPreOrderDetailsSetKey(preOrderId);
            List<PreOrderDetails> detailsList = new ArrayList<>(cacheClient.getSetMembers(detailsKey));

            if (ObjectUtil.isNotEmpty(detailsList)) {
                BatchSeatIntervalInsertDTO batchSeatDTO = buildSeatReleaseDto(preOrder, detailsList);
                Result<Void> feignResult = ticketFeignClient.updateSeatStatus(batchSeatDTO);

                if (!feignResult.isSuccess()) {
                    throw new OpenFeignException("预订单释放座位失败：" + feignResult.getMessage());
                }
            }

            // 清理缓存
            cacheClient.delete(preOrderKey);
            cacheClient.delete(detailsKey);

        } catch (OpenFeignException e) {
            throw e;
        } catch (Exception e) {
            throw new MqException("预订单超时消息消费失败", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private String buildPreOrderKey(Long userId, Long trainId) {
        return String.format("%s%s:%d:%s:%d",
                OrderRedisConstants.RAIL_PRE_ORDER_PREFIX, "userId", userId, "trainId", trainId);
    }

    private String buildPreOrderDetailsSetKey(Long id) {
        return String.format("%s%s:%d",
                OrderRedisConstants.RAIL_PRE_ORDER_DETAILS_PREFIX, "preOrderId", id);
    }

    private BatchSeatIntervalInsertDTO buildSeatReleaseDto(PreOrder oldPreOrder, List<PreOrderDetails> oldDetails) {
        BatchSeatIntervalInsertDTO batchSeatDTO = new BatchSeatIntervalInsertDTO()
                .setTrainId(oldPreOrder.getTrainId())
                .setOrderId(oldPreOrder.getId())
                .setOrderType(OrderTypeConstants.PREORDER)
                .setDepartureCode(oldPreOrder.getDepartureCode())
                .setStatus(SeatIntervalStatusConstants.RELEASED)
                .setArrivalCode(oldPreOrder.getArrivalCode())
                .setExpireTime(oldPreOrder.getExpireTime())
                .setSeatList(new ArrayList<>());

        for (PreOrderDetails oldDetail : oldDetails) {
            SeatBaseDTO seatBaseDTO = new SeatBaseDTO();
            seatBaseDTO.setSeatType(oldDetail.getSeatType());
            seatBaseDTO.setCarriageNumber(oldDetail.getCarriageNumber());
            seatBaseDTO.setSeatNo(oldDetail.getTempSeatNo());
            batchSeatDTO.getSeatList().add(seatBaseDTO);
        }
        return batchSeatDTO;
    }
}