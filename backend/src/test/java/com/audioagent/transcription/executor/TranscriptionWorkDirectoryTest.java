package com.audioagent.transcription.executor;

import com.audioagent.transcription.config.TranscriptionProperties;
import com.audioagent.transcription.exception.TranscriptionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscriptionWorkDirectoryTest {

    @TempDir
    Path tempDirectory;

    @Test
    void cleansAllTaskTemporaryFiles() throws Exception {
        TranscriptionProperties properties = new TranscriptionProperties();
        properties.setTempRoot(tempDirectory.resolve("transcriptions")
                .toString());
        TranscriptionWorkDirectory workDirectory =
                new TranscriptionWorkDirectory(properties);

        Path taskDirectory = workDirectory.prepare(91L);
        Files.writeString(taskDirectory.resolve("source.wav"), "source");
        Files.createDirectories(taskDirectory.resolve("nested"));
        Files.writeString(taskDirectory.resolve("nested/result.wav"),
                "result");
        assertTrue(Files.exists(taskDirectory));

        workDirectory.cleanQuietly(91L);

        assertFalse(Files.exists(taskDirectory));
    }

    @Test
    void rejectsInvalidTaskIdentifier() {
        TranscriptionProperties properties = new TranscriptionProperties();
        properties.setTempRoot(tempDirectory.toString());
        TranscriptionWorkDirectory workDirectory =
                new TranscriptionWorkDirectory(properties);
        assertThrows(TranscriptionException.class,
                () -> workDirectory.prepare(0L));
    }
}
