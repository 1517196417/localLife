package com.hmdp.dto;

import lombok.Data;

import javax.validation.constraints.*;

/**
 * 商铺更新DTO（带参数校验）
 */
@Data
public class ShopUpdateDTO {

    /**
     * 商铺ID
     */
    @NotNull(message = "商铺ID不能为空")
    private Long id;

    /**
     * 商铺名称
     */
    @NotBlank(message = "商铺名称不能为空")
    @Size(min = 2, max = 50, message = "商铺名称长度必须在2-50之间")
    private String name;

    /**
     * 商铺类型ID
     */
    @NotNull(message = "商铺类型不能为空")
    private Long typeId;

    /**
     * 商铺图片
     */
    @NotBlank(message = "商铺图片不能为空")
    private String images;

    /**
     * 商铺地址
     */
    @NotBlank(message = "商铺地址不能为空")
    @Size(max = 200, message = "商铺地址长度不能超过200")
    private String address;

    /**
     * 经纬度
     */
    @NotNull(message = "经度不能为空")
    @DecimalMin(value = "-180.0", message = "经度范围必须在-180到180之间")
    @DecimalMax(value = "180.0", message = "经度范围必须在-180到180之间")
    private Double x;

    @NotNull(message = "纬度不能为空")
    @DecimalMin(value = "-90.0", message = "纬度范围必须在-90到90之间")
    @DecimalMax(value = "90.0", message = "纬度范围必须在-90到90之间")
    private Double y;

    /**
     * 营业时间
     */
    @NotBlank(message = "营业时间不能为空")
    private String openHours;

    /**
     * 联系电话
     */
    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}
