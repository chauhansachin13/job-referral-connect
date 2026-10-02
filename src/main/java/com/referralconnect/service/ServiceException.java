package com.referralconnect.service;

/** A rule was broken; the message is written for the person using the app. */
public class ServiceException extends RuntimeException {
    public ServiceException(String message) {
        super(message);
    }
}
