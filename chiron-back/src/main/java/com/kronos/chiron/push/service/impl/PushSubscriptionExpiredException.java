package com.kronos.chiron.push.service.impl;

class PushSubscriptionExpiredException extends RuntimeException {

    PushSubscriptionExpiredException(String message) {
        super(message);
    }
}
