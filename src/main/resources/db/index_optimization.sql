-- ============================================
-- 数据库索引优化脚本
-- 目的：提升查询性能，减少慢查询
-- ============================================

-- 1. tb_blog 表索引优化
-- 优化热门博客查询（ORDER BY liked DESC）
ALTER TABLE tb_blog ADD INDEX idx_liked (liked DESC);

-- 优化用户博客查询（WHERE user_id = ? ORDER BY create_time DESC）
ALTER TABLE tb_blog ADD INDEX idx_user_id_create_time (user_id, create_time DESC);

-- 优化商铺博客查询（WHERE shop_id = ?）
ALTER TABLE tb_blog ADD INDEX idx_shop_id (shop_id);

-- 2. tb_voucher_order 表索引优化
-- 优化一人一单查询（WHERE user_id = ? AND voucher_id = ?）
ALTER TABLE tb_voucher_order ADD UNIQUE INDEX uk_user_voucher (user_id, voucher_id);

-- 优化订单查询（WHERE user_id = ?）
ALTER TABLE tb_voucher_order ADD INDEX idx_user_id (user_id);

-- 3. tb_follow 表索引优化
-- 优化关注关系查询（WHERE user_id = ? AND follow_user_id = ?）
ALTER TABLE tb_follow ADD UNIQUE INDEX uk_user_follow (user_id, follow_user_id);

-- 优化粉丝列表查询（WHERE follow_user_id = ?）
ALTER TABLE tb_follow ADD INDEX idx_follow_user_id (follow_user_id);

-- 4. tb_sign 表索引优化
-- 优化签到查询（WHERE user_id = ? AND year = ? AND month = ?）
ALTER TABLE tb_sign ADD INDEX idx_user_year_month (user_id, year, month);

-- 5. tb_shop 表索引优化
-- 优化商铺类型查询（WHERE type_id = ?）
ALTER TABLE tb_shop ADD INDEX idx_type_id (type_id);

-- 优化商铺评分查询（ORDER BY score DESC）
ALTER TABLE tb_shop ADD INDEX idx_score (score DESC);

-- 6. tb_blog_comments 表索引优化
-- 优化博客评论查询（WHERE blog_id = ? ORDER BY create_time）
ALTER TABLE tb_blog_comments ADD INDEX idx_blog_id_create_time (blog_id, create_time);

-- ============================================
-- 索引优化说明
-- ============================================
-- 1. 唯一索引（UNIQUE INDEX）：保证数据唯一性，同时提升查询性能
-- 2. 联合索引：遵循最左前缀原则，提高多条件查询效率
-- 3. 降序索引（DESC）：优化ORDER BY DESC查询
-- 
-- 验证索引效果：
-- EXPLAIN SELECT * FROM tb_blog ORDER BY liked DESC LIMIT 10;
-- EXPLAIN SELECT * FROM tb_voucher_order WHERE user_id = 1 AND voucher_id = 100;
-- ============================================
