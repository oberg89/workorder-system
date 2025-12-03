// File: src/main/java/com/swedtrac/workorder/web/MaterialController.java
package com.swedtrac.workorder.web;

import com.swedtrac.workorder.pricelist.PriceItem;
import com.swedtrac.workorder.pricelist.PriceListService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Enkel REST controller för prislistan.
 * GET /api/materials/{articleNumber} - hämta materialinfo
 * POST /api/materials/reload - tvinga omläsning av prisfil (behörighetskontroll kan läggas till senare)
 */
@RestController
@RequestMapping("/api/materials")
@CrossOrigin(origins = "*")
public class MaterialController {

    private final PriceListService priceListService;

    public MaterialController(PriceListService priceListService) {
        this.priceListService = priceListService;
    }

    @GetMapping("/{articleNumber}")
    public ResponseEntity<PriceItem> getByArticleNumber(@PathVariable String articleNumber) {
        PriceItem item = priceListService.findByEmNrNormalize(articleNumber);
        if (item == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(item);
    }

    /**
     * Reload prislistan (körs t.ex. efter att du ersatt fil på filsystemet).
     * OBS: i produktion bör denna endpoint skyddas med autentisering/behörighet.
     */
    @PostMapping("/reload")
    public ResponseEntity<String> reload() {
        try {
            priceListService.reload();
            return ResponseEntity.ok("Prislista reloadad");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Kunde inte ladda prislista: " + e.getMessage());
        }
    }
}