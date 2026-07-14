# 🤖 智能行程助手接入说明
本项目配套了基于Spring AI Alibaba框架开发的智能行程助手智能体，提供对话式交互能力，大幅提升用户购票体验。

## 核心特性
- 基于Spring AI Alibaba框架实现自然语言对话式交互
- 深度对接系统车次站点、时刻表与客运规则数据
- 支持用户自然语言行程查询、目的地推荐
- 智能生成个性化换乘方案与购票建议
- 订单规则、退改签政策等常见问题智能解答
- 简化用户操作流程，覆盖80%的用户高频咨询场景

## 智能体仓库
地址：[https://github.com/Victoria-Spencer/rs-agent](https://github.com/Victoria-Spencer/rs-agent)

## 快速接入
1. 克隆智能体项目仓库：
```bash
git clone https://github.com/Victoria-Spencer/rs-agent.git
cd rs-agent
```
2. 配置RailTick系统接口地址、大模型API密钥等参数
3. 启动智能体服务（默认端口：8087）
4. 在前端项目中接入对话入口，即可为用户提供AI智能咨询能力