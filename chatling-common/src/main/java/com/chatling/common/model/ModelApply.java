package com.chatling.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelApply {
    private Long id;
    private String applicantName;
    private String department;
    private String apiKey;
    private String modelName;
    private String applyReason;
    private Integer status; // 0-待审批, 1-已通过, 2-已驳回
    private Date createdTime;
    private Date updatedTime;
}
