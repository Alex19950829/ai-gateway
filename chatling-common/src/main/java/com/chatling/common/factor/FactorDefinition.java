package com.chatling.common.factor;

import java.io.Serializable;

public class FactorDefinition implements Serializable {
    private String factorCode;
    private String factorName;
    private FactorType factorType;
    private String dataType;
    private String aggregatorCode;
    private String description;

    public FactorDefinition() {}

    public FactorDefinition(String factorCode, String factorName, FactorType factorType, String dataType, String aggregatorCode, String description) {
        this.factorCode = factorCode;
        this.factorName = factorName;
        this.factorType = factorType;
        this.dataType = dataType;
        this.aggregatorCode = aggregatorCode;
        this.description = description;
    }

    public String getFactorCode() { return factorCode; }
    public void setFactorCode(String factorCode) { this.factorCode = factorCode; }
    public String getFactorName() { return factorName; }
    public void setFactorName(String factorName) { this.factorName = factorName; }
    public FactorType getFactorType() { return factorType; }
    public void setFactorType(FactorType factorType) { this.factorType = factorType; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public String getAggregatorCode() { return aggregatorCode; }
    public void setAggregatorCode(String aggregatorCode) { this.aggregatorCode = aggregatorCode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
