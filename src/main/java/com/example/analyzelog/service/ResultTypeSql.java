package com.example.analyzelog.service;

final class ResultTypeSql {

    static final String FUNCTION_TYPE_LIST =
            "'FunctionGeneratedResponse','FunctionExecutionError','FunctionThrottledError'";

    static final String RESULT_TYPE_SUMS = resultTypeSums("");

    static String resultTypeSums(String tableAlias) {
        String p = tableAlias.isEmpty() ? "" : tableAlias + ".";
        return """
                SUM(CASE WHEN %1$sedge_response_result_type = 'Hit'  THEN 1 ELSE 0 END) as hit,
                SUM(CASE WHEN %1$sedge_response_result_type = 'Miss' THEN 1 ELSE 0 END) as miss,
                SUM(CASE WHEN %1$sedge_response_result_type IN (%2$s) THEN 1 ELSE 0 END) as function,
                SUM(CASE WHEN %1$sedge_response_result_type = 'Error' THEN 1 ELSE 0 END) as error\
                """.formatted(p, FUNCTION_TYPE_LIST);
    }

    // Same classification as resultTypeSums, but per-row 0/1 flags instead of a GROUP BY aggregate.
    static String resultTypeFlags(String tableAlias) {
        String p = tableAlias.isEmpty() ? "" : tableAlias + ".";
        return """
                CASE WHEN %1$sedge_response_result_type = 'Hit'  THEN 1 ELSE 0 END as hit,
                CASE WHEN %1$sedge_response_result_type = 'Miss' THEN 1 ELSE 0 END as miss,
                CASE WHEN %1$sedge_response_result_type IN (%2$s) THEN 1 ELSE 0 END as function,
                CASE WHEN %1$sedge_response_result_type = 'Error' THEN 1 ELSE 0 END as error\
                """.formatted(p, FUNCTION_TYPE_LIST);
    }

    static final String ORDER_BY_TOTAL_DESC = "ORDER BY (hit + miss + function + error) DESC\n";

    private ResultTypeSql() {}
}
