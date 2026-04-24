package com.hmdp.exception;

import lombok.Getter;

/**
 * 错误码枚举
 * 统一管理所有业务错误码
 */
@Getter
public enum ResultCode {

    // 成功
    SUCCESS(200, "操作成功"),

    // 客户端错误 400-499
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有权限"),
    NOT_FOUND(404, "资源不存在"),
    PARAM_ERROR(400, "参数校验失败"),

    // 服务器错误 500-599
    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),
    SERVICE_UNAVAILABLE(503, "服务不可用"),

    // 业务错误 1000-9999
    USER_NOT_EXIST(1001, "用户不存在"),
    USER_ALREADY_EXIST(1002, "用户已存在"),
    PASSWORD_ERROR(1003, "密码错误"),
    PHONE_FORMAT_ERROR(1004, "手机号格式错误"),
    VERIFY_CODE_ERROR(1005, "验证码错误"),
    VERIFY_CODE_EXPIRED(1006, "验证码已过期"),

    SHOP_NOT_EXIST(2001, "商铺不存在"),
    SHOP_UPDATE_ERROR(2002, "商铺更新失败"),

    VOUCHER_NOT_EXIST(3001, "优惠券不存在"),
    VOUCHER_STOCK_NOT_ENOUGH(3002, "库存不足"),
    VOUCHER_ALREADY_BUY(3003, "您已经购买过了"),
    VOUCHER_NOT_BEGIN(3004, "优惠券尚未开始"),
    VOUCHER_ALREADY_END(3005, "优惠券已结束"),

    BLOG_NOT_EXIST(4001, "笔记不存在"),
    BLOG_LIKE_ERROR(4002, "点赞失败"),

    ORDER_NOT_EXIST(5001, "订单不存在"),
    ORDER_PAY_ERROR(5002, "支付失败"),

    SYSTEM_BUSY(9001, "系统繁忙，请稍后重试"),
    RATE_LIMIT(9002, "请求过于频繁，请稍后重试");

    /**
     * 错误码
     */
    private final Integer code;

    /**
     * 错误信息
     */
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
