package com.chatling.common.factor;

public enum FactorType {
    FIELD("字段特征", "直接从当前请求头、参数或Body提取"),
    AGGREGATION("聚合特征", "通过Java聚合实现类进行统计计算 (滑动窗口/计数/扣减)"),
    LIST("名单特征", "通过Redis/内存白名单、黑名单进行比对判定");

    private final String title;
    private final String description;

    FactorType(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }
}
