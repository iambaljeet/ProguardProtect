package app.proguard.models;

/**
 * Base class with a private method that has the same signature as
 * the interface default method. R8 devirtualization can cause
 * IllegalAccessError when it resolves calls to this private method
 * instead of the interface default.
 */
public class BaseProcessor {
    private String process(String input) {
        return "BaseProcessor private: " + input;
    }

    public String getBaseInfo() {
        return "BaseProcessor v1.0";
    }
}
