-- 私信消息表
CREATE TABLE IF NOT EXISTS tb_private_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID',
    from_user_id BIGINT NOT NULL COMMENT '发送者ID',
    to_user_id BIGINT NOT NULL COMMENT '接收者ID',
    content VARCHAR(1000) NOT NULL COMMENT '消息内容（文字或图片URL）',
    type VARCHAR(20) DEFAULT 'text' COMMENT '消息类型：text=文字, image=图片',
    status INT DEFAULT 0 COMMENT '消息状态：0=未读, 1=已读',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_from_user (from_user_id),
    INDEX idx_to_user (to_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='私信消息表';