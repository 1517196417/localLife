# 评论功能 MVP 版本实现说明

## 📋 功能概述

实现了评论功能的MVP版本（P0），包含以下4个核心功能：

1. ✅ **发表一级评论** - 用户可以在笔记详情页发表评论
2. ✅ **评论列表（时间排序）** - 按时间倒序展示所有评论
3. ✅ **点赞评论** - 用户可以对评论点赞
4. ✅ **删除自己的评论** - 用户只能删除自己的评论（软删除）

---

## 🏗️ 技术架构

### 后端架构

```
Controller层（BlogCommentsController）
    ↓
Service层（BlogCommentsServiceImpl）
    ↓
Mapper层（BlogCommentsMapper）
    ↓
数据库（tb_blog_comments）
```

### 前端架构

```
blog-detail.html（Vue.js）
    ↓
axios请求
    ↓
后端API接口
```

---

## 📝 实现的文件

### 后端文件

#### 1. IBlogCommentsService.java
**路径**: `src/main/java/com/hmdp/service/IBlogCommentsService.java`

**新增方法**:
```java
Result addComment(Long blogId, String content);           // 发表评论
Result getCommentsByBlogId(Long blogId);                   // 查询评论列表
Result likeComment(Long commentId);                        // 点赞评论
Result deleteComment(Long commentId);                      // 删除评论
```

---

#### 2. BlogCommentsServiceImpl.java
**路径**: `src/main/java/com/hmdp/service/impl/BlogCommentsServiceImpl.java`

**核心实现**:

**发表评论**:
- 获取当前登录用户ID
- 参数校验（内容不能为空，不超过500字）
- 创建评论对象（parentId=0，一级评论）
- 保存到数据库
- 返回评论对象

**查询评论列表**:
- 按blogId查询，status=0（正常状态）
- 按创建时间倒序排列
- 填充用户信息（昵称、头像）
- 返回CommentVO列表

**点赞评论**:
- 查询评论是否存在
- 点赞数+1
- 更新数据库
- 返回新的点赞数

**删除评论**:
- 验证是否是评论作者
- 软删除（status=1）
- 返回操作结果

**CommentVO类**:
```java
static class CommentVO {
    private Long id;
    private Long userId;
    private String userName;
    private String userIcon;
    private String content;
    private Integer liked;
    private LocalDateTime createTime;
}
```

---

#### 3. BlogCommentsController.java
**路径**: `src/main/java/com/hmdp/controller/BlogCommentsController.java`

**API接口**:

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 发表评论 | POST | `/blog-comments/blog/{blogId}?content=xxx` | 发表一级评论 |
| 评论列表 | GET | `/blog-comments/blog/{blogId}` | 查询评论列表 |
| 点赞评论 | PUT | `/blog-comments/like/{commentId}` | 点赞评论 |
| 删除评论 | DELETE | `/blog-comments/{commentId}` | 删除评论 |

---

### 前端文件

#### blog-detail.html
**路径**: `hmdp/blog-detail.html`

**新增数据**:
```javascript
data: {
  comments: [],          // 评论列表
  commentContent: '',    // 评论内容输入框
}
```

**新增方法**:
```javascript
queryComments(blogId)         // 查询评论列表
submitComment()               // 提交评论
likeComment(comment)          // 点赞评论
deleteCommentFunc(commentId)  // 删除评论
formatCommentTime(time)       // 格式化评论时间
```

**UI组件**:
- 评论区头部：显示评论总数
- 评论列表：循环展示评论
- 评论输入框：底部输入框+发布按钮
- 评论操作：点赞、删除按钮

**CSS样式**:
- 评论输入框样式（圆角、背景色）
- 评论列表样式（头像、内容、操作栏）
- 按钮悬停效果

---

## 🗄️ 数据库设计

### tb_blog_comments 表

```sql
CREATE TABLE `tb_blog_comments` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '评论用户id',
  `blog_id` bigint(20) UNSIGNED NOT NULL COMMENT '笔记id',
  `parent_id` bigint(20) UNSIGNED NOT NULL DEFAULT 0 COMMENT '父评论id（一级评论为0）',
  `answer_id` bigint(20) UNSIGNED NOT NULL DEFAULT 0 COMMENT '回复的评论id',
  `content` varchar(500) NOT NULL COMMENT '评论内容',
  `liked` int(8) UNSIGNED DEFAULT 0 COMMENT '点赞数',
  `status` tinyint(1) UNSIGNED DEFAULT 0 COMMENT '状态：0-正常，1-被举报/删除',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_blog_id` (`blog_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='博客评论表';
```

**关键字段**:
- `parent_id = 0`: 一级评论
- `status = 0`: 正常状态
- `status = 1`: 已删除（软删除）

---

## 🔄 数据流程

### 1. 发表评论

```
用户输入评论
    ↓
点击"发布"按钮
    ↓
前端校验（非空）
    ↓
POST /blog-comments/blog/{blogId}?content=xxx
    ↓
后端校验（内容长度、登录状态）
    ↓
保存到数据库（status=0, parentId=0）
    ↓
返回成功
    ↓
前端重新查询评论列表
    ↓
显示新评论
```

---

### 2. 查询评论列表

```
进入笔记详情页
    ↓
GET /blog-comments/blog/{blogId}
    ↓
查询数据库（status=0, 按时间倒序）
    ↓
填充用户信息（昵称、头像）
    ↓
返回CommentVO列表
    ↓
前端渲染评论列表
```

---

### 3. 点赞评论

```
点击"👍"按钮
    ↓
PUT /blog-comments/like/{commentId}
    ↓
查询评论
    ↓
liked + 1
    ↓
更新数据库
    ↓
返回新的点赞数
    ↓
前端更新显示
```

---

### 4. 删除评论

```
点击"删除"按钮
    ↓
确认删除
    ↓
DELETE /blog-comments/{commentId}
    ↓
验证是否是评论作者
    ↓
软删除（status=1）
    ↓
返回成功
    ↓
前端重新查询评论列表
```

---

## 🧪 测试步骤

### 前置条件

1. 启动后端服务：
```bash
cd D:\javacode\github_code\localLife\localLife
mvn spring-boot:run
```

2. 启动Nginx（或直接打开前端）：
```bash
cd D:\nginx
.\nginx.exe
```

3. 确保数据库表 `tb_blog_comments` 已创建

---

### 测试1：发表评论

**步骤**:
1. 访问 `http://localhost/blog-detail.html?id=1`
2. 滚动到底部评论输入框
3. 输入评论内容："这是一家非常好的店！"
4. 点击"发布"按钮

**预期结果**:
- ✅ 显示"评论成功"提示
- ✅ 评论列表立即显示新评论
- ✅ 评论总数+1
- ✅ 显示你的昵称和头像
- ✅ 显示"刚刚"

**验证数据库**:
```sql
SELECT * FROM tb_blog_comments WHERE blog_id = 1 ORDER BY create_time DESC LIMIT 1;
```

---

### 测试2：查询评论列表

**步骤**:
1. 访问笔记详情页
2. 滚动到评论区

**预期结果**:
- ✅ 显示所有正常状态的评论
- ✅ 按时间倒序排列（最新的在最上面）
- ✅ 显示评论者昵称和头像
- ✅ 显示评论内容
- ✅ 显示相对时间（刚刚、5分钟前、2小时前等）

---

### 测试3：点赞评论

**步骤**:
1. 找到任意评论
2. 点击"👍"按钮

**预期结果**:
- ✅ 显示"点赞成功"提示
- ✅ 点赞数+1
- ✅ 数据库中的liked字段更新

**验证数据库**:
```sql
SELECT id, liked FROM tb_blog_comments WHERE id = <评论ID>;
```

---

### 测试4：删除评论

**步骤**:
1. 找到自己的评论
2. 点击"删除"按钮
3. 确认删除

**预期结果**:
- ✅ 显示"删除成功"提示
- ✅ 评论从列表中消失
- ✅ 评论总数-1
- ✅ 数据库中status=1（软删除）

**验证数据库**:
```sql
SELECT id, status FROM tb_blog_comments WHERE id = <评论ID>;
-- status应该为1
```

---

### 测试5：权限验证

**测试A：删除别人的评论**
1. 用用户A发表一条评论
2. 用用户B登录
3. 尝试删除用户A的评论

**预期结果**:
- ❌ "删除"按钮不显示（v-if="comment.userId === user.id"）
- ✅ 即使调用接口，也会返回"只能删除自己的评论"

**测试B：未登录用户评论**
1. 退出登录
2. 尝试发表评论

**预期结果**:
- ✅ 返回401错误
- ✅ 自动跳转到登录页

---

## 🎨 UI展示

### 评论区布局

```
┌─────────────────────────────────┐
│ 网友评论（3）                     │
├─────────────────────────────────┤
│ [头像] 张三                      │
│ 这是一家非常好的店！              │
│ 刚刚  👍 0  删除                 │
├─────────────────────────────────┤
│ [头像] 李四                      │
│ 价格实惠，味道不错                │
│ 5分钟前  👍 2                    │
├─────────────────────────────────┤
│ [头像] 王五                      │
│ 环境很好，服务周到                │
│ 1小时前  👍 5                    │
├─────────────────────────────────┤
│ [头像] 写评论...      [发布]     │
└─────────────────────────────────┘
```

---

## 📊 API接口测试

### 使用Postman或curl测试

#### 1. 发表评论
```bash
curl -X POST "http://localhost:8080/blog-comments/blog/1?content=测试评论" \
  -H "authorization: <your-token>"
```

**预期响应**:
```json
{
  "success": true,
  "data": {
    "id": 1,
    "userId": 1,
    "blogId": 1,
    "content": "测试评论",
    "liked": 0,
    "createTime": "2026-04-24T10:00:00"
  }
}
```

#### 2. 查询评论列表
```bash
curl -X GET "http://localhost:8080/blog-comments/blog/1"
```

**预期响应**:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "userId": 1,
      "userName": "张三",
      "userIcon": "...",
      "content": "测试评论",
      "liked": 0,
      "createTime": "2026-04-24T10:00:00"
    }
  ]
}
```

#### 3. 点赞评论
```bash
curl -X PUT "http://localhost:8080/blog-comments/like/1" \
  -H "authorization: <your-token>"
```

**预期响应**:
```json
{
  "success": true,
  "data": 1
}
```

#### 4. 删除评论
```bash
curl -X DELETE "http://localhost:8080/blog-comments/1" \
  -H "authorization: <your-token>"
```

**预期响应**:
```json
{
  "success": true
}
```

---

## ⚠️ 注意事项

### 1. 安全性
- ✅ 只能删除自己的评论
- ✅ 评论内容长度限制（500字）
- ✅ 需要登录才能评论
- ✅ 软删除（数据不丢失）

### 2. 性能
- ⚠️ 评论列表查询时，每个评论都查询一次用户信息（N+1问题）
- 💡 后续优化：批量查询用户信息

### 3. 扩展性
- ✅ 预留了parentId和answerId字段（支持回复功能）
- ✅ 预留了images字段（支持图片评论）
- ✅ 预留了at_users字段（支持@功能）

---

## 🚀 后续迭代计划

### Week 2：回复功能（P0）
- [ ] 楼中楼回复
- [ ] @好友功能
- [ ] @通知（WebSocket）

### Week 3：高级功能（P1）
- [ ] 敏感词过滤
- [ ] 表情包支持
- [ ] 评论审核
- [ ] 热门评论排序

### Week 4：优化迭代（P1-P2）
- [ ] 评论举报
- [ ] 图片评论
- [ ] 评论标签（达人标识）
- [ ] 性能优化（批量查询、缓存）

---

## 📚 相关文件

| 文件 | 路径 | 说明 |
|------|------|------|
| 评论实体 | `src/main/java/com/hmdp/entity/BlogComments.java` | 数据库实体类 |
| 评论Mapper | `src/main/java/com/hmdp/mapper/BlogCommentsMapper.java` | 数据访问层 |
| 评论Service接口 | `src/main/java/com/hmdp/service/IBlogCommentsService.java` | 服务接口 |
| 评论Service实现 | `src/main/java/com/hmdp/service/impl/BlogCommentsServiceImpl.java` | 服务实现 |
| 评论Controller | `src/main/java/com/hmdp/controller/BlogCommentsController.java` | 控制器 |
| 前端页面 | `hmdp/blog-detail.html` | 笔记详情页 |
| 设计方案 | `hmdp/ai/comment.txt` | 评论功能完整设计 |

---

## ✅ 验收标准

- [x] 用户可以发表一级评论
- [x] 评论列表按时间倒序显示
- [x] 用户可以对评论点赞
- [x] 用户可以删除自己的评论
- [x] 不能删除别人的评论
- [x] 评论内容不能为空
- [x] 评论内容不超过500字
- [x] 评论显示用户昵称和头像
- [x] 评论显示相对时间
- [x] 软删除（数据不丢失）

---

**文档版本**: v1.0  
**创建日期**: 2026-04-24  
**维护者**: LocalLife技术团队
