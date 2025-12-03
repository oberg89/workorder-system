// File: src/main/java/com/swedtrac/workorder/pricelist/PriceItem.java
package com.swedtrac.workorder.pricelist;

public class PriceItem {
    private String emNr;
    private String name;
    private double price;
    private String unit;
    private String sourceSheet;

    public String getEmNr() {
        return emNr;
    }

    public void setEmNr(String emNr) {
        this.emNr = emNr;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getSourceSheet() {
        return sourceSheet;
    }

    public void setSourceSheet(String sourceSheet) {
        this.sourceSheet = sourceSheet;
    }

    @Override
    public String toString() {
        return "PriceItem{" +
                "emNr='" + emNr + '\'' +
                ", name='" + name + '\'' +
                ", price=" + price +
                ", unit='" + unit + '\'' +
                ", sourceSheet='" + sourceSheet + '\'' +
                '}';
    }
}