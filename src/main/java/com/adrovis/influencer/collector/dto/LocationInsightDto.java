package com.adrovis.influencer.collector.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationInsightDto {
    private String location;
    private long count;
    private double percentage;
}