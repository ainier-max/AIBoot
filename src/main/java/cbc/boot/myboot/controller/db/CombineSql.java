package cbc.boot.myboot.controller.db;

import cbc.boot.myboot.controller.db.util.CombineSqlUtil;
import cbc.boot.myboot.util.GetIPUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.SimpleDateFormat;
import java.util.*;

@RestController
public class CombineSql {
    
    @Autowired
    private CombineSqlUtil combineSqlUtil;

    @PostMapping("/cbc/combineSql.cbc")
    public ResponseEntity<List<Object>> combineSql(@RequestBody String param, HttpServletRequest request,
            HttpServletResponse response) {
        System.out.println("----------Start(Author:陈斌才)----------");
        System.out.println("执行组合SQL操作!");
        System.out.println("传入参数:");
        System.out.println(param);
        List<Object> returnList = new ArrayList<Object>();
        HashMap<String, Object> hashMap = new HashMap<String, Object>();
        Date date1 = new Date();
        String realIP = GetIPUtil.getIpAddr(request);
        System.out.println("请求客户端IP地址：" + realIP);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String nowTime = dateFormat.format(date1);
        System.out.println("请求时间：" + nowTime);
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map map = mapper.readValue(param, Map.class);
            
            Object result = combineSqlUtil.executeCombineSql(map);
            
            hashMap.put("state", "success");
            Date date2 = new Date();
            String durationTime = (date2.getTime() - date1.getTime()) + "MS";
            hashMap.put("time", durationTime);
            System.out.println("执行时间：" + durationTime);
            hashMap.put("objects", result);
            returnList.add(hashMap);
            System.out.println("----------End(Author:陈斌才)----------");
            return ResponseEntity.ok(returnList);
        } catch (Exception e) {
            e.printStackTrace();
            hashMap.put("state", "error");
            hashMap.put("message", e.getMessage());
            returnList.add(hashMap);
            System.out.println("----------End(Author:陈斌才)----------");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(returnList);
        }
    }
}
