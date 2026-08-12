package com.audioagent.contentanalysis.validation;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

public record AnalysisResponseDiagnostics(
        int responseLength,
        String responseSha256,
        List<String> topLevelFieldNames,
        boolean summaryPresent,
        Integer keyPointCount,
        Integer chapterCount,
        Integer speechIssueCount,
        String firstCharacterType,
        String lastCharacterType,
        boolean markdownCodeFencePresent) {

    private static final Set<String> SAFE_TOP_LEVEL_FIELDS = Set.of(
            "summary", "keyPoints", "chapters", "speechIssues");

    public static AnalysisResponseDiagnostics from(
            String response, JsonNode root) {
        String value = response == null ? "" : response;
        List<String> fieldNames = new ArrayList<>();
        if (root != null && root.isObject()) {
            root.fieldNames().forEachRemaining(field ->
                    fieldNames.add(SAFE_TOP_LEVEL_FIELDS.contains(field)
                            ? field : "UNKNOWN_FIELD"));
        }
        return new AnalysisResponseDiagnostics(
                value.length(),
                sha256(value),
                fieldNames.stream().distinct().toList(),
                root != null && root.isObject()
                        && root.hasNonNull("summary"),
                arraySize(root, "keyPoints"),
                arraySize(root, "chapters"),
                arraySize(root, "speechIssues"),
                characterType(value, true),
                characterType(value, false),
                value.contains("```"));
    }

    private static Integer arraySize(JsonNode root, String field) {
        if (root == null || !root.isObject()) {
            return null;
        }
        JsonNode value = root.get(field);
        return value != null && value.isArray() ? value.size() : null;
    }

    private static String characterType(String value, boolean first) {
        if (value.isEmpty()) {
            return "NONE";
        }
        char character = first
                ? value.charAt(0) : value.charAt(value.length() - 1);
        if (Character.isWhitespace(character)) {
            return "WHITESPACE";
        }
        if (character == '{') {
            return "OBJECT_BOUNDARY";
        }
        if (character == '[' || character == ']') {
            return "ARRAY_BOUNDARY";
        }
        if (character == '"' || character == '\'') {
            return "QUOTE";
        }
        if (Character.isLetter(character)) {
            return "LETTER";
        }
        if (Character.isDigit(character)) {
            return "DIGIT";
        }
        return "OTHER";
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
