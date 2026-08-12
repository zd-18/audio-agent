package com.audioagent.transcription.exception;

import com.audioagent.common.enums.ErrorCode;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Component;

import java.sql.SQLRecoverableException;
import java.sql.SQLSyntaxErrorException;
import java.sql.SQLTransientException;

@Component
public class TranscriptionErrorClassifier {

    private static final String PERSISTENCE_MESSAGE =
            "文字稿保存失败，请联系管理员";
    private static final String TEMPORARY_MESSAGE =
            "音频转写暂时失败，请稍后重试";

    public TranscriptionException classify(Throwable error) {
        if (error instanceof TranscriptionException transcriptionError) {
            return transcriptionError;
        }
        if (hasCause(error, BadSqlGrammarException.class)
                || hasCause(error, SQLSyntaxErrorException.class)) {
            return new TranscriptionException(
                    ErrorCode.TRANSCRIPT_PERSISTENCE_FAILED,
                    false,
                    PERSISTENCE_MESSAGE,
                    error);
        }
        if (error instanceof DataAccessException) {
            boolean retryable =
                    error instanceof TransientDataAccessException
                            || hasCause(error, SQLTransientException.class)
                            || hasCause(error, SQLRecoverableException.class);
            return new TranscriptionException(
                    ErrorCode.TRANSCRIPT_PERSISTENCE_FAILED,
                    retryable,
                    retryable ? TEMPORARY_MESSAGE : PERSISTENCE_MESSAGE,
                    error);
        }
        return new TranscriptionException(
                ErrorCode.TRANSCRIPTION_FAILED,
                true,
                TEMPORARY_MESSAGE,
                error);
    }

    private boolean hasCause(Throwable error,
                             Class<? extends Throwable> causeType) {
        Throwable current = error;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
