package com.adrovis.influencer.campaignmanagement.dashboard.service;

import com.adrovis.influencer.campaignmanagement.application.dto.PublisherCampaignApplicationResponse;
import com.adrovis.influencer.campaignmanagement.application.enums.CampaignApplicationStatus;
import com.adrovis.influencer.campaignmanagement.campaign.dto.ManagedCampaignResponse;
import com.adrovis.influencer.campaignmanagement.campaign.enums.CampaignStatus;
import com.adrovis.influencer.campaignmanagement.campaign.repository.ManagedCampaignRepository;
import com.adrovis.influencer.campaignmanagement.dashboard.dto.ApplicantCsvExport;
import com.adrovis.influencer.campaignmanagement.dashboard.dto.CampaignDashboardOverview;
import com.adrovis.influencer.campaignmanagement.dashboard.repository.CampaignDashboardRepository;
import com.adrovis.influencer.campaignmanagement.exception.CampaignManagementException;
import com.adrovis.influencer.campaignmanagement.publisher.enums.PublisherType;
import com.adrovis.influencer.security.CurrentAccount;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasAnyRole('BRAND', 'AGENCY', 'ADMIN')")
public class CampaignDashboardService {

    private final CampaignDashboardRepository dashboard;
    private final ManagedCampaignRepository campaigns;
    private final CurrentAccount currentAccount;
    private final ApplicantCsvRenderer csv;
    private final CampaignExportAudit exportAudit;
    private final int exportMaxRows;
    private final int exportMaxBytes;

    public CampaignDashboardService(
            CampaignDashboardRepository dashboard,
            ManagedCampaignRepository campaigns,
            CurrentAccount currentAccount,
            ApplicantCsvRenderer csv,
            CampaignExportAudit exportAudit,
            @Value("${app.campaign-dashboard.export-max-rows:500}")
            int exportMaxRows,
            @Value("${app.campaign-dashboard.export-max-bytes:2097152}")
            int exportMaxBytes
    ) {
        if (exportMaxRows < 1 || exportMaxRows > 1000) {
            throw new IllegalStateException(
                    "Export row limit must be between 1 and 1000."
            );
        }

        if (exportMaxBytes < 1024 || exportMaxBytes > 10_485_760) {
            throw new IllegalStateException(
                    "Export byte limit must be between 1024 and 10485760."
            );
        }

        this.dashboard = dashboard;
        this.campaigns = campaigns;
        this.currentAccount = currentAccount;
        this.csv = csv;
        this.exportAudit = exportAudit;
        this.exportMaxRows = exportMaxRows;
        this.exportMaxBytes = exportMaxBytes;
    }

    /**
     * Repeatable-read keeps both sets of aggregate counts on one DB snapshot.
     * Counts are all-time and include archived campaign history.
     */
    @Transactional(
            value = "primaryTransactionManager",
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ
    )
    public CampaignDashboardOverview overview() {
        Scope scope = scope();

        Map<CampaignStatus, Long> campaignCounts =
                new EnumMap<>(CampaignStatus.class);

        for (CampaignStatus status : CampaignStatus.values()) {
            campaignCounts.put(status, 0L);
        }

        dashboard.countCampaigns(
                scope.admin(),
                scope.accountId(),
                scope.publisherTypes()
        ).forEach(row -> campaignCounts.put(
                row.getStatus(), row.getTotal()
        ));

        Map<CampaignApplicationStatus, Long> applicationCounts =
                new EnumMap<>(CampaignApplicationStatus.class);

        for (CampaignApplicationStatus status
                : CampaignApplicationStatus.values()) {
            applicationCounts.put(status, 0L);
        }

        dashboard.countApplications(
                scope.admin(),
                scope.accountId(),
                scope.publisherTypes()
        ).forEach(row -> applicationCounts.put(
                row.getStatus(), row.getTotal()
        ));

        return new CampaignDashboardOverview(
                campaignCounts.values().stream()
                        .mapToLong(Long::longValue).sum(),
                Map.copyOf(campaignCounts),
                applicationCounts.values().stream()
                        .mapToLong(Long::longValue).sum(),
                Map.copyOf(applicationCounts)
        );
    }

    @Transactional(
            value = "primaryTransactionManager",
            readOnly = true
    )
    public Page<ManagedCampaignResponse> campaigns(
            String search,
            CampaignStatus status,
            PublisherType publisherType,
            UUID publisherId,
            boolean includeArchived,
            int page,
            int size
    ) {
        Scope scope = scope();

        if (status == CampaignStatus.ARCHIVED && !includeArchived) {
            throw error(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_FILTER",
                    "Use includeArchived=true when filtering archived campaigns."
            );
        }

        return dashboard.searchCampaigns(
                scope.admin(),
                scope.accountId(),
                scope.publisherTypes(),
                includeArchived,
                status,
                publisherType,
                publisherId,
                searchPattern(search),
                pagination(page, size, "createdAt")
        ).map(ManagedCampaignResponse::from);
    }

    @Transactional(
            value = "primaryTransactionManager",
            readOnly = true
    )
    public Page<PublisherCampaignApplicationResponse> applicants(
            UUID campaignId,
            String search,
            CampaignApplicationStatus status,
            int page,
            int size
    ) {
        authorizeCampaignRead(campaignId);

        return dashboard.searchApplications(
                campaignId,
                status,
                searchPattern(search),
                pagination(page, size, "submittedAt")
        ).map(PublisherCampaignApplicationResponse::from);
    }

    /**
     * No response bytes are sent until authorization, bounds checks
     * and audit insertion have succeeded.
     */
    @Transactional(
            value = "primaryTransactionManager",
            isolation = Isolation.REPEATABLE_READ
    )
    public ApplicantCsvExport export(
            UUID campaignId,
            String search,
            CampaignApplicationStatus status
    ) {
        var account = currentAccount.require();
        authorizeCampaignRead(campaignId);

        String pattern = searchPattern(search);

        var rows = dashboard.exportApplications(
                campaignId,
                status == null ? "" : status.name(),
                pattern,
                exportMaxRows + 1
        );

        if (rows.size() > exportMaxRows) {
            throw error(
                    HttpStatus.CONFLICT,
                    "EXPORT_TOO_LARGE",
                    "Too many applicants to export. Narrow the filters."
            );
        }

        byte[] content = csv.render(rows, exportMaxBytes);

        exportAudit.record(
                account.id(),
                campaignId,
                status,
                search != null && !search.isBlank(),
                rows.size(),
                content.length
        );

        return new ApplicantCsvExport(
                "campaign-" + campaignId + "-applicants.csv",
                content
        );
    }

    /**
     * Historical applicant reads remain available after campaign archiving.
     */
    private void authorizeCampaignRead(UUID campaignId) {
        var account = currentAccount.require();

        var campaign = campaigns.findById(campaignId)
                .orElseThrow(this::campaignNotFound);

        boolean admin = account.roles().contains("ADMIN");
        var publisher = campaign.getPublisherProfile();

        if (!admin
                && !publisher.getOwnerAccountId().equals(account.id())) {
            throw campaignNotFound();
        }

        if (!admin
                && !account.roles().contains(
                publisher.getPublisherType().name()
        )) {
            throw new AccessDeniedException(
                    "Your account cannot access this publisher."
            );
        }
    }

    private Scope scope() {
        var account = currentAccount.require();
        boolean admin = account.roles().contains("ADMIN");

        List<PublisherType> types = admin
                ? List.of(PublisherType.values())
                : Arrays.stream(PublisherType.values())
                .filter(type -> account.roles().contains(type.name()))
                .toList();

        if (types.isEmpty()) {
            throw new AccessDeniedException(
                    "A publisher role is required."
            );
        }

        return new Scope(account.id(), admin, types);
    }

    private PageRequest pagination(int page, int size, String sortField) {
        if (page < 0 || page > 1_000_000 || size < 1 || size > 100) {
            throw error(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAGINATION",
                    "Use page between 0 and 1000000 and size between 1 and 100."
            );
        }

        return PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc(sortField),
                        Sort.Order.desc("id")
                )
        );
    }

    private String searchPattern(String search) {
        String value = search == null ? "" : search.trim();

        if (value.length() > 100) {
            throw error(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_FILTER",
                    "Search must not exceed 100 characters."
            );
        }

        // Treat user '%' and '_' as literal characters, not SQL wildcards.
        value = value.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");

        return "%" + value + "%";
    }

    private CampaignManagementException campaignNotFound() {
        return error(
                HttpStatus.NOT_FOUND,
                "CAMPAIGN_NOT_FOUND",
                "Campaign not found."
        );
    }

    private CampaignManagementException error(
            HttpStatus status,
            String code,
            String message
    ) {
        return new CampaignManagementException(status, code, message);
    }

    private record Scope(
            UUID accountId,
            boolean admin,
            List<PublisherType> publisherTypes
    ) {
    }
}