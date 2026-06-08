package pl.zzpj.auth_server.exception;

public class DuplicateUserFieldException extends RuntimeException {

    private final String field;

    public DuplicateUserFieldException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
