### es

```
# 1. 先配置系统内存限制（否则ES启动失败，执行一次即可）
sysctl -w vm.max_map_count=262144
echo "vm.max_map_count=262144" >> /etc/sysctl.conf

# 2. 启动ES容器（和你现有配置一致：单节点、端口映射、数据持久化）
docker run -d \
  --name rs-es \
  --network rs-net \
  -p 9200:9200 \
  -p 9300:9300 \
  -v /opt/es-data:/usr/share/elasticsearch/data \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" \
  elasticsearch:9.3.0
```

### kibana

```
docker run -d \
  --name rs-kibana \
  --network rs-net \
  -p 5601:5601 \
  -e "ELASTICSEARCH_HOSTS=http://rs-es:9200" \
  kibana:9.3.0
```

### redis

1. 启动后访问 http://你的服务器IP:5601 即可进入 Kibana 界面

```
docker run -d \
  --name rs-redis \
  --network rs-net \
  -p 6379:6379 \
  -v /opt/redis-data:/data \
  redis:latest \
  redis-server --appendonly yes
```

### mq

1. 管理界面地址：http://你的服务器IP:15672，账号密码：admin/admin
2. 5672：AMQP 协议端口，供应用程序连接；15672：Web 管理界面端口

```
docker run -d \
  --name mq \
  --network rs-net \
  -p 5672:5672 \
  -p 15672:15672 \
  -e "RABBITMQ_DEFAULT_USER=admin" \
  -e "RABBITMQ_DEFAULT_PASS=admin" \
  rabbitmq:3.8-management
```

### seata

1. SEATA_IP：替换为你的服务器公网 IP / 内网 IP，供微服务连接

```
docker run -d \
  --name seata \
  --network rs-net \
  -p 7099:7099 \
  -p 8099:8099 \
  # ========== 基础服务配置 ==========
  -e "SEATA_IP=你的服务器宿主机IP" \
  -e "SEATA_PORT=8099" \
  -e "SEATA_APPLICATION=seata-server" \
  # ========== 注册中心：Nacos ==========
  -e "SEATA_REGISTRY_TYPE=nacos" \
  -e "SEATA_REGISTRY_NACOS_SERVER_ADDR=nacos:8848" \
  -e "SEATA_REGISTRY_NACOS_NAMESPACE=public" \
  -e "SEATA_REGISTRY_NACOS_GROUP=SEATA_GROUP" \
  -e "SEATA_REGISTRY_NACOS_USERNAME=nacos" \
  -e "SEATA_REGISTRY_NACOS_PASSWORD=nacos" \
  # ========== 配置中心：Nacos ==========
  -e "SEATA_CONFIG_TYPE=nacos" \
  -e "SEATA_CONFIG_NACOS_SERVER_ADDR=nacos:8848" \
  -e "SEATA_CONFIG_NACOS_NAMESPACE=public" \
  -e "SEATA_CONFIG_NACOS_GROUP=SEATA_GROUP" \
  -e "SEATA_CONFIG_NACOS_USERNAME=nacos" \
  -e "SEATA_CONFIG_NACOS_PASSWORD=nacos" \
  # ========== 事务存储：MySQL ==========
  -e "SEATA_STORE_MODE=db" \
  -e "SEATA_STORE_DB_DRIVER_CLASS_NAME=com.mysql.cj.jdbc.Driver" \
  -e "SEATA_STORE_DB_URL=jdbc:mysql://mysql92:3306/seata?useUnicode=true&characterEncoding=utf8&allowMultiQueries=true&useSSL=false&serverTimezone=Asia/Shanghai" \
  -e "SEATA_STORE_DB_USER=root" \
  -e "SEATA_STORE_DB_PASSWORD=你的MySQL root密码" \
  seataio/seata-server:1.5.2
```

### mysql

```
docker run -d \
  --name mysql92 \
  --network rs-net \
  -p 3306:3306 \
  -v /opt/mysql-data:/var/lib/mysql \
  -e "MYSQL_ROOT_PASSWORD=你的root密码" \
  -e "TZ=Asia/Shanghai" \
  mysql:9.2
```

### nacos

1. 这边使用数据库存储配置，需提前在 MySQL 中创建nacos_config数据库，并导入 Nacos 官方 SQL 脚本
2. 管理界面地址：http://你的服务器IP:8848/nacos，默认账号密码：nacos/nacos

```
docker run -d \
  --name nacos \
  --network rs-net \
  -p 8848:8848 \
  -p 9848:9848 \
  -p 9849:9849 \
  -e "MODE=standalone" \
  -e "SPRING_DATASOURCE_PLATFORM=mysql" \
  -e "MYSQL_SERVICE_HOST=你的MySQL容器名或IP" \
  -e "MYSQL_SERVICE_DB_NAME=nacos_config" \
  -e "MYSQL_SERVICE_USER=root" \
  -e "MYSQL_SERVICE_PASSWORD=你的MySQL密码" \
  nacos/nacos-server:v2.1.0-slim

```
