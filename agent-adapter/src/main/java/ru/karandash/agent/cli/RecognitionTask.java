package ru.karandash.agent.cli;

/**
 * Что распознать. Содержимое — недоверенный пользовательский ввод.
 */
public sealed interface RecognitionTask {

    String kind();

    record Text(String description, String context) implements RecognitionTask {

        public Text(String description) {
            this(description, null);
        }

        @Override
        public String kind() {
            return "text";
        }
    }

    record Photo(byte[] bytes, ImageType type, String context) implements RecognitionTask {

        public Photo(byte[] bytes, ImageType type) {
            this(bytes, type, null);
        }

        @Override
        public String kind() {
            return "photo";
        }
    }

    /** Подбор обеда по меню столовой: меню и рамки собирает ядро, недоверенного ввода здесь нет. */
    record Lunch(String prompt) implements RecognitionTask {

        @Override
        public String kind() {
            return "lunch";
        }
    }
}
