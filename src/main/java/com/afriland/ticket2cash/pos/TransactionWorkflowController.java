package com.afriland.ticket2cash.pos;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
public class TransactionWorkflowController {
    private final TransactionWorkflowService service;
    public TransactionWorkflowController(TransactionWorkflowService service) { this.service = service; }

    @GetMapping("/{id}/workflow")
    public TransactionWorkflowService.WorkflowView workflow(@PathVariable Long id, HttpServletRequest request) { return service.view(id, request); }

    @PostMapping("/{id}/workflow/validate-step")
    public TransactionWorkflowService.WorkflowView validateStep(@PathVariable Long id, @RequestBody(required=false) Map<String,String> body, HttpServletRequest request) {
        return service.validateStep(id, value(body,"step"), value(body,"comment"), request);
    }

    @PostMapping("/{id}/workflow/reject")
    public TransactionWorkflowService.WorkflowView reject(@PathVariable Long id, @RequestBody(required=false) Map<String,String> body, HttpServletRequest request) {
        return service.reject(id, value(body,"reason"), request);
    }

    @PostMapping("/{id}/workflow/manual-review")
    public TransactionWorkflowService.WorkflowView manualReview(@PathVariable Long id, @RequestBody(required=false) Map<String,String> body, HttpServletRequest request) {
        return service.manualReview(id, value(body,"comment"), request);
    }

    @PostMapping("/{id}/workflow/approve-for-payment")
    public TransactionWorkflowService.WorkflowView approveForPayment(@PathVariable Long id, @RequestBody(required=false) Map<String,String> body, HttpServletRequest request) {
        return service.approveForPayment(id, value(body,"comment"), request);
    }

    @PostMapping("/{id}/workflow/approve-for-credit")
    public TransactionWorkflowService.WorkflowView approveForCredit(@PathVariable Long id, @RequestBody(required=false) Map<String,String> body, HttpServletRequest request) {
        return service.approveForCredit(id, value(body,"comment"), request);
    }

    @PostMapping("/workflow/validate-cashback-before-payment")
    public Map<String, Object> validateCashbackBeforePayment(HttpServletRequest request) {
        return service.validateCashbackBeforePayment(request);
    }

    private String value(Map<String,String> body, String key) { return body == null ? null : body.get(key); }
}
