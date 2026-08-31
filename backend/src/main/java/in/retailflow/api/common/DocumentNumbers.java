package in.retailflow.api.common;

public final class DocumentNumbers {

    private DocumentNumbers() {}

    public static String format(String prefix, long value) {
        return prefix + String.format("%06d", value);
    }
}
