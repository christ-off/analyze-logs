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
                SUM(CASE WHEN %1$sedge_response_result_type IN (
                        'FunctionGeneratedResponse',
                        'FunctionExecutionError',
                        'FunctionThrottledError')                    THEN 1 ELSE 0 END) as function,
                SUM(CASE WHEN %1$sedge_response_result_type = 'Error' THEN 1 ELSE 0 END) as error\
                """.formatted(p);
    }

    static final String ORDER_BY_TOTAL_DESC = "ORDER BY (hit + miss + function + error) DESC\n";

    private ResultTypeSql() {}
}
