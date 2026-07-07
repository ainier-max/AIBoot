package com.fourfaith.iot.irrigation.controller.dynamic;

import com.alibaba.excel.EasyExcel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fourfaith.dubbo.utils.SecurityUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 动态查询并导出 Excel。
 */
@RestController
public class DynamicExportExcel {
    private static final String EXPORT_CONFIG_PATH = "mapper/mysql/dynamic/export.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @PostMapping("/api/irrigation-monitoring-platform/{namespace}/exportExcel/{sqlId}")
    public void exportByNamespace(@PathVariable("namespace") String namespace,
            @PathVariable("sqlId") String sqlId, @RequestBody String param, HttpServletRequest request,
            HttpServletResponse response) {
        System.out.println("执行导出操作,查询信息" + namespace + "." + sqlId);
        export(param, namespace, sqlId, request, response);
    }

    private void export(String param, String namespace, String sqlId, HttpServletRequest request,
            HttpServletResponse response) {
        System.out.println("传入参数:");
        System.out.println(param);
        SqlSession sqlSession = null;
        Date date1 = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String nowTime = dateFormat.format(date1);
        System.out.println("请求时间：" + nowTime);
        try {
            Map map = OBJECT_MAPPER.readValue(param, Map.class);
            injectSecurityScope(map);
            String dynamicSql = namespace + "." + sqlId;
            if (dynamicSql != null && !"".equals(dynamicSql)) {
                map.put("sql", dynamicSql);
            }

            sqlSession = sqlSessionFactory.openSession();
            List<Map<String, Object>> list = sqlSession.selectList((String) map.get("sql"), map);

            Date date2 = new Date();
            String durationTime = (date2.getTime() - date1.getTime()) + "MS";
            System.out.println("执行时间：" + durationTime);

            ExportConfig exportConfig = resolveExportConfig(namespace, sqlId);
            String fileName = getStringValue(map.get("fileName"), getStringValue(exportConfig.getFileName(), "导出数据"));
            String sheetName = getStringValue(map.get("sheetName"), getStringValue(exportConfig.getSheetName(), fileName));
            writeExcel(request, response, fileName, sheetName, list, exportConfig.getFieldNameMapping());
            System.out.println("----------End----------");
        } catch (Exception e) {
            e.printStackTrace();
            writeError(response, e);
            System.out.println("----------End----------");
        } finally {
            if (sqlSession != null) {
                sqlSession.close();
                sqlSession = null;
            }
        }
    }

    private void writeExcel(HttpServletRequest request, HttpServletResponse response, String fileName, String sheetName,
            List<Map<String, Object>> list, Map<String, String> fieldNameMapping) throws Exception {
        List<String> columns = resolveColumns(list, fieldNameMapping);
        List<List<String>> head = buildHead(columns, fieldNameMapping);
        List<List<Object>> data = buildData(list, columns);

        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", buildContentDisposition(request, fileName));

        EasyExcel.write(response.getOutputStream())
                .head(head)
                .sheet(sheetName)
                .doWrite(data);
    }

    private List<String> resolveColumns(List<Map<String, Object>> list, Map<String, String> fieldNameMapping) {
        Set<String> actualColumns = new LinkedHashSet<String>();
        if (list != null) {
            for (Map<String, Object> row : list) {
                if (row != null) {
                    actualColumns.addAll(row.keySet());
                }
            }
        }

        if (fieldNameMapping != null && !fieldNameMapping.isEmpty()) {
            Set<String> resultColumns = new LinkedHashSet<String>();
            for (String field : fieldNameMapping.keySet()) {
                if (actualColumns.contains(field)) {
                    resultColumns.add(field);
                }
            }
            if (resultColumns.isEmpty()) {
                resultColumns.add("data");
            }
            return new ArrayList<String>(resultColumns);
        }

        if (actualColumns.isEmpty()) {
            actualColumns.add("data");
        }
        return new ArrayList<String>(actualColumns);
    }

    private List<List<String>> buildHead(List<String> columns, Map<String, String> fieldNameMapping) {
        List<List<String>> head = new ArrayList<List<String>>();
        for (String column : columns) {
            List<String> item = new ArrayList<String>();
            item.add(resolveFieldName(column, fieldNameMapping));
            head.add(item);
        }
        return head;
    }

    private List<List<Object>> buildData(List<Map<String, Object>> list, List<String> columns) {
        List<List<Object>> data = new ArrayList<List<Object>>();
        if (list == null) {
            return data;
        }
        for (Map<String, Object> row : list) {
            List<Object> item = new ArrayList<Object>();
            for (String column : columns) {
                item.add(row == null ? null : row.get(column));
            }
            data.add(item);
        }
        return data;
    }

    private String getStringValue(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String str = String.valueOf(value);
        if ("".equals(str.trim())) {
            return defaultValue;
        }
        return str;
    }

    private String resolveFieldName(String column, Map<String, String> fieldNameMapping) {
        if (fieldNameMapping == null || fieldNameMapping.isEmpty()) {
            return column;
        }
        return getStringValue(fieldNameMapping.get(column), column);
    }

    private void injectSecurityScope(Map map) {
        map.put("tenantId", SecurityUtils.getTenantId());
        map.put("applicationId", SecurityUtils.getApplicationId());
    }

    private String buildContentDisposition(HttpServletRequest request, String fileName) throws Exception {
        System.out.println("导出文件名1："+fileName);
        String exportFileName = fileName + ".xlsx";
        String encodedFileName = URLEncoder.encode(exportFileName, "UTF-8").replaceAll("\\+", "%20");
        String userAgent = request == null ? "" : getStringValue(request.getHeader("User-Agent"), "");
        String userAgentLower = userAgent.toLowerCase();
        System.out.println("导出文件名2："+exportFileName);

        if (userAgentLower.contains("msie") || userAgentLower.contains("trident") || userAgentLower.contains("edge")) {
            return "attachment;filename=" + encodedFileName;
        }
        return "attachment;filename*=UTF-8''" + encodedFileName;
    }

    private ExportConfig resolveExportConfig(String namespace, String sqlId) {
        Map<String, ExportConfig> exportConfigMap = loadExportConfigMap();
        ExportConfig exportConfig = exportConfigMap.get(namespace + "/" + sqlId);
        return exportConfig == null ? ExportConfig.empty() : exportConfig;
    }

    private Map<String, ExportConfig> loadExportConfigMap() {
        try {
            ClassPathResource resource = new ClassPathResource(EXPORT_CONFIG_PATH);
            if (!resource.exists()) {
                return Collections.emptyMap();
            }
            InputStream inputStream = resource.getInputStream();
            try {
                Map<String, ExportConfig> configMap = OBJECT_MAPPER.readValue(inputStream,
                        new TypeReference<Map<String, ExportConfig>>() {
                        });
                return configMap == null ? Collections.<String, ExportConfig>emptyMap() : configMap;
            } finally {
                inputStream.close();
            }
        } catch (Exception e) {
            throw new IllegalStateException("读取导出配置失败: " + EXPORT_CONFIG_PATH, e);
        }
    }

    private static class ExportConfig {
        private String fileName;
        private String sheetName;
        private Map<String, String> fieldNameMapping;

        public static ExportConfig empty() {
            return new ExportConfig();
        }

        public String getFileName() {
            return fileName;
        }

        public String getSheetName() {
            return sheetName;
        }

        public Map<String, String> getFieldNameMapping() {
            return fieldNameMapping == null ? Collections.<String, String>emptyMap() : fieldNameMapping;
        }
    }

    private void writeError(HttpServletResponse response, Exception e) {
        try {
            if (!response.isCommitted()) {
                response.reset();
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
                response.setCharacterEncoding("UTF-8");
                response.setContentType("application/json;charset=UTF-8");
                Map<String, Object> resultMap = new HashMap<String, Object>();
                resultMap.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
                resultMap.put("success", false);
                resultMap.put("message", e.getMessage());
                resultMap.put("data", null);
                response.getWriter().write(OBJECT_MAPPER.writeValueAsString(resultMap));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
