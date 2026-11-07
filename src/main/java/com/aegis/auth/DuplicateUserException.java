package com.aegis.auth;

public class DuplicateUserException extends RuntimeException {

    public DuplicateUserException() {
        super("Username or email already registered");
    }
}