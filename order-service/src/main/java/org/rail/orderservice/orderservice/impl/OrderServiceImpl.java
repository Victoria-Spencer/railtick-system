package org.rail.orderservice.orderservice.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import org.rail.api.client.TicketFeignClient;
import org.rail.api.client.UserFeignClient;
import org.rail.api.constant.OrderTypeConstants;
import org.rail.orderservice.constant.OrderRedisConstants;
import org.rail.common.core.context.RequestContext;
import org.rail.common.core.context.RequestContextHolder;
import org.rail.common.core.exception.*;
import org.rail.common.core.util.LogUtils;
import org.rail.common.core.util.SnowflakeIdGenerator;
import org.rail.common.redis.api.ICacheClient;
import org.rail.common.core.constant.RedisCommonConstants;
import org.rail.common.redis.result.AggCacheResult;
import org.rail.common.core.model.result.PageResult;
import org.rail.common.core.model.result.Result;
import org.rail.api.dto.*;
import org.rail.orderservice.constant.OrderPaymentStatusConstants;
import org.rail.orderservice.constant.PreOrderStatusConstants;
import org.rail.api.constant.SeatIntervalStatusConstants;
import org.rail.orderservice.mapper.OrderMapper;
import org.rail.orderservice.model.bo.PreOrderContext;
import org.rail.orderservice.mq.producer.OrderCreateProducer;
import org.rail.orderservice.mq.producer.OrderDelayProducer;
import org.rail.orderservice.mq.producer.PreOrderDelayProducer;
import org.rail.orderservice.orderservice.OrderService;
import org.rail.orderservice.model.dto.*;
import org.rail.orderservice.model.entity.Order;
import org.rail.orderservice.model.entity.OrderDetails;
import org.rail.orderservice.model.entity.PreOrder;
import org.rail.orderservice.model.entity.PreOrderDetails;
import org.rail.orderservice.model.vo.*;
import org.rail.orderservice.util.OrderSnUtil;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    private ICacheClient cacheClient;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private PreOrderDelayProducer preOrderDelayProducer;
    @Autowired
    private OrderCreateProducer orderCreateProducer;
    @Autowired
    private OrderDelayProducer orderDelayProducer;

    /**
     * 1.创建预订单，临时锁定座位
     * 2.避免造成长期锁座现象
     * （
     *      *********************************************************************************
     *      引入Redisson分布式锁，锁粒度控制在「同一用户 + 同一车次」，确保同一用户对同一车次的预订单操作串行化，
     *      避免并发导致的数据不一致问题（如重复预订、座位锁定冲突等）
     *      双层锁
     *      第一层（业务锁）	用户ID + 车次ID	防止同一个用户重复创建预订单
     *      第二层（资源锁）	座位ID（车厢+座位号）	防止不同用户抢同一个座位（超卖）
     *      *********************************************************************************
     *      2.1. 同一用户 + 同一车次的预订单 “覆盖机制”
     *      2.2. TODO 退出选座界面：立即释放座位
     *      2.3. 过期时间，自动释放座位
     *      *********************************************************************************
     *  ）
     * @param createPreOrderDTO 预订单创建参数（包含用户ID、列车ID、乘客信息、选座信息等）
     * @return 预订单数据（预订单号、订单防重令牌），供前端展示和后续订单创建使用
     */
    // 全局事务
    @Override
//    @GlobalTransactional
    public String createPreOrder(CreatePreOrderDTO createPreOrderDTO) {
        RequestContext context = RequestContextHolder.getRequestContext();
        if (context == null || context.getAccountId() == null) {
            throw new BizException("请先登录");
        }
        long userId = Long.parseLong(context.getAccountId());
        createPreOrderDTO.setUserId(userId);
        Long trainId = createPreOrderDTO.getTrainId();
        String preOrderKey = buildPreOrderKey(userId, trainId);

        PreOrderContext ctx = preparePreOrderContext(createPreOrderDTO, preOrderKey);

        String lockKey = OrderRedisConstants.RAIL_LOCK_PRE_ORDER_CREATE_PREFIX + userId + ":" + trainId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean isLocked = lock.tryLock(3, 30, TimeUnit.SECONDS);

            if (!isLocked) {
                throw new UserConcurrentLockException("操作频繁，请稍后再试！");
            }

            PreOrder preOrder = cacheClient.get(preOrderKey);

            String preOrderSn;
            if(ObjectUtil.isNotNull(preOrder)) {
                preOrderSn = updateExistPreOrder(ctx, preOrder);
            } else {
                preOrderSn = createNewPreOrder(ctx);
            }

            preOrderDelayProducer.sendPreOrderDelayMsg(userId, trainId, preOrderSn);
            return preOrderSn;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UserOperateInterruptedException("预订单创建被中断，请重试", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 预订单上下文统一准备
     */
    private PreOrderContext preparePreOrderContext(CreatePreOrderDTO createPreOrderDTO, String preOrderKey) {
        PreOrderContext ctx = buildPreOrderContext(createPreOrderDTO);
        clearOldPreOrderSeatAndCache(preOrderKey, ctx);
        return ctx;
    }

    /**
     * 清理旧预订单缓存和座位锁
     */
    private void clearOldPreOrderSeatAndCache(String preOrderKey, PreOrderContext ctx) {
        PreOrder oldPreOrder = cacheClient.get(preOrderKey);
        if (ObjectUtil.isNotNull(oldPreOrder)) {
            String detailsKey = buildPreOrderDetailsSetKey(oldPreOrder.getId());
            List<PreOrderDetails> oldDetails = new ArrayList<>(cacheClient.getSetMembers(detailsKey));
            // 释放旧明细关联的座位锁
            releaseOldPreOrderSeatLock(ctx.getReqDTO(), oldPreOrder, oldDetails);
            // 删除旧明细（仅删明细，不删主记录）
            cacheClient.delete(detailsKey);
        }
    }

    /**
     * 锁外前置处理，封装业务上下文
     */
    private PreOrderContext buildPreOrderContext(CreatePreOrderDTO createPreOrderDTO) {
        List<Long> passengerIds = createPreOrderDTO.getPassengerOrderDetailDTOList().stream()
                .map(PassengerOrderDetailDTO::getId)
                .toList();
        Result<List<PassengerRemoteDTO>> passengerResult = userFeignClient.batchListPassenger(passengerIds);
        if (!passengerResult.isSuccess() || CollectionUtil.isEmpty(passengerResult.getData())) {
            throw new OpenFeignException("获取乘客信息失败");
        }

        PreOrderContext ctx = new PreOrderContext();
        ctx.setReqDTO(createPreOrderDTO);
        ctx.setPassengerList(passengerResult.getData());
        return ctx;
    }

    private String buildPreOrderKey(Long userId, Long trainId) {
        if (userId == null || trainId == null) {
            throw new IllegalArgumentException("userId 和 trainId 不能为 null");
        }
        return String.format("%s%s:%d:%s:%d",
                OrderRedisConstants.RAIL_PRE_ORDER_PREFIX, "userId", userId, "trainId", trainId);
    }

    /**
     * 更新已存在的预订单，保留主记录，仅替换明细
     */
    private String updateExistPreOrder(PreOrderContext ctx, PreOrder preOrder) {
        // 更新预订单主记录（重置过期时间、状态）
        updatePreOrderMainInfo(ctx.getReqDTO(), preOrder);

        // 插入新明细
        insertNewPreOrderDetails(ctx, preOrder.getId());

        return preOrder.getPreOrderSn();
    }

    private String buildPreOrderDetailsSetKey(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id（preOrder）不能为 null");
        }
        return String.format("%s%s:%d",
                OrderRedisConstants.RAIL_PRE_ORDER_DETAILS_PREFIX, "preOrderId", id);
    }

    /**
     * 更新预订单主表信息
     */
    private void updatePreOrderMainInfo(CreatePreOrderDTO createPreOrderDTO, PreOrder preOrder) {
        BigDecimal newTotalAmount = calculateTotalAmount(createPreOrderDTO.getPassengerOrderDetailDTOList());
        preOrder.setTotalAmount(newTotalAmount);
        preOrder.setExpireTime(calculateExpireTime());
        preOrder.setStatus(PreOrderStatusConstants.VALID);

        String preOrderKey = buildPreOrderKey(preOrder.getUserId(), preOrder.getTrainId());
        cacheClient.set(preOrderKey, preOrder, RedisCommonConstants.RAIL_DEFAULT_TTL, TimeUnit.MINUTES);
//        orderMapper.updatePreOrder(preOrder);
    }

    /**
     * 释放旧预订单关联的座位锁
     * @param oldPreOrder 旧预订单主记录（用于获取列车ID等核心信息）
     * @param oldDetails  旧预订单明细（包含需要释放的座位信息）
     */
    private void releaseOldPreOrderSeatLock(
            CreatePreOrderDTO createPreOrderDTO,
            PreOrder oldPreOrder,
            List<PreOrderDetails> oldDetails) {

        if (ObjectUtil.isEmpty(oldDetails)) {
            return;
        }

        BatchSeatIntervalInsertDTO batchSeatDTO = buildSeatReleaseDto(createPreOrderDTO, oldPreOrder, oldDetails);
        if (ObjectUtil.isNull(batchSeatDTO) || ObjectUtil.isNull(batchSeatDTO.getSeatList())) {
            return;
        }

        String params = batchSeatDTO.toString();

        Result<Void> feignResult = ticketFeignClient.updateSeatStatus(batchSeatDTO);

        if (!feignResult.isSuccess()) {
            LogUtils.error("OrderService", "releaseOldPreOrderSeatLock", "/api/ticket-service/ticket/seat-status/update",
                    params, feignResult.getMessage());
            throw new OpenFeignException("释放旧预订单座位锁失败：" + feignResult.getMessage());
        }

        LogUtils.info("OrderService", "releaseOldPreOrderSeatLock", "/api/ticket-service/ticket/seat-status/update",
                params, feignResult);
    }

    /**
     * 构建座位释放DTO
     */
    private BatchSeatIntervalInsertDTO buildSeatReleaseDto(
            CreatePreOrderDTO createPreOrderDTO,
            PreOrder oldPreOrder,
            List<PreOrderDetails> oldDetails) {

        // 初始化批量座位占用DTO
        BatchSeatIntervalInsertDTO batchSeatDTO = new BatchSeatIntervalInsertDTO();
        buildBatchSeatIntervalInsertDTO(createPreOrderDTO, oldPreOrder.getId(), batchSeatDTO);

        for (PreOrderDetails oldDetail : oldDetails) {
            SeatBaseDTO seatBaseDTO = new SeatBaseDTO();
            seatBaseDTO.setSeatType(oldDetail.getSeatType());
            seatBaseDTO.setCarriageNumber(oldDetail.getCarriageNumber());
            seatBaseDTO.setSeatNo(oldDetail.getTempSeatNo());

            batchSeatDTO.getSeatList().add(seatBaseDTO);
        }

        return batchSeatDTO;
    }

    /**
     * 构建单个预订单明细对象
     */
    private PreOrderDetails buildPreOrderDetail(
            Long preOrderId,
            PassengerOrderDetailDTO preOrderPsgrDTO,
            PassengerRemoteDTO actualPsgrDTO
    ) {
        long detailId = SnowflakeIdGenerator.nextId();

        PreOrderDetails preOrderDetails = new PreOrderDetails();
        preOrderDetails.setId(detailId);
        preOrderDetails.setPreOrderId(preOrderId);

        // 后端查询（敏感字段）
        preOrderDetails.setRealName(actualPsgrDTO.getRealName());
        preOrderDetails.setIdType(actualPsgrDTO.getIdType());
        preOrderDetails.setIdCard(actualPsgrDTO.getIdCard());

        //前端传递
        preOrderDetails.setTicketType(preOrderPsgrDTO.getTicketType());
        preOrderDetails.setSeatType(preOrderPsgrDTO.getSeatType());
        preOrderDetails.setAmount(preOrderPsgrDTO.getAmount());

        // 默认不选座
        preOrderDetails.setCarriageNumber(null);
        preOrderDetails.setTempSeatNo(null);
        return preOrderDetails;
    }

    /**
     * 创建订单，并返回订单数据
     * @param createOrderDTO 订单创建参数（包含预订单号、支付信息等）
     * @return 订单数据（订单号、订单明细等）
     */
    // 全局事务
    @Override
//    @GlobalTransactional
    public CreateOrderVO createOrder(CreateOrderDTO createOrderDTO) {
        RequestContext context = RequestContextHolder.getRequestContext();
        if (context == null) {
            throw new BizException("请先登录");
        }
        Long userId = Long.valueOf(context.getAccountId());
        Long trainId = createOrderDTO.getTrainId();

        String preOrderSn = createOrderDTO.getPreOrderSn();
        String orderVoCacheKey = OrderRedisConstants.RAIL_ORDER_CREATE_VO_PREFIX + preOrderSn;

        // 无锁快速拦截重复请求
        CreateOrderVO existVo = cacheClient.get(orderVoCacheKey, CreateOrderVO.class);
        if (ObjectUtil.isNotNull(existVo)) {
            return existVo;
        }

        String lockKey = OrderRedisConstants.RAIL_LOCK_ORDER_CREATE_PREFIX + preOrderSn;
        RLock lock = redissonClient.getLock(lockKey);

        Order order = null;
        List<OrderDetails> orderDetailsList = null;

        try {
            boolean isLocked = lock.tryLock(1, 30, TimeUnit.SECONDS);
            if (!isLocked) {
                throw new UserConcurrentLockException("操作频繁，请稍后再试！");
            }

            // 锁内双检，防止并发穿透
            existVo = cacheClient.get(orderVoCacheKey, CreateOrderVO.class);
            if (ObjectUtil.isNotNull(existVo)) {
                return existVo;
            }

            String preOrderKey = buildPreOrderKey(userId, trainId);
            PreOrder preOrder = cacheClient.get(preOrderKey);
            if (ObjectUtil.isNull(preOrder)) {
                throw new OrderNotFoundException("预订单不存在或已过期！");
            }

            if (!PreOrderStatusConstants.VALID.equals(preOrder.getStatus())) {
                throw new BizException("预订单状态无效，无法创建正式订单");
            }
            if (preOrder.getExpireTime().isBefore(LocalDateTime.now())) {
                throw new BizException("预订单已过期，请重新下单");
            }

            // 创建订单主表
            order = createOrderMain(preOrder);
            // 处理订单明细 & 座位分配
            orderDetailsList = handleOrderDetails(createOrderDTO, preOrder, order);

            try {
                orderCreateProducer.sendOrderCreateMsg(order, orderDetailsList);
                orderDelayProducer.sendOrderDelayMsg(order.getOrderSn());
            } catch (Exception e) {
                // MQ发送失败
                throw new BizException("订单创建失败，请重试");
            }

            String convertFlagKey = OrderRedisConstants.RAIL_PRE_ORDER_CONVERTED + preOrderSn;
            cacheClient.setIfAbsent(convertFlagKey, "converted", RedisCommonConstants.RAIL_DEFAULT_TTL, TimeUnit.MINUTES);
            // 彻底删除预订单缓存
            cacheClient.delete(preOrderKey);
            cacheClient.delete(buildPreOrderDetailsSetKey(preOrder.getId()));

            CreateOrderVO finalVo = buildCreateOrderVo(order.getOrderSn(), createOrderDTO, orderDetailsList);
            cacheClient.set(orderVoCacheKey, finalVo, RedisCommonConstants.RAIL_DEFAULT_TTL, TimeUnit.MINUTES);
            return finalVo;
        } catch (UserConcurrentLockException e) {
            throw e;
        } catch (Exception e) {
            cleanUpOnException(orderVoCacheKey, order);
            throw new BizException("订单创建失败，请重试");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 订单创建异常时的统一清理方法
     */
    private void cleanUpOnException(String orderVoCacheKey, Order order) {
        // 清理防重缓存
        cacheClient.delete(orderVoCacheKey);

        if (ObjectUtil.isNull(order)) {
            return;
        }

        String orderKey = buildOrderKey(order.getOrderSn());
        cacheClient.delete(orderKey);

        String orderDetailsHashKey = buildOrderDetailsHashKey(order.getId());
        cacheClient.delete(orderDetailsHashKey);
    }


    /**
     * 构建订单返回VO
     */
    private CreateOrderVO buildCreateOrderVo(String orderSn, CreateOrderDTO dto, List<OrderDetails> detailsList) {
        CreateOrderVO vo = BeanUtil.copyProperties(dto, CreateOrderVO.class);
        vo.setOrderSn(orderSn);
        List<OrderDetailsVO> createOrderDetailsVOS = BeanUtil.copyToList(detailsList, OrderDetailsVO.class);
        vo.setCreateOrderDetailsVOList(createOrderDetailsVOS);
        return vo;
    }

    /**
     * 处理订单明细和座位逻辑
     */
    private List<OrderDetails> handleOrderDetails(CreateOrderDTO createOrderDTO, PreOrder preOrder, Order order) {
        String preOrderDetailsKey = buildPreOrderDetailsSetKey(preOrder.getId());
        List<PreOrderDetails> preDetailsList = new ArrayList<>(this.cacheClient.getSetMembers(preOrderDetailsKey));
        // 拷贝订单明细
        List<OrderDetails> detailsList = BeanUtil.copyToList(
                preDetailsList,
                OrderDetails.class,
                CopyOptions.create()
                        .setFieldMapping(new HashMap<>(){{
                            put("id", "preOrderDetailId");
                            put("tempSeatNo", "seatNo");
                        }})
        );

        Map<String, OrderDetails> detailsMap = new HashMap<>();
        for (OrderDetails details : detailsList) {
            details.setId(SnowflakeIdGenerator.nextId());
            details.setOrderId(order.getId());
            details.setRefundStatus(false);

            details.setDeparture(createOrderDTO.getDeparture());
            details.setArrival(createOrderDTO.getArrival());
            details.setDepartureCode(createOrderDTO.getDepartureCode());
            details.setArrivalCode(createOrderDTO.getArrivalCode());
            details.setRidingDate(createOrderDTO.getRidingDate());
            details.setTrainId(createOrderDTO.getTrainId());
            details.setDepartureTime(createOrderDTO.getDepartureTime());
            details.setArrivalTime(createOrderDTO.getArrivalTime());

            detailsMap.put(details.getId().toString(), details);
        }

        String orderDetailsHashKey = buildOrderDetailsHashKey(order.getId());
        cacheClient.hPutAllWholeExpire(orderDetailsHashKey, detailsMap,
                RedisCommonConstants.RAIL_DEFAULT_TTL, TimeUnit.MINUTES);

        return detailsList;
    }

    /**
     * 构建正式订单明细Hash缓存Key
     */
    private String buildOrderDetailsHashKey(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId 不能为 null");
        }
        return String.format("%sorderId:%d",
                OrderRedisConstants.RAIL_ORDER_DETAILS_PREFIX, orderId);
    }

    /**
     * 创建订单主表记录
     */
    private Order createOrderMain(PreOrder preOrder) {
        Order order = BeanUtil.copyProperties(preOrder, Order.class);
        order.setId(SnowflakeIdGenerator.nextId());
        order.setStatus(OrderPaymentStatusConstants.PENDING_PAYMENT);
        order.setOrderSn(OrderSnUtil.generateOrderSn());
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());

        String orderKey = buildOrderKey(order.getOrderSn());
        cacheClient.setIfAbsent(orderKey, order, RedisCommonConstants.RAIL_DEFAULT_TTL, TimeUnit.MINUTES);
        return order;
    }

    /**
     * 构建正式订单主表缓存Key
     */
    private String buildOrderKey(String orderSn) {
        if (StrUtil.isBlank(orderSn)) {
            throw new IllegalArgumentException("orderSn 不能为 null 或空");
        }
        return String.format("%s%s",
                OrderRedisConstants.RAIL_ORDER_PREFIX, orderSn);
    }

    /**
     * 远程调用座位服务 + 统一校验
     */
    private List<AvailableSeatRemoteDTO> doGetAvailableSeat(RandomSeatQueryDTO randomSeatQueryDTO, Integer need) {
        String params = randomSeatQueryDTO.toString();

        Result<List<AvailableSeatRemoteDTO>> feignResult = ticketFeignClient.getAvailableSeats(randomSeatQueryDTO);
        if (!feignResult.isSuccess()) {
            LogUtils.error("OrderService", "doGetAvailableSeat", "/api/ticket-service/ticket/seats/available", params,
                    feignResult.getMessage());
            throw new OpenFeignException(feignResult.getMessage());
        }

        LogUtils.info("OrderService", "doGetAvailableSeat", "/api/ticket-service/ticket/seats/available", params, feignResult);

        List<AvailableSeatRemoteDTO> availableSeatList = feignResult.getData();
        if (availableSeatList == null || availableSeatList.size() < need) {
            throw new BizException("可用座位数不足");
        }

        return new ArrayList<>(availableSeatList);
    }

    /**
     * 分页查询订单
     * @param orderPageQueryDTO 订单分页查询参数（包含用户ID、订单状态、订单类型、日期范围、车次等查询条件，以及分页参数）
     * @return 订单分页数据（分页结果 + 依赖的单表Key列表，用于构建聚合缓存）
     */
    @Override
    public PageResult<OrderPageQueryVO> orderPageQuery(OrderPageQueryDTO orderPageQueryDTO) {
        RequestContext context = RequestContextHolder.getRequestContext();
        if (context == null || context.getAccountId() == null) {
            throw new BizException("请先登录");
        }
        Long userId = Long.valueOf(context.getAccountId());
        Integer orderStatus = orderPageQueryDTO.getOrderStatus();
        orderPageQueryDTO.setUserId(userId);

        String cacheKey = buildOrderUserAllKey(userId, orderStatus);
        // 缓存订单分页查询信息
        TypeReference<List<OrderPageQueryVO>> typeRef = new TypeReference<>() {};
        List<OrderPageQueryVO> allOrders = cacheClient.queryAggCacheWithNullCache(
                cacheKey,
                typeRef,
                // 缓存未命中时，查库
                this::queryUserAllOrderFromDb,
                new OrderDbQueryDTO(userId, orderStatus),
                RedisCommonConstants.RAIL_DEFAULT_TTL,
                TimeUnit.MINUTES
        );

        // 空值处理
        if (CollectionUtil.isEmpty(allOrders)) {
            return new PageResult<>(0L, Collections.emptyList(), orderPageQueryDTO.getPageNumber(), orderPageQueryDTO.getPageSize());
        }

        // 内存中按查询条件过滤
        List<OrderPageQueryVO> filteredOrders = filterOrders(allOrders, orderPageQueryDTO);

        // 内存分页逻辑
        long total = filteredOrders.size();
        int pageNum = orderPageQueryDTO.getPageNumber();
        int pageSize = orderPageQueryDTO.getPageSize();
        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, filteredOrders.size());
        List<OrderPageQueryVO> pageList = filteredOrders.subList(start, end);

        return new PageResult<>(total, pageList, pageNum, pageSize);
    }

    /**
     * 内存过滤订单（按查询条件）
     */
    private List<OrderPageQueryVO> filterOrders(List<OrderPageQueryVO> allOrders, OrderPageQueryDTO dto) {
        List<OrderPageQueryVO> filteredList = allOrders.stream()
                // 过滤乘车日期范围
                .filter(order -> {
                    if (dto.getStartDate() == null || dto.getEndDate() == null) {
                        return true;
                    }
                    LocalDate ridingDate = order.getRidingDate();
                    return !ridingDate.isBefore(dto.getStartDate()) && !ridingDate.isAfter(dto.getEndDate());
                })
                // 过滤订单号
                .filter(order -> StrUtil.isBlank(dto.getOrderSn()) || order.getOrderSn().contains(dto.getOrderSn()))
                // 过滤车次号
                .filter(order -> StrUtil.isBlank(dto.getTrainNumber()) || order.getTrainNumber().contains(dto.getTrainNumber()))
                // 过滤乘客姓名
                // 模糊匹配乘车人姓名（遍历订单明细，只要有一个乘客匹配就保留）
                .filter(order -> {
                    if (StrUtil.isBlank(dto.getRealName())) {
                        return true;
                    }
                    List<OrderDetailsVO> details = order.getOrderDetailsVOList();
                    if (CollectionUtil.isEmpty(details)) {
                        return false;
                    }
                    String realName = dto.getRealName();
                    return details.stream().anyMatch(detail -> StrUtil.contains(detail.getRealName(), realName));
                })
                .collect(Collectors.toList());

        // 内存动态排序
        if (dto.getOrderType() != null) {
            filteredList.sort((o1, o2) -> {
                if (dto.getOrderType() == 0) {
                    // 0：按订票日期降序
                    return o2.getOrderDate().compareTo(o1.getOrderDate());
                } else if (dto.getOrderType() == 1) {
                    // 1：按乘车日期降序
                    return o2.getRidingDate().compareTo(o1.getRidingDate());
                }
                return 0;
            });
        }
        // 无orderType：不排序，直接使用数据库默认的 create_time 排序结果

        return filteredList;
    }

    /**
     * 构建订单分页查询的全量聚合缓存Key
     */
    private String buildOrderUserAllKey(Long userId, Integer orderStatus) {
        if (orderStatus == null) {
            throw new IllegalArgumentException("订单状态不能为空");
        }
        return String.format("%suserId:%d:orderStatus:%s",
                OrderRedisConstants.RAIL_AGG_ORDER_USER_ALL_PREFIX,
                userId,
                orderStatus
        );
    }

    /**
     * 缓存未命中：查询该用户所有订单（无分页）
     */
    private AggCacheResult<List<OrderPageQueryVO>> queryUserAllOrderFromDb(OrderDbQueryDTO dbQueryDTO) {
        // 直接查询用户所有订单，禁用PageHelper分页插件，获取完整订单列表（后续在内存中根据查询条件过滤和分页）
        List<OrderPageQueryVO> allUserOrders = orderMapper.getOrderPageByQueryDTO(dbQueryDTO);

        // 组装缓存依赖Key（用于缓存更新）
        List<String> dependSingleKeys = new ArrayList<>();
        for (OrderPageQueryVO vo : allUserOrders) {
            // orderKey
            String orderKey = buildOrderKey(vo.getOrderSn());
            dependSingleKeys.add(orderKey);

            // detailKeys
            String orderDetailsHashKey = buildOrderDetailsHashKey(vo.getId());
            dependSingleKeys.add(orderDetailsHashKey);
        }

        return AggCacheResult.of(allUserOrders, dependSingleKeys);
    }

    /**
     * 分页查询本人车票
     * @param frontSelfTicketPageDTO 本人车票分页查询参数（包含用户ID、车票状态、日期范围、车次等查询条件，以及分页参数）
     * @return 本人车票分页数据（分页结果 + 依赖的单表Key列表）
     */
    @Override
    public PageResult<SelfTicketPageVO> selfTicketPageQuery(FrontSelfTicketPageDTO frontSelfTicketPageDTO) {
        RequestContext context = RequestContextHolder.getRequestContext();
        if (context == null || context.getAccountId() == null) {
            throw new BizException("请先登录");
        }
        Long userId = Long.valueOf(context.getAccountId());
        frontSelfTicketPageDTO.setUserId(userId);

        // 以userId为key，缓存全量本人车票数据
        String cacheKey = buildSelfTicketUserAllKey(userId);

        TypeReference<List<SelfTicketPageVO>> typeRef = new TypeReference<>() {};
        List<SelfTicketPageVO> allSelfTickets = cacheClient.queryAggCacheWithNullCache(
                cacheKey,
                typeRef,
                this::loadSelfTicketFromDb,
                userId,
                RedisCommonConstants.RAIL_DEFAULT_TTL,
                TimeUnit.MINUTES
        );

        if (CollectionUtil.isEmpty(allSelfTickets)) {
            return new PageResult<>(0L, Collections.emptyList(),
                    frontSelfTicketPageDTO.getPageNumber(),
                    frontSelfTicketPageDTO.getPageSize());
        }

        // 内存条件过滤
        List<SelfTicketPageVO> filteredTickets = filterSelfTickets(allSelfTickets, frontSelfTicketPageDTO);

        // 内存分页（和订单分页逻辑完全一致）
        long total = filteredTickets.size();
        int pageNum = frontSelfTicketPageDTO.getPageNumber();
        int pageSize = frontSelfTicketPageDTO.getPageSize();
        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, filteredTickets.size());
        List<SelfTicketPageVO> pageList = filteredTickets.subList(start, end);

        return new PageResult<>(total, pageList, pageNum, pageSize);
    }

    /**
     * 本人车票：内存条件过滤
     */
    private List<SelfTicketPageVO> filterSelfTickets(List<SelfTicketPageVO> allSelfTickets, FrontSelfTicketPageDTO dto) {
        return allSelfTickets.stream()
                // 乘车日期范围过滤
                .filter(ticket -> {
                    if (dto.getStartDate() == null || dto.getEndDate() == null) {
                        return true;
                    }
                    LocalDate ridingDate = ticket.getRidingDate();
                    return !ridingDate.isBefore(dto.getStartDate()) && !ridingDate.isAfter(dto.getEndDate());
                })
                // 车次号模糊过滤
                .filter(ticket -> StrUtil.isBlank(dto.getTrainNumber()) || ticket.getTrainNumber().contains(dto.getTrainNumber()))
                // 车票类型过滤
                .filter(ticket -> ObjectUtil.isNull(dto.getTicketType()) || Objects.equals(ticket.getTicketType(), dto.getTicketType()))
                .collect(Collectors.toList());
    }

    /**
     * 构建：本人车票 - 用户全量缓存Key（仅用userId）
     */
    private String buildSelfTicketUserAllKey(Long userId) {
        return String.format("%suserId:%d",
                OrderRedisConstants.RAIL_AGG_SELF_TICKET_USER_ALL_PREFIX,
                userId
        );
    }

    /**
     * 本人车票分页查询：数据库查询 + 构建聚合缓存依赖Key
     */
    private AggCacheResult<List<SelfTicketPageVO>> loadSelfTicketFromDb(Long userId) {
        String params = String.valueOf(userId);

        // 远程调用user-service，根据userId查询idType和idCard
        Result<UserIdCardDTO> userIdCardDTOResult = userFeignClient.getIdCardInfo();

        if (!userIdCardDTOResult.isSuccess()) {
            LogUtils.error("OrderService", "loadSelfTicketFromDb", "/api/user-service/user/{id}", params, userIdCardDTOResult.getMessage());
            throw new OpenFeignException(userIdCardDTOResult.getMessage());
        }

        LogUtils.info("OrderService", "loadSelfTicketFromDb", "/api/user-service/user/{id}", params, userIdCardDTOResult);

        UserIdCardDTO userIdCardDTO = userIdCardDTOResult.getData();
        SelfTicketPageDTO selfTicketDTO = new SelfTicketPageDTO();
        selfTicketDTO.setIdType(userIdCardDTO.getIdType());
        selfTicketDTO.setIdCard(userIdCardDTO.getIdCard());

        // 查询该用户所有本人车票
        List<SelfTicketPageVO> selfTicketPageVOList = orderMapper.getSelfTicketPageByQueryDTO(selfTicketDTO);

        // 组装所有依赖的单表Key
        List<String> selfTicketKeys = selfTicketPageVOList.stream()
                .map(selfTicketPageVO -> OrderRedisConstants.RAIL_SELF_TICKET_PREFIX + userId)
                .toList();
        List<String> dependSingleKeys = new ArrayList<>(selfTicketKeys);

        return AggCacheResult.of(selfTicketPageVOList, dependSingleKeys);
    }

    /**
     * 取消车票订单
     * @param orderSn 订单号
     */
    @Override
    public void cancelOrder(String orderSn) {
        orderMapper.updateOrder(orderSn, OrderPaymentStatusConstants.CANCELED);
        // 自动清理订单及相关聚合key
        String orderKey = buildOrderKey(orderSn);
        cacheClient.autoClearAggCache(orderKey);
    }

    /**
     * 创建全新预订单
     */
    private String createNewPreOrder(PreOrderContext ctx) {
        CreatePreOrderDTO createPreOrderDTO = ctx.getReqDTO();
        //  构建预订单主表
        Long preOrderId = SnowflakeIdGenerator.nextId();
        String preOrderSn = OrderSnUtil.generatePreOrderSn();
        BigDecimal totalAmount = calculateTotalAmount(createPreOrderDTO.getPassengerOrderDetailDTOList());

        PreOrder preOrder = BeanUtil.copyProperties(createPreOrderDTO, PreOrder.class);
        preOrder.setId(preOrderId);
        preOrder.setPreOrderSn(preOrderSn);
        preOrder.setDepartureCode(createPreOrderDTO.getDepartureCode());
        preOrder.setArrivalCode(createPreOrderDTO.getArrivalCode());
        preOrder.setExpireTime(calculateExpireTime());
        preOrder.setCreateTime(LocalDateTime.now());
        preOrder.setStatus(PreOrderStatusConstants.VALID);
        preOrder.setTotalAmount(totalAmount);

        // 插入预订单主表
        String preOrderKey = buildPreOrderKey(preOrder.getUserId(), preOrder.getTrainId());
        cacheClient.set(preOrderKey, preOrder, RedisCommonConstants.RAIL_DEFAULT_TTL, TimeUnit.MINUTES);

        // 插入明细
        insertNewPreOrderDetails(ctx, preOrderId);

        return preOrderSn;
    }

    /**
     * 插入新预订单明细
     */
    private void insertNewPreOrderDetails(PreOrderContext ctx, Long preOrderId) {
        CreatePreOrderDTO reqDTO = ctx.getReqDTO();
        List<PassengerRemoteDTO> psgrRemoteList = new ArrayList<>(ctx.getPassengerList());
        List<AvailableSeatRemoteDTO> availableSeatList = new ArrayList<>(getAvailableSeatListFromRemote(ctx, preOrderId));

        List<PassengerOrderDetailDTO> psgrDetailList = reqDTO.getPassengerOrderDetailDTOList();

        if (ObjectUtil.isEmpty(psgrDetailList)) {
            throw new IllegalArgumentException("预订单乘客详情列表不能为空");
        }

        List<PreOrderDetails> detailsList = new ArrayList<>();
        for (PassengerOrderDetailDTO passengerDTO : psgrDetailList) {
            PassengerRemoteDTO realPassenger = psgrRemoteList.removeFirst();
            if (realPassenger == null) {
                throw new BizException("乘客信息不存在");
            }

            PreOrderDetails details = buildPreOrderDetail(preOrderId, passengerDTO, realPassenger);

            AvailableSeatRemoteDTO seat = availableSeatList.removeFirst();
            details.setCarriageNumber(seat.getCarriageNumber());
            details.setTempSeatNo(seat.getSeatNo());

            detailsList.add(details);
        }

        // 批量插入
        String detailsKey = buildPreOrderDetailsSetKey(preOrderId);
        cacheClient.addSetMembersWithExpire(detailsKey, detailsList, RedisCommonConstants.RAIL_DEFAULT_TTL, TimeUnit.MINUTES);
    }

    /**
     * 远程申请可用座位
     */
    private List<AvailableSeatRemoteDTO> getAvailableSeatListFromRemote(PreOrderContext ctx, Long preOrderId) {
        CreatePreOrderDTO reqDTO = ctx.getReqDTO();
        List<PassengerOrderDetailDTO> psgrDetailList = reqDTO.getPassengerOrderDetailDTOList();

        if (ObjectUtil.isEmpty(psgrDetailList)) {
            throw new IllegalArgumentException("预订单乘客详情列表不能为空");
        }
        Integer seatType = psgrDetailList.getFirst().getSeatType();
        Integer need = psgrDetailList.size();

        RandomSeatQueryDTO queryDTO = RandomSeatQueryDTO.builder()
                .trainId(reqDTO.getTrainId())
                .orderId(preOrderId)
                .seatType(seatType)
                .departureCode(reqDTO.getDepartureCode())
                .arrivalCode(reqDTO.getArrivalCode())
                .passengerCount(need)
                .preferredSeatSymbols(reqDTO.getPreferredSeatSymbols())
                .orderType(OrderTypeConstants.PREORDER)
                .status(SeatIntervalStatusConstants.LOCKED)
                .build();

        return doGetAvailableSeat(queryDTO, need);
    }

    /**
     * 初始化批量座位占用DTO
     * @param createPreOrderDTO 预订单创建参数（包含用户ID、列车ID、乘客信息、选座信息等）
     * @param preOrderId        预订单ID（用于关联座位锁定记录）
     * @param batchSeatDTO      批量座位占用DTO（将被填充公共字段和座位列表，用于后续批量锁定座位）
     */
    private void buildBatchSeatIntervalInsertDTO(
            CreatePreOrderDTO createPreOrderDTO,
            Long preOrderId,
            BatchSeatIntervalInsertDTO batchSeatDTO
    ) {
        // 设置 公共字段 ，所有座位完全一致，只赋值1次
        batchSeatDTO.setTrainId(createPreOrderDTO.getTrainId());
        batchSeatDTO.setOrderId(preOrderId);
        batchSeatDTO.setOrderType(OrderTypeConstants.PREORDER);
        batchSeatDTO.setDepartureCode(createPreOrderDTO.getDepartureCode());
        batchSeatDTO.setArrivalCode(createPreOrderDTO.getArrivalCode());
        batchSeatDTO.setStatus(SeatIntervalStatusConstants.RELEASED);
        batchSeatDTO.setExpireTime(null);
        // 初始化座位列表
        batchSeatDTO.setSeatList(new ArrayList<>());
    }

    private BigDecimal calculateTotalAmount(List<PassengerOrderDetailDTO> passengerList) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (PassengerOrderDetailDTO dto : passengerList) {
            totalAmount = totalAmount.add(dto.getAmount());
        }
        return totalAmount;
    }

    /**
     * 计算过期时间
     * @return 过期时间（当前时间 + 预设的过期分钟数，默认15分钟）
     */
    private LocalDateTime calculateExpireTime() {
        LocalDateTime now = LocalDateTime.now();
        int minutes = (preOrderExpireMinutes != null) ? preOrderExpireMinutes : 15;
        return now.plusMinutes(minutes);
    }
}
