package com.spendwise.ml;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class BertInferenceEngine {

    private static final String MODEL_PATH = "sms_ner_model.tflite";
    private static final String VOCAB_PATH = "vocab.txt";
    private static final int FALLBACK_MAX_SEQ_LEN = 128;
    private static final String[] DEFAULT_LABELS = {
            "O",
            "B-AMOUNT",
            "B-MERCHANT", "I-MERCHANT",
            "B-INSTRUMENT_TYPE", "I-INSTRUMENT_TYPE",
            "B-ACCOUNT",
            "B-DATE"
    };
    
    private final Interpreter tflite;
    private final Map<String, Integer> vocab = new HashMap<>();
    private final int maxSeqLen;
    private final int numLabels;

    public static final class InferenceResult {
        private final int[] predictedTags;
        private final Map<String, String> entities;
        private final float meanConfidence;

        InferenceResult(int[] predictedTags, Map<String, String> entities, float meanConfidence) {
            this.predictedTags = predictedTags;
            this.entities = entities;
            this.meanConfidence = meanConfidence;
        }

        public int[] getPredictedTags() {
            return predictedTags;
        }

        public Map<String, String> getEntities() {
            return entities;
        }

        public float getMeanConfidence() {
            return meanConfidence;
        }
    }

    public BertInferenceEngine(Context context) throws IOException {
        tflite = new Interpreter(loadModelFile(context));
        loadVocabulary(context);
        maxSeqLen = resolveMaxSeqLen();
        numLabels = resolveNumLabels();
    }

    private int resolveMaxSeqLen() {
        try {
            int[] outShape = tflite.getOutputTensor(0).shape();
            if (outShape != null && outShape.length >= 2 && outShape[1] > 0) {
                return outShape[1];
            }
        } catch (Exception ignored) {
        }
        return FALLBACK_MAX_SEQ_LEN;
    }

    private int resolveNumLabels() {
        try {
            int[] outShape = tflite.getOutputTensor(0).shape();
            if (outShape != null && outShape.length >= 3 && outShape[2] > 0) {
                return outShape[2];
            }
        } catch (Exception ignored) {
        }
        return DEFAULT_LABELS.length;
    }

    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_PATH);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private void loadVocabulary(Context context) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open(VOCAB_PATH)))) {
            String line;
            int index = 0;
            while ((line = reader.readLine()) != null) {
                vocab.put(line.trim(), index++);
            }
        }
    }

    private List<String> tokenizeToPieces(String text) {
        List<String> pieces = new ArrayList<>();
        pieces.add("[CLS]");

        String[] words = text.toLowerCase().split("\\s+");
        for (String word : words) {
            int start = 0;
            while (start < word.length()) {
                int end = word.length();
                String curSubword = null;
                while (start < end) {
                    String subword = word.substring(start, end);
                    if (start > 0) subword = "##" + subword;
                    if (vocab.containsKey(subword)) {
                        curSubword = subword;
                        break;
                    }
                    end--;
                }
                if (curSubword == null) {
                    pieces.add("[UNK]");
                    start = word.length();
                } else {
                    pieces.add(curSubword);
                    start = end;
                }
            }
        }
        pieces.add("[SEP]");
        return pieces;
    }

    private String labelForId(int labelId) {
        if (labelId >= 0 && labelId < DEFAULT_LABELS.length) {
            return DEFAULT_LABELS[labelId];
        }
        return "LABEL_" + labelId;
    }

    private static boolean isSpecialToken(String token) {
        return "[CLS]".equals(token) || "[SEP]".equals(token) || "[PAD]".equals(token);
    }

    private static void appendPiece(StringBuilder builder, String token) {
        if (token == null || token.isEmpty() || "[UNK]".equals(token)) {
            return;
        }
        if (token.startsWith("##")) {
            builder.append(token.substring(2));
            return;
        }
        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(token);
    }

    private static float softmaxAt(float[] logits, int index) {
        float max = Float.NEGATIVE_INFINITY;
        for (float value : logits) {
            if (value > max) {
                max = value;
            }
        }

        float sum = 0f;
        for (float value : logits) {
            sum += (float) Math.exp(value - max);
        }
        if (sum == 0f || index < 0 || index >= logits.length) {
            return 0f;
        }
        return (float) (Math.exp(logits[index] - max) / sum);
    }

    private static void flushEntity(Map<String, List<String>> groupedEntities, String type, StringBuilder span) {
        if (type == null || span.length() == 0) {
            return;
        }
        groupedEntities.computeIfAbsent(type, k -> new ArrayList<>()).add(span.toString().trim());
    }

    private Map<String, String> extractEntities(List<String> tokens, int[] predictedTags, int realTokenCount) {
        if (tokens.isEmpty() || predictedTags.length == 0 || realTokenCount == 0) {
            return Collections.emptyMap();
        }

        Map<String, List<String>> grouped = new LinkedHashMap<>();
        String currentType = null;
        StringBuilder currentSpan = new StringBuilder();

        for (int i = 0; i < realTokenCount && i < tokens.size() && i < predictedTags.length; i++) {
            String token = tokens.get(i);
            if (isSpecialToken(token)) {
                continue;
            }

            String label = labelForId(predictedTags[i]);
            if ("O".equals(label) || !label.contains("-")) {
                flushEntity(grouped, currentType, currentSpan);
                currentType = null;
                currentSpan.setLength(0);
                continue;
            }

            String[] parts = label.split("-", 2);
            String prefix = parts[0];
            String entityType = parts[1];

            if ("B".equals(prefix) || !entityType.equals(currentType)) {
                flushEntity(grouped, currentType, currentSpan);
                currentType = entityType;
                currentSpan.setLength(0);
                appendPiece(currentSpan, token);
                continue;
            }

            appendPiece(currentSpan, token);
        }

        flushEntity(grouped, currentType, currentSpan);

        Map<String, String> selected = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            List<String> values = entry.getValue();
            if (values == null || values.isEmpty()) {
                continue;
            }

            String chosen = values.get(0);
            if ("MERCHANT".equals(entry.getKey())) {
                for (String candidate : values) {
                    if (candidate.length() > chosen.length()) {
                        chosen = candidate;
                    }
                }
            }
            selected.put(entry.getKey(), chosen);
        }
        return selected;
    }

    public int[] runInference(String text) {
        return infer(text).getPredictedTags();
    }

    public InferenceResult infer(String text) {
        List<String> tokenPieces = tokenizeToPieces(text == null ? "" : text);
        int realTokenCount = Math.min(tokenPieces.size(), maxSeqLen);

        int[][] inputIds = new int[1][maxSeqLen];
        int[][] attentionMask = new int[1][maxSeqLen];
        int[][] tokenTypeIds = new int[1][maxSeqLen];
        int padId = vocab.getOrDefault("[PAD]", 0);

        for (int i = 0; i < maxSeqLen; i++) {
            if (i < realTokenCount) {
                String piece = tokenPieces.get(i);
                inputIds[0][i] = vocab.getOrDefault(piece, vocab.getOrDefault("[UNK]", 100));
                attentionMask[0][i] = 1;
            } else {
                inputIds[0][i] = padId;
                attentionMask[0][i] = 0;
            }
            tokenTypeIds[0][i] = 0;
        }

        float[][][] output = new float[1][maxSeqLen][numLabels];
        Object[] inputs = {inputIds, attentionMask, tokenTypeIds};
        Map<Integer, Object> outputs = new HashMap<>();
        outputs.put(0, output);

        tflite.runForMultipleInputsOutputs(inputs, outputs);

        int[] predictedTags = new int[maxSeqLen];
        float confidenceSum = 0f;
        int confidenceCount = 0;

        for (int i = 0; i < maxSeqLen; i++) {
            int maxIdx = 0;
            float maxVal = Float.NEGATIVE_INFINITY;
            for (int j = 0; j < numLabels; j++) {
                if (output[0][i][j] > maxVal) {
                    maxVal = output[0][i][j];
                    maxIdx = j;
                }
            }
            predictedTags[i] = maxIdx;

            if (i < realTokenCount) {
                confidenceSum += softmaxAt(output[0][i], maxIdx);
                confidenceCount++;
            }
        }

        float meanConfidence = confidenceCount == 0 ? 0f : (confidenceSum / confidenceCount);
        Map<String, String> entities = extractEntities(tokenPieces, predictedTags, realTokenCount);
        return new InferenceResult(predictedTags, entities, meanConfidence);
    }

    public void close() {
        if (tflite != null) tflite.close();
    }
}
