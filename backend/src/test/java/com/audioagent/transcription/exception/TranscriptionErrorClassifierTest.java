package com.audioagent.transcription.exception;

import com.audioagent.common.enums.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;

import java.sql.SQLSyntaxErrorException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscriptionErrorClassifierTest {

    private final TranscriptionErrorClassifier classifier =
            new TranscriptionErrorClassifier();

    @Test
    void badSqlGrammarIsPermanentAndDoesNotExposeSql() {
        BadSqlGrammarException source = new BadSqlGrammarException(
                "insert transcript segment",
                "INSERT INTO audio_transcript_segment(user_id) VALUES (?)",
                new SQLSyntaxErrorException(
                        "Unknown column 'user_id' in 'field list'"));

        TranscriptionException result = classifier.classify(source);

        assertEquals(ErrorCode.TRANSCRIPT_PERSISTENCE_FAILED,
                result.getErrorCode());
        assertFalse(result.isRetryable());
        assertFalse(result.getMessage().contains("user_id"));
        assertFalse(result.getMessage().contains(
                "audio_transcript_segment"));
        assertSame(source, result.getCause());
    }

    @Test
    void nestedSqlSyntaxErrorIsPermanent() {
        RuntimeException source = new RuntimeException(
                new SQLSyntaxErrorException("table shape mismatch"));

        TranscriptionException result = classifier.classify(source);

        assertEquals(ErrorCode.TRANSCRIPT_PERSISTENCE_FAILED,
                result.getErrorCode());
        assertFalse(result.isRetryable());
    }

    @Test
    void unknownOperationalFailureKeepsExistingRetryBehavior() {
        TranscriptionException result = classifier.classify(
                new IllegalStateException("temporary failure"));

        assertEquals(ErrorCode.TRANSCRIPTION_FAILED,
                result.getErrorCode());
        assertTrue(result.isRetryable());
    }
}
