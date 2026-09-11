package ru.karandash.agent.cli;

/**
 * Что распознать. Содержимое — недоверенный пользовательский ввод.
 */
public sealed interface RecognitionTask {

    String kind();

    record Text(String description) implements RecognitionTask {

        @Override
        public String kind() {
            return "text";
        }
    }

    record Photo(byte[] bytes, ImageType type) implements RecognitionTask {

        @Override
        public String kind() {
            return "photo";
        }
    }
}
