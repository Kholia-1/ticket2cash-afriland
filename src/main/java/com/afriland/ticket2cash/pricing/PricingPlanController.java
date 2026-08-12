package com.afriland.ticket2cash.pricing;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pricing-plans")
public class PricingPlanController {

    private final PricingPlanRepository repo;

    public PricingPlanController(PricingPlanRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<PricingPlan> all() {
        return repo.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PricingPlan> one(@PathVariable Long id) {
        return repo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public PricingPlan create(@RequestBody PricingPlan p) {
        p.setId(null);
        return repo.save(p);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PricingPlan> update(@PathVariable Long id, @RequestBody PricingPlan in) {
        return repo.findById(id).map(p -> {
            p.setName(in.getName());
            p.setCode(in.getCode());
            p.setMonthlyFee(in.getMonthlyFee());
            p.setCommissionRate(in.getCommissionRate());
            p.setIncludedInstances(in.getIncludedInstances());
            if (in.getCurrency() != null) p.setCurrency(in.getCurrency());
            p.setDescription(in.getDescription());
            if (in.getActive() != null) p.setActive(in.getActive());
            return ResponseEntity.ok(repo.save(p));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
