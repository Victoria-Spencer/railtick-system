package org.rail.orderservice.orderservice.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.TypeReference;
import com.github.pagehelper.PageHelper;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.rail.api.client.TicketFeignClient;
import org.rail.api.client.UserFeignClient;
import org.rail.api.constant.OrderTypeConstants;
import org.rail.common.redis.constant.RedisConstants;
import org.rail.common.core.exception.BusinessException;
import org.rail.common.core.exception.OpenFeignException;
import org.rail.common.core.exception.OrderNotFoundException;
import org.rail.common.redis.result.AggCacheResult;
import org.rail.common.core.result.PageResult;
import org.rail.common.core.result.Result;
import org.rail.common.redis.util.CacheClient;
import org.rail.api.dto.*;
import org.rail.orderservice.constant.PreOrderStatusConstants;
import org.rail.api.constant.SeatIntervalStatusConstants;
import org.rail.orderservice.mapper.OrderMapper;
import org.rail.orderservice.orderservice.OrderService;
import org.rail.orderservice.pojo.dto.*;
import org.rail.orderservice.pojo.entity.Order;
import org.rail.orderservice.pojo.entity.OrderDetails;
import org.rail.orderservice.pojo.entity.PreOrder;
import org.rail.orderservice.pojo.entity.PreOrderDetails;
import org.rail.orderservice.pojo.vo.CreateOrderVO;
import org.rail.orderservice.pojo.vo.OrderDetailsVO;
import org.rail.orderservice.pojo.vo.OrderPageQueryVO;
import org.rail.orderservice.pojo.vo.SelfTicketPageVO;
import org.rail.orderservice.util.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    // 从配置文件注入预订单有效期（分钟）
    @Value("${order.pre.expire-minutes : 15}") // 默认15分钟
    private Integer preOrderExpireMinutes;
    @Autowired
    private UserFeignClient userFeignClient;
    @Autowired
    private TicketFeignClient ticketFeignClient;
    @Autowired
    private CacheClient cacheClient;

    /**
     * 1.创建预订单，临时锁定座位
     * 2.避免造成长期锁座现象
     * （
     *      2.1.同一用户 + 同一车次的预订单 “覆盖机制”
     *      2.2.TODO 退出选座界面：立即释放座位
     *      2.3.TODO 定时任务清理过期预订单
     *  ）
     * @param createPreOrderDTO
     * @return
    // 全局事务
    @GlobalTransactional
    public String createPreOrder(CreatePreOrderDTO createPreOrderDTO) {
        // 1.根据用户ID和列车ID，查询是否已存在预订单
        PreOrder preOrder = orderMapper.getByPreOrderUserIdAndTrainId(createPreOrderDTO.getUserId(), createPreOrderDTO.getTrainId());
        // 2.若已经存在，则直接修改
        if(preOrder != null) {
            // TODO 订单明细不同，创建新订单
            // 获取旧预订单的明细
            List<PreOrderDetails> oldDetails = orderMapper.getTempSeatInfoByPreOrderId(preOrder.getId());
            // 获取新提交的明细（乘客+选座）
            List<PassengerOrderDetailDTO> newPassengers = createPreOrderDTO.getPassengerOrderDetailDTOList();
            List<ChooseSeatDTO> newSeats = createPreOrderDTO.getChooseSeats();

            // 判断明细是否不同（乘客数量/信息、选座信息不一致）
            boolean isDetailsDifferent = checkPreOrderDetailsDifferent(oldDetails, newPassengers, newSeats);

            // 若明细不同 → 先删除旧预订单（释放座位锁），再创建新订单
            if (isDetailsDifferent) {
                // ===== 删除旧预订单前，先释放关联的座位锁 =====
                releaseOldPreOrderSeatLock(preOrder, oldDetails);
                // ===== 删除旧预订单的明细和主记录 =====
                deleteOldPreOrder(preOrder.getId());
                // 创建新预订单
                return createNewPreOrder(createPreOrderDTO);
            }

            // 重新生成过期时间和预订单状态
            preOrder.setExpireTime(calculateExpireTime());
            preOrder.setStatus(PreOrderStatusConstants.VALID);
            preOrder.setCreateTime(LocalDateTime.now());
            orderMapper.updatePreOrder(preOrder);

            // 修改临时座位信息
            Long preOrderId = preOrder.getId();
            List<PreOrderDetails> preOrderDetailsList = orderMapper.getTempSeatInfoByPreOrderId(preOrderId);
            List<ChooseSeatDTO> chooseSeats = createPreOrderDTO.getChooseSeats();

            // 校验预订单详情列表不为空（必须有乘客信息）
            if (preOrderDetailsList == null || preOrderDetailsList.isEmpty()) {
                throw new IllegalArgumentException("预订单详情列表不能为空");
            }

            // 构建远程调用的条件，【先前的座位信息，标记为已释放】
            SeatIntervalOccupyDTO sioDTO = new SeatIntervalOccupyDTO();
            if (preOrderDetailsList.get(0).getTempSeatNo() != null) {
                List<SeatIntervalOccupyUpdateDTO> updateDTOList = new ArrayList<>();
                updateDTOList = BeanUtil.copyToList(
                        preOrderDetailsList,
                        SeatIntervalOccupyUpdateDTO.class,
                        CopyOptions
                                .create()
                                .setFieldMapping(new HashMap<>() {{
                                    put("preOrderId", "orderId");
                                    put("tempSeatNo", "seatNo");
                                }})
                );

                Long trainId = preOrder.getTrainId(); // 列车ID
                for (SeatIntervalOccupyUpdateDTO updateDTO : updateDTOList) {
                    updateDTO.setTrainId(trainId);
                    updateDTO.setOrderType(OrderTypeConstants.PREORDER); // 订单类型为预订单
                    updateDTO.setStatus(SeatIntervalStatusConstants.RELEASED); // 座位占用已释放
                }
                sioDTO.setUpdateDTOList(updateDTOList);
            }

            // 处理“取消选座”场景（chooseSeats为空）
            if (chooseSeats == null || chooseSeats.isEmpty()) {
                // 清空所有座位信息
                for (PreOrderDetails details : preOrderDetailsList) {
                    details.setCarriageNumber(null);
                    details.setTempSeatNo(null);
                }
            } else {
                // 处理“选座”场景（校验数量匹配后更新）
                if (preOrderDetailsList.size() != chooseSeats.size()) {
                    throw new IllegalArgumentException("预订单详情列表与座位数量不匹配");
                }

                List<SeatIntervalOccupyInsertDTO> insertDTOList = new ArrayList<>();

                for (int i = 0; i < preOrderDetailsList.size(); i++) {
                    PreOrderDetails details = preOrderDetailsList.get(i);
                    ChooseSeatDTO seat = chooseSeats.get(i);
                    details.setCarriageNumber(seat.getCarriageNumber());
                    details.setTempSeatNo(seat.getTempSeatNo());

                    // 构建远程调用的条件，【现在的座位信息，新增占用记录】
                    SeatIntervalOccupyInsertDTO insertDTO = new SeatIntervalOccupyInsertDTO();
                    BeanUtil.copyProperties(
                            details,
                            insertDTO,
                            CopyOptions
                                    .create()
                                    .setFieldMapping(new HashMap<>() {{
                                        put("preOrderId", "orderId");
                                        put("tempSeatNo", "seatNo");
                                    }})
                    );
                    insertDTO.setTrainId(preOrder.getTrainId());
                    insertDTO.setDepartureCode(createPreOrderDTO.getDepartureCode());
                    insertDTO.setArrivalCode(createPreOrderDTO.getArrivalCode());
                    insertDTO.setOrderType(OrderTypeConstants.PREORDER); // 订单类型为预订单
                    insertDTO.setStatus(SeatIntervalStatusConstants.LOCKED); // 座位锁定中
                    insertDTOList.add(insertDTO);
                }

                sioDTO.setInsertDTOList(insertDTOList);
            }

            // 执行更新
            orderMapper.updatePreOrderDetailsList(preOrderDetailsList);

            // 远程调用，修改占用区间与更新座位状态
            if (sioDTO != null && !sioDTO.isEmpty()){
                ticketFeignClient.updateSeatStatus(sioDTO);
            }

            return preOrder.getPreOrderSn();
        }

        // 3.未存在，则重新生成
        return createNewPreOrder(createPreOrderDTO);
    }*/

    /**
     * 1.创建预订单，临时锁定座位
     * 2.避免造成长期锁座现象
     * （
     *      2.1.同一用户 + 同一车次的预订单 “覆盖机制”
     *      2.2.TODO 退出选座界面：立即释放座位
     *      2.3.TODO 定时任务清理过期预订单
     *  ）
     * @param createPreOrderDTO 预订单创建参数（包含用户ID、列车ID、乘客信息、选座信息等）
     * @return 预订单号（唯一标识预订单，格式：PRE + 雪花ID）
     */
    // 全局事务
    @GlobalTransactional
    public String createPreOrder(CreatePreOrderDTO createPreOrderDTO) {
        // 1. 根据用户ID和列车ID，查询是否已存在预订单
        PreOrder preOrder = orderMapper.getByPreOrderUserIdAndTrainId(createPreOrderDTO.getUserId(), createPreOrderDTO.getTrainId());

        if(preOrder != null) {
            // 2. 有旧预订单：保留主记录，仅替换明细
            // 2.1 释放旧明细关联的座位锁
            List<PreOrderDetails> oldDetails = orderMapper.getTempSeatInfoByPreOrderId(preOrder.getId());
            releaseOldPreOrderSeatLock(preOrder, oldDetails);

            // 2.2 删除旧明细（仅删明细，不删主记录）
            orderMapper.deletePreOrderDetailsByPreOrderId(preOrder.getId());

            // 2.3 更新预订单主记录（重置过期时间、状态）
            // 修复：新增总金额计算并更新
            Double newTotalAmount = createPreOrderDTO.getPassengerOrderDetailDTOList().stream()
                    .map(PassengerOrderDetailDTO::getAmount)
                    .reduce(0.0, Double::sum);
            preOrder.setTotalAmount(newTotalAmount);
            preOrder.setExpireTime(calculateExpireTime());
            preOrder.setStatus(PreOrderStatusConstants.VALID);
            orderMapper.updatePreOrder(preOrder);

            // 2.4 插入新明细
            insertNewPreOrderDetails(preOrder.getId(), createPreOrderDTO);

            return preOrder.getPreOrderSn(); // 预订单号不变
        } else {
            // 3. 无旧预订单：创建新预订单+新明细
            return createNewPreOrder(createPreOrderDTO);
        }
    }

    /**
     * 释放旧预订单关联的座位锁
     * @param oldPreOrder 旧预订单主记录（用于获取列车ID等核心信息）
     * @param oldDetails  旧预订单明细（包含需要释放的座位信息）
     */
    private void releaseOldPreOrderSeatLock(PreOrder oldPreOrder, List<PreOrderDetails> oldDetails) {
        // 1. 空值校验：无旧明细则无需释放座位锁，直接返回
        if (oldDetails == null || oldDetails.isEmpty()) {
            return;
        }

        // 2. 构建座位锁释放的核心DTO（用于远程调用票务服务更新座位状态）
        SeatIntervalOccupyDTO releaseDTO = new SeatIntervalOccupyDTO();

        // 3. 将旧预订单明细转换为座位状态更新DTO列表
        // 字段映射：preOrderId → orderId（票务服务统一用orderId标识订单/预订单）、tempSeatNo → seatNo
        List<SeatIntervalOccupyUpdateDTO> updateDTOList = BeanUtil.copyToList(
                oldDetails,
                SeatIntervalOccupyUpdateDTO.class,
                CopyOptions.create()
                        .setFieldMapping(new HashMap<>() {{
                            put("preOrderId", "orderId");  // 预订单ID映射为票务服务的orderId
                            put("tempSeatNo", "seatNo");    // 临时座位号映射为正式座位号
                        }})
        );

        // 4. 为每个更新DTO补充必要的业务字段（保证票务服务能正确识别并释放对应座位）
        Long trainId = oldPreOrder.getTrainId(); // 从旧预订单获取列车ID
        for (SeatIntervalOccupyUpdateDTO updateDTO : updateDTOList) {
            updateDTO.setTrainId(trainId);                          // 绑定列车ID，避免跨车次释放座位
            updateDTO.setOrderType(OrderTypeConstants.PREORDER);    // 标记为预订单类型（区分正式订单）
            updateDTO.setStatus(SeatIntervalStatusConstants.RELEASED); // 座位状态改为「已释放」
        }

        // 5. 将更新列表设置到核心DTO中
        releaseDTO.setUpdateDTOList(updateDTOList);

        // 6. 远程调用票务服务释放座位锁（捕获异常，保证分布式事务能感知失败并回滚）
        try {
            if (!releaseDTO.isEmpty()) { // 非空校验：避免空调用
                ticketFeignClient.updateSeatStatus(releaseDTO);
            }
        } catch (Exception e) {
            // 抛出业务异常，触发Seata全局事务回滚（避免「预订单已删但座位未释放」的脏数据）
            throw new OpenFeignException("释放旧预订单座位锁失败：" + e.getMessage());
        }
    }

    /**
     * 插入新预订单明细
     * @param preOrderId 预订单ID
     * @param createPreOrderDTO 入参
     */
    private void insertNewPreOrderDetails(Long preOrderId, CreatePreOrderDTO createPreOrderDTO) {
        List<PassengerOrderDetailDTO> passengerOrderDetailDTOList = createPreOrderDTO.getPassengerOrderDetailDTOList();
        List<ChooseSeatDTO> chooseSeats = createPreOrderDTO.getChooseSeats();

        // 校验乘客明细不能为空
        if (passengerOrderDetailDTOList == null || passengerOrderDetailDTOList.isEmpty()) {
            throw new IllegalArgumentException("预订单详情列表不能为空");
        }

        // 构建新明细列表
        List<PreOrderDetails> preOrderDetailsList = new ArrayList<>();
        SeatIntervalOccupyDTO sioDTO = new SeatIntervalOccupyDTO();
        List<SeatIntervalOccupyInsertDTO> insertDTOList = new ArrayList<>();

        for (int i = 0; i < passengerOrderDetailDTOList.size(); i++) {
            // 默认不选座
            PassengerOrderDetailDTO passengerDTO = passengerOrderDetailDTOList.get(i);
            PreOrderDetails preOrderDetails = BeanUtil.copyProperties(passengerDTO, PreOrderDetails.class);
            preOrderDetails.setPreOrderId(preOrderId);
            preOrderDetails.setCarriageNumber(null);
            preOrderDetails.setTempSeatNo(null);

            // 处理选座逻辑
            if (chooseSeats != null && !chooseSeats.isEmpty() && i < chooseSeats.size()) {
                ChooseSeatDTO seat = chooseSeats.get(i);
                preOrderDetails.setCarriageNumber(seat.getCarriageNumber());
                preOrderDetails.setTempSeatNo(seat.getTempSeatNo());

                // 构建座位锁定DTO
                SeatIntervalOccupyInsertDTO insertDTO = new SeatIntervalOccupyInsertDTO();
                BeanUtil.copyProperties(
                        preOrderDetails,
                        insertDTO,
                        CopyOptions.create().setFieldMapping(new HashMap<>() {{
                            put("preOrderId", "orderId");
                            put("tempSeatNo", "seatNo");
                        }})
                );
                insertDTO.setTrainId(createPreOrderDTO.getTrainId());
                insertDTO.setDepartureCode(createPreOrderDTO.getDepartureCode());
                insertDTO.setArrivalCode(createPreOrderDTO.getArrivalCode());
                insertDTO.setOrderType(OrderTypeConstants.PREORDER);
                insertDTO.setStatus(SeatIntervalStatusConstants.LOCKED);
                insertDTOList.add(insertDTO);
            }

            preOrderDetailsList.add(preOrderDetails);
        }

        // 插入新明细
        orderMapper.batchInsertPreOrderDetails(preOrderDetailsList);

        // 远程调用锁定新座位
        sioDTO.setInsertDTOList(insertDTOList);
        if (!sioDTO.isEmpty()) {
            ticketFeignClient.updateSeatStatus(sioDTO);
        }
    }

    /**
     * 创建订单，并返回订单数据
     * @param createOrderDTO 订单创建参数（包含预订单号、支付信息等）
     * @return 订单数据（订单号、订单明细等）
     */
    // 全局事务
    @GlobalTransactional
    public CreateOrderVO createOrder(CreateOrderDTO createOrderDTO) {
        /**       插入订单数据        **/
        // 1. 查询预订单数据
        String preOrderSn = createOrderDTO.getPreOrderSn();
        PreOrder preOrder = orderMapper.getByPreOrderSn(preOrderSn);
        if(preOrder == null){
            throw new OrderNotFoundException("预订单不存在！");
        }
        // 2.预订单数据拷贝
        Order order = BeanUtil.copyProperties(preOrder, Order.class);
        // 3.orderSn（雪花算法随机生成）
        order.setOrderSn(SnowflakeIdGenerator.generateOrderSn());
        // 4.createTime, updateTime
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.insertOrder(order);


        /**       插入订单明细数据        **/
        // 1.获取预订单明细数据
        Long preOrderId = preOrder.getId();
        List<PreOrderDetails> preOrderDetailsList = orderMapper.getDetailsByPreOrderId(preOrderId);

        // 2.预订单明细数据拷贝
        List<OrderDetails> orderDetailsList = BeanUtil.copyToList(
                preOrderDetailsList,
                OrderDetails.class,
                CopyOptions.create()
                        .setFieldMapping(new HashMap<String, String>(){{
                            put("id", "preOrderDetailId");
                            put("tempSeatNo", "seatNo");
                        }})
                );

        // 3.遍历orderDetails,拷贝属性并设置外键
        for (OrderDetails orderDetails : orderDetailsList) {
            BeanUtil.copyProperties(createOrderDTO, orderDetails);
            // 设置外键
            orderDetails.setOrderId(order.getId());
        }

        // 构建远程调用的条件
        SeatIntervalOccupyDTO sioDTO = new SeatIntervalOccupyDTO();

        // 4.判断座位是否为空，若为空，则随机分配
        String seatNo = orderDetailsList.getFirst().getSeatNo();
        if (seatNo == null) {
            Map<Integer, List<SeatDTO>> seatTypeToSeatsMap = getSeatTypeToSeatsMap(order, orderDetailsList);

            // 遍历orderDetailsList，为每个乘客添加座位信息
            for (OrderDetails orderDetails : orderDetailsList) {
                List<SeatDTO> pairs = seatTypeToSeatsMap.get(orderDetails.getSeatType());

                if (pairs == null) {
                    throw new BusinessException("无可分配座位");
                }
                SeatDTO first = pairs.getFirst();
                String carriageNumber = first.getCarriageNumber();
                String currentSeatNo = first.getSeatNo();
                orderDetails.setCarriageNumber(carriageNumber);
                orderDetails.setSeatNo(currentSeatNo);
                pairs.remove(first);
            }
            // 座位占用区间，【新增占用记录】
            List<SeatIntervalOccupyInsertDTO> insertDTOList = BeanUtil.copyToList(orderDetailsList, SeatIntervalOccupyInsertDTO.class);
            for (SeatIntervalOccupyInsertDTO insertDTO : insertDTOList) {
                insertDTO.setTrainId(order.getTrainId());
                // 订单类型为订单
                insertDTO.setOrderType(OrderTypeConstants.ORDER);
                // 座位区间状态锁定中
                insertDTO.setStatus(SeatIntervalStatusConstants.LOCKED);
            }
            sioDTO.setInsertDTOList(insertDTOList);
        } else {
            // 预订单修改占用状态为已释放
            List<SeatIntervalOccupyUpdateDTO> updateDTOList = BeanUtil.copyToList(
                    preOrderDetailsList,
                    SeatIntervalOccupyUpdateDTO.class,
                    CopyOptions
                            .create()
                            .setFieldMapping(new HashMap<>(){{
                                put("tempSeatNo", "seatNo");
                                put("preOrderId", "orderId");
                            }})
            );
            for (SeatIntervalOccupyUpdateDTO updateDTO : updateDTOList) {
                updateDTO.setTrainId(preOrder.getTrainId());
                updateDTO.setOrderType(OrderTypeConstants.PREORDER);
                updateDTO.setStatus(SeatIntervalStatusConstants.RELEASED);
            }
            sioDTO.setUpdateDTOList(updateDTOList);


            // 订单新生成占用区间
            List<SeatIntervalOccupyInsertDTO> insertDTOList = BeanUtil.copyToList(orderDetailsList, SeatIntervalOccupyInsertDTO.class);
            for (SeatIntervalOccupyInsertDTO insertDTO : insertDTOList) {
                insertDTO.setTrainId(order.getTrainId());
                insertDTO.setOrderType(OrderTypeConstants.ORDER);
                insertDTO.setStatus(SeatIntervalStatusConstants.LOCKED);
            }
            sioDTO.setInsertDTOList(insertDTOList);
        }

        orderMapper.batchInsertOrderDetails(orderDetailsList);
        // 标记预订单为已转为正式订单
        markPreOrderStatus2(preOrderId);

        // 远程调用，修改区间和更新座位的状态
        if (!sioDTO.isEmpty()) {
            ticketFeignClient.updateSeatStatus(sioDTO);
        }


        /**        封装数据,返回        **/
        CreateOrderVO createOrderVO = BeanUtil.copyProperties(createOrderDTO, CreateOrderVO.class);
        // 设置订单号
        createOrderVO.setOrderSn(preOrder.getPreOrderSn());
        List<OrderDetailsVO> createOrderDetailsVOS = BeanUtil.copyToList(orderDetailsList, OrderDetailsVO.class);
        // 设置订单明细数据
        createOrderVO.setCreateOrderDetailsVOList(createOrderDetailsVOS);

        return createOrderVO;
    }

    private Map<Integer, List<SeatDTO>> getSeatTypeToSeatsMap(Order order, List<OrderDetails> orderDetailsList) {
        // 远程调用，判断是否还有空座位（车厢号，座位号），若有则返回
        // 列车ID, 席别类型，出发站点编码，到达站点编码
        RandomSeatQueryDTO randomSeatQueryDTO = new RandomSeatQueryDTO();
        randomSeatQueryDTO.setTrainId(order.getTrainId());
        // 提取每个乘客的席别类型
        List<Integer> seatTypes = orderDetailsList.stream()
                .map(OrderDetails::getSeatType)
                .collect(Collectors.toList());
        randomSeatQueryDTO.setSeatTypes(seatTypes);
        randomSeatQueryDTO.setDepartureCode(orderDetailsList.getFirst().getDepartureCode());
        randomSeatQueryDTO.setArrivalCode(orderDetailsList.getFirst().getArrivalCode());

        // 远程调用
        Result<List<AvailableSeatDTO>> feignResult = ticketFeignClient.getAvailableSeats(randomSeatQueryDTO);
        if (!feignResult.isSuccess()) {
            throw new OpenFeignException(feignResult.getMessage());
        }

        List<AvailableSeatDTO> availableSeatDTOList = feignResult.getData();
        if (availableSeatDTOList == null || availableSeatDTOList.size() < seatTypes.size()) {
            throw new BusinessException("可用座位数不足");
        }

        // 核心：构建席别类型与（车厢号，座位号）列表的映射Map
        Map<Integer, List<SeatDTO>> seatTypeToSeatsMap = new HashMap<>();

        for (AvailableSeatDTO seatDTO : availableSeatDTOList) {
            // 从可用座位DTO中获取席别类型
            Integer seatType = seatDTO.getSeatType();
            // 获取车厢号和座位号
            String carriageNumber = seatDTO.getCarriageNumber();
            String currentSeatNo = seatDTO.getSeatNo();

            // 为当前席别类型初始化列表（若不存在则创建），并添加座位信息
            seatTypeToSeatsMap.computeIfAbsent(seatType, k -> new ArrayList<>())
                    .add(new SeatDTO(carriageNumber, currentSeatNo));
        }
        return seatTypeToSeatsMap;
    }

    /**
     * 分页查询订单
     * @param orderPageQueryDTO 订单分页查询参数（包含用户ID、订单状态、订单类型、日期范围、车次等查询条件，以及分页参数）
     * @return 订单分页数据（分页结果 + 依赖的单表Key列表，用于构建聚合缓存）
     */
    public PageResult<OrderPageQueryVO> orderPageQuery(OrderPageQueryDTO orderPageQueryDTO) {
        String aggKey = buildOrderPageCacheKey(orderPageQueryDTO);
        // 缓存订单分页查询信息
        TypeReference<PageResult<OrderPageQueryVO>> typeRef = new TypeReference<>() {};
        return cacheClient.queryAggCache(
                aggKey,
                typeRef,
                // 缓存未命中时，查库
                this::queryOrderPageDb,
                orderPageQueryDTO,
                RedisConstants.RAIL_DEFAULT_TTL,
                TimeUnit.MINUTES
        );
        // 分页查询
        /*PageHelper.startPage(orderPageQueryDTO.getPageNumber(), orderPageQueryDTO.getPageSize());
        List<OrderPageQueryVO> orderPageQueryVOList = orderMapper.getOrderPageByQueryDTO(orderPageQueryDTO);
        return new PageResult<>(orderPageQueryVOList);*/
    }

    /**
     * 订单分页缓存未命中时：数据库查询 + 构建聚合缓存依赖Key
     * @param dto 订单分页查询参数
     * @return 聚合缓存结果（分页数据 + 依赖单表Key）
     */
    private AggCacheResult<PageResult<OrderPageQueryVO>> queryOrderPageDb(OrderPageQueryDTO dto) {
        // 分页必须放在dbFallback内部（PageHelper线程绑定）
        PageHelper.startPage(dto.getPageNumber(), dto.getPageSize());
        List<OrderPageQueryVO> orderPageQueryVOList = orderMapper.getOrderPageByQueryDTO(dto);

        // 组装所有依赖的单表Key
        List<String> dependSingleKeys = new ArrayList<>();
        for (OrderPageQueryVO orderPageQueryVO : orderPageQueryVOList) {
            // orderKey
            String orderKey = RedisConstants.RAIL_ORDER_PREFIX + orderPageQueryVO.getOrderSn();
            dependSingleKeys.add(orderKey);

            // detailKeys
            List<OrderDetailsVO> detailsVOList = orderPageQueryVO.getOrderDetailsVOList();
            List<String> detailKeys = detailsVOList.stream()
                    .map(detail -> RedisConstants.RAIL_ORDER_DETAILS_PREFIX + detail.getId())
                    .toList();
            dependSingleKeys.addAll(detailKeys);
        }

//                    return new PageResult<>(orderPageQueryVOList);
        return AggCacheResult.of(new PageResult<>(orderPageQueryVOList), dependSingleKeys);
    }

    /**
     * 生成订单唯一的缓存Key
     * @param dto 订单分页查询参数（包含用户ID、订单状态、订单类型、日期范围、车次等查询条件，以及分页参数）
     * @return 订单分页查询的唯一缓存Key（格式：前缀 + 用户ID + : + 各查询条件 + 分页参数，确保同一用户相同查询条件的请求命中同一缓存）
     */
    private String buildOrderPageCacheKey(OrderPageQueryDTO dto) {
        String prefix = RedisConstants.RAIL_AGG_ORDER_PAGE_USER_PREFIX + dto.getUserId() + ":";

        // 拼接所有非空的查询条件和分页参数
        String conditions = Stream.of(
                "orderStatus:" + Objects.toString(dto.getOrderStatus(), ""),
                "orderType:" + Objects.toString(dto.getOrderType(), ""),
                "startDate:" + Objects.toString(dto.getStartDate(), ""),
                "endDate:" + Objects.toString(dto.getEndDate(), ""),
                "orderSn:" + Objects.toString(dto.getOrderSn(), ""),
                "trainNumber:" + Objects.toString(dto.getTrainNumber(), ""),
                "realName:" + Objects.toString(dto.getRealName(), ""),
                "page:" + dto.getPageNumber(),
                "size:" + dto.getPageSize()
        ).filter(s -> !s.endsWith(":")).collect(Collectors.joining(":"));

        return prefix + conditions;
    }

    /**
     * 分页查询本人车票
     * @param frontSelfTicketPageDTO 本人车票分页查询参数（包含用户ID、车票状态、日期范围、车次等查询条件，以及分页参数）
     * @return 本人车票分页数据（分页结果 + 依赖的单表Key列表）
     */
    @GlobalTransactional
    public PageResult<SelfTicketPageVO> selfTicketPageQuery(FrontSelfTicketPageDTO frontSelfTicketPageDTO) {
        String aggKey = buildSelfTicketCacheKey(frontSelfTicketPageDTO);
        TypeReference<PageResult<SelfTicketPageVO>> typeRef = new TypeReference<>() {};
        return cacheClient.queryAggCache(
                aggKey,
                typeRef,
                this::loadSelfTicketFromDb,
                frontSelfTicketPageDTO,
                RedisConstants.RAIL_DEFAULT_TTL,
                TimeUnit.MINUTES
        );
    }

    private AggCacheResult<PageResult<SelfTicketPageVO>> loadSelfTicketFromDb(FrontSelfTicketPageDTO frontSelfTicketPageDTO) {
        // 远程调用user-service，根据userId查询idType和idCard，UserIdCardDTO
        Result<UserIdCardDTO> userIdCardDTOResult = userFeignClient.getIdCardInfo(frontSelfTicketPageDTO.getUserId());
        if(!userIdCardDTOResult.isSuccess()) {
            throw new OpenFeignException(userIdCardDTOResult.getMessage());
        }
        UserIdCardDTO userIdCardDTO = userIdCardDTOResult.getData();

        // 拷贝
        SelfTicketPageDTO selfTicketPageDTO = BeanUtil.copyProperties(frontSelfTicketPageDTO, SelfTicketPageDTO.class);
        selfTicketPageDTO.setIdType(userIdCardDTO.getIdType());
        selfTicketPageDTO.setIdCard(userIdCardDTO.getIdCard());

        // 分页查询
        PageHelper.startPage(selfTicketPageDTO.getPageNumber(), selfTicketPageDTO.getPageSize());
        List<SelfTicketPageVO> selfTicketPageVOList = orderMapper.getSelfTicketPageByQueryDTO(selfTicketPageDTO);

        // 组装所有依赖的单表Key
        List<String> selfTicketKeys = selfTicketPageVOList.stream()
                .map(selfTicketPageVO -> RedisConstants.RAIL_SELF_TICKET_PREFIX + selfTicketPageVO.getId())
                .toList();
        List<String> dependSingleKeys = new ArrayList<>(selfTicketKeys);

//        return new PageResult<>(SelfTicketPageVOList);
        return AggCacheResult.of(new PageResult<>(selfTicketPageVOList), dependSingleKeys);
    }

    /**
     * 生成本人车票查询的唯一缓存Key
     * 包含：身份证信息 + 车票状态 + 日期范围 + 车次 + 分页参数
     */
    private String buildSelfTicketCacheKey(FrontSelfTicketPageDTO dto) {
        // 先远程获取idType和idCard，用于拼接Key（保证Key唯一性）
        String idType = "";
        String idCard = "";
        Result<UserIdCardDTO> userIdCardDTOResult = userFeignClient.getIdCardInfo(dto.getUserId());
        if (userIdCardDTOResult.isSuccess() && userIdCardDTOResult.getData() != null) {
            idType = Objects.toString(userIdCardDTOResult.getData().getIdType(), "");
            idCard = Objects.toString(userIdCardDTOResult.getData().getIdCard(), "");
        }

        // 拼接所有非空查询条件和分页参数
        String conditions = Stream.of(
                "idType:" + idType,
                "idCard:" + idCard,
                "ticketType:" + Objects.toString(dto.getTicketType(), ""),
                "startDate:" + Objects.toString(dto.getStartDate(), ""),
                "endDate:" + Objects.toString(dto.getEndDate(), ""),
                "trainNumber:" + Objects.toString(dto.getTrainNumber(), ""),
                "page:" + dto.getPageNumber(),
                "size:" + dto.getPageSize()
        ).filter(s -> !s.endsWith(":")).collect(Collectors.joining(":"));

        return RedisConstants.RAIL_TICKET_SELF_PAGE_PREFIX + conditions;
    }

    /**
     * 取消车票订单
     * @param orderSn 订单号
     */
    public void cancelOrder(String orderSn) {
        orderMapper.updateOrderByOrderSn(orderSn);
        // 自动清理订单及相关聚合key
        cacheClient.autoClearAggCache(RedisConstants.RAIL_ORDER_PREFIX + orderSn);
    }

    /**
     * 标记预订单为已转为正式订单
     * @param preOrderId 预订单ID
     */
    private void markPreOrderStatus2(Long preOrderId) {
        /**        标记预订单为已转为正式订单        **/
        PreOrder newPreOrder = new PreOrder();
        newPreOrder.setId(preOrderId);
        newPreOrder.setStatus(PreOrderStatusConstants.CONVERTED_TO_ORDER);
        orderMapper.updatePreOrder(newPreOrder);
    }

    /**
     * 重新生成新的预订单
     * @param createPreOrderDTO 预订单创建参数（包含用户ID、列车ID、乘客信息、选座信息等）
     * @return 新预订单号（唯一标识预订单，格式：PRE + 雪花ID）
     */
    private String createNewPreOrder(CreatePreOrderDTO createPreOrderDTO) {
        // 1.生成预订单对象
        PreOrder preOrder = BeanUtil.copyProperties(createPreOrderDTO, PreOrder.class);
        // 用雪花算法生成预订单号
        String preOrderSn = SnowflakeIdGenerator.generatePreOrderSn();
        preOrder.setPreOrderSn(preOrderSn);
        preOrder.setExpireTime(calculateExpireTime());
        preOrder.setCreateTime(LocalDateTime.now());

        Double totalAmount = 0.0;
        for (PassengerOrderDetailDTO passengerOrderDetailDTO : createPreOrderDTO.getPassengerOrderDetailDTOList()) {
            // 计算总金额
            totalAmount += passengerOrderDetailDTO.getAmount();
        }

        // 2.插入预订单到数据库,并返回订单id
        preOrder.setTotalAmount(totalAmount);
        orderMapper.insertPreOrder(preOrder);
        Long preOrderId = preOrder.getId();


        // 3.生成预订单明细对象列表
        List<PassengerOrderDetailDTO> passengerOrderDetailDTOList = createPreOrderDTO.getPassengerOrderDetailDTOList();
        // 校验预订单详情列表不为空（必须有乘客信息）
        if (passengerOrderDetailDTOList == null || passengerOrderDetailDTOList.isEmpty()) {
            throw new IllegalArgumentException("预订单详情列表不能为空");
        }

        List<PreOrderDetails> preOrderDetailsList = new ArrayList<>();
        List<ChooseSeatDTO> chooseSeats = createPreOrderDTO.getChooseSeats();
        // 构建远程调用的条件，更新占用信息和座位状态，【新的座位信息，标记为已占用】
        SeatIntervalOccupyDTO sioDTO = new SeatIntervalOccupyDTO();
        List<SeatIntervalOccupyInsertDTO> insertDTOList = new ArrayList<>();

        for (int i = 0; i < passengerOrderDetailDTOList.size(); i++) {
            PassengerOrderDetailDTO passengerDTO = passengerOrderDetailDTOList.get(i); // 第i个乘客
            // 拷贝乘客基本信息到预订单明细（姓名、证件等）
            PreOrderDetails preOrderDetails = BeanUtil.copyProperties(passengerDTO, PreOrderDetails.class);
            // 设置外键，车厢号和座位号
            preOrderDetails.setPreOrderId(preOrderId);
            // 初始化座位信息为null（默认不选座）
            preOrderDetails.setCarriageNumber(null);
            preOrderDetails.setTempSeatNo(null);

            // 若选座且座位列表不为空，补充座位信息
            if (chooseSeats != null && !chooseSeats.isEmpty()) {
                // 校验座位数量与乘客数量匹配
                if (i >= chooseSeats.size()) {
                    throw new IllegalArgumentException("座位列表数量少于乘客数量");
                }
                ChooseSeatDTO seat = chooseSeats.get(i);
                preOrderDetails.setCarriageNumber(seat.getCarriageNumber());
                preOrderDetails.setTempSeatNo(seat.getTempSeatNo());

                // 构建远程调用的条件，更新座位的状态（status），【座位信息】
                SeatIntervalOccupyInsertDTO insertDTO = new SeatIntervalOccupyInsertDTO();
                BeanUtil.copyProperties (
                        preOrderDetails,
                        insertDTO,
                        CopyOptions
                                .create()
                                .setFieldMapping(new HashMap<>(){{
                                    put("preOrderId", "orderId");
                                    put("tempSeatNo", "seatNo");
                                }})
                );
                insertDTO.setTrainId(createPreOrderDTO.getTrainId());
                insertDTO.setDepartureCode(createPreOrderDTO.getDepartureCode());
                insertDTO.setArrivalCode(createPreOrderDTO.getArrivalCode());
                insertDTO.setOrderType(OrderTypeConstants.PREORDER);
                insertDTO.setStatus(SeatIntervalStatusConstants.LOCKED);
                insertDTOList.add(insertDTO);
            }

            preOrderDetailsList.add(preOrderDetails);
        }


        // 4.插入预订单明细到数据库
        orderMapper.batchInsertPreOrderDetails(preOrderDetailsList);

        sioDTO.setInsertDTOList(insertDTOList);
        if(!sioDTO.isEmpty()) {
            // 5.远程调用，新增新的占用区间，并更新座位的状态
            ticketFeignClient.updateSeatStatus(sioDTO);
        }

        return preOrderSn;
    }

    /**
     * 计算过期时间
     * @return 过期时间（当前时间 + 预设的过期分钟数，默认15分钟）
     */
    public LocalDateTime calculateExpireTime() {
        // 1. 获取当前时间
        LocalDateTime now = LocalDateTime.now();

        // 2. 处理null情况（避免空指针，设置默认值，例如15分钟）
        int minutes = (preOrderExpireMinutes != null) ? preOrderExpireMinutes : 15;

        // 3. 计算过期时间：当前时间 + 过期分钟数
        return now.plusMinutes(minutes);
    }
}
