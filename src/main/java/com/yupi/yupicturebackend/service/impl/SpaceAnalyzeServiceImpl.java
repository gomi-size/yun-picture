package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.mapper.SpaceMapper;
import com.yupi.yupicturebackend.model.dto.sapce.analyze.*;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.UserRoleEnum;
import com.yupi.yupicturebackend.model.vo.space.anslyze.*;
import com.yupi.yupicturebackend.service.PictureService;
import com.yupi.yupicturebackend.service.SpaceAnalyzeService;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.UserService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author MECHREVO
 * @createDate 2026-04-13 15:40:54
 */
@Service
public class SpaceAnalyzeServiceImpl extends ServiceImpl<SpaceMapper, Space>
        implements SpaceAnalyzeService {

    @Resource
    private UserService userService;
    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceService spaceService;

    /**
     * 分析空间使用情况（先查询指定返回图片的size成为一个集合，然后再相加）
     *
     * @param spaceUsageAnalyzeRequest
     * @param loginUser
     * @return
     */
    @Override
    public SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser) {
        //校验参数
        ThrowUtils.throwIf(spaceUsageAnalyzeRequest == null, ErrorCode.PARAMS_ERROR, "请求参数不能为空");

        if (spaceUsageAnalyzeRequest.getSpaceId()==null) {
            //仅管理员可以使用
            checkSpaceAnalyzeAuth(spaceUsageAnalyzeRequest, loginUser);
            //开始查询合计图片大小
            QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
            queryWrapper.select("picSize").select();
            //补充查询参数
            fillAnalyzeQueryWrapper(spaceUsageAnalyzeRequest, queryWrapper);
            //总结一下，selectObjs 是一个提取单列数据的利器，性能极高，但千万记得只能给它 select 一个字段。
            List<Object> pictureObjList = pictureService.getBaseMapper().selectObjs(queryWrapper);
            //统计大小
            long useSize = pictureObjList.stream().mapToLong(obj -> (long) obj).sum();
            //统计数量
            Long useCount = (long) pictureObjList.size();
            SpaceUsageAnalyzeResponse spaceUsageAnalyzeResponse = new SpaceUsageAnalyzeResponse();
            spaceUsageAnalyzeResponse.setUsedSize(useSize);
            spaceUsageAnalyzeResponse.setMaxSize(null);
            spaceUsageAnalyzeResponse.setSizeUsageRatio(null);
            spaceUsageAnalyzeResponse.setUsedCount(useCount);
            spaceUsageAnalyzeResponse.setMaxCount(null);
            spaceUsageAnalyzeResponse.setCountUsageRatio(null);
            return spaceUsageAnalyzeResponse;
        } else {
            //特定的空间直接重Space中查询
            Long spaceId = spaceUsageAnalyzeRequest.getSpaceId();
            ThrowUtils.throwIf(spaceId == null, ErrorCode.PARAMS_ERROR, "空间不能为空");
            Space space = getById(spaceId);
            ThrowUtils.throwIf(ObjUtil.isNull(space), ErrorCode.PARAMS_ERROR, "空间不存在");
            //校验权限
            checkSpaceAnalyzeAuth(spaceUsageAnalyzeRequest, loginUser);
            //设置参数
            SpaceUsageAnalyzeResponse spaceUsageAnalyzeResponse = new SpaceUsageAnalyzeResponse();
            spaceUsageAnalyzeResponse.setUsedSize(space.getTotalSize());
            spaceUsageAnalyzeResponse.setMaxSize(space.getMaxSize());
            spaceUsageAnalyzeResponse.setUsedCount(space.getTotalCount());
            spaceUsageAnalyzeResponse.setMaxCount(space.getMaxCount());
            //计算比例
            double sizeUsageRatio = NumberUtil.round(space.getTotalSize() * 100.0 / space.getMaxSize(),2).doubleValue();
            double countUsageRatio = NumberUtil.round(space.getTotalCount() * 100.0 / space.getMaxCount(),2).doubleValue();
            spaceUsageAnalyzeResponse.setCountUsageRatio(countUsageRatio);
            spaceUsageAnalyzeResponse.setSizeUsageRatio(sizeUsageRatio);
            return spaceUsageAnalyzeResponse;
        }
    }

    /**
     * 获取分类的使用情况
     * @param spaceCategoryAnalyzeRequest
     * @param loginUser
     * @return
     */
    @Override
    public List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyzeList(SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, User loginUser) {
        //校验参数
       ThrowUtils.throwIf(spaceCategoryAnalyzeRequest==null,ErrorCode.PARAMS_ERROR,"请求参数为空");

       //校验权限
       checkSpaceAnalyzeAuth(spaceCategoryAnalyzeRequest, loginUser);
       //构建查询条件
       QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceCategoryAnalyzeRequest, queryWrapper);

        queryWrapper.select("category","count(*) as count","sum(picSize) as totalSize")
                .groupBy("category");

        //查询并转换结果
        return pictureService.getBaseMapper().selectMaps(queryWrapper)
                .stream()
                .map(result->{
                    String category = (String) result.get("category");
                    Long count = (Long) result.get("count");
                    Number totalSizeNum = (Number) result.get("totalSize");
                    long totalSize = totalSizeNum.longValue();
                    return new SpaceCategoryAnalyzeResponse(category,count,totalSize);
                }).collect(Collectors.toList());
    }

    /**
     * 获取图片标签使用情况
     * @param spaceTagAnalyzeRequest
     * @param loginUser
     * @return
     */
    @Override
    public List<SpaceTagAnalyzeResponse> getSpaceTagAnalyzeList(SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, User loginUser) {
        //校验参数
        ThrowUtils.throwIf(spaceTagAnalyzeRequest==null,ErrorCode.PARAMS_ERROR,"请求参数为空");

        //校验权限
        checkSpaceAnalyzeAuth(spaceTagAnalyzeRequest, loginUser);
        //构建查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceTagAnalyzeRequest, queryWrapper);

        queryWrapper.select("tags");

        //查询符合所有符合条件的标签
        List<String> tagsJsonList = pictureService.getBaseMapper()
                .selectMaps(queryWrapper)
                .stream()
                .filter(ObjUtil::isNotNull)
                /*.map(ObjUtil::toString)*/
                //这样就直接取出[{tags=["A","B"]}, {tags=["C"]}]中["A","B"]值来了
                .map(map -> ObjUtil.toString(map.get("tags")))
                .collect(Collectors.toList());

        //扁平化处理
        //解析标签
        Map<String, Long> tagCountMap = tagsJsonList.stream()
                .flatMap(tagsJson -> JSONUtil.toList(tagsJson, String.class).stream())
                .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()));

        //转化为响应类，按照使用次数进行排序
        return tagCountMap.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
                .map(entry -> new SpaceTagAnalyzeResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

    }

    /**
     * 获取图片大小分类请求
     * @param spaceSizeAnalyzeRequest
     * @param loginUser
     * @return
     */
    @Override
    public List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyzeList(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser) {
        //校验参数
        ThrowUtils.throwIf(spaceSizeAnalyzeRequest==null,ErrorCode.PARAMS_ERROR,"请求参数为空");

        //校验权限
        checkSpaceAnalyzeAuth(spaceSizeAnalyzeRequest, loginUser);
        //构建查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceSizeAnalyzeRequest, queryWrapper);

        queryWrapper.select("picSize");
        //查询符合所有符合条件的图片大小
        List<Long> picSizeList = pictureService.getBaseMapper().selectObjs(queryWrapper).stream()
                .filter(ObjUtil::isNotNull)
                .map(size->(Long) size)
                .collect(Collectors.toList());

        //定义分段范围，注意使用有序的Map
        LinkedHashMap<String, Long> sizeRanges = new LinkedHashMap<>();
        sizeRanges.put("<100KB", picSizeList.stream().filter(size -> size < 100 * 1024).count());
        sizeRanges.put("100KB-500KB", picSizeList.stream().filter(size -> size >= 100 * 1024 && size < 500 * 1024).count());
        sizeRanges.put("500KB-1MB", picSizeList.stream().filter(size -> size >= 500 * 1024 && size < 1 * 1024 * 1024).count());
        sizeRanges.put(">1MB", picSizeList.stream().filter(size -> size >= 1 * 1024 * 1024).count());

        //转换为响应对象
        return sizeRanges.entrySet()
                .stream()
                .map(entry->new SpaceSizeAnalyzeResponse(entry.getKey(),entry.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * 空间用户上传行为情况
     * @param spaceUserAnalyzeRequest
     * @param loginUser
     * @return
     */
    @Override
    public List<SpaceUserAnalyzeResponse> getSpaceUserAnalyzeList(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser) {
        //校验参数
        ThrowUtils.throwIf(spaceUserAnalyzeRequest==null,ErrorCode.PARAMS_ERROR,"请求参数为空");
        //校验权限
        checkSpaceAnalyzeAuth(spaceUserAnalyzeRequest, loginUser);

        //凭借查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        Long userId = spaceUserAnalyzeRequest.getUserId();
        queryWrapper.eq(ObjUtil.isNotNull(userId),"userId",userId);
        fillAnalyzeQueryWrapper(spaceUserAnalyzeRequest, queryWrapper);

        // 分析维度：每日、每周、每月
        String timeDimension = spaceUserAnalyzeRequest.getTimeDimension();
        switch (timeDimension) {
            case "day":
                queryWrapper.select("DATE_FORMAT(createTime, '%Y-%m-%d') AS period", "COUNT(*) AS count");
                break;
            case "week":
                queryWrapper.select("YEARWEEK(createTime) AS period", "COUNT(*) AS count");
                break;
            case "month":
                queryWrapper.select("DATE_FORMAT(createTime, '%Y-%m') AS period", "COUNT(*) AS count");
                break;
            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的时间维度");
        }
        //进行分组
        queryWrapper.groupBy("period").orderByAsc("period");
        List<Map<String, Object>> queryResult = pictureService.getBaseMapper().selectMaps(queryWrapper);

        //查询并转换结果
        return queryResult
                .stream()
                .map(result->{
                    String period = result.get("period").toString();
                    Number number = (Number) result.get("count");
                    long count = number.longValue();
                    return new SpaceUserAnalyzeResponse(period,count);
                }).collect(Collectors.toList());
    }

    /**
     * 获取空间排名（仅管理员使用）
     * @param spaceRankAnalyzeRequest
     * @param loginUser
     * @return
     */
    @Override
    public List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser) {
        //校验参数
        ThrowUtils.throwIf(spaceRankAnalyzeRequest==null,ErrorCode.PARAMS_ERROR,"请求参数为空");
        //仅管理员
        ThrowUtils.throwIf(!userService.isAdmin(loginUser),ErrorCode.NO_AUTH_ERROR,"仅管理员使用");

        //构造查询请求
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id","spaceName","userId","totalSize")
                .orderByDesc("totalSize")
                .last("limit "+spaceRankAnalyzeRequest.getTopN());

        return spaceService.list(queryWrapper);
    }

    /**
     * 校验分析空间权限
     *
     * @param spaceAnalyzeRequest
     * @param loginUser
     */
    private void checkSpaceAnalyzeAuth(SpaceAnalyzeRequest spaceAnalyzeRequest, User loginUser) {
        Long spaceId = spaceAnalyzeRequest.getSpaceId();
        boolean queryPublic = spaceAnalyzeRequest.isQueryPublic();
        boolean queryAll = spaceAnalyzeRequest.isQueryAll();
        //分析公开的和全部的只能是管理员
        if (queryPublic || queryAll) {
            ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "仅管理员可用");
        } else {
            //私有空间就是只能管理员和本人分析
            ThrowUtils.throwIf(spaceId == null, ErrorCode.PARAMS_ERROR);
            Space space = getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            //仅限用户本人或者管理员进行操作
            ThrowUtils.throwIf(!space.getUserId().equals(loginUser.getId()) &&
                    !UserRoleEnum.ADMIN.getValue().equals(loginUser.getUserRole()), ErrorCode.NO_AUTH_ERROR);
        }
    }

    /**
     * 凭借查询条件
     *
     * @param spaceAnalyzeRequest
     * @param queryWrapper
     */
    private void fillAnalyzeQueryWrapper(SpaceAnalyzeRequest spaceAnalyzeRequest, QueryWrapper<Picture> queryWrapper) {
        if (spaceAnalyzeRequest.isQueryAll()) {
            return;
        }
        if (spaceAnalyzeRequest.isQueryPublic()) {
            queryWrapper.isNull("spaceId");
            return;
        }
        Long spaceId = spaceAnalyzeRequest.getSpaceId();
        if (spaceId != null) {
            queryWrapper.eq("spaceId", spaceId);
            return;
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "未查询指定范围");
    }


}




