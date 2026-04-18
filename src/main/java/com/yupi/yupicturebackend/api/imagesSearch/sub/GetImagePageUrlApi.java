package com.yupi.yupicturebackend.api.imagesSearch.sub;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * 获取以图搜图页面地址（step 1）
 */
@Slf4j
public class GetImagePageUrlApi {

    /**
     * 通过图片 URL 上传到百度图片搜索
     * @param imageUrl 图片的 URL 地址
     * @return 百度返回的 JSON 结果
     */
    public static String getImagePageUrl(String imageUrl) {
        // 下载图片
        byte[] imageBytes = HttpUtil.downloadBytes(imageUrl);

        // 上传到百度
        long uptime = System.currentTimeMillis();
        String uploadUrl = "https://graph.baidu.com/upload?uptime=" + uptime;

        HttpResponse response = HttpRequest.post(uploadUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Origin", "https://graph.baidu.com")
                .header("Referer", "https://graph.baidu.com/")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-origin")
                .header("Acs-Token", "")
                .form("image", imageBytes, "image.jpg")
                .form("tn", "pc")
                .form("from", "pc")
                .form("image_source", "PC_UPLOAD_URL")
                .timeout(30000)
                .execute();

        JSONObject jsonObject = JSONUtil.parseObj(response.body());
        String url =   (String) jsonObject.get("data", JSONObject.class).get("url");
        return url;
    }
    public static void main(String[] args) {
        // 测试以图搜图功能
        String imageUrl = "https://yupii-1417936672.cos.ap-shanghai.myqcloud.com/public/2038180064522829825/2026-04-15_lRQZyL1mGwOfVbGI_thumbnail.jpg";
        String result = getImagePageUrl(imageUrl);
        System.out.println("搜索成功，结果 URL：" + result);
    }
}
