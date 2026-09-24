package com.amandadamata.vetcare.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * Line-oriented console input with retry loops for values that can be parsed locally.
 */
public final class ConsoleInput {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("uuuu-MM-dd", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    private final BufferedReader reader;
    private final PrintWriter output;

    public ConsoleInput(Reader reader, PrintWriter output) {
        this.reader = new BufferedReader(Objects.requireNonNull(reader, "reader"));
        this.output = Objects.requireNonNull(output, "output");
    }

    public String readLine(String prompt) {
        output.print(prompt);
        output.flush();
        try {
            String line = reader.readLine();
            if (line == null) {
                throw new InputClosedException();
            }
            return line.trim();
        } catch (IOException exception) {
            throw new InputClosedException("Console input could not be read.", exception);
        }
    }

    public String readRequiredText(String prompt) {
        while (true) {
            String value = readLine(prompt);
            if (!value.isBlank()) {
                return value;
            }
            output.println("A value is required. Please try again.");
        }
    }

    public int readInt(String prompt, int minimum, int maximum) {
        while (true) {
            String value = readLine(prompt);
            try {
                int parsed = Integer.parseInt(value);
                if (parsed >= minimum && parsed <= maximum) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
                // A concise message below covers both syntax and range errors.
            }
            output.printf("Enter a whole number from %d to %d.%n", minimum, maximum);
        }
    }

    public int readIntOrDefault(String prompt, int minimum, int maximum, int defaultValue) {
        while (true) {
            String value = readLine(prompt);
            if (value.isBlank()) {
                return defaultValue;
            }
            try {
                int parsed = Integer.parseInt(value);
                if (parsed >= minimum && parsed <= maximum) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
                // A concise message below covers both syntax and range errors.
            }
            output.printf("Enter a whole number from %d to %d, or press Enter to keep %d.%n",
                    minimum, maximum, defaultValue);
        }
    }

    public LocalDate readDate(String prompt) {
        while (true) {
            String value = readLine(prompt);
            try {
                return LocalDate.parse(value, DATE_FORMATTER);
            } catch (DateTimeParseException ignored) {
                output.println("Enter a valid date in yyyy-MM-dd format (for example, 2026-10-15).");
            }
        }
    }

    public LocalDate readDateOrDefault(String prompt, LocalDate defaultValue) {
        while (true) {
            String value = readLine(prompt);
            if (value.isBlank()) {
                return defaultValue;
            }
            try {
                return LocalDate.parse(value, DATE_FORMATTER);
            } catch (DateTimeParseException ignored) {
                output.println("Enter a valid date in yyyy-MM-dd format, or press Enter for the default date.");
            }
        }
    }

    public LocalDate readOptionalDate(String prompt) {
        while (true) {
            String value = readLine(prompt);
            if (value.isBlank()) {
                return null;
            }
            try {
                return LocalDate.parse(value, DATE_FORMATTER);
            } catch (DateTimeParseException ignored) {
                output.println("Enter a valid date in yyyy-MM-dd format, or press Enter for any date.");
            }
        }
    }

    public LocalDateTime readDateTime(String prompt) {
        while (true) {
            String value = readLine(prompt);
            try {
                return LocalDateTime.parse(value, DATE_TIME_FORMATTER);
            } catch (DateTimeParseException ignored) {
                output.println("Enter a valid date and time in yyyy-MM-dd HH:mm format "
                        + "(for example, 2026-10-15 14:30).");
            }
        }
    }

    public LocalDateTime readDateTimeOrDefault(String prompt, LocalDateTime defaultValue) {
        while (true) {
            String value = readLine(prompt);
            if (value.isBlank()) {
                return defaultValue;
            }
            try {
                return LocalDateTime.parse(value, DATE_TIME_FORMATTER);
            } catch (DateTimeParseException ignored) {
                output.println("Enter a valid date and time in yyyy-MM-dd HH:mm format, "
                        + "or press Enter to keep the current value.");
            }
        }
    }

    public boolean readConfirmation(String prompt) {
        while (true) {
            String value = readLine(prompt).toLowerCase(Locale.ROOT);
            if (value.equals("y") || value.equals("yes")) {
                return true;
            }
            if (value.equals("n") || value.equals("no")) {
                return false;
            }
            output.println("Enter yes or no.");
        }
    }

    public <E extends Enum<E>> E readEnum(
            String heading,
            E[] values,
            Function<E, String> labelProvider) {
        printEnumOptions(heading, values, labelProvider, false);
        while (true) {
            String inputValue = readLine("Selection: ");
            E selected = matchEnum(inputValue, values, labelProvider);
            if (selected != null) {
                return selected;
            }
            output.printf("Choose a number from 1 to %d or enter one of the option names.%n", values.length);
        }
    }

    public <E extends Enum<E>> E readEnumOrDefault(
            String heading,
            E[] values,
            Function<E, String> labelProvider,
            E defaultValue) {
        printEnumOptions(heading, values, labelProvider, false);
        while (true) {
            String inputValue = readLine("Selection (Enter keeps " + labelProvider.apply(defaultValue) + "): ");
            if (inputValue.isBlank()) {
                return defaultValue;
            }
            E selected = matchEnum(inputValue, values, labelProvider);
            if (selected != null) {
                return selected;
            }
            output.printf("Choose a number from 1 to %d, enter an option name, or press Enter.%n", values.length);
        }
    }

    public <E extends Enum<E>> E readOptionalEnum(
            String heading,
            E[] values,
            Function<E, String> labelProvider) {
        printEnumOptions(heading, values, labelProvider, true);
        while (true) {
            String inputValue = readLine("Selection: ");
            if (inputValue.isBlank() || inputValue.equals("0")) {
                return null;
            }
            E selected = matchEnum(inputValue, values, labelProvider);
            if (selected != null) {
                return selected;
            }
            output.printf("Choose 0 through %d, enter an option name, or press Enter for any.%n", values.length);
        }
    }

    private <E extends Enum<E>> void printEnumOptions(
            String heading,
            E[] values,
            Function<E, String> labelProvider,
            boolean includeAny) {
        output.println(heading);
        if (includeAny) {
            output.println("  0. Any");
        }
        for (int index = 0; index < values.length; index++) {
            output.printf("  %d. %s%n", index + 1, labelProvider.apply(values[index]));
        }
    }

    private <E extends Enum<E>> E matchEnum(
            String inputValue,
            E[] values,
            Function<E, String> labelProvider) {
        try {
            int option = Integer.parseInt(inputValue);
            if (option >= 1 && option <= values.length) {
                return values[option - 1];
            }
        } catch (NumberFormatException ignored) {
            // The same input may still match an enum or display name.
        }

        String normalizedInput = normalizeOptionName(inputValue);
        for (E value : values) {
            if (normalizeOptionName(value.name()).equals(normalizedInput)
                    || normalizeOptionName(labelProvider.apply(value)).equals(normalizedInput)) {
                return value;
            }
        }
        return null;
    }

    private String normalizeOptionName(String value) {
        return value.trim()
                .replace('-', ' ')
                .replace('_', ' ')
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    public static final class InputClosedException extends RuntimeException {
        public InputClosedException() {
            super("Console input was closed.");
        }

        public InputClosedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
