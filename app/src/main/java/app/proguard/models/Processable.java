package app.proguard.models;

/**
 * Interface with a default method that has the same signature
 * as BaseProcessor's private method.
 */
public interface Processable {
    default String process(String input) {
        return "Processable default: " + input.toUpperCase();
    }
}
