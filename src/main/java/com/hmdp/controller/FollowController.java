package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private IFollowService followService;



    //关注功能
    @PutMapping("/{id}/{isFollow}")
    public Result Follow(@PathVariable Long id, @PathVariable Boolean isFollow){
        return followService.Follow(id, isFollow);
    }

    //判断是否关注
    @GetMapping("/or/not/{id}")
    public Result IsFollow(@PathVariable Long id){
        return followService.isFollow(id);
    }

    @GetMapping("/common/{id}")
    public Result FollowCommon (@PathVariable Long id){
        return followService.followCommon(id);
    }


}
