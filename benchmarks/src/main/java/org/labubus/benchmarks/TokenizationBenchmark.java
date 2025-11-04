package org.labubus.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
public class TokenizationBenchmark {

    private static final Pattern WORD_PATTERN = Pattern.compile("[a-z]+");

    private String shortText;
    private String mediumText;
    private String longText;

    @Setup
    public void setup() {
        shortText = generateText(100);      // ~100 words
        mediumText = generateText(1000);    // ~1,000 words
        longText = generateText(10000);     // ~10,000 words
    }

    @Benchmark
    public void tokenizeShortText(Blackhole blackhole) {
        Set<String> words = tokenize(shortText);
        blackhole.consume(words);
    }

    @Benchmark
    public void tokenizeMediumText(Blackhole blackhole) {
        Set<String> words = tokenize(mediumText);
        blackhole.consume(words);
    }

    @Benchmark
    public void tokenizeLongText(Blackhole blackhole) {
        Set<String> words = tokenize(longText);
        blackhole.consume(words);
    }

    private Set<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase().split("\\s+"))
                .map(String::trim)
                .filter(word -> WORD_PATTERN.matcher(word).matches())
                .filter(word -> word.length() > 2)
                .collect(Collectors.toSet());
    }

    private String generateText(int wordCount) {
        StringBuilder sb = new StringBuilder();
        String[] words = {"the", "quick", "brown", "fox", "jumps", "over", "lazy", "dog",
                "alice", "wonderland", "pride", "prejudice", "frankenstein"};

        for (int i = 0; i < wordCount; i++) {
            sb.append(words[i % words.length]).append(" ");
        }

        return sb.toString();
    }
}