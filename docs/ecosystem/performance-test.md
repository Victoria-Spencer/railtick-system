# 📊 性能压测脚本使用说明
本项目提供了完整的压测脚本和场景用例，用于验证系统在高并发购票场景下的性能表现。

## 压测脚本仓库
地址：[https://github.com/Victoria-Spencer/railtick-scripts](https://github.com/Victoria-Spencer/railtick-scripts)

## 包含的压测场景
- 用户注册/登录接口压测
- 车次查询/余票查询接口压测
- 座位锁定/订单创建全链路压测
- 支付回调/订单状态流转压测
- 高并发下库存一致性验证场景

## 快速使用
1. 克隆压测脚本仓库：
```bash
git clone https://github.com/Victoria-Spencer/railtick-scripts.git
cd railtick-scripts
```
2. 准备压测环境和测试数据：
   Windows环境直接运行根目录下的 `run_chain.bat` 预处理脚本，脚本会自动生成测试用户、认证令牌、预置车次等测试数据，一键完成所有压测前置准备工作。

3. 打开JMeter客户端，导入对应场景的压测脚本：
   - 余票查询压测：`jmeter/HTTP Request.jmx`
   - 下单全链路压测：根据实际业务场景选择对应`.jmx`文件
4. 在JMeter中调整压测参数（线程数、ramp-up时间、循环次数等）
5. 启动压测，在JMeter界面实时查看压测结果和性能指标