package com.uninook.ai;

import com.uninook.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/pending-actions")
public class PendingActionController {

    private final PendingActionService pendingActionService;

    public PendingActionController(PendingActionService pendingActionService) {
        this.pendingActionService = pendingActionService;
    }

    @PostMapping("/{actionId}/confirm")
    public ApiResponse<ConfirmPendingActionResponse> confirmPost(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String actionId,
            @Valid @RequestBody ConfirmPendingPostRequest request) {
        return ApiResponse.success(pendingActionService.confirmPost(authorization, actionId, request.categoryId()));
    }

    @DeleteMapping("/{actionId}")
    public ApiResponse<Boolean> cancel(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String actionId) {
        pendingActionService.cancel(authorization, actionId);
        return ApiResponse.success(true);
    }
}
