package cbc.boot.myboot.controller.db.util;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class CombineSqlUtil {
    
    @Autowired
    private SqlSessionFactory sqlSessionFactory;
    
    /**
     * 执行组合 SQL
     * @param param 包含 sqls 数组和其他参数
     * @return 最后一个 SQL 的执行结果
     */
    public Object executeCombineSql(Map<String, Object> param) {
        System.out.println("executeCombineSql--执行组合 SQL");
        SqlSession sqlSession = null;
        try {
            sqlSession = sqlSessionFactory.openSession();
            
            @SuppressWarnings("unchecked")
            List<String> sqls = (List<String>) param.get("sqls");
            
            if (sqls == null || sqls.isEmpty()) {
                return null;
            }
            
            Map<String, Object> currentParam = new HashMap<>(param);
            Object result = null;
            
            for (int i = 0; i < sqls.size(); i++) {
                String sqlId = sqls.get(i);
                
                // 执行当前 SQL
                List<Map<String, Object>> list = sqlSession.selectList(sqlId, currentParam);
                result = list;
                
                // 如果不是最后一个 SQL，将结果合并到参数中供下一个 SQL 使用
                if (i < sqls.size() - 1 && list != null && !list.isEmpty()) {
                    Map<String, Object> firstRow = list.get(0);
                    currentParam.putAll(firstRow);
                }
            }
            
            return result;
            
        } finally {
            if (sqlSession != null) {
                sqlSession.close();
            }
        }
    }

    /**
     * 调用示例（需在 Spring 容器中运行）
     * 
     * 示例场景：通过图层名称"网吧"查询对应表名，再统计该表总条数
     *
     * Map<String, Object> param = new HashMap<>();
     * param.put("layerName", "网吧");                          // 查询条件
     * param.put("sqls", Arrays.asList(
     *     "ai_chat.getTableNameByLayerName",                   // 第1步：查 table_name
     *     "ai_chat.getTableCount"                              // 第2步：统计总条数
     * ));
     *
     * Object result = combineSql.executeCombineSql(param);
     * // result => [{count(*): 100}]
     *
     * 执行流程：
     * 1. 执行 getTableNameByLayerName(layerName="网吧")
     *    => 返回 [{table_name: "pt_wangba"}]
     * 2. 将 table_name="pt_wangba" 合并到 param 中
     * 3. 执行 getTableCount(tableName="pt_wangba")
     *    => 返回 [{count(*): 100}]
     */
    public void example() {
        Map<String, Object> param = new HashMap<>();
        param.put("layerName", "网吧");
        param.put("sqls", Arrays.asList(
            "ai_chat.getTableNameByLayerName",
            "ai_chat.getTableCount"
        ));

        Object result = executeCombineSql(param);
        System.out.println("查询结果：" + result);
    }
}
