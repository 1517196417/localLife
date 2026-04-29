环境要求

- **JDK 1.8+**
- **Maven 3.6+**
- **MySQL 5.7+**
- **Redis 6.2+**
- **RabbitMQ 3.8+**（可选，用于异步消息）
- **Elasticsearch 7.12+**（可选，用于全文搜索）

### 技术栈

| 分类 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 后端框架 | Spring Boot | 2.3.12 | 应用容器 |
| ORM | MyBatis-Plus | 3.4.3 | 数据库操作 |
| 缓存 | Redis / Caffeine | 6.2 / 2.9.3 | 多级缓存 |
| 消息队列 | RabbitMQ | 3.8 | 异步削峰、日志收集 |
| 搜索引擎 | Elasticsearch | 7.12 | 商户全文搜索 |
| 分布式锁 | Redisson | 3.13.6 | 秒杀场景互斥 |
| 数据库 | MySQL | 5.7 | 持久化存储 |
| 前端 | Vue.js 2 + Element UI | - | 移动端 H5 |
| 工具 | Hutool | 5.7.17 | 通用工具库 |
| API 文档 | Knife4j (Swagger) | 3.0.3 | 接口文档 |
| WebSocket | Spring WebSocket | - | 即时通讯 |
| AOP | AspectJ | 1.9.7 | 日志切面 |




### 项目页面展示

<div align="center">

| 首页页面 | 消息页面 |
| :---: | :---: |
| <img src="https://github.com/user-attachments/assets/64022a86-2d06-402b-8675-78bfa08ec2f4" width="350" alt="首页页面" /> | <img src="https://github.com/user-attachments/assets/d68531a3-b96b-4512-830f-e1c30f631364" width="350" alt="消息页面" /> |

| 个人中心页面 | 内容发布页面 |
| :---: | :---: |
| <img src="https://github.com/user-attachments/assets/acba971d-be3f-4774-ab96-45dc5d9ebbf1" width="350" alt="个人中心页面" /> | <img src="https://github.com/user-attachments/assets/7dcb706a-4fdd-4c36-8e9c-62ba3ee8e0ea" width="350" alt="内容发布页面" /> |

| 内容详情页面 | 登录页面 |
| :---: | :---: |
| <img src="https://github.com/user-attachments/assets/5d31b054-b6e7-40b2-ac8e-35a320ebca1c" width="350" alt="内容详情页面" /> | <img src="https://github.com/user-attachments/assets/45eb6bd6-cf50-4a80-a9cd-1e16e418ce25" width="350" alt="登录页面" /> |

| 编辑页面 | - |
| :---: | :---: |
| <img src="https://github.com/user-attachments/assets/a380bbee-60d0-4876-8a55-b3fdf0817191" width="350" alt="编辑页面" /> | - |

</div>
