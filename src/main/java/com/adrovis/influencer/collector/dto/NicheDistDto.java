package com.adrovis.influencer.collector.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NicheDistDto {
    private String niche;
    private long count;
    private double percentage;
}