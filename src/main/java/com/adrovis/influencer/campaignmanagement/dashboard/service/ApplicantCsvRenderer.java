package com.adrovis.influencer.campaignmanagement.dashboard.service;

import com.adrovis.influencer.campaignmanagement.dashboard.repository.CampaignDashboardRepository.ExportRow;
import com.adrovis.influencer.campaignmanagement.exception.CampaignManagementException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ApplicantCsvRenderer {

    public byte[] render(List<ExportRow> rows, int maxBytes) {
        StringBuilder output = new StringBuilder("\uFEFF");

        int bytes = 3; // UTF-8 BOM for spreadsheet compatibility.

        String header = line(
                "applicationId",
                "creatorAccountId",
                "verifiedEmail",
                "status",
                "submittedAtUtc",
                "submittedName",
                "instagramUrl",
                "gender",
                "followerCount",
                "phone",
                "portfolioUrl"
        );

        bytes = append(output, header, bytes, maxBytes);

        for (ExportRow row : rows) {
            String csvRow = line(
                    row.getApplicationId(),
                    row.getCreatorAccountId(),
                    row.getCreatorEmail(),
                    row.getStatus(),
                    row.getSubmittedAt(),
                    row.getSubmittedName(),
                    row.getInstagramUrl(),
                    row.getGender(),
                    row.getFollowerCount(),
                    row.getPhone(),
                    row.getPortfolioUrl()
            );

            bytes = append(output, csvRow, bytes, maxBytes);
        }

        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    private int append(
            StringBuilder output,
            String value,
            int existingBytes,
            int maxBytes
    ) {
        int additionalBytes = value.getBytes(StandardCharsets.UTF_8).length;

        if ((long) existingBytes + additionalBytes > maxBytes) {
            throw new CampaignManagementException(
                    HttpStatus.CONFLICT,
                    "EXPORT_TOO_LARGE",
                    "Export exceeds the size limit. Narrow the filters."
            );
        }

        output.append(value);
        return existingBytes + additionalBytes;
    }

    private String line(String... values) {
        StringBuilder row = new StringBuilder();

        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                row.append(',');
            }

            row.append(cell(values[index]));
        }

        return row.append("\r\n").toString();
    }

    /**
     * Quotes CSV fields and neutralizes leading spreadsheet formula markers.
     * Contact numbers beginning '+' are exported as text.
     */
    private String cell(String raw) {
        String value = raw == null
                ? ""
                : raw.replace('\r', ' ').replace('\n', ' ');

        String probe = value.replaceFirst(
                "^[\\s\\p{Z}\\p{Cc}]+",
                ""
        );

        if (!probe.isEmpty()
                && "=+-@".indexOf(probe.charAt(0)) >= 0) {
            value = "'" + value;
        }

        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}