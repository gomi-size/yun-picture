package com.yupi.yupicturebackend.api.aliyunAi;


import cn.hutool.core.util.StrUtil;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.yupi.yupicturebackend.api.aliyunAi.model.CreateOutPaintingTaskRequest;
import com.yupi.yupicturebackend.api.aliyunAi.model.CreateOutPaintingTaskResponse;
import com.yupi.yupicturebackend.api.aliyunAi.model.GetOutPaintingTaskResponse;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class aliyunAiApi {
    /**
     * 读取配置文件
     */
    @Value("${aliYunAi.apiKey}")
    private String apiKey;

    public static final String CREAT_OUT_PAINTING_TASK_URL="https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/out-painting";
    public static final String GET_OUT_PAINTING_TASK_URL="https://dashscope.aliyuncs.com/api/v1/tasks/%s";



    /**
     * 创建任务
     * @param createOutPaintingTaskRequest
     * @return
     */
    public CreateOutPaintingTaskResponse createOutPaintingTask(CreateOutPaintingTaskRequest createOutPaintingTaskRequest) {
        ThrowUtils.throwIf(createOutPaintingTaskRequest==null, ErrorCode.PARAMS_ERROR,"参数不存在");

        //发送请求
        HttpRequest httpRequest = HttpRequest.post(CREAT_OUT_PAINTING_TASK_URL)
                .header(Header.AUTHORIZATION, "Bearer" + apiKey)
                //开启异步执行
                .header("X-DashScope-Async", "enable")
                .header("Content-Type", "application/json")
                //是设置请求的主要类，方便用户进行只定义操作
                .body(JSONUtil.toJsonStr(createOutPaintingTaskRequest));

        //处理响应
        try(HttpResponse httpResponse = httpRequest.execute()) {
            if(!httpResponse.isOk()){
                log.error("请求异常：{}",httpResponse.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"Ai扩图失败");
            }
            //将得到的body转换为我们要封装返回的类
            CreateOutPaintingTaskResponse response = JSONUtil.toBean(httpResponse.body(), CreateOutPaintingTaskResponse.class);
            //这里还需要判断一下
            String code = response.getCode();
            if(StrUtil.isNotBlank(code)){
                String message = response.getMessage();
                log.error("AI扩图失败，{}",message);
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"ai扩图失败");
            }
        return response;
        }
    }


    /**
     * 查询创建的任务结果(因为是异步请求所以要查询一下)
     * @param taskId
     * @return
     */
    public GetOutPaintingTaskResponse getOutPaintingTask(String taskId) {

        ThrowUtils.throwIf(StrUtil.isBlank(taskId),ErrorCode.PARAMS_ERROR);
        HttpRequest httpRequest = HttpRequest.get(String.format(GET_OUT_PAINTING_TASK_URL, taskId));
        try(HttpResponse httpResponse = httpRequest
                .header(Header.AUTHORIZATION, "Bearer" + apiKey)
                .execute()
        ) {
            if(!httpResponse.isOk()){
                log.error("查询请求结果异常：{}",httpResponse.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"获取任务失败");
            }
            return JSONUtil.toBean(httpResponse.body(), GetOutPaintingTaskResponse.class);
        }
    }
}
