package com.adrovis.influencer.campaignmanagement.publisher.service;

import com.adrovis.influencer.campaignmanagement.publisher.dto.CreatePublisherProfileRequest;
import com.adrovis.influencer.campaignmanagement.publisher.dto.PublisherProfileResponse;
import com.adrovis.influencer.campaignmanagement.publisher.entity.PublisherProfile;
import com.adrovis.influencer.campaignmanagement.publisher.repository.PublisherProfileRepository;
import com.adrovis.influencer.security.CurrentAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

@Service
@Validated
@RequiredArgsConstructor
public class PublisherProfileService {

    private static final String OWNER_TYPE_CONSTRAINT =
            "uq_publisher_owner_type";

    private final PublisherProfileRepository profiles;
    private final CurrentAccount currentAccount;

    /**
     * Creates a profile owned by the authenticated account.
     * Request input cannot select another owner or grant a platform role.
     */
    @PreAuthorize("hasAnyRole('BRAND', 'AGENCY', 'ADMIN')")
    @Transactional("primaryTransactionManager")
    public PublisherProfileResponse create(
            @NotNull @Valid CreatePublisherProfileRequest request
    ) {
        var account = currentAccount.require();

        // A BRAND cannot request an AGENCY or ADMIN profile.
        // Multi-role accounts may create profiles for roles they actually hold.
        if (!account.roles().contains(request.type().name())) {
            throw new AccessDeniedException(
                    "Your account does not have the requested publisher role."
            );
        }

        if (profiles.existsByOwnerAccountIdAndPublisherType(
                account.id(),
                request.type()
        )) {
            throw duplicateProfile();
        }

        PublisherProfile profile = new PublisherProfile(
                account.id(),
                request.type(),
                request.displayName()
        );

        try {
            // Flush here so a concurrent duplicate is detected within this call.
            PublisherProfile saved = profiles.saveAndFlush(profile);
            return PublisherProfileResponse.from(saved);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateOwnerType(exception)) {
                throw duplicateProfile();
            }

            // Do not misreport unrelated database problems as duplicate profiles.
            throw exception;
        }
    }

    /**
     * Returns only this account's non-archived publisher profiles.
     * Even an admin's "me" endpoint lists their own profiles only.
     */
    @PreAuthorize("hasAnyRole('BRAND', 'AGENCY', 'ADMIN')")
    @Transactional(
            value = "primaryTransactionManager",
            readOnly = true
    )
    public List<PublisherProfileResponse> getOwnProfiles() {
        var account = currentAccount.require();

        return profiles
                .findAllByOwnerAccountIdAndArchivedAtIsNull(account.id())
                .stream()
                .map(PublisherProfileResponse::from)
                .toList();
    }

    /**
     * Identifies only the known ownership/type unique constraint.
     * Other integrity failures must retain their original error handling.
     */
    private boolean isDuplicateOwnerType(Throwable exception) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof ConstraintViolationException violation
                    && OWNER_TYPE_CONSTRAINT.equals(
                    violation.getConstraintName()
            )) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    private ResponseStatusException duplicateProfile() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "A publisher profile already exists for this account and type."
        );
    }
}