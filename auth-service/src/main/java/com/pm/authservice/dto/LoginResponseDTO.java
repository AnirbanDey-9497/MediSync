package com.pm.authservice.dto;

public class LoginResponseDTO {
    private final String token;

    public LoginResponseDTO(String token) {
        this.token = token;
    }

    //We need getters because packages like JSON would use this to serialise tokens
    public String getToken() {
        return token;
    }
}
