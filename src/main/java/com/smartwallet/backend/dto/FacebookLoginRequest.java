package com.smartwallet.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FacebookLoginRequest {
    private String email;
    private String displayName;
    private String photoUrl;
    private String accessToken; // Token fourni par le SDK Facebook
}
