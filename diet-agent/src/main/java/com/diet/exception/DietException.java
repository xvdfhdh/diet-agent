package com.diet.exception;

public class DietException extends RuntimeException {
    public DietException(String message) {
        super(message);
    }

    public DietException(String message, Throwable cause) {
        super(message, cause);
    }
}




