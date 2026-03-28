package com.smartwallet.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AiCommandResponse {
    private String action;
    private Map<String, Object> params;
    private String responseMessage;
}
