package cbc.boot.myboot.controller.db;

import cbc.boot.myboot.controller.db.util.DynamicDataSourceHolder;
import cbc.boot.myboot.util.GetIPUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by cbc on 2018/7/5.
 */
@RestController
public class DynamicSelect {
    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @PostMapping("/api/select/{namespace}/{sqlId}")
    public ResponseEntity<Map<String, Object>> selectByNamespace(@PathVariable("namespace") String namespace,
            @PathVariable("sqlId") String sqlId, @RequestBody String param, HttpServletRequest request,
            HttpServletResponse response) {
        System.out.println("执行查询操作,查询信息" + namespace+"."+sqlId);
        return select(param, namespace + "." + sqlId, request, response);
    }

    private ResponseEntity<Map<String, Object>> select(String param, String dynamicSql, HttpServletRequest request,
            HttpServletResponse response) {
        System.out.println("传入参数:");
        System.out.println(param);
        HashMap<String, Object> resultMap = new HashMap<String, Object>();
        SqlSession sqlSession = null;
        Date date1 = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");// 可以方便地修改日期格式
        String nowTime = dateFormat.format(date1);
        System.out.println("请求时间：" + nowTime);
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map map = mapper.readValue(param, Map.class);
            if (dynamicSql != null && !"".equals(dynamicSql)) {
                map.put("sql", dynamicSql);
            }
            // 设置数据源
            String db = (String) map.get("db");
            if (db != null && !"".equals(db)) {
                DynamicDataSourceHolder.setDataSourceType(db);
            }
            sqlSession = sqlSessionFactory.openSession();
            List<Map<String, Object>> list = sqlSession.selectList((String) map.get("sql"), map);
            Date date2 = new Date();
            String durationTime = (date2.getTime() - date1.getTime()) + "MS";
            System.out.println("执行时间：" + durationTime);
            resultMap.put("code", 200);
            resultMap.put("success", true);
            resultMap.put("message", "操作成功！");
            resultMap.put("data", list);
            resultMap.put("time", durationTime);
            System.out.println("----------End(Author:陈斌才)----------");
            return ResponseEntity.ok(resultMap);
        } catch (Exception e) {
            e.printStackTrace();
            resultMap.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
            resultMap.put("success", false);
            resultMap.put("message", e.getMessage());
            resultMap.put("data", null);
            System.out.println("----------End(Author:陈斌才)----------");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resultMap);
        } finally {
            if (sqlSession != null) {
                sqlSession.close();
                sqlSession = null;
            }
        }
    }
}
