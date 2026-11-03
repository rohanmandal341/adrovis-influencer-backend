package com.adrovis.influencer.collector.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrendDataPointDto {
    private String date;
    private long totalCreators;
    private long uniqueEmails;
}