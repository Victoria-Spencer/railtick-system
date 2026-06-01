package org.rail.ticketservice.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.rail.api.constant.OrderTypeConstants;
import org.rail.api.constant.SeatIntervalStatusConstants;
import org.rail.api.dto.AvailableSeatRemoteDTO;
import org.rail.api.dto.RandomSeatQueryDTO;
import org.rail.common.core.exception.SeatLockFailedException;
import org.rail.common.core.util.SnowflakeIdGenerator;
import org.rail.common.core.util.thread.ThreadLocalUtils;
import org.rail.common.redis.api.ICacheClient;
import org.rail.ticketservice.model.entity.Station;
import org.rail.ticketservice.model.vo.SeatBusinessVO;
import org.rail.ticketservice.mq.producer.SeatOccupySyncProducer;
import org.rail.ticketservice.service.SeatService;
import org.rail.ticketservice.task.StationLocalCacheTask;
import org.rail.ticketservice.task.TrainStopStationLocalCacheTask;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceImplGetAvailableSeatTest {

    @InjectMocks
    private TicketServiceImpl ticketService;

    @Mock
    private SeatService seatService;
    @Mock
    private StationLocalCacheTask stationCacheTask;
    @Mock
    private TrainStopStationLocalCacheTask trainStopCacheTask;

    private MockedStatic<SnowflakeIdGenerator> snowflakeMock;
    @Mock
    private ICacheClient cacheClient;
    @Mock
    private SeatOccupySyncProducer seatOccupySyncProducer;

    private final Long TRAIN_ID = 1L;
    private final Integer SEAT_TYPE = 2; // 二等座
    private final Integer PASSENGER_COUNT = 2;
    private final String DEP_CODE = "SHH";
    private final String ARR_CODE = "BJP";
    private final Integer DEP_SEQ = 2;
    private final Integer ARR_SEQ = 5;
    private final List<String> PREFERRED_SEATS = List.of("A", "B"); // 偏好座位
    private final Integer ORDER_TYPE = OrderTypeConstants.PREORDER;
    private final Integer STATUS = SeatIntervalStatusConstants.LOCKED;
    private final Long ORDER_ID = 10086L;

    @BeforeEach
    void setUp() {
        snowflakeMock = Mockito.mockStatic(SnowflakeIdGenerator.class);
        snowflakeMock.when(SnowflakeIdGenerator::nextId).thenReturn(123456789L);
        ReflectionTestUtils.setField(ticketService, "preOrderExpireMinutes", 15);

        ThreadLocalUtils.removeAll();
    }

    @AfterEach
    void tearDown() {
        if (snowflakeMock != null) {
            snowflakeMock.close();
        }
        ThreadLocalUtils.removeAll();
    }

    /**
     * 正常流程：无偏好座位 → 随机选座成功
     */
    @Test
    void getAvailableSeats_ShouldReturnRandomSeats_WhenNoPreference() {
        RandomSeatQueryDTO queryDTO = buildBaseRandomSeatQueryDTO();
        queryDTO.setPreferredSeatSymbols(null); // 无偏好

        mockCommonDependencies();
        when(seatService.getFreeSeatIdsByBitmap(anyLong(), anyString(), anyString()))
                .thenReturn(List.of(1L, 2L));
        when(seatService.getSeatBaseInfo(anyLong(), anyLong()))
                .thenAnswer(invocation -> {
                    Long seatId = invocation.getArgument(1);
                    return buildSeatBusinessVO(seatId.intValue());
                });
        when(cacheClient.hMultiGet(anyString(), anyList()))
                .thenReturn(List.of(1L, 2L));
        when(cacheClient.executeLuaFile(
                eq("lua/seatLock.lua"),
                anyList(),
                anyInt(),
                anyInt(),
                anyInt(),
                anyInt()
        )).thenReturn(1L);
        doNothing().when(cacheClient).hPutAll(anyString(), anyMap());
        when(trainStopCacheTask.getTrainTerminalSeq(anyLong())).thenReturn(5);

        List<AvailableSeatRemoteDTO> result = ticketService.getAvailableSeats(queryDTO);

        assertNotNull(result);
        assertEquals(PASSENGER_COUNT, result.size());
        // 验证选座逻辑（验证内部调用链）
        verify(seatService, times(1)).getFreeSeatIdsByBitmap(anyLong(), anyString(), anyString());
        verify(cacheClient, times(2)).executeLuaFile(
                eq("lua/seatLock.lua"),
                anyList(),                              // 断言传入了bitmap key列表
                eq(DEP_SEQ),                            // 断言开始站点序列=2（和mock一致）
                eq(ARR_SEQ),                            // 断言结束站点序列=5（和mock一致）
                eq(OrderTypeConstants.PREORDER),        // 断言订单类型=预订单
                eq(SeatIntervalStatusConstants.LOCKED)  // 断言锁座状态=锁定
        );
        verify(cacheClient, times(1)).hPutAll(anyString(), anyMap());
        verify(seatOccupySyncProducer, times(1)).sendSeatOccupySyncMsg(anyList());
    }

    /**
     * 正常流程：有偏好座位 → 偏好选座成功（连坐）
     */
    @Test
    void getAvailableSeats_ShouldReturnPreferredSeats_WhenPreferenceFulfilled() {
        RandomSeatQueryDTO queryDTO = buildBaseRandomSeatQueryDTO();
        queryDTO.setPreferredSeatSymbols(PREFERRED_SEATS);

        mockCommonDependencies();
        when(seatService.getFreeSeatIdsByBitmap(anyLong(), anyString(), anyString()))
                .thenReturn(List.of(1L, 2L));
        when(seatService.getSeatBaseInfo(anyLong(), anyLong()))
                .thenAnswer(invocation -> {
                    Long seatId = invocation.getArgument(1);
                    // seatId=1 → seq=0(A)，seatId=2 → seq=1(B)
                    return SeatBusinessVO.builder()
                            .id(seatId)
                            .trainId(TRAIN_ID)
                            .seatType(SEAT_TYPE)
                            .carriageNumber("1")
                            .seatNo(seatId.equals(1L) ? "1A" : "1B")
                            .seatSeq(seatId.equals(1L) ? 0 : 1)
                            .rowNum(1)
                            .build();
                });
        when(cacheClient.hMultiGet(anyString(), anyList()))
                .thenReturn(List.of(1L, 2L));
        when(cacheClient.executeLuaFile(
                eq("lua/seatLock.lua"),
                anyList(),
                anyInt(),
                anyInt(),
                anyInt(),
                anyInt()
        )).thenReturn(1L);
        doNothing().when(cacheClient).hPutAll(anyString(), anyMap());
        when(trainStopCacheTask.getTrainTerminalSeq(anyLong())).thenReturn(5);

        // 执行业务方法
        List<AvailableSeatRemoteDTO> result = ticketService.getAvailableSeats(queryDTO);

        assertNotNull(result);
        assertEquals(PASSENGER_COUNT, result.size());
        verify(seatService, times(1)).getFreeSeatIdsByBitmap(anyLong(), anyString(), anyString());
        verify(cacheClient, times(2)).executeLuaFile(
                eq("lua/seatLock.lua"),
                anyList(),
                eq(DEP_SEQ),
                eq(ARR_SEQ),
                eq(OrderTypeConstants.PREORDER),
                eq(SeatIntervalStatusConstants.LOCKED)
        );
        verify(cacheClient, times(1)).hPutAll(anyString(), anyMap());
        verify(seatOccupySyncProducer, times(1)).sendSeatOccupySyncMsg(anyList());
    }

    /**
     * 异常流程：Lua锁座失败（重试3次，最终抛异常）
     */
    @Test
    void getAvailableSeats_ShouldThrowException_WhenLockFailedAfterRetry() {
        RandomSeatQueryDTO queryDTO = buildBaseRandomSeatQueryDTO();

        mockCommonDependencies();
        when(seatService.getFreeSeatIdsByBitmap(anyLong(), anyString(), anyString()))
                .thenReturn(List.of(1L));
        when(seatService.getSeatBaseInfo(anyLong(), anyLong()))
                .thenReturn(buildSeatBusinessVO(1));
        when(cacheClient.hMultiGet(anyString(), anyList()))
                .thenReturn(List.of(1L));
        // 模拟Lua锁座失败（返回0），触发重试机制
        when(cacheClient.executeLuaFile(
                eq("lua/seatLock.lua"),
                anyList(),
                anyInt(),
                anyInt(),
                anyInt(),
                anyInt()
        )).thenReturn(0L);

        SeatLockFailedException ex = assertThrows(SeatLockFailedException.class,
                () -> ticketService.getAvailableSeats(queryDTO));
        assertEquals("座位已被抢占，请重新选座", ex.getMessage());

        verify(cacheClient, atLeastOnce()).executeLuaFile(
                eq("lua/seatLock.lua"),
                anyList(),
                eq(DEP_SEQ),
                eq(ARR_SEQ),
                eq(OrderTypeConstants.PREORDER),
                eq(SeatIntervalStatusConstants.LOCKED)
        );
        // 验证重试机制：getFreeSeatIdsByBitmap被调用了3次（对应循环3次）
        verify(seatService, times(3)).getFreeSeatIdsByBitmap(anyLong(), anyString(), anyString());
    }

    /**
     * 异常流程：参数校验失败（必填项为空）
     */
    @Test
    void getAvailableSeats_ShouldReturnNull_WhenParamInvalid() {
        RandomSeatQueryDTO queryDTO = buildBaseRandomSeatQueryDTO();
        // 置空任意一个必填字段
        queryDTO.setTrainId(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ticketService.getAvailableSeats(queryDTO)
        );
    }


    /**
     * 构造基础随机选座DTO（公共参数）
     */
    private RandomSeatQueryDTO buildBaseRandomSeatQueryDTO() {
        RandomSeatQueryDTO dto = new RandomSeatQueryDTO();
        dto.setTrainId(TRAIN_ID);
        dto.setSeatType(SEAT_TYPE);
        dto.setPassengerCount(PASSENGER_COUNT);
        dto.setDepartureCode(DEP_CODE);
        dto.setArrivalCode(ARR_CODE);
        dto.setOrderType(ORDER_TYPE);
        dto.setStatus(STATUS);
        dto.setOrderId(ORDER_ID);
        return dto;
    }

    /**
     * 构造单个座位VO
     */
    private SeatBusinessVO buildSeatBusinessVO(int seatSeq) {
        return SeatBusinessVO.builder()
                .id((long) seatSeq)
                .trainId(TRAIN_ID)
                .seatType(SEAT_TYPE)
                .carriageNumber("1")
                .seatNo(String.format("%dA", seatSeq))
                .seatSeq(seatSeq)
                .rowNum(1)
                .trainSeatClassId(null)
                .build();
    }

    /**
     * 公共Mock依赖
     */
    private void mockCommonDependencies() {
        // 站点缓存Mock
        Station stationShh = new Station();
        stationShh.setId(1L);
        stationShh.setCode(DEP_CODE);

        Station stationBjp = new Station();
        stationBjp.setId(2L);
        stationBjp.setCode(ARR_CODE);

        when(stationCacheTask.getAllStations()).thenReturn(List.of(stationShh, stationBjp));

        // 经停站缓存Mock
        when(trainStopCacheTask.getCacheByTrainId(anyLong()))
                .thenReturn(new org.rail.ticketservice.model.dto.TrainStopStationCacheDTO(Map.of(1L, DEP_SEQ, 2L, ARR_SEQ), 5));
    }
}