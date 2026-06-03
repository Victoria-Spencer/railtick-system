package org.rail.ticketservice.service.impl;

import cn.hutool.core.lang.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.rail.common.core.util.thread.ThreadLocalUtils;
import org.rail.common.redis.api.ICacheClient;
import org.rail.ticketservice.mapper.*;
import org.rail.ticketservice.model.dto.TicketQueryDTO;
import org.rail.ticketservice.model.vo.SeatClassVO;
import org.rail.ticketservice.model.vo.TicketQueryVO;
import org.rail.ticketservice.model.vo.TrainDetailVO;
import org.rail.ticketservice.model.vo.TrainTypeVO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TicketServiceImplQueryTicketTest {

    @InjectMocks
    private TicketServiceImpl ticketService;

    @Mock
    private ICacheClient cacheClient;
    @Mock
    private TrainStopStationMapper trainStopStationMapper;

    /**
     * 测试前初始化参数
     */
    @BeforeEach
    void setUp() {
        ThreadLocalUtils.removeAll();
    }

    /**
     * 测试查询车票列表（空数据）
     */
    @Test
    void queryTicket_ShouldReturnEmptyList_WhenNoData() {
        TicketQueryDTO ticketQueryDTO = new TicketQueryDTO();
        ticketQueryDTO.setDepartureDate(LocalDate.now());
        ticketQueryDTO.setDepartureCodes(Collections.singletonList("SHH"));
        ticketQueryDTO.setArrivalCodes(Collections.singletonList("BJS"));

        when(cacheClient.queryAggCacheWithNullCache(
                anyString(),
                any(TypeReference.class),
                any(Function.class),
                any(),
                anyLong(),
                any(TimeUnit.class)
        )).thenReturn(Collections.emptyList());

        List<TicketQueryVO> result = ticketService.queryTicket(ticketQueryDTO);

        assertTrue(result.isEmpty());
        verify(cacheClient, times(1)).queryAggCacheWithNullCache(
                anyString(), any(TypeReference.class), any(Function.class), any(), anyLong(), any(TimeUnit.class)
        );
    }

    /**
     * 测试查询车票列表（真实数据）
     */
    @Test
    void queryTicket_ShouldReturnData_WhenHasValidTrainAndSeatData() {
        TicketQueryDTO ticketQueryDTO = new TicketQueryDTO();
        ticketQueryDTO.setDepartureDate(LocalDate.now());
        ticketQueryDTO.setDepartureCodes(Collections.singletonList("SHH"));
        ticketQueryDTO.setArrivalCodes(Collections.singletonList("BJS"));

        final Long TRAIN_ID = 1L;
        List<TrainDetailVO> trainList = buildMockTrainList(TRAIN_ID, "SHH", "BJS");
        List<SeatClassVO> seatList = buildMockSeatList(TRAIN_ID);

        // Mock 依赖行为
        when(cacheClient.queryAggCacheWithNullCache(
                anyString(),
                any(TypeReference.class),
                any(Function.class),
                any(),
                anyLong(),
                any(TimeUnit.class)
        )).thenReturn(trainList);
        when(cacheClient.batchQueryAggCache(
                any(Function.class), anyList(),
                any(TypeReference.class),
                any(Function.class),
                anyLong(),
                any(TimeUnit.class)
        )).thenReturn(seatList);
        when(trainStopStationMapper.isDepartureStation(TRAIN_ID, 1001)).thenReturn(true);
        when(trainStopStationMapper.isTerminalStation(TRAIN_ID, 1002)).thenReturn(false);

        List<TicketQueryVO> result = ticketService.queryTicket(ticketQueryDTO);

        // 断言结果
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());

        TicketQueryVO vo = result.getFirst();
        assertEquals("G1234", vo.getTrain().getTrainNumber());
        assertEquals(LocalDateTime.of(2026, 4, 21, 8, 0), vo.getDepartureTime());
        assertEquals(240, vo.getDuration());
        assertEquals(1, vo.getSeatClassFrontVOList().size());
        assertEquals("一等座", vo.getSeatClassFrontVOList().get(0).getName());

        verify(cacheClient).queryAggCacheWithNullCache(
                anyString(), any(TypeReference.class), any(Function.class), any(), anyLong(), any(TimeUnit.class)
        );
        verify(cacheClient).batchQueryAggCache(
                any(Function.class), anyList(), any(TypeReference.class), any(Function.class), anyLong(), any(TimeUnit.class)
        );
    }

    /**
     * 构造模拟车次数据
     */
    private List<TrainDetailVO> buildMockTrainList(Long trainId, String depCode, String arrCode) {
        TrainDetailVO detail = new TrainDetailVO();
        detail.setTrainId(trainId);
        detail.setDepartureCode(depCode);
        detail.setArrivalCode(arrCode);
        detail.setDepartureTime(LocalDateTime.of(2026, 4, 21, 8, 0));
        detail.setArrivalTime(LocalDateTime.of(2026, 4, 21, 12, 0));
        detail.setDepartureStationId(1001);
        detail.setArrivalStationId(1002);
        detail.setStartSequence(1);
        detail.setEndSequence(5);
        detail.setTrainNumber("G1234");

        TrainTypeVO typeVO = new TrainTypeVO(1, "高铁", "G");
        detail.setTrainTypeVOList(Collections.singletonList(typeVO));

        return Collections.singletonList(detail);
    }

    /**
     * 构造模拟席别数据
     */
    private List<SeatClassVO> buildMockSeatList(Long trainId) {
        SeatClassVO seatVO = new SeatClassVO();
        seatVO.setTrainId(trainId);
        seatVO.setSeatClassId(1L);
        seatVO.setSeatType(1);
        seatVO.setName("一等座");
        seatVO.setPrice(100);
        seatVO.setAvailableSeatNum(5);
        seatVO.setTotalSeatNum(100);
        return Collections.singletonList(seatVO);
    }
}
