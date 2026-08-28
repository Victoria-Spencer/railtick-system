# RailTick 铁路票务微服务系统

## 🚄 项目概述
RailTick 是一套基于微服务架构的铁路票务系统，实现了用户管理、车次查询、座位锁定、订单管理、支付对接、退改签等完整的票务业务流程，支持高并发购票场景，具备分布式事务、多级缓存、服务降级等高可用特性。

## 🏗️ 系统架构
```
[客户端/前端] → [API网关] → [业务服务集群] → [公共组件层] → [基础设施层]
```
- **接入层**：API网关（统一入口、鉴权、限流、路由）
- **业务层**：用户服务、车票服务、订单服务、支付服务、日志服务、工具服务
- **公共层**：Feign API模块、RocketMQ消息队列、Redis分布式缓存、公共组件库
- **基础设施**：MySQL分库分表、Elasticsearch检索、Nacos注册配置中心、Seata分布式事务

## 🛠️ 技术栈
| 技术/组件 | 版本 | 说明 |
|----------|------|------|
| Java | 21 | 开发语言 |
| Spring Boot | 3.5.7 | 应用框架 |
| Spring Cloud | 2025.0.0 | 微服务框架 |
| Spring Cloud Alibaba | 2025.0.0.0 | 微服务生态 |
| Nacos | 3.1.0 | 注册中心 + 配置中心 |
| OpenFeign | 4.3.0 | 服务调用 |
| MyBatis | 3.0.3 | ORM框架 |
| MySQL | 8.0.30 | 关系型数据库 |
| Redisson | 3.50.0 | 分布式锁 + 缓存 |
| RocketMQ | -- | 消息队列（异步解耦、流量削峰） |
| Seata | -- | 分布式事务 |
| Elasticsearch | 7.12.1 | 日志检索 + 统计分析 |
| JWT | 0.11.5 | 身份认证 |
| Lombok | 1.18.42 | 代码简化 |
| Hutool | 5.8.12 | 工具类库 |

## 📦 模块说明
```
railtick-system
├── gateway-service     # API网关服务（统一入口、鉴权、限流）
├── user-service        # 用户服务（用户管理、实名认证、乘客信息）
├── ticket-service      # 车票服务（车次管理、座位管理、余票查询）
├── order-service       # 订单服务（订单创建、退改签、状态流转）
├── pay-service         # 支付服务（支付对接、交易记录、退款处理）
├── log-service         # 日志服务（操作日志、行为审计）
├── tool-service        # 工具服务（短信通知、文件上传等通用能力）
├── api                 # Feign API模块（跨服务调用接口、DTO、常量定义）
└── common              # 公共组件库
    ├── common-core     # 核心工具、异常、常量定义
    ├── common-web      # Web配置、全局异常处理、参数校验
    ├── common-db       # 数据库配置、MyBatis配置、分页插件
    ├── common-redis    # Redis配置、Redisson分布式锁实现
    ├── common-feign    # Feign调用配置、降级兜底实现
    └── common-mq       # 消息队列配置、生产者/消费者封装
```

## 🚀 快速开始

### 环境要求
| 依赖 | 版本要求 |
|------|----------|
| JDK | 21+ |
| Maven | 3.8.6+ |
| MySQL | 8.0+ |
| Redis | 6.0+ |
| Nacos | 3.1.0 |
| RocketMQ | 4.9+ |
| Seata | 1.7+ |

### 1. 克隆项目
```bash
git clone https://github.com/Victoria-Spencer/railtick-system.git
cd railtick-system
```

### 2. 初始化数据库
所有SQL文件位于 `docs/init-sql/` 目录，按以下顺序执行：
```sql
-- 1. 基础组件库
source nacos.sql       # Nacos配置中心库
source seata.sql       # Seata分布式事务库

-- 2. 业务库
source rs-user.sql     # 用户服务库
source rs-ticket.sql   # 车票服务库
source rs-order.sql    # 订单服务库
source rs-pay.sql      # 支付服务库
source rs-log.sql      # 日志服务库
```
> 所有SQL文件已自动包含数据库创建命令，无需手动建库

### 3. 启动中间件
#### Docker环境前置准备（使用Docker启动必看）
如果选择Docker方式启动中间件，请先完成以下准备工作：
1. 配置Docker镜像加速器：参考 [Docker加速器配置](docs/docker-start/docker加速器配置.md)
2. 安装Docker容器环境：参考 [Docker环境安装指南](docs/docker-start/rs-docker-create.md)

#### 方式一：Docker快速启动（推荐）
```bash
# 1. 将 docs/docker-start/docker_service_controller.sh 脚本上传到服务器/虚拟机
# 2. 进入脚本所在目录，添加执行权限
chmod +x docker_service_controller.sh
# 3. 启动所有基础中间件
./docker_service_controller.sh start
```

#### 方式二：手动启动
1. 启动Nacos（默认端口：8848）
2. 启动Seata（默认端口：8091）
3. 启动Redis（默认端口：6379）
4. 启动RocketMQ（Namesrv:9876，Broker:10911）
5. 启动MySQL（默认端口：3306）
6. 启动Elasticsearch（默认端口：9200）

### 4. 配置Nacos
在Nacos控制台创建各服务配置：
- 参考各服务的`application.yml`文件创建对应的配置项
- 修改数据库、Redis、MQ等连接信息为你的环境配置

### 5. 启动服务
建议启动顺序：
```
1. gateway-service  # 网关服务（端口：8080）
2. user-service     # 用户服务（端口：8081）
3. ticket-service   # 车票服务（端口：8082）
4. order-service    # 订单服务（端口：8083）
5. pay-service      # 支付服务（端口：8084）
6. log-service      # 日志服务（端口：8085）
7. tool-service     # 工具服务（端口：8086）
```

### 6. 访问验证
- 网关地址：http://localhost:8080
- 接口文档JSON：`docs/api-docs/12306.openapi.json`

## 🧩 扩展生态
本项目提供丰富的周边扩展能力，可根据业务需求选择接入：

| 扩展模块 | 说明 | 文档链接 |
|---------|------|----------|
| 📊 性能压测脚本 | 完整的高并发购票场景压测用例，包含接口压测、全链路压测、一致性验证等场景 | [使用说明](docs/ecosystem/performance-test.md) |
| 🤖 智能行程助手 | 基于Spring AI Alibaba的对话式智能体，支持自然语言行程查询、换乘方案推荐、智能问答等能力 | [接入说明](docs/ecosystem/ai-assistant.md) |
| 🗄️ 缓存元数据治理中台 | 企业级缓存全生命周期治理平台，零侵入接入、多模式失效保障、自愈调度、全链路可观测 | [详情说明](docs/ecosystem/cache-management.md) |


## 📚 目录说明
```
docs/
├── docker-start/       # Docker部署相关脚本和配置
├── init-sql/           # 数据库初始化SQL文件
├── api-docs/           # 接口文档（OpenAPI格式）
└── ecosystem/          # 扩展生态模块文档（压测、智能助手、缓存中台等）
```


## 📝 开发规范
1. **代码规范**：遵循阿里巴巴Java开发规范
2. **提交规范**：`feat: 新功能` / `fix: 修复bug` / `refactor: 重构` / `docs: 文档更新`
3. **接口规范**：RESTful风格，统一返回格式，错误码统一管理
4. **数据库规范**：逻辑删除、公共字段（create_time、update_time、is_deleted）必填
5. **缓存规范**：热点数据优先走缓存，缓存key统一前缀，过期时间合理设置

## 🤝 贡献指南
1. Fork 本仓库
2. 新建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'feat: add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 新建 Pull Request
