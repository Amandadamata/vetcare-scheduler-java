package com.amandadamata.vetcare.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.Function;

public final class GsonFactory {
    private GsonFactory() {
    }

    public static Gson create() {
        return new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(LocalDate.class, new StringTypeAdapter<>(LocalDate::parse))
                .registerTypeAdapter(LocalDateTime.class, new StringTypeAdapter<>(LocalDateTime::parse))
                .registerTypeAdapter(Instant.class, new StringTypeAdapter<>(Instant::parse))
                .create();
    }

    private static final class StringTypeAdapter<T> extends TypeAdapter<T> {
        private final Function<String, T> parser;

        private StringTypeAdapter(Function<String, T> parser) {
            this.parser = parser;
        }

        @Override
        public void write(JsonWriter writer, T value) throws IOException {
            if (value == null) {
                writer.nullValue();
            } else {
                writer.value(value.toString());
            }
        }

        @Override
        public T read(JsonReader reader) throws IOException {
            if (reader.peek() == JsonToken.NULL) {
                reader.nextNull();
                return null;
            }
            return parser.apply(reader.nextString());
        }
    }
}
