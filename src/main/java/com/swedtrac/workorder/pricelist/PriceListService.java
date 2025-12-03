// File: src/main/java/com/swedtrac/workorder/pricelist/PriceListService.java
package com.swedtrac.workorder.pricelist;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.openxml4j.exceptions.OLE2NotOfficeXmlFileException;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.EncryptedDocumentException;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PriceListService {

    private final Map<String, PriceItem> byEm = new HashMap<>(); // key = normalized em or name-key
    private final List<PriceItem> allItems = new ArrayList<>();

    @PostConstruct
    public void load() {
        reload();
    }

    public synchronized void reload() {
        byEm.clear();
        allItems.clear();
        loadData();
    }

    private void loadData() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("prislista.xlsx")) {
            if (in == null) {
                System.err.println("Kunde inte hitta prislista.xlsx i classpath");
                return;
            }

            Workbook wb;
            try {
                wb = WorkbookFactory.create(in);
            } catch (OLE2NotOfficeXmlFileException | EncryptedDocumentException oe) {
                // Fallback: kanske .xls older format
                in.reset();
                wb = WorkbookFactory.create(in);
            }

            for (int si = 0; si < wb.getNumberOfSheets(); si++) {
                Sheet sheet = wb.getSheetAt(si);
                if (sheet == null) continue;
                String sheetName = sheet.getSheetName();
                System.out.println("Läser blad: " + sheetName);
                readSheetFlexible(sheet, sheetName);
            }

            System.out.println("Prislista: laddad totalt " + allItems.size() + " artiklar (unika nycklar: " + byEm.size() + ")");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Försök läsa ett blad med flexibel heuristik:
     * - hitta header-rad genom att leta efter typiska kolumnnamn
     * - mappa kolumner
     * - iterera rader under header och skapa PriceItem när rimligt
     */
    private void readSheetFlexible(Sheet sheet, String sheetName) {
        if (sheet.getLastRowNum() < 0) return;

        int headerRowIndex = findHeaderRow(sheet, 0, 12);
        Map<String, Integer> colMap = new HashMap<>();

        if (headerRowIndex >= 0) {
            Row hdr = sheet.getRow(headerRowIndex);
            colMap = mapHeaderColumns(hdr);
        } else {
            // Ingen tydlig header hittad — försök heuristiskt
            // Vi försöker hitta en rad med flera textceller som kan vara "Material" etc.
            headerRowIndex = findHeaderRowByKeywords(sheet);
            if (headerRowIndex >= 0) {
                Row hdr = sheet.getRow(headerRowIndex);
                colMap = mapHeaderColumns(hdr);
            } else {
                // fallback: försök läsa tabellformat utan header — sök priser i varje rad
                processRowsHeuristically(sheet, sheetName);
                return;
            }
        }

        // Om vi lyckats mappa minst en relevant kolumn, processa rader efter header
        if (!colMap.isEmpty()) {
            for (int r = headerRowIndex + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                // Hoppa över rader som återigen är headers eller tomma
                if (isRowEmpty(row)) continue;
                if (looksLikeHeaderRowAgain(row)) continue;

                String material = getCellStringSafe(row, colMap.getOrDefault("material", -1));
                String emNr = getCellStringSafe(row, colMap.getOrDefault("em", -1));
                String artnr = getCellStringSafe(row, colMap.getOrDefault("artnr", -1));
                String unit = getCellStringSafe(row, colMap.getOrDefault("unit", -1));
                Double priceCust = getCellDoubleSafe(row, colMap.getOrDefault("price_customer", -1));
                Double pricePurchase = getCellDoubleSafe(row, colMap.getOrDefault("price_purchase", -1));
                Double priceAny = (priceCust != null && priceCust > 0) ? priceCust : (pricePurchase != null ? pricePurchase : null);

                if ((emNr == null || emNr.isBlank()) && (artnr == null || artnr.isBlank()) && (material == null || material.isBlank()) && priceAny == null) {
                    // sannolikt rad utan data
                    continue;
                }

                addPriceItemIfValid(material, emNr, artnr, priceAny, unit, sheetName);
            }
        } else {
            // Ingen mappning: fallback heuristik
            processRowsHeuristically(sheet, sheetName);
        }
    }

    private int findHeaderRow(Sheet sheet, int startRow, int maxRowsToCheck) {
        int last = Math.min(sheet.getLastRowNum(), startRow + maxRowsToCheck);
        int bestRow = -1;
        int bestScore = 0;
        for (int r = startRow; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            int score = 0;
            for (Cell c : row) {
                String txt = getCellString(c).toLowerCase(Locale.ROOT);
                if (txt.isBlank()) continue;
                if (txt.contains("em") || txt.contains("em nr") || txt.contains("em nr /") || txt.contains("em nr / leverantör") || txt.contains("em nr/leverantör")) score++;
                if (txt.contains("art") || txt.contains("art nr") || txt.contains("artnr") || txt.contains("benämning")) score++;
                if (txt.contains("pris") || txt.contains("pris inköp") || txt.contains("pris till kund") || txt.contains("pris/inköp")) score++;
                if (txt.contains("enhet") || txt.contains("st") || txt.contains("kg")) score++;
                if (txt.contains("material") || txt.contains("benämning")) score++;
            }
            if (score > bestScore && score >= 2) {
                bestScore = score;
                bestRow = r;
            }
        }
        return bestRow;
    }

    private int findHeaderRowByKeywords(Sheet sheet) {
        // bredare sökning: leta i fler rader efter rader innehållande "Material" eller "Pris inköp" etc.
        for (int r = 0; r <= Math.min(sheet.getLastRowNum(), 40); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            int hits = 0;
            for (Cell c : row) {
                String txt = getCellString(c).toLowerCase(Locale.ROOT);
                if (txt.contains("material")) hits++;
                if (txt.contains("pris inköp") || txt.contains("pris/inköp") || txt.contains("pris inköp")) hits++;
                if (txt.contains("pris till kund") || txt.contains("pris till")) hits++;
                if (txt.contains("em nr") || txt.contains("em nr /")) hits++;
                if (txt.contains("art") || txt.contains("art nr")) hits++;
            }
            if (hits >= 1) return r;
        }
        return -1;
    }

    private Map<String, Integer> mapHeaderColumns(Row hdr) {
        Map<String, Integer> mm = new HashMap<>();
        if (hdr == null) return mm;
        for (Cell c : hdr) {
            String text = getCellString(c).toLowerCase(Locale.ROOT).trim();
            int idx = c.getColumnIndex();
            if (text.isBlank()) continue;
            if (text.contains("material") || text.contains("benämning") || text.contains("art nr/benämning") || text.contains("art nr/ benämning")) {
                mm.put("material", idx);
                continue;
            }
            if (text.contains("em nr") || text.contains("em") || text.contains("em nr /")) {
                mm.put("em", idx);
                continue;
            }
            if (text.contains("art nr") || text.contains("artnr") || text.contains("art")) {
                mm.put("artnr", idx);
                continue;
            }
            if (text.contains("pris till") || text.contains("pris till kund") || text.contains("pris kund") || text.contains("pris till")) {
                mm.put("price_customer", idx);
                continue;
            }
            if (text.contains("pris inköp") || text.contains("pris/inköp") || text.contains("pris inköp")) {
                mm.put("price_purchase", idx);
                continue;
            }
            if (text.contains("enhet") || text.contains("unit")) {
                mm.put("unit", idx);
                continue;
            }
            if (text.contains("antal") || text.contains("per lok") || text.contains("per lok")) {
                mm.put("qty", idx);
            }
            // support for service table headers
            if (text.contains("kr/tim") || text.contains("kr/tim") || text.contains("kr/timme") || text.contains("tid") || text.contains("litt")) {
                mm.put("service", idx);
            }
        }
        return mm;
    }

    private void processRowsHeuristically(Sheet sheet, String sheetName) {
        // Om inget header hittades, gå igenom rader och leta efter rader som innehåller pris (nummer) och namn/emnr
        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            if (isRowEmpty(row)) continue;

            // Hitta första textcell (troligtvis namn) och första numeriska cell (troligtvis pris)
            String possibleName = null;
            String possibleEm = null;
            String possibleUnit = null;
            Double possiblePrice = null;

            for (Cell c : row) {
                String cs = getCellString(c);
                if (possibleName == null && looksLikeName(cs)) possibleName = cs;
                if (possibleEm == null && looksLikeEm(cs)) possibleEm = cs;
                if (possiblePrice == null) {
                    Double dv = getCellDoubleSafe(c);
                    if (dv != null && dv > 0) {
                        possiblePrice = dv;
                    } else {
                        // sometimes price is string with comma
                        String norm = cs.replaceAll("[^0-9,\\.\\-]", "").replace(',', '.');
                        try {
                            if (!norm.isBlank()) {
                                double pd = Double.parseDouble(norm);
                                if (pd > 0) possiblePrice = pd;
                            }
                        } catch (Exception ignored) {}
                    }
                }
                if (possibleUnit == null && cs != null && cs.matches(".*(st|kg|l|L|dag).*")) possibleUnit = cs;
            }

            if ((possibleEm != null || possibleName != null) && possiblePrice != null) {
                addPriceItemIfValid(possibleName, possibleEm, null, possiblePrice, possibleUnit, sheetName);
            }
        }
    }

    private boolean looksLikeName(String s) {
        if (s == null) return false;
        String t = s.trim();
        return t.length() >= 2 && !t.matches("^[0-9\\s\\-\\.,]+$");
    }

    private boolean looksLikeEm(String s) {
        if (s == null) return false;
        String t = s.trim();
        // em numbers often contain digits and spaces, maybe letters
        return t.matches(".*\\d.*") && t.length() <= 20;
    }

    private boolean isRowEmpty(Row row) {
        for (Cell c : row) {
            String v = getCellString(c);
            if (v != null && !v.isBlank()) return false;
        }
        return true;
    }

    private boolean looksLikeHeaderRowAgain(Row row) {
        // skip rows that look like repeated header lines (contain 'Material' or 'Pris inköp' etc.)
        for (Cell c : row) {
            String txt = getCellString(c).toLowerCase(Locale.ROOT);
            if (txt.contains("material") || txt.contains("pris inköp") || txt.contains("pris till kund") || txt.contains("em nr") || txt.contains("art nr")) {
                return true;
            }
        }
        return false;
    }

    private String getCellStringSafe(Row row, int idx) {
        if (row == null || idx < 0) return null;
        Cell c = row.getCell(idx);
        return getCellString(c);
    }

    private Double getCellDoubleSafe(Row row, int idx) {
        if (row == null || idx < 0) return null;
        Cell c = row.getCell(idx);
        return getCellDoubleSafe(c);
    }

    private Double getCellDoubleSafe(Cell c) {
        if (c == null) return null;
        try {
            if (c.getCellType() == CellType.NUMERIC) {
                return c.getNumericCellValue();
            } else if (c.getCellType() == CellType.STRING) {
                String s = c.getStringCellValue();
                return parseDoubleFromString(s);
            } else if (c.getCellType() == CellType.FORMULA) {
                try {
                    return c.getNumericCellValue();
                } catch (Exception e) {
                    String s = c.getStringCellValue();
                    return parseDoubleFromString(s);
                }
            } else {
                String s = getCellString(c);
                return parseDoubleFromString(s);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private Double parseDoubleFromString(String s) {
        if (s == null) return null;
        String norm = s.replaceAll("[^0-9,\\.\\-]", "").replace(',', '.').trim();
        if (norm.isEmpty()) return null;
        try {
            return Double.parseDouble(norm);
        } catch (Exception e) {
            return null;
        }
    }

    private String getCellString(Cell c) {
        if (c == null) return "";
        try {
            if (c.getCellType() == CellType.STRING) return c.getStringCellValue().trim();
            if (c.getCellType() == CellType.NUMERIC) {
                if (DateUtil.isCellDateFormatted(c)) return c.getDateCellValue().toString();
                double d = c.getNumericCellValue();
                // show as integer-like if no fraction
                if (d == Math.floor(d)) return String.valueOf((long) d);
                return String.valueOf(d);
            }
            if (c.getCellType() == CellType.BOOLEAN) return String.valueOf(c.getBooleanCellValue());
            if (c.getCellType() == CellType.FORMULA) {
                try {
                    CellValue val = c.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator().evaluate(c);
                    if (val == null) return "";
                    switch (val.getCellType()) {
                        case STRING: return val.getStringValue().trim();
                        case NUMERIC:
                            double d = val.getNumberValue();
                            if (d == Math.floor(d)) return String.valueOf((long) d);
                            return String.valueOf(d);
                        case BOOLEAN: return String.valueOf(val.getBooleanValue());
                        default: return "";
                    }
                } catch (Exception e) {
                    return c.toString().trim();
                }
            }
            return c.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private void addPriceItemIfValid(String material, String emNr, String artnr, Double price, String unit, String sheetName) {
        // Normalize inputs
        String name = (material != null && !material.isBlank()) ? material.trim() : (artnr != null ? artnr.trim() : null);
        String em = (emNr != null && !emNr.isBlank()) ? emNr.trim() : null;

        if ((em == null || em.isBlank()) && (name == null || name.isBlank())) return;
        double pr = (price != null) ? price : 0.0;
        String u = (unit != null && !unit.isBlank()) ? unit.trim() : "st";

        PriceItem item = new PriceItem();
        // set emNr if available, otherwise use generated name-key
        if (em != null && !em.isBlank()) {
            item.setEmNr(em);
        } else {
            item.setEmNr("NAME:" + (name.length() > 60 ? name.substring(0, 60) : name));
        }
        item.setName(name != null ? name : "");
        item.setPrice(pr);
        item.setUnit(u);
        item.setSourceSheet(sheetName);

        String key = normalize(item.getEmNr());
        // If duplicate key exists, we keep the first found (you can change to replace if you prefer)
        if (!byEm.containsKey(key)) {
            byEm.put(key, item);
            allItems.add(item);
        } else {
            // If existing has price 0 and new has price>0, replace
            PriceItem existing = byEm.get(key);
            if ((existing.getPrice() == 0.0 || existing.getPrice() < 0.0001) && item.getPrice() > 0.0) {
                byEm.put(key, item);
                // replace in allItems as well
                allItems.remove(existing);
                allItems.add(item);
            }
        }
    }

    private String normalize(String s) {
        if (s == null) return "";
        return s.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    // Public API methods used by controller / frontend

    /**
     * Find by article number (emNr or artnr). Returns null if not found.
     */
    public PriceItem findByArticleNumber(String articleNumber) {
        if (articleNumber == null) return null;
        String k = normalize(articleNumber);
        PriceItem p = byEm.get(k);
        if (p != null) return p;
        // fallback: try contains match on name
        return allItems.stream()
                .filter(it -> normalize(it.getEmNr()).equals(k) || normalize(it.getName()).contains(k))
                .findFirst()
                .orElse(null);
    }

    /**
     * Search by prefix (autocomplete). Returns up to 'limit' items.
     */
    public List<PriceItem> searchByPrefix(String prefix, int limit) {
        if (prefix == null || prefix.trim().isEmpty()) return Collections.emptyList();
        String p = normalize(prefix);
        return allItems.stream()
                .filter(it -> normalize(it.getEmNr()).startsWith(p) || normalize(it.getName()).contains(p))
                .limit(limit)
                .collect(Collectors.toList());
    }

    // convenience wrapper used by controller in earlier code
    public List<PriceItem> searchByPrefixNormalize(String prefix) {
        return searchByPrefix(prefix, 20);
    }

    /*
     * Backwards-compatible adapter methods — finns för att matcha äldre controller-kod
     */

    /**
     * Kompatibilitetsmetod: returnerar ett enstaka PriceItem för ett emNr (samma som findByArticleNumber)
     */
    public PriceItem findByEmNrNormalize(String emNr) {
        return findByArticleNumber(emNr);
    }

    /**
     * Kompatibilitetsmetod: returnerar en lista (autocomplete) för ett prefix
     */
    public List<PriceItem> findByEmNrNormalizeList(String prefix) {
        return searchByPrefixNormalize(prefix);
    }

    /**
     * Hjälpmetod för att kunna visa allt (kan vara användbar vid debugging)
     */
    public List<PriceItem> getAllItems() {
        return Collections.unmodifiableList(allItems);
    }
}