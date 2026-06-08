package pl.zzpj.auth_server.exception;

public class UnknownRegistrationPropertyException extends RuntimeException {

    public UnknownRegistrationPropertyException(String property) {
        super("Unknown registration property: " + property);
    }
}
