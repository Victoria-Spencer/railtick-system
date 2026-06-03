package org.rail.ticketservice.service.impl;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.rail.common.core.util.thread.ThreadLocalUtils;
import org.rail.ticketservice.mapper.*;
import org.rail.ticketservice.model.dto.*;
import org.rail.ticketservice.model.entity.Train;
import org.rail.ticketservice.model.vo.*;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TicketServiceImplQueryPlannedTicketTest {

    @InjectMocks
    private TicketServiceImpl ticketService;

    @Mock
    private TrainMapper trainMapper;
    @Mock
    private TrainStopStationMapper trainStopStationMapper;
    @Mock
    private SeatClassMapper seatClassMapper;
    @Mock
    private TrainTypeDictMapper trainTypeDictMapper;

    private final Long TRAIN_ID = 1L;
    private final String DEP_CODE = "SHH";
    private final String ARR_CODE = "BJS";

    /**
     * 测试前初始化参数
     */
    @BeforeEach
    void setUp() {
        ThreadLocalUtils.removeAll();
    }

    /**
     * 拟购票接口正常流程（全字段验证+依赖调用验证）
     */
    @Test
    void queryPlannedTicket_ShouldReturnCompleteData_WhenAllDataValid() {
        PlannedTicketQueryDTO plannedTicketQueryDTO = buildValidPlannedTicketDTO();
        StopInfoDTO mockStopInfo = buildMockStopInfoDTO();
        Train mockTrain = buildMockTrain();
        List<SeatClassVO> mockSeatList = buildMockSeatList();
        List<TrainTypeVO> mockTrainTypeList = buildMockTrainTypeList();

        when(trainMapper.getById(TRAIN_ID)).thenReturn(mockTrain);
        when(trainStopStationMapper.getStopInfoByQueryDTO(any(PlannedTicketQueryDTO.class))).thenReturn(mockStopInfo);
        when(seatClassMapper.batchQuerySeatInfoByDTOList(anyList())).thenReturn(mockSeatList);
        when(trainTypeDictMapper.getTrainTypeDictByTrainId(TRAIN_ID)).thenReturn(mockTrainTypeList);
        when(trainStopStationMapper.isDepartureStation(TRAIN_ID, mockStopInfo.getDepartureStationId())).thenReturn(true);
        when(trainStopStationMapper.isTerminalStation(TRAIN_ID, mockStopInfo.getArrivalStationId())).thenReturn(false);

        TicketQueryVO result = ticketService.queryPlannedTicket(plannedTicketQueryDTO);

        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.getTrain());
        Assertions.assertEquals(mockTrain.getTrainNumber(), result.getTrain().getTrainNumber());
        Assertions.assertEquals(mockStopInfo.getDepartureTime(), result.getDepartureTime());
        Assertions.assertEquals(mockStopInfo.getArrivalTime(), result.getArrivalTime());
        Assertions.assertEquals(mockStopInfo.getDeparture(), result.getDeparture());
        Assertions.assertEquals(mockStopInfo.getArrival(), result.getArrival());
        Assertions.assertEquals(mockStopInfo.getDepartureCode(), result.getDepartureCode());
        Assertions.assertEquals(mockStopInfo.getArrivalCode(), result.getArrivalCode());
        Assertions.assertEquals(240, result.getDuration());
        Assertions.assertEquals("一等座", result.getSeatClassFrontVOList().getFirst().getName());
        Assertions.assertEquals("高铁", result.getTrainTypeVOList().getFirst().getTypeName());
        assertTrue(result.isDepartureFlag());
        Assertions.assertFalse(result.isArrivalFlag());

        verify(trainMapper, times(1)).getById(TRAIN_ID);
        verify(trainStopStationMapper, times(1)).getStopInfoByQueryDTO(plannedTicketQueryDTO);
        verify(seatClassMapper, times(1)).batchQuerySeatInfoByDTOList(anyList());
        verify(trainTypeDictMapper, times(1)).getTrainTypeDictByTrainId(TRAIN_ID);
        verify(trainStopStationMapper, times(1)).isDepartureStation(TRAIN_ID, mockStopInfo.getDepartureStationId());
        verify(trainStopStationMapper, times(1)).isTerminalStation(TRAIN_ID, mockStopInfo.getArrivalStationId());
    }

    /**
     * 席别信息为空（边界场景：无可用席别）
     */
    @Test
    void queryPlannedTicket_ShouldHandleEmptySeatData_WhenNoSeatClass() {
        PlannedTicketQueryDTO plannedTicketQueryDTO = buildValidPlannedTicketDTO();
        StopInfoDTO mockStopInfo = buildMockStopInfoDTO();
        Train mockTrain = buildMockTrain();
        List<TrainTypeVO> mockTrainTypeList = buildMockTrainTypeList();

        when(trainMapper.getById(TRAIN_ID)).thenReturn(mockTrain);
        when(trainStopStationMapper.getStopInfoByQueryDTO(any(PlannedTicketQueryDTO.class))).thenReturn(mockStopInfo);
        when(seatClassMapper.batchQuerySeatInfoByDTOList(anyList())).thenReturn(Collections.emptyList());
        when(trainTypeDictMapper.getTrainTypeDictByTrainId(TRAIN_ID)).thenReturn(mockTrainTypeList);

        TicketQueryVO result = ticketService.queryPlannedTicket(plannedTicketQueryDTO);

        // 断言：席别列表为空，其他字段正常
        Assertions.assertNotNull(result);
        assertTrue(result.getSeatClassFrontVOList().isEmpty());
        Assertions.assertEquals("G1234", result.getTrain().getTrainNumber());
    }

    /**
     * 列车类型信息为空（边界场景：无列车类型数据）
     */
    @Test
    void queryPlannedTicket_ShouldHandleEmptyTrainTypeData_WhenNoTrainType() {
        PlannedTicketQueryDTO plannedTicketQueryDTO = buildValidPlannedTicketDTO();
        StopInfoDTO mockStopInfo = buildMockStopInfoDTO();
        Train mockTrain = buildMockTrain();
        List<SeatClassVO> mockSeatList = buildMockSeatList();

        // Mock依赖：列车类型查询返回空列表
        when(trainMapper.getById(TRAIN_ID)).thenReturn(mockTrain);
        when(trainStopStationMapper.getStopInfoByQueryDTO(any(PlannedTicketQueryDTO.class))).thenReturn(mockStopInfo);
        when(seatClassMapper.batchQuerySeatInfoByDTOList(anyList())).thenReturn(mockSeatList);
        when(trainTypeDictMapper.getTrainTypeDictByTrainId(TRAIN_ID)).thenReturn(Collections.emptyList());

        TicketQueryVO result = ticketService.queryPlannedTicket(plannedTicketQueryDTO);

        // 断言：列车类型列表为空，其他字段正常
        Assertions.assertNotNull(result);
        assertTrue(result.getTrainTypeVOList().isEmpty());
        Assertions.assertEquals("一等座", result.getSeatClassFrontVOList().getFirst().getName());
    }

    /**
     * 验证席别查询参数正确性（捕获入参，确保start/end sequence传递正确）
     */
    @Test
    void queryPlannedTicket_ShouldPassCorrectSeatQueryParams_WhenBuildingSeatList() {
        PlannedTicketQueryDTO plannedTicketQueryDTO = buildValidPlannedTicketDTO();
        Train mockTrain = buildMockTrain();
        List<SeatClassVO> mockSeatList = buildMockSeatList();
        List<TrainTypeVO> mockTrainTypeList = buildMockTrainTypeList();
        StopInfoDTO mockStopInfo = buildMockStopInfoDTO();
        // 校验参数传递正确性
        mockStopInfo.setDepartureSequence(2);
        mockStopInfo.setArrivalSequence(4);

        when(trainMapper.getById(TRAIN_ID)).thenReturn(mockTrain);
        when(trainStopStationMapper.getStopInfoByQueryDTO(any(PlannedTicketQueryDTO.class))).thenReturn(mockStopInfo);
        when(seatClassMapper.batchQuerySeatInfoByDTOList(anyList())).thenReturn(mockSeatList);
        when(trainTypeDictMapper.getTrainTypeDictByTrainId(TRAIN_ID)).thenReturn(mockTrainTypeList);

        // 用ArgumentCaptor捕获传入seatClassMapper的参数
        ArgumentCaptor<List<SeatQueryDTO>> seatQueryCaptor = ArgumentCaptor.forClass(List.class);

        ticketService.queryPlannedTicket(plannedTicketQueryDTO);

        // 验证：传递的trainId/start/end sequence与StopInfoDTO一致
        verify(seatClassMapper).batchQuerySeatInfoByDTOList(seatQueryCaptor.capture());
        List<SeatQueryDTO> capturedList = seatQueryCaptor.getValue();
        Assertions.assertEquals(1, capturedList.size());
        SeatQueryDTO capturedDto = capturedList.getFirst();
        Assertions.assertEquals(TRAIN_ID, capturedDto.getTrainId());
        Assertions.assertEquals(mockStopInfo.getDepartureSequence(), capturedDto.getStartSequence());
        Assertions.assertEquals(mockStopInfo.getArrivalSequence(), capturedDto.getEndSequence());
    }

    /**
     * 验证时长计算正确性（边界场景：2小时/120分钟）
     */
    @Test
    void queryPlannedTicket_ShouldCalculateDurationCorrectly_WhenTwoHourDiff() {
        PlannedTicketQueryDTO plannedTicketQueryDTO = buildValidPlannedTicketDTO();
        Train mockTrain = buildMockTrain();
        List<SeatClassVO> mockSeatList = buildMockSeatList();
        List<TrainTypeVO> mockTrainTypeList = buildMockTrainTypeList();
        // 自定义时间差（2小时=120分钟）
        StopInfoDTO mockStopInfo = buildMockStopInfoDTO();
        mockStopInfo.setDepartureTime(LocalDateTime.of(2026, 4, 21, 8, 0));
        mockStopInfo.setArrivalTime(LocalDateTime.of(2026, 4, 21, 10, 0));

        when(trainMapper.getById(TRAIN_ID)).thenReturn(mockTrain);
        when(trainStopStationMapper.getStopInfoByQueryDTO(any(PlannedTicketQueryDTO.class))).thenReturn(mockStopInfo);
        when(seatClassMapper.batchQuerySeatInfoByDTOList(anyList())).thenReturn(mockSeatList);
        when(trainTypeDictMapper.getTrainTypeDictByTrainId(TRAIN_ID)).thenReturn(mockTrainTypeList);

        TicketQueryVO result = ticketService.queryPlannedTicket(plannedTicketQueryDTO);

        // 断言：时长计算正确
        Assertions.assertEquals(120, result.getDuration());
    }

    /**
     * 构造合法的DTO入参
     */
    private PlannedTicketQueryDTO buildValidPlannedTicketDTO() {
        PlannedTicketQueryDTO dto = new PlannedTicketQueryDTO();
        dto.setTrainId(TRAIN_ID);
        dto.setDepartureCode(DEP_CODE);
        dto.setArrivalCode(ARR_CODE);
        return dto;
    }

    /**
     * 构造模拟席别数据
     */
    private List<SeatClassVO> buildMockSeatList() {
        SeatClassVO seatVO = new SeatClassVO();
        seatVO.setTrainId(TRAIN_ID);
        seatVO.setSeatClassId(1L);
        seatVO.setSeatType(1);
        seatVO.setName("一等座");
        seatVO.setPrice(100);
        seatVO.setAvailableSeatNum(5);
        seatVO.setTotalSeatNum(10);
        return Collections.singletonList(seatVO);
    }

    /**
     * 构造模拟列车数据
     */
    private Train buildMockTrain() {
        Train train = new Train();
        train.setId(TRAIN_ID);
        train.setTrainNumber("G1234");
        train.setDaysArrived(1);
        train.setSaleTime(LocalDateTime.now());
        train.setSaleStatus(1);
        return train;
    }

    /**
     * 构造模拟经停站信息
     */
    private StopInfoDTO buildMockStopInfoDTO() {
        StopInfoDTO stopInfo = new StopInfoDTO();
        stopInfo.setDepartureTime(LocalDateTime.of(2026, 4, 21, 8, 0));
        stopInfo.setArrivalTime(LocalDateTime.of(2026, 4, 21, 12, 0));
        stopInfo.setDepartureSequence(1);
        stopInfo.setArrivalSequence(5);
        stopInfo.setDepartureStationId(1001);
        stopInfo.setArrivalStationId(1002);
        stopInfo.setDepartureCode("SHH");
        stopInfo.setArrivalCode("BJS");
        stopInfo.setDeparture("上海虹桥");
        stopInfo.setArrival("北京南");
        return stopInfo;
    }

    /**
     * 构造模拟列车类型列表
     */
    private List<TrainTypeVO> buildMockTrainTypeList() {
        TrainTypeVO typeVO = new TrainTypeVO();
        typeVO.setTypeId(1);
        typeVO.setTypeName("高铁");
        typeVO.setTypeCode("G");
        return Collections.singletonList(typeVO);
    }
}