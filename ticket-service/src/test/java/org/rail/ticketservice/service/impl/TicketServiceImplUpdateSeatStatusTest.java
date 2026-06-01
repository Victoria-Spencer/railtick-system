package org.rail.ticketservice.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.rail.api.constant.OrderTypeConstants;
import org.rail.api.constant.SeatIntervalStatusConstants;
import org.rail.api.dto.BatchSeatIntervalInsertDTO;
import org.rail.api.dto.SeatBaseDTO;
import org.rail.common.core.exception.SeatLockFailedException;
import org.rail.common.core.util.SnowflakeIdGenerator;
import org.rail.common.core.util.thread.ThreadLocalUtils;
import org.rail.common.redis.api.ICacheClient;
import org.rail.ticketservice.model.dto.TrainStopStationCacheDTO;
import org.rail.ticketservice.model.entity.Station;
import org.rail.ticketservice.mq.producer.SeatOccupySyncProducer;
import org.rail.ticketservice.task.StationLocalCacheTask;
import org.rail.ticketservice.task.TrainStopStationLocalCacheTask;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceImplUpdateSeatStatusTest {

    @InjectMocks
    private TicketServiceImpl ticketService;

    @Mock
    private SeatOccupySyncProducer seatOccupySyncProducer;
    @Mock
    private ICacheClient cacheClient;
    @Mock
    private StationLocalCacheTask stationCacheTask;
    @Mock
    private TrainStopStationLocalCacheTask trainStopCacheTask;

    private MockedStatic<SnowflakeIdGenerator> snowflakeMock;

    private final Long TRAIN_ID = 1001L;
    private final String DEP_CODE = "SHH";
    private final String ARR_CODE = "BJP";
    private final String CARRIAGE_NUM = "01";
    private final String SEAT_NO = "A01";
    private final Integer DEP_SEQUENCE = 2;
    private final Integer ARR_SEQUENCE = 5;

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
     * 正常流程：预订单锁座成功
     */
    @Test
    void updateSeatStatus_ShouldSuccess_WhenPreorderLockSeat() {
        BatchSeatIntervalInsertDTO batchDTO = buildBaseBatchSeatDTO();
        mockCommonCacheDependencies();
        // Mock专属逻辑
        when(cacheClient.executeLuaFile(
                eq("lua/seatLock.lua"),
                anyList(),
                eq(DEP_SEQUENCE),
                eq(ARR_SEQUENCE),
                eq(OrderTypeConstants.PREORDER),
                eq(SeatIntervalStatusConstants.LOCKED)
        )).thenReturn(1L);

        // 执行+验证
        assertDoesNotThrow(() -> ticketService.updateSeatStatus(batchDTO));
        verify(cacheClient).executeLuaFile(anyString(), anyList(), anyInt(), anyInt(), anyInt(), anyInt());
        verify(seatOccupySyncProducer, times(1)).sendSeatOccupySyncMsg(anyList());
    }

    /**
     * 异常流程：Lua锁座失败
     */
    @Test
    void updateSeatStatus_ShouldThrowExceptionAndRollback_WhenLuaLockFailed() {
        BatchSeatIntervalInsertDTO batchDTO = buildBaseBatchSeatDTO();
        mockCommonCacheDependencies();
        // Mock专属逻辑
        when(cacheClient.executeLuaFile(
                eq("lua/seatLock.lua"),
                anyList(),
                eq(DEP_SEQUENCE),
                eq(ARR_SEQUENCE),
                eq(OrderTypeConstants.PREORDER),
                eq(SeatIntervalStatusConstants.LOCKED)
        )).thenReturn(0L);

        assertThrows(SeatLockFailedException.class,
                () -> ticketService.updateSeatStatus(batchDTO));
        verify(cacheClient, never()).setRangeBits(anyString(), anyInt(), anyInt(), anyBoolean());
    }

    /**
     * 异常流程：缓存写入失败
     */
    @Test
    void updateSeatStatus_ShouldThrowExceptionAndRollback_WhenCacheWriteFailed() {
        BatchSeatIntervalInsertDTO batchDTO = buildBaseBatchSeatDTO();
        mockCommonCacheDependencies();

        when(cacheClient.executeLuaFile(
                eq("lua/seatLock.lua"),
                anyList(),
                eq(DEP_SEQUENCE),
                eq(ARR_SEQUENCE),
                eq(OrderTypeConstants.PREORDER),
                eq(SeatIntervalStatusConstants.LOCKED)
        )).thenReturn(1L);

        doThrow(new RuntimeException("Redis Hash写入失败")).when(cacheClient).hPutAll(anyString(), anyMap());
        assertThrows(SeatLockFailedException.class,
                () -> ticketService.updateSeatStatus(batchDTO));
        // 有锁定座位，回滚
        verify(cacheClient).setRangeBits(anyString(), eq(2L), eq(5L), eq(false));
    }

    /**
     * 参数校验：空座位列表
     */
    @Test
    void updateSeatStatus_ShouldDoNothing_WhenSeatListEmpty() {
        BatchSeatIntervalInsertDTO batchDTO = buildBaseBatchSeatDTO();
        batchDTO.setSeatList(null);

        assertThrows(IllegalArgumentException.class, () -> ticketService.updateSeatStatus(batchDTO));
        verify(cacheClient, never()).executeLuaFile(anyString(), anyList(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    /**
     * 构造基础合法的批量锁座DTO
     */
    private BatchSeatIntervalInsertDTO buildBaseBatchSeatDTO() {
        BatchSeatIntervalInsertDTO batchDTO = new BatchSeatIntervalInsertDTO();
        batchDTO.setTrainId(TRAIN_ID);
        batchDTO.setDepartureCode(DEP_CODE);
        batchDTO.setArrivalCode(ARR_CODE);
        batchDTO.setOrderType(OrderTypeConstants.PREORDER);
        batchDTO.setStatus(SeatIntervalStatusConstants.LOCKED);
        batchDTO.setSeatList(List.of(buildSeatBaseDTO()));
        return batchDTO;
    }

    /**
     * 构造单个座位基础信息
     */
    private SeatBaseDTO buildSeatBaseDTO() {
        SeatBaseDTO seat = new SeatBaseDTO();
        seat.setCarriageNumber(CARRIAGE_NUM);
        seat.setSeatNo(SEAT_NO);
        return seat;
    }

    /**
     * 构造 mock 站点信息
     */
    private List<Station> buildMockStations() {
        Station stationShh = new Station();
        stationShh.setCode(DEP_CODE);
        stationShh.setId(1L);

        Station stationBjp = new Station();
        stationBjp.setCode(ARR_CODE);
        stationBjp.setId(2L);
        return List.of(stationShh, stationBjp);
    }

    /**
     * 统一Mock：站点缓存 + 经停站序列缓存
     */
    private void mockCommonCacheDependencies() {
        // Mock站点信息
        when(stationCacheTask.getAllStations()).thenReturn(buildMockStations());
        // Mock经停站序列
        TrainStopStationCacheDTO stopCacheDTO = new TrainStopStationCacheDTO(
                Map.of(1L, DEP_SEQUENCE, 2L, ARR_SEQUENCE),
                ARR_SEQUENCE
        );
        when(trainStopCacheTask.getCacheByTrainId(anyLong())).thenReturn(stopCacheDTO);
        // Mock座位ID查询
        when(cacheClient.hMultiGet(anyString(), anyList())).thenReturn(List.of(TRAIN_ID));
    }

}