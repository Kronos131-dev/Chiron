package com.kronos.chiron.coach.context;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
@Getter
@Setter
public class VoiceSessionContext {
    private Long pinnedSeanceId;
}
