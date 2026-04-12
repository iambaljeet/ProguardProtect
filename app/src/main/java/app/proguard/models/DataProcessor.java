package app.proguard.models;

/**
 * Child class that extends BaseProcessor and implements Processable
 * WITHOUT overriding process(). This creates a devirtualization
 * hazard: R8 may resolve calls to the base class's private method
 * instead of the interface default method.
 */
public class DataProcessor extends BaseProcessor implements Processable {
    // NOT overriding process() — this creates a devirtualization hazard:
    // R8 may resolve calls to BaseProcessor's private process() instead of
    // Processable's default process().
    // Fix: Uncomment the @Override below to delegate to the interface default.
    //
    // @Override
    // public String process(String input) {
    //     return Processable.super.process(input);
    // }

    public String getName() {
        return "DataProcessor";
    }
}
