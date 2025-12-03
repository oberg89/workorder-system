package com.swedtrac.workorder.pricelist;

import java.util.Objects;

/**
 * Enkel POJO som representerar en artikel i prislistan.
 * Denna är inte en JPA-entitet — den hålls i minnet av PriceListService.
 */
public class Material {

    private String articleNumber; // artikelnummer (key)
    private String name;          // benämning
    private double price;         // pris per enhet (decimal)
    private String unit;          // enhet, t.ex. 'st', 'L', 'kg'

    public Material() {}

    public Material(String articleNumber, String name, double price, String unit) {
        this.articleNumber = articleNumber;
        this.name = name;
        this.price = price;
        this.unit = unit;
    }

    public String getArticleNumber() {
        return articleNumber;
    }

    public void setArticleNumber(String articleNumber) {
        this.articleNumber = articleNumber;
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

    // equals/hashCode baserat på articleNumber
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Material)) return false;
        Material material = (Material) o;
        return Objects.equals(articleNumber, material.articleNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(articleNumber);
    }

    @Override
    public String toString() {
        return "Material{" +
                "articleNumber='" + articleNumber + '\'' +
                ", name='" + name + '\'' +
                ", price=" + price +
                ", unit='" + unit + '\'' +
                '}';
    }
}