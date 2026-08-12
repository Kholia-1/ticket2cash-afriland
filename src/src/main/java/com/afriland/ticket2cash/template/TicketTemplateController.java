package com.afriland.ticket2cash.template;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ticket-templates")
public class TicketTemplateController {

    private final TicketTemplateRepository repo;

    public TicketTemplateController(TicketTemplateRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<TicketTemplate> all() {
        return repo.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketTemplate> one(@PathVariable Long id) {
        return repo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public TicketTemplate create(@RequestBody TicketTemplate t) {
        t.setId(null);
        return repo.save(t);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TicketTemplate> update(@PathVariable Long id, @RequestBody TicketTemplate in) {
        return repo.findById(id).map(t -> {
            t.setName(in.getName());
            t.setMerchantId(in.getMerchantId());
            t.setStoreNamePattern(in.getStoreNamePattern());
            t.setTotalKeyword(in.getTotalKeyword());
            t.setDateFormat(in.getDateFormat());
            t.setSampleText(in.getSampleText());
            if (in.getActive() != null) t.setActive(in.getActive());
            return ResponseEntity.ok(repo.save(t));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
