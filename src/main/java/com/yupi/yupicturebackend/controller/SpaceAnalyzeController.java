package com.yupi.yupicturebackend.controller;


import com.yupi.yupicturebackend.common.BaseResponse;
import com.yupi.yupicturebackend.common.ResultUtils;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.model.dto.sapce.analyze.*;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.space.anslyze.*;
import com.yupi.yupicturebackend.service.SpaceAnalyzeService;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/space/analyze")
@Slf4j
public class SpaceAnalyzeController {

    @Resource
    private UserService userService;
    @Resource
    private SpaceService spaceService;
    @Resource
    private SpaceAnalyzeService spaceAnalyzeService;


    /**
     * 获取空间的使用状态
     */
    @PostMapping("/usage")
    public BaseResponse<SpaceUsageAnalyzeResponse> GetSpaceUsageAnalyze(@RequestBody SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest,HttpServletRequest request) {
        ThrowUtils.throwIf(spaceUsageAnalyzeRequest==null,ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        SpaceUsageAnalyzeResponse spaceUsageAnalyze = spaceAnalyzeService.getSpaceUsageAnalyze(spaceUsageAnalyzeRequest, loginUser);
        return ResultUtils.success(spaceUsageAnalyze);
    }
    /**
     * 获取空间图片分类分析
     */
    @PostMapping("/category")
    public BaseResponse<List<SpaceCategoryAnalyzeResponse>> GetSpaceCategoryAnalyze(@RequestBody SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceCategoryAnalyzeRequest==null,ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        List<SpaceCategoryAnalyzeResponse> spaceCategoryAnalyzeList = spaceAnalyzeService.getSpaceCategoryAnalyzeList(spaceCategoryAnalyzeRequest, loginUser);
        return ResultUtils.success(spaceCategoryAnalyzeList);
    }


    /**
     * 获取空间图片分标签分析
     */
    @PostMapping("/tags")
    public BaseResponse<List<SpaceTagAnalyzeResponse>> GetSpaceTagAnalyzeList(@RequestBody SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceTagAnalyzeRequest==null,ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(spaceAnalyzeService.getSpaceTagAnalyzeList(spaceTagAnalyzeRequest, loginUser));
    }
    /**
     * 获取空间图片大小分析
     */
    @PostMapping("/size")
    public BaseResponse<List<SpaceSizeAnalyzeResponse>> getSpaceSizeAnalyzeList(@RequestBody SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceSizeAnalyzeRequest==null,ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(spaceAnalyzeService.getSpaceSizeAnalyzeList(spaceSizeAnalyzeRequest, loginUser));
    }

    /**
     * 空间用户上传行为情况
     */
    @PostMapping("/user")
    public BaseResponse<List<SpaceUserAnalyzeResponse>> getSpaceUserAnalyzeList(@RequestBody SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceUserAnalyzeRequest==null,ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(spaceAnalyzeService.getSpaceUserAnalyzeList(spaceUserAnalyzeRequest, loginUser));
    }

    /**
     * 获取空间使用排行分析
     */
    @PostMapping("rank")
    public BaseResponse<List<Space>> getSpaceRankAnalyze(@RequestBody SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceRankAnalyzeRequest==null,ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(spaceAnalyzeService.getSpaceRankAnalyze(spaceRankAnalyzeRequest, loginUser));
    }


}