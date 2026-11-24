package utility;

/**
 * ApiException (Utility Layer)
 * -------------------------------------------------------------------------
 * Custom checked exception class for handling network and game API errors.
 * Demonstrates custom exception handling in Java (extending Exception,
 * storing HTTP status codes, and providing user-friendly troubleshooting advice).
 */
public class ApiException extends Exception {

    private final int statusCode;
    private final String troubleshootingTip;

    public ApiException(String message, int statusCode, String troubleshootingTip) {
        super(message);
        this.statusCode = statusCode;
        this.troubleshootingTip = troubleshootingTip;
    }

    public ApiException(String message, Throwable cause, int statusCode, String troubleshootingTip) {
        super(message, cause);
        this.statusCode = statusCode;
        this.troubleshootingTip = troubleshootingTip;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getTroubleshootingTip() {
        return troubleshootingTip;
    }

    @Override
    public String toString() {
        return String.format("ApiException [HTTP %d]: %s | Tip: %s",
                statusCode, getMessage(), troubleshootingTip);
    }
}
