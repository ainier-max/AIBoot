package com.fourfaith.iot.irrigation.controller.dynamic;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fourfaith.common.pojo.ResultDto;
import com.fourfaith.iot.irrigation.pojo.vo.pump.PumpStatVo;
import com.fourfaith.iot.irrigation.pojo.entity.BaseEntity;
import com.fourfaith.iot.irrigation.pojo.entity.project.Project;
import com.fourfaith.iot.irrigation.service.pump.PumpControlRecordService;
import com.fourfaith.iot.irrigation.service.system.UserPermissionService;
import com.fourfaith.dubbo.utils.SecurityUtils;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

/**
 * Created by cbc on 2018/7/5.
 */
@RestController
@RequestMapping(value = "/api/irrigation-monitoring-platform/dashboard", produces = "application/json;charset=UTF-8")
public class DynamicSelect {
    @Autowired
    private SqlSessionFactory sqlSessionFactory;
    @Autowired
    private PumpControlRecordService pumpControlRecordService;
    @Autowired
    private UserPermissionService userPermissionService;

    @PostMapping("/api/irrigation-monitoring-platform/{namespace}/select/{sqlId}")
    public ResponseEntity<Map<String, Object>> selectByNamespace(@PathVariable("namespace") String namespace,
            @PathVariable("sqlId") String sqlId, @RequestBody String param, HttpServletRequest request,
            HttpServletResponse response) {
        System.out.println("执行查询操作,查询信息" + namespace+"."+sqlId);
        return select(param, namespace + "." + sqlId, request, response);
    }

    /**
     * 统计泵站在线设备数量及总数量
     * @param pumpType 设备类型 (1:泵站(机井）, 2:阀门, 3:闸门, 不传则统计全部)
     * @return ResultDto<PumpStatVo>
     */
    @GetMapping("/stat")
    public ResultDto<PumpStatVo> getPumpStat(@RequestParam(value = "pumpType", required = false) Integer pumpType) {
        PumpStatVo statVo = pumpControlRecordService.getPumpStat(pumpType);
        return ResultDto.returnSuccessData(statVo);
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
            injectSecurityScope(map);
            if (dynamicSql != null && !"".equals(dynamicSql)) {
                map.put("sql", dynamicSql);
            }
            sqlSession = sqlSessionFactory.openSession();
            String sql = (String) map.get("sql");
            MappedStatement mappedStatement = sqlSession.getConfiguration().getMappedStatement(sql);
            BoundSql boundSql = mappedStatement.getBoundSql(map);
            System.out.println("执行SQL:");
            System.out.println(getExecutableSql(sqlSession.getConfiguration(), boundSql, map));
            List<Map<String, Object>> list = sqlSession.selectList(sql, map);
            Date date2 = new Date();
            String durationTime = (date2.getTime() - date1.getTime()) + "MS";
            System.out.println("执行时间：" + durationTime);
            resultMap.put("code", 200);
            resultMap.put("success", true);
            resultMap.put("message", "操作成功！");
            resultMap.put("data", list);
            resultMap.put("time", durationTime);
            System.out.println("----------End----------");
            return ResponseEntity.ok(resultMap);
        } catch (Exception e) {
            e.printStackTrace();
            resultMap.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
            resultMap.put("success", false);
            resultMap.put("message", e.getMessage());
            resultMap.put("data", null);
            System.out.println("----------End----------");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resultMap);
        } finally {
            if (sqlSession != null) {
                sqlSession.close();
                sqlSession = null;
            }
        }
    }

    private void injectSecurityScope(Map map) {
        map.put("tenantId", SecurityUtils.getTenantId());
        map.put("applicationId", SecurityUtils.getApplicationId());
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            map.put("projectIds", Collections.emptyList());
            return;
        }
        List<Project> userProject = userPermissionService.getUserProject(userId);
        List<Long> projectIds = userProject == null ? Collections.<Long>emptyList()
                : userProject.stream().map(BaseEntity::getId).filter(Objects::nonNull).collect(Collectors.toList());
        map.put("projectIds", projectIds);
    }

    private String getExecutableSql(Configuration configuration, BoundSql boundSql, Object parameterObject) {
        String sql = boundSql.getSql().replaceAll("[\\s]+", " ");
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        if (parameterMappings == null || parameterMappings.isEmpty()) {
            return sql;
        }
        MetaObject metaObject = parameterObject == null ? null : configuration.newMetaObject(parameterObject);
        for (ParameterMapping parameterMapping : parameterMappings) {
            String propertyName = parameterMapping.getProperty();
            Object value;
            if (boundSql.hasAdditionalParameter(propertyName)) {
                value = boundSql.getAdditionalParameter(propertyName);
            } else if (parameterObject == null) {
                value = null;
            } else if (configuration.getTypeHandlerRegistry().hasTypeHandler(parameterObject.getClass())) {
                value = parameterObject;
            } else {
                value = metaObject == null ? null : metaObject.getValue(propertyName);
            }
            sql = sql.replaceFirst("\\?", Matcher.quoteReplacement(formatSqlValue(value)));
        }
        return sql;
    }

    private String formatSqlValue(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Date) {
            return "'" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format((Date) value) + "'";
        }
        return "'" + String.valueOf(value).replace("'", "''") + "'";
    }
}
