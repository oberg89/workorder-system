// File: src/main/java/com/swedtrac/workorder/pricelist/PriceListController.java
package com.swedtrac.workorder.pricelist;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/pricelist")
public class PriceListController {

    private final PriceListService priceListService;

    public PriceListController(PriceListService priceListService) {
        this.priceListService = priceListService;
    }

    // Exakt lookup
    @GetMapping("/{emNr}")
    public ResponseEntity<PriceItem> getByEmNr(@PathVariable String emNr) {
        PriceItem item = priceListService.findByEmNrNormalize(emNr);
        if (item == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(item);
    }

    // Prefix search (autocomplete)
    @GetMapping("/search")
    public List<PriceItem> searchByPrefix(@RequestParam(name = "prefix", required = false, defaultValue = "") String prefix) {
        return priceListService.searchByPrefixNormalize(prefix);
    }
}