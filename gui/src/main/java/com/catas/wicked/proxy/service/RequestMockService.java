package com.catas.wicked.proxy.service;

import com.catas.wicked.common.bean.IdGenerator;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.bean.message.ResponseMessage;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.catas.wicked.common.pipeline.Topic;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class RequestMockService {
    
    @Inject
    private RequestViewService requestViewService;

    @Inject
    private MessageQueue messageQueue;

    @Inject
    private IdGenerator idGenerator;

    private int index;

    private static final List<String> hostList = new ArrayList<>();
    private static final List<String> reqHeadersList = new ArrayList<>();;
    private static final List<String> respHeadersList = new ArrayList<>();;

    private static final String requestHeadersStr = """
            Accept: image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8
            Accept-Encoding: gzip, deflate, br
            Accept-Language: zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7
            Connection: keep-alive
            Cookie: user_device_id=5e9b3e1b29bb4a9994a6407ca6550a12; user_device_id_timestamp=1690470481782
            DNT: 1
            Host: tu.duoduocdn.com
            Sec-Fetch-Dest: image
            Sec-Fetch-Mode: no-cors
            Sec-Fetch-Site: cross-site
            User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36
            sec-ch-ua: "Google Chrome";v="119", "Chromium";v="119", "Not?A_Brand";v="24"
            sec-ch-ua-mobile: ?0
            """;

    private static final String respHeadersStr = """
            Server: AliyunOSS
            Date: Wed, 13 Sep 2023 22:20:43 GMT
            x-oss-request-id: 650235BBC428EB3735CEDBE5
            x-oss-object-type: Normal
            x-oss-hash-crc64ecma: 10411571979871187322
            x-oss-storage-class: Standard
            x-oss-meta-gid: 0
            x-oss-meta-mode: 33188
            x-oss-meta-mtime: 1680603328
            x-oss-meta-uid: 0
            x-oss-server-time: 91
            X-NWS-LOG-UUID: 14805626941644973238
            X-Cache-Lookup: Cache Hit
            Access-Control-Allow-Methods: GET,POST,OPTIONS;
            Last-Modified: Tue, 04 Apr 2023 10:18:39 GMT
            Etag: "0ADD3C939714B20EEC35F7188426EC11"
            Content-Type: image/png
            Content-MD5: Ct08k5cUsg7sNfcYhCbsEQ==
            Content-Length: 1666
            Accept-Ranges: bytes      
            """;

    private static final String hostStr = """
            GET https://www.google.com/index/1
            GET https://www.google.com/index/2
            GET https://www.google.com/index/3
            GET https://www.google.com/index/4
            GET https://www.google.com/index/5
            GET https://www.google.com/index/page/1
            POST https://www.google.com/index/page/2
            POST https://www.google.com/index/page/3?name=111&age=222&host=333.3
            GET https://www.amzaon.com/home
            PUT https://www.google.com/page
            DELETE https://www.google.com/home/deftail/2
            GET https://www.google.com/home/deftail/2?name=jack&host=local
            DELETE https://www.amazon.com
            PUT https://www.bing.com/index
            POST https://www.bing.com/home
            OPTION https://www.bing.com/home/getUserInfo
            OPTION https://www.bing.com/home/getUserInfo
            OPTION https://www.bing.com/home/getUserInfo
            OPTION https://www.bing.com/home/getUserInfo
            POST https://www.bing.com/home/getUserInfo
            POST https://www.bing.com/home/getUserInfo
            POST https://www.bing.com/home/getUserInfo
            POST https://www.bing.com/home
            POST https://www.bing.com/home
            POST https://www.microsoft.com/search
            GET https://www.microsoft.com/lolo
            GET https://www.bing.com
            GET https://www.baidu.com/index/1
            POST https://www.baidu.com/index/2
            POST https://www.baidu.com/index/3
            DELETE https://www.baidu.com/del
            DELETE https://www.baidu.com/ssl
            GET https://www.baidu.com/searchsearch?q=huaban&form=QBLH&sp=-1&lq=0&pq=huaban&sc=10-6&qs=n&sk=&cvid=12
            GET https://www.baidu.com/searchsearch?q=asfef&form=ss&sp=-1&lq=0&pq=huaban&sc=10-6&qs=n&sk=&cvid=12
            """;

    private static final String sampleJson = """
            {"position":"web-discover-feedback","pit_name":"发现页-结果问卷","is_dynamic":0,"idea_cnt":1,"weight_down_flag":false,"pit_modules":[{"module_id":2849,"module_name":"创意2023-10-24 14:29:10","plan_id":1980,"plan_name":"用户评测-发现页推荐结果反馈","stay_seconds":0,"trigger_type":null,"trigger_condition":null,"module_type":"FUNCTION","is_now_pop_up":true,"pit_mark":"web-discover-feedback","pit_name":"发现页-结果问卷","material":{"answers":[{"question":"您认为当前内容质量存在什么问题？","options":[{"value":" 图像质量低"},{"value":"配色缺乏质感"},{"value":"元素结构不合理"},{"value":"内容主题质量低"},{"value":"其他"}],"title":"内容质量低"},{"question":"您认为当前内容新颖性存在什么问题？","options":[{"value":"缺乏主流媒体流行风格"},{"value":"缺乏流行创意题材"},{"value":"缺乏流行表现形式"},{"value":"其他"}],"title":"新颖性不足"},{"question":"您认为当前内容丰富性存在什么问题？","options":[{"value":"风格单一"},{"value":"配色单一"},{"value":"设计类型单一 （例如海报）"},{"value":"设计手法单一（例如3D）"},{"value":"内容主题单一"},{"value":"其他"}],"title":"丰富性不足"},{"question":"您认为当前内容惊喜性存在什么问题？","options":[{"value":"未推荐其他我感兴趣的内容"},{"value":"其他"}],"title":"惊喜性不足"},{"question":"您认为当前内容推荐实时性存在什么问题？","options":[{"value":"推荐的内容没太大变化"},{"value":"未推荐我近期喜爱的内容"},{"value":"其他"}],"title":"实时性不足"},{"question":"您认为当前内容推荐缺乏哪些前沿内容？","options":[{"value":"缺乏视觉流行趋势的内容"},{"value":"缺乏先锋艺术家的作品内容"},{"value":"缺乏新技术的视觉内容"},{"value":"其他"}],"title":"前沿性不足"}]},"start_time":1698076800000,"end_time":1704038399000,"material_type":"search_feedback"}]}
            """;

    private static final String sampleXml = String.join("\n", new String[] {
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>",
            "<!-- Sample XML -->",
            "< orders >",
            "	<Order number=\"1\" table=\"center\">",
            "		<items>",
            "			<Item>",
            "				<type>ESPRESSO</type>",
            "				<shots>2</shots>",
            "				<iced>false</iced>",
            "				<orderNumber>1</orderNumber>",
            "			</Item>",
            "			<Item>",
            "				<type>CAPPUCCINO</type>",
            "				<shots>1</shots>",
            "				<iced>false</iced>",
            "				<orderNumber>1</orderNumber>",
            "			</Item>",
            "			<Item>",
            "			<type>LATTE</type>",
            "				<shots>2</shots>",
            "				<iced>false</iced>",
            "				<orderNumber>1</orderNumber>",
            "			</Item>",
            "			<Item>",
            "				<type>MOCHA</type>",
            "				<shots>3</shots>",
            "				<iced>true</iced>",
            "				<orderNumber>1</orderNumber>",
            "			</Item>",
            "		</items>",
            "	</Order>",
            "</orders>"
    });

    static {
        hostList.addAll(List.of(hostStr.split("\\n")));
        reqHeadersList.addAll(List.of(requestHeadersStr.split("\\n")));
        respHeadersList.addAll(List.of(respHeadersStr.split("\\n")));
    }

    public void mockRequest() {
        String url = hostList.get(index % (hostList.size() - 1));
        String[] split = url.split(" ");

        RequestMessage msg = new RequestMessage(split[1]);
        msg.setRequestId(idGenerator.nextId());
        msg.setMethod(split[0]);
        msg.setHeaders(getHeaders(reqHeadersList, index));
        msg.setBody(sampleJson.getBytes(StandardCharsets.UTF_8));

        ResponseMessage responseMessage = new ResponseMessage();
        Map<String, String> respHeaders = getHeaders(respHeadersList, index);
        if (index % 2 == 0) {
            responseMessage.setContent(sampleXml.getBytes(StandardCharsets.UTF_8));
            respHeaders.put("Content-Type", "application/xml");
        } else {
            responseMessage.setContent(sampleJson.getBytes());
            respHeaders.put("Content-Type", "application/json");
        }
        responseMessage.setStatus(200);
        responseMessage.setHeaders(respHeaders);

        msg.setResponse(responseMessage);
        messageQueue.pushMsg(Topic.RECORD, msg);

        if (index > 1000) {
            index = 0;
        } else {
            index ++;
        }
    }

    private Map<String, String> getHeaders(List<String> headersList, int index) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : headersList) {
            int idx = line.indexOf(':');
            String key = line.substring(0, idx);
            String val = line.substring(idx);
            map.put(key, val);
        }
        List<String> ls = List.of("application/json", "application/xml",
                "application/x-www-form-urlencoded", "text/html");
        map.put("Content-Type", ls.get(index % 4));
        return map;
    }
}
