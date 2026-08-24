package com.kronos.chiron.push.dto;

public record PushSubscriptionRequestDto(String endpoint, PushSubscriptionKeysDto keys) {
}
