package com.aegis.pipeline;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aegis.common.api.CurrentUserProvider;
import com.aegis.common.dto.AgentRequest;
import com.aegis.common.dto.AgentResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api")
public class PipelineController {

    /** Only the message is accepted from the body; identity comes from the JWT. */
    public record MessageRequest(@NotBlank @Size(max = 2000) String message) {
    }

    private final SecurityPipeline pipeline;
    private final CurrentUserProvider currentUserProvider;

    public PipelineController(SecurityPipeline pipeline, CurrentUserProvider currentUserProvider) {
        this.pipeline = pipeline;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/requests")
    public ResponseEntity<AgentResponse> submit(@Valid @RequestBody MessageRequest body) {
        AgentRequest request = AgentRequest.of(body.message(), currentUserProvider.getCurrentUser());
        AgentResponse response = pipeline.handle(request);
        HttpStatus status = switch (response.status()) {
            case EXECUTED -> HttpStatus.OK;
            case REJECTED -> HttpStatus.FORBIDDEN;
            case PENDING_REVIEW -> HttpStatus.ACCEPTED;
            case FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(response);
    }
}