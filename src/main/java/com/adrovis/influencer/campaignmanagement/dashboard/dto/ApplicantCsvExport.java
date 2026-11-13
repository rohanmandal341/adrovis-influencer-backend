package com.adrovis.influencer.campaignmanagement.dashboard.dto;

public record ApplicantCsvExport(
        String filename,
        byte[] content
) {
}