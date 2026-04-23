package com.yupi.yupicturebackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupicturebackend.model.dto.sapce.analyze.*;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.space.anslyze.*;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 空间分析
 */
@Service
public interface SpaceAnalyzeService extends IService<Space> {


    /**
     * 分析空间使用情况
     *
     * @param spaceUsageAnalyzeRequest
     * @param loginUser
     * @return
     */
    SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser);


    /**
     * 获取图片分类使用情况
     */
    List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyzeList(SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, User loginUser);

    /**
     *获取图片标签使用情况
     */
    List<SpaceTagAnalyzeResponse> getSpaceTagAnalyzeList(SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, User loginUser);

    /**
     * 获取图片大小分类情况
     */
    List<SpaceSizeAnalyzeResponse>  getSpaceSizeAnalyzeList(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser);

    /**
     * 空间用户上传行为情况
     */
    List<SpaceUserAnalyzeResponse>  getSpaceUserAnalyzeList(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser);


    /**
     * 获取空间排行分析
     */
    List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser);
}
