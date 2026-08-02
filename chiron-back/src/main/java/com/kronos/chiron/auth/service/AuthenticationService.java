package com.kronos.chiron.auth.service;

import com.kronos.chiron.auth.dto.AuthenticationRequest;
import com.kronos.chiron.auth.dto.AuthenticationResponse;
import com.kronos.chiron.auth.dto.RegisterRequest;

public interface AuthenticationService {

    AuthenticationResponse register(RegisterRequest request);

    AuthenticationResponse authenticate(AuthenticationRequest request);
}
