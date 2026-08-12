package com.audioagent.contentanalysis.exception;

import com.audioagent.common.enums.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ContentAnalysisErrorClassifierTest {

    private final ContentAnalysisErrorClassifier classifier =
            new ContentAnalysisErrorClassifier();

    @Test
    void databaseErrorsAreNotRetried() {
        ContentAnalysisException failure = classifier.classify(
                new DataIntegrityViolationException("bad schema"));

        assertEquals(ErrorCode.AI_RESULT_PERSISTENCE_FAILED,
                failure.getErrorCode());
        assertFalse(failure.isRetryable());
    }

    @Test
    void badSqlGrammarIsANonRetryableSchemaFailure() {
        BadSqlGrammarException source = new BadSqlGrammarException(
                "insert", "INSERT INTO missing_column",
                new SQLException("Unknown column", "42S22", 1054));

        ContentAnalysisException failure = classifier.classify(source);

        assertEquals(ErrorCode.DATABASE_SCHEMA_ERROR,
                failure.getErrorCode());
        assertFalse(failure.isRetryable());
    }
}
