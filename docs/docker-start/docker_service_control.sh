#!/bin/bash

# ===================== 配置区：按你的服务顺序写 =====================
# 启动顺序：mysql → nacos → redis → es → kibana → seata → mq
START_ORDER=(
    "mysql92"
    "nacos"
    "rs-redis"
    "rs-es"
    "rs-kibana"
    "seata"
    "mq"
)

# 关闭顺序：mq → seata → kibana → es → redis → nacos → mysql
STOP_ORDER=(
    "mq"
    "seata"
    "rs-kibana"
    "rs-es"
    "rs-redis"
    "nacos"
    "mysql92"
)

# 启动间隔：给依赖服务初始化的时间（单位：秒，可根据需要调整）
START_INTERVAL=3
# 关闭间隔：给容器优雅退出的时间（单位：秒）
STOP_INTERVAL=2
# ==============================================================

# 启动所有服务
start_services() {
    echo "=== 🔄 开始按顺序启动服务 ==="
    for container in "${START_ORDER[@]}"; do
        echo "▶️  正在启动容器: $container"
        docker start "$container"
        # 给服务初始化时间，避免后续服务依赖失败（比如 nacos 依赖 mysql）
        sleep $START_INTERVAL
    done
    echo "✅ 所有服务启动完成！"
    echo "提示：使用 docker ps 查看容器运行状态"
}

# 关闭所有服务
stop_services() {
    echo "=== ⏹️  开始按顺序关闭服务 ==="
    for container in "${STOP_ORDER[@]}"; do
        echo "◀️  正在关闭容器: $container"
        docker stop "$container"
        # 给容器优雅退出的时间，避免强制终止
        sleep $STOP_INTERVAL
    done
    echo "✅ 所有服务关闭完成！"
}

# 主逻辑：根据参数执行操作
case "$1" in
    start)
        start_services
        ;;
    stop)
        stop_services
        ;;
    restart)
        echo "=== 🔁 重启服务：先关闭再启动 ==="
        stop_services
        echo "------------------------"
        start_services
        ;;
    *)
        echo "用法: $0 {start|stop|restart}"
        echo "  start   按顺序启动所有服务"
        echo "  stop    按反序关闭所有服务"
        echo "  restart 重启所有服务（先关再启）"
        exit 1
        ;;
esac

exit 0
