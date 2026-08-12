package com.audioagent.contentanalysis.exception;

import com.audioagent.common.enums.ErrorCode;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Component;

import java.sql.SQLSyntaxErrorException;

@Component
public class ContentAnalysisErrorClassifier {

    public ContentAnalysisException classify(Throwable error) {
        if (error instanceof ContentAnalysisException known) {
            return known;
        }
        if (hasCause(error, BadSqlGrammarException.class)
                || hasCause(error, SQLSyntaxErrorException.class)) {
            return new ContentAnalysisException(
                    ErrorCode.DATABASE_SCHEMA_ERROR, false,
                    "智能分析数据库结构异常，请联系管理员", error);
        }
        if (hasCause(error, DataIntegrityViolationException.class)
                || hasCause(error, DataAccessException.class)) {
            return new ContentAnalysisException(
                    ErrorCode.AI_RESULT_PERSISTENCE_FAILED, false,
                    "智能分析数据保存失败，请联系管理员", error);
        }
        return new ContentAnalysisException(
                ErrorCode.AI_ANALYSIS_FAILED, false,
                "智能内容分析失败，请稍后重试", error);
    }

    private boolean hasCause(Throwable error,
                             Class<? extends Throwable> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
