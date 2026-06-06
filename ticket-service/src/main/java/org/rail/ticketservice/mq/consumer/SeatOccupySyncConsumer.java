package org.rail.ticketservice.mq.consumer;

import org.rail.common.mq.exception.MqException;
import org.rail.ticketservice.mapper.SeatIntervalOccupyMapper;
import org.rail.ticketservice.mq.config.TicketRabbitMQConfig;
import org.rail.ticketservice.mq.message.SeatOccupySyncMessage;
import org.rail.ticketservice.model.entity.SeatIntervalOccupy;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 座位占用记录 消费者
 * 消费消息并批量落库
 */
@Component
public class SeatOccupySyncConsumer {

    @Autowired
    private SeatIntervalOccupyMapper seatIntervalOccupyMapper;

    @Transactional(rollbackFor = Exception.class)
    @RabbitListener(queues = TicketRabbitMQConfig.SEAT_OCCUPY_SYNC_QUEUE)
    public void consume(SeatOccupySyncMessage message) {
        List<SeatIntervalOccupy> occupyList = message.getOccupyList();
        Long lockId = occupyList.getFirst().getLockId();
        try {
            Integer count = seatIntervalOccupyMapper.countByLockId(lockId);
            if (count > 0) {
                return;
            }

            // 批量插入数据库
            seatIntervalOccupyMapper.batchInsertSIOOccupyRecords(occupyList);
        } catch (Exception e) {
            throw new MqException("座位占用记录异步落库失败", e);
        }
    }
}