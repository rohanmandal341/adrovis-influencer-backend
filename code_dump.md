### .\docker-compose.yml
```yml
version: '3.8'

services:
  api:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: influencer-core-api
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - SUPABASE_DB_URL=${SUPABASE_DB_URL:-jdbc:postgresql://db.dhfstnonquoyfgnpswsw.supabase.co:5432/postgres?sslmode=require}
      - SUPABASE_DB_USER=${SUPABASE_DB_USER:-postgres}
      - SUPABASE_DB_PASSWORD=${SUPABASE_DB_PASSWORD}
      - BACKEND_BASE_URL=http://localhost:8080
    restart: unless-stopped

```

### .\Dockerfile
```dockerfile
# ==========================================
# 1. Build Stage (Maven on Java 17)
# ==========================================
FROM maven:3.9.9-eclipse-temurin-17-alpine AS builder
WORKDIR /app

# Cache Maven dependencies
COPY pom.xml ./
RUN mvn dependency:go-offline -B || true

# Copy application source and build the JAR (test sources are not compiled)
COPY src ./src
RUN mvn clean package -Dmaven.test.skip=true

# ==========================================
# 2. Lightweight Production Runtime Stage (Java 17)
# ==========================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Add non-root user for container security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/target/*.jar app.jar

# Render supplies dynamic port via $PORT
ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE ${PORT}

ENTRYPOINT ["sh", "-c", "java -XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -Dserver.port=${PORT} -jar app.jar"]
```

### .\pom.xml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>
    <groupId>com.adrovis</groupId>
    <artifactId>influencer-core-api</artifactId>
    <version>1.0.0</version>
    <name>influencer-core-api</name>
    <description>Enterprise Core API Service for Influencer Campaign Discovery and Marketing Automation</description>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
    </properties>

    <dependencies>
        <!-- Web & REST -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Trust access tokens issued by the separate Adrovis account service. -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
        </dependency>

        <!-- JPA & PostgreSQL -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Swagger / OpenAPI UI for Spring Boot 3.x -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.6.0</version>
        </dependency>

        <!-- Bean Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Fast Dev Reload (Zero wait during local coding) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-devtools</artifactId>
            <scope>runtime</scope>
            <optional>true</optional>
        </dependency>

        <!-- Monitoring & Health check for Render / Cloud Deployment -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Lombok for Boilerplate reduction -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <dependency>
            <groupId>com.razorpay</groupId>
            <artifactId>razorpay-java</artifactId>
            <version>1.4.5</version>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
            <!-- Use the same org.json implementation as the gateway SDK, not a competing Android test copy. -->
            <exclusions>
                <exclusion>
                    <groupId>com.vaadin.external.google</groupId>
                    <artifactId>android-json</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>

```

### .\render.yaml
```yaml
services:
  - type: web
    name: influencer-core-api
    runtime: docker
    plan: free
    region: singapore # or frankfurt/ohio
    dockerfilePath: Dockerfile
    envVars:
      - key: SPRING_PROFILES_ACTIVE
        value: prod
      - key: DATABASE_URL
        sync: false
      - key: DATABASE_USERNAME
        value: postgres
      - key: DATABASE_PASSWORD
        sync: false
      - key: BACKEND_BASE_URL
        sync: false # Set to your Render URL e.g. https://influencer-core-api.onrender.com
      - key: ADROVIS_AUTH_ISSUER
        sync: false
      - key: ADROVIS_AUTH_AUDIENCE
        value: adrovis-platform
      - key: ADROVIS_AUTH_PUBLIC_KEY_LOCATION
        value: file:/etc/secrets/adrovis-public.pem
      - key: ADROVIS_AUTH_STATUS_URL
        sync: false
      - key: ADROVIS_FRONTEND_ORIGIN
        sync: false
    healthCheckPath: /api/v1/health

```

### .github\workflows\verify-security.yml
```yml
name: Verify security integration
on:
  push:
    branches: [main]
  pull_request:
permissions:
  contents: read
jobs:
  verify:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_USER: quota_test
          POSTGRES_PASSWORD: disposable-ci-password
          POSTGRES_DB: guest_apply_test
        ports:
          - 5432:5432
        options: >-
          --health-cmd "pg_isready -U quota_test -d guest_apply_test"
          --health-interval 5s --health-timeout 5s --health-retries 10
    env:
      GUEST_APPLY_TEST_DATABASE_URL: jdbc:postgresql://127.0.0.1:5432/guest_apply_test
      GUEST_APPLY_TEST_DATABASE_PASSWORD: disposable-ci-password
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - run: mvn -B -ntp verify

```

### .\src\main\java\com\adrovis\influencer\InfluencerApplication.java
```java
package com.adrovis.influencer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class InfluencerApplication {

    public static void main(String[] args) {
        SpringApplication.run(InfluencerApplication.class, args);
    }
}

```

### .\src\main\java\com\adrovis\influencer\campaign\access\CampaignApplyAccessService.java
```java
package com.adrovis.influencer.campaign.access;

import com.adrovis.influencer.service.CampaignService;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignApplyAccessService {

    private final CampaignService campaigns;
    private final GuestApplyRepository guests;
    private final int limit;
    private final GuestApplyCountingMode mode;
    /** Initializes the explicit dependencies and validates this component's configuration. */

    public CampaignApplyAccessService(CampaignService campaigns, GuestApplyRepository guests,
                                      @Value("${app.guest-apply.limit:3}") int limit,
                                      @Value("${app.guest-apply.counting-mode:CLICKS}") GuestApplyCountingMode mode) {
        if (limit < 1 || limit > 100) {
            throw new IllegalStateException("Guest campaign allowance must be between 1 and 100");
        }
        this.campaigns = campaigns;
        this.guests = guests;
        this.limit = limit;
        this.mode = java.util.Objects.requireNonNull(mode, "Configure a guest Apply counting mode");
    }
    /** Validates the destination and atomically enforces the selected guest policy before recording a click. */

    @Transactional("primaryTransactionManager")
    public String grant(UUID campaignId, String guestHash, boolean authenticated) {
        // Missing campaigns and invalid destinations must not consume a guest allowance.
        campaigns.getApplicationDestination(campaignId);
        if (!authenticated && !guests.allow(guestHash, campaignId, limit, mode)) {
            throw new SignupRequiredException();
        }
        return campaigns.trackAndGetApplicationUrl(campaignId);
    }
}

```

### .\src\main\java\com\adrovis\influencer\campaign\access\GuestApplyCookie.java
```java
package com.adrovis.influencer.campaign.access;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class GuestApplyCookie {

    public static final String NAME = "adrovis_guest_apply";
    private final SecureRandom random = new SecureRandom();
    private final boolean secure;
    private final String sameSite;
    /** Initializes the explicit dependencies and validates this component's configuration. */

    public GuestApplyCookie(@Value("${app.guest-apply.cookie-secure:true}") boolean secure,
                            @Value("${app.guest-apply.cookie-same-site:Lax}") String sameSite,
                            Environment environment) {
        if (!java.util.Set.of("Lax", "Strict", "None").contains(sameSite)
                || ("None".equals(sameSite) && !secure)
                || (environment.acceptsProfiles(Profiles.of("prod")) && !secure)) {
            throw new IllegalStateException("Guest cookies require a valid SameSite value and Secure in production");
        }
        this.secure = secure;
        this.sameSite = sameSite;
    }
    /** Reuses a valid random browser identity or creates a fresh cryptographic identifier. */

    public String identity(String raw) {
        if (raw != null && raw.matches("[A-Za-z0-9_-]{43}")) {
            return raw;
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    /** Derives the stored guest identifier without retaining the raw cookie. */

    public String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
    /** Writes the scoped HttpOnly guest cookie; production requires Secure and an explicit SameSite policy. */

    public void write(HttpServletResponse response, String raw) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(NAME, raw)
                .httpOnly(true).secure(secure).sameSite(sameSite).path("/api/v1/campaigns")
                .maxAge(Duration.ofDays(30)).build().toString());
    }
}

```

### .\src\main\java\com\adrovis\influencer\campaign\access\GuestApplyCountingMode.java
```java
package com.adrovis.influencer.campaign.access;

/** CLICKS matches the current trial requirement; DISTINCT_CAMPAIGNS retains the previous optional policy. */
public enum GuestApplyCountingMode {
    CLICKS,
    DISTINCT_CAMPAIGNS
}

```

### .\src\main\java\com\adrovis\influencer\campaign\access\GuestApplyExceptionHandler.java
```java
package com.adrovis.influencer.campaign.access;

import com.adrovis.influencer.controller.CampaignController;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = CampaignController.class)
public class GuestApplyExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GuestApplyExceptionHandler.class);

    /** Returns the stable frontend signup trigger without disclosing the external application URL. */
    @ExceptionHandler(SignupRequiredException.class)
    public ResponseEntity<Map<String, Object>> signupRequired(SignupRequiredException exception) {
        return ResponseEntity.status(403).header("Cache-Control", "no-store").body(Map.of(
                "success", false,
                "errorCode", "SIGNUP_REQUIRED",
                "message", exception.getMessage()));
    }

    /** Returns a retryable failure without bypassing account or data checks. */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> unavailable(DataAccessException exception) {
        // ADDED: logs the real Postgres reason server-side only; the response body stays generic
        log.error("Guest apply DB failure: {}", exception.getMostSpecificCause().getMessage(), exception);

        return ResponseEntity.status(503).header("Cache-Control", "no-store").body(Map.of(
                "success", false, "errorCode", "APPLICATION_ACCESS_UNAVAILABLE",
                "message", "Application access is temporarily unavailable. Please try again."));
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaign\access\GuestApplyRepository.java
```java
package com.adrovis.influencer.campaign.access;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;

@Repository
public class GuestApplyRepository {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    /** Initializes the explicit dependencies and validates this component's configuration. */

    public GuestApplyRepository(@Qualifier("primaryDataSource") DataSource dataSource,
                                @Qualifier("primaryTransactionManager") PlatformTransactionManager manager) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.transactions = new TransactionTemplate(manager);
    }

    /** Serializes quota and grant writes on one guest row; the caller's primary transaction also records the click. */
    public boolean allow(String guestHash, UUID campaignId, int limit, GuestApplyCountingMode mode) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            jdbc.update("INSERT INTO public.guest_apply_sessions (guest_hash) VALUES (?) ON CONFLICT DO NOTHING",
                    guestHash);
            // Serialize all quota decisions for this guest, including requests for different campaigns.
            Integer clicks = jdbc.queryForObject(
                    "SELECT apply_count FROM public.guest_apply_sessions WHERE guest_hash = ? FOR UPDATE",
                    Integer.class, guestHash);
            if (mode == GuestApplyCountingMode.CLICKS && clicks != null && clicks >= limit) {
                return false;
            }
            Boolean repeat = jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM public.guest_campaign_access
                    WHERE guest_hash = ? AND campaign_id = ?)
                    """, Boolean.class, guestHash, campaignId);
            if (mode == GuestApplyCountingMode.DISTINCT_CAMPAIGNS && Boolean.TRUE.equals(repeat)) {
                jdbc.update("UPDATE public.guest_apply_sessions SET apply_count = apply_count + 1 WHERE guest_hash = ?",
                        guestHash);
                return true;
            }
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM public.guest_campaign_access WHERE guest_hash = ?", Integer.class, guestHash);
            if (mode == GuestApplyCountingMode.DISTINCT_CAMPAIGNS && count != null && count >= limit) {
                return false;
            }
            jdbc.update("INSERT INTO public.guest_campaign_access (guest_hash, campaign_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    guestHash, campaignId);
            jdbc.update("UPDATE public.guest_apply_sessions SET apply_count = apply_count + 1 WHERE guest_hash = ?",
                    guestHash);
            return true;
        }));
    }
}

```

### .\src\main\java\com\adrovis\influencer\campaign\access\SignupRequiredException.java
```java
package com.adrovis.influencer.campaign.access;

public class SignupRequiredException extends RuntimeException {
    /** Initializes the explicit dependencies and validates this component's configuration. */

    public SignupRequiredException() {
        super("Create an account or log in to apply to more campaigns.");
    }
}

```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\application\controller\CreatorCampaignApplicationController.java
```java
package com.adrovis.influencer.campaignmanagement.application.controller;

import com.adrovis.influencer.campaignmanagement.application.dto.CreatorApplicationUpdateResponse;
import com.adrovis.influencer.campaignmanagement.application.dto.CreatorCampaignApplicationResponse;
import com.adrovis.influencer.campaignmanagement.application.dto.SubmitCampaignApplicationRequest;
import com.adrovis.influencer.campaignmanagement.application.service.CreatorCampaignApplicationService;
import com.adrovis.influencer.dto.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CreatorCampaignApplicationController {

    private final CreatorCampaignApplicationService applications;

    @PostMapping("/api/v1/campaigns/managed/{campaignId}/applications")
    public ResponseEntity<ApiResponse<CreatorCampaignApplicationResponse>> submit(
            @PathVariable UUID campaignId,
            @Valid @RequestBody SubmitCampaignApplicationRequest request
    ) {
        var application = applications.submit(campaignId, request);

        return ResponseEntity.created(URI.create(
                        "/api/v1/users/me/campaign-applications/"
                                + application.id()
                ))
                .header("Cache-Control", "no-store")
                .body(ApiResponse.ok(
                        "Application submitted successfully.",
                        application
                ));
    }

    @GetMapping("/api/v1/users/me/campaign-applications")
    public ResponseEntity<ApiResponse<Page<CreatorCampaignApplicationResponse>>> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(ApiResponse.ok(applications.listMine(page, size)));
    }

    @GetMapping("/api/v1/users/me/campaign-applications/{applicationId}")
    public ResponseEntity<ApiResponse<CreatorCampaignApplicationResponse>> detail(
            @PathVariable UUID applicationId
    ) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(ApiResponse.ok(applications.getMine(applicationId)));
    }

    @GetMapping("/api/v1/users/me/campaign-applications/{applicationId}/updates")
    public ResponseEntity<ApiResponse<List<CreatorApplicationUpdateResponse>>> updates(
            @PathVariable UUID applicationId
    ) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(ApiResponse.ok(applications.updates(applicationId)));
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\application\controller\PublisherCampaignApplicationController.java
```java
package com.adrovis.influencer.campaignmanagement.application.controller;

import com.adrovis.influencer.campaignmanagement.application.dto.PublisherApplicationDetailResponse;
import com.adrovis.influencer.campaignmanagement.application.dto.PublisherCampaignApplicationResponse;
import com.adrovis.influencer.campaignmanagement.application.dto.ReviewCampaignApplicationRequest;
import com.adrovis.influencer.campaignmanagement.application.enums.CampaignApplicationStatus;
import com.adrovis.influencer.campaignmanagement.application.service.PublisherCampaignApplicationService;
import com.adrovis.influencer.campaignmanagement.exception.CampaignManagementException;
import com.adrovis.influencer.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portal/campaigns/{campaignId}/applications")
@RequiredArgsConstructor
public class PublisherCampaignApplicationController {

    private final PublisherCampaignApplicationService applications;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PublisherCampaignApplicationResponse>>> list(
            @PathVariable UUID campaignId,
            @RequestParam(required = false) CampaignApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(ApiResponse.ok(
                        applications.list(campaignId, status, page, size)
                ));
    }

    @GetMapping("/{applicationId}")
    public ResponseEntity<ApiResponse<PublisherApplicationDetailResponse>> detail(
            @PathVariable UUID campaignId,
            @PathVariable UUID applicationId
    ) {
        var detail = applications.detail(campaignId, applicationId);

        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .eTag("\"" + detail.application().version() + "\"")
                .body(ApiResponse.ok(detail));
    }

    @PatchMapping("/{applicationId}/status")
    public ResponseEntity<ApiResponse<PublisherCampaignApplicationResponse>> review(
            @PathVariable UUID campaignId,
            @PathVariable UUID applicationId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ReviewCampaignApplicationRequest request
    ) {
        var result = applications.review(
                campaignId,
                applicationId,
                parseApplicationVersion(ifMatch),
                request
        );

        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .eTag("\"" + result.version() + "\"")
                .body(ApiResponse.ok(
                        "Application review saved successfully.",
                        result
                ));
    }

    private long parseApplicationVersion(String value) {
        if (value == null || !value.matches("\"(0|[1-9][0-9]*)\"")) {
            throw invalidVersion();
        }

        try {
            return Long.parseLong(value.substring(1, value.length() - 1));
        } catch (NumberFormatException exception) {
            throw invalidVersion();
        }
    }

    private CampaignManagementException invalidVersion() {
        return new CampaignManagementException(
                HttpStatus.BAD_REQUEST,
                "INVALID_VERSION_HEADER",
                "If-Match must contain a quoted application version."
        );
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\application\dto\ApplicationDecisionResponse.java
```java
package com.adrovis.influencer.campaignmanagement.application.dto;

import com.adrovis.influencer.campaignmanagement.application.entity.CampaignApplicationDecision;
import com.adrovis.influencer.campaignmanagement.application.enums.CampaignApplicationStatus;
import java.time.Instant;
import java.util.UUID;

public record ApplicationDecisionResponse(
        UUID id,
        UUID actorAccountId,
        CampaignApplicationStatus previousStatus,
        CampaignApplicationStatus newStatus,
        String internalNote,
        String creatorMessage,
        Instant createdAt
) {

    public static ApplicationDecisionResponse from(
            CampaignApplicationDecision decision
    ) {
        return new ApplicationDecisionResponse(
                decision.getId(),
                decision.getActorAccountId(),
                decision.getPreviousStatus(),
                decision.getNewStatus(),
                decision.getInternalNote(),
                decision.getCreatorMessage(),
                decision.getCreatedAt()
        );
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\application\dto\CreatorApplicationUpdateResponse.java
```java
package com.adrovis.influencer.campaignmanagement.application.dto;

import com.adrovis.influencer.campaignmanagement.application.entity.CampaignApplicationDecision;
import com.adrovis.influencer.campaignmanagement.application.enums.CampaignApplicationStatus;
import java.time.Instant;

public record CreatorApplicationUpdateResponse(
        CampaignApplicationStatus previousStatus,
        CampaignApplicationStatus newStatus,
        String message,
        Instant createdAt
) {

    public static CreatorApplicationUpdateResponse from(
            CampaignApplicationDecision decision
    ) {
        return new CreatorApplicationUpdateResponse(
                decision.getPreviousStatus(),
                decision.getNewStatus(),
                decision.getCreatorMessage(),
                decision.getCreatedAt()
        );
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\application\dto\CreatorCampaignApplicationResponse.java
```java
package com.adrovis.influencer.campaignmanagement.application.dto;

import com.adrovis.influencer.campaignmanagement.application.entity.ManagedCampaignApplication;
import com.adrovis.influencer.campaignmanagement.application.enums.CampaignApplicationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record CreatorCampaignApplicationResponse(
        UUID id,
        UUID campaignId,
        String campaignTitle,
        UUID formVersionId,
        int formVersion,
        CampaignApplicationStatus status,
        JsonNode answers,
        Instant submittedAt,
        Long version
) {

    public static CreatorCampaignApplicationResponse from(
            ManagedCampaignApplication application
    ) {
        return new CreatorCampaignApplicationResponse(
                application.getId(),
                application.getCampaignId(),
                application.getCampaignTitle(),
                application.getFormVersionId(),
                application.getFormVersionNumber(),
                application.getStatus(),
                application.getAnswers(),
                application.getSubmittedAt(),
                application.getVersion()
        );
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\application\dto\PublisherApplicationDetailResponse.java
```java
package com.adrovis.influencer.campaignmanagement.application.dto;

import com.adrovis.influencer.campaignmanagement.form.dto.ApplicationFormVersionResponse;
import java.util.List;

public record PublisherApplicationDetailResponse(
        PublisherCampaignApplicationResponse application,
        ApplicationFormVersionResponse form,
        List<ApplicationDecisionResponse> reviewHistory
) {
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\application\dto\PublisherCampaignApplicationResponse.java
```java
package com.adrovis.influencer.campaignmanagement.application.dto;

import com.adrovis.influencer.campaignmanagement.application.entity.ManagedCampaignApplication;
import com.adrovis.influencer.campaignmanagement.application.enums.CampaignApplicationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record PublisherCampaignApplicationResponse(
        UUID id,
        UUID campaignId,
        UUID creatorAccountId,
        String creatorEmail,
        UUID formVersionId,
        int formVersion,
        CampaignApplicationStatus status,
        JsonNode answers,
        Instant submittedAt,
        Long version
) {

    public static PublisherCampaignApplicationResponse from(
            ManagedCampaignApplication application
    ) {
        return new PublisherCampaignApplicationResponse(
                application.getId(),
                application.getCampaignId(),
                application.getCreatorAccountId(),
                application.getCreatorEmail(),
                application.getFormVersionId(),
                application.getFormVersionNumber(),
                application.getStatus(),
                application.getAnswers(),
                application.getSubmittedAt(),
                application.getVersion()
        );
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\application\dto\ReviewCampaignApplicationRequest.java
```java
package com.adrovis.influencer.campaignmanagement.application.dto;

import com.adrovis.influencer.campaignmanagement.application.enums.CampaignApplicationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewCampaignApplicationRequest(

        @NotNull
        CampaignApplicationStatus status,

        @Size(max = 3000)
        String internalNote,

        @Size(max = 2000)
        String creatorMessage
) {

    public ReviewCampaignApplicationRequest {
        internalNote = optional(internalNote);
        creatorMessage = optional(creatorMessage);
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\application\dto\SubmitCampaignApplicationRequest.java
```java
package com.adrovis.influencer.campaignmanagement.application.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

public record SubmitCampaignApplicationRequest(

        @NotNull
        UUID formVersionId,

        @NotNull
        @Size(max = 30)
        Map<@NotBlank @Size(max = 50) String, JsonNode> answers,

        @NotNull @Valid
        Consent consent
) {

    public record Consent(
            @NotNull
            @AssertTrue(message = "Application consent is required.")
            Boolean accepted,

            @NotBlank
            @Size(max = 64)
            String noticeVersion
    ) {
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\application\entity\CampaignApplicationDecision.java
```java
package com.adrovis.influencer.campaignmanagement.application.entity;

import com.adrovis.influencer.campaignmanagement.application.enums.CampaignApplicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "managed_campaign_application_decisions", schema = "public")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampaignApplicationDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "actor_account_id", nullable = false, updatable = false)
    private UUID actorAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, updatable = false, length = 30)
    private CampaignApplicationStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, updatable = false, length = 30)
    private CampaignApplicationStatus newStatus;

    @Column(name = "internal_note", updatable = false, length = 3000)
    private String internalNote;

    @Column(name = "creator_message", updatable = false, length = 2000)
    private String creatorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public CampaignApplicationDecision(
            UUID applicationId,
            UUID actorAccountId,
            CampaignApplicationStatus previousStatus,
            CampaignApplicationStatus newStatus,
            String internalNote,
            String creatorMessage,
            Instant createdAt
    ) {
        this.applicationId = Objects.requireNonNull(applicationId);
        this.actorAccountId = Objects.requireNonNull(actorAccountId);
        this.previousStatus = Objects.requireNonNull(previousStatus);
        this.newStatus = Objects.requireNonNull(newStatus);
        this.internalNote = internalNote;
        this.creatorMessage = creatorMessage;
        this.createdAt = Objects.requireNonNull(createdAt);
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\application\entity\ManagedCampaignApplication.java
```java
package com.adrovis.influencer.campaignmanagement.application.entity;

import com.adrovis.influencer.campaignmanagement.application.enums.CampaignApplicationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "managed_campaign_applications",
        schema = "public",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_managed_application_campaign_creator",
                columnNames = {"campaign_id", "creator_account_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ManagedCampaignApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "campaign_id", nullable = false, updatable = false)
    private UUID campaignId;

    @Column(name = "campaign_title", nullable = false, updatable = false, length = 200)
    private String campaignTitle;

    @Column(name = "creator_account_id", nullable = false, updatable = false)
    private UUID creatorAccountId;

    @Column(name = "creator_email", nullable = false, updatable = false, length = 254)
    private String creatorEmail;

    @Column(name = "form_version_id", nullable = false, updatable = false)
    private UUID formVersionId;

    @Column(name = "form_version_number", nullable = false, updatable = false)
    private int formVersionNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private JsonNode answers;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CampaignApplicationStatus status;

    @Column(name = "consent_notice_version", nullable = false, updatable = false, length = 64)
    private String consentNoticeVersion;

    @Column(name = "consent_notice_text", nullable = false, updatable = false, length = 1000)
    private String consentNoticeText;

    @Column(name = "consented_at", nullable = false, updatable = false)
    private Instant consentedAt;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public ManagedCampaignApplication(
            UUID campaignId,
            String campaignTitle,
            UUID creatorAccountId,
            String creatorEmail,
            UUID formVersionId,
            int formVersionNumber,
            JsonNode answers,
            String consentNoticeVersion,
            String consentNoticeText,
            Instant now
    ) {
        this.campaignId = Objects.requireNonNull(campaignId);
        this.campaignTitle = Objects.requireNonNull(campaignTitle);
        this.creatorAccountId = Objects.requireNonNull(creatorAccountId);
        this.creatorEmail = Objects.requireNonNull(creatorEmail);
        this.formVersionId = Objects.requireNonNull(formVersionId);

        if (formVersionNumber < 1 || answers == null || !answers.isObject()) {
            throw new IllegalArgumentException("Invalid application snapshot.");
        }

        this.formVersionNumber = formVersionNumber;
        this.answers = answers.deepCopy();
        this.consentNoticeVersion = Objects.requireNonNull(consentNoticeVersion);
        this.consentNoticeText = Objects.requireNonNull(consentNoticeText);
        this.consentedAt = Objects.requireNonNull(now);
        this.submittedAt = now;
        this.status = CampaignApplicationStatus.SUBMITTED;
    }

    public JsonNode getAnswers() {
        return answers == null ? null : answers.deepCopy();
    }

    /**
     * Reviewers cannot withdraw applications or return them to SUBMITTED.
     * Accepted/rejected/withdrawn applications are terminal in this version.
     */
    public boolean canReviewAs(CampaignApplicationStatus target) {
        if (target == null) {
            return false;
        }

        return switch (status) {
            case SUBMITTED ->
                    target == CampaignApplicationStatus.UNDER_REVIEW
                            || target == CampaignApplicationStatus.REJECTED;

            case UNDER_REVIEW ->
                    target == CampaignApplicationStatus.SHORTLISTED
                            || target == CampaignApplicationStatus.REJECTED;

            case SHORTLISTED ->
                    target == CampaignApplicationStatus.ACCEPTED
                            || target == CampaignApplicationStatus.REJECTED;

            case ACCEPTED, REJECTED, WITHDRAWN -> false;
        };
    }

    public void review(CampaignApplicationStatus target) {
        if (!canReviewAs(target)) {
            throw new IllegalStateException(
                    "Application status transition is not permitted."
            );
        }

        status = target;
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\application\enums\CampaignApplicationStatus.java
```java
package com.adrovis.influencer.campaignmanagement.application.enums;

public enum CampaignApplicationStatus {
    SUBMITTED,
    UNDER_REVIEW,
    SHORTLISTED,
    ACCEPTED,
    REJECTED,
    WITHDRAWN
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\application\exception\ApplicationAnswersInvalidException.java
```java
package com.adrovis.influencer.campaignmanagement.application.exception;

import com.adrovis.influencer.campaignmanagement.exception.CampaignManagementException;
import java.util.Map;
import org.springframework.http.HttpStatus;

public class ApplicationAnswersInvalidException
        extends CampaignManagementException {

    private final Map<String, String> fieldErrors;

    public ApplicationAnswersInvalidException(
            Map<String, String> fieldErrors
    ) {
        super(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "APPLICATION_VALIDATION_FAILED",
                "Please correct the application fields."
        );

        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\application\repository\CampaignApplicationDecisionRepository.java
```java
package com.adrovis.influencer.campaignmanagement.application.repository;

import com.adrovis.influencer.campaignmanagement.application.entity.CampaignApplicationDecision;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface CampaignApplicationDecisionRepository
        extends Repository<CampaignApplicationDecision, UUID> {

    CampaignApplicationDecision save(CampaignApplicationDecision decision);

    List<CampaignApplicationDecision>
    findAllByApplicationIdOrderByCreatedAtAscIdAsc(UUID applicationId);
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\application\repository\ManagedCampaignApplicationRepository.java
```java
package com.adrovis.influencer.campaignmanagement.application.repository;

import com.adrovis.influencer.campaignmanagement.application.entity.ManagedCampaignApplication;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.adrovis.influencer.campaignmanagement.application.enums.CampaignApplicationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ManagedCampaignApplicationRepository
        extends JpaRepository<ManagedCampaignApplication, UUID> {

    // Prevents duplicate applications by the same creator.
    boolean existsByCampaignIdAndCreatorAccountId(
            UUID campaignId,
            UUID creatorAccountId
    );

    // Lists only the authenticated creator's applications.
    Page<ManagedCampaignApplication> findAllByCreatorAccountId(
            UUID creatorAccountId,
            Pageable pageable
    );

    // Prevents creators from reading another creator's application.
    Optional<ManagedCampaignApplication> findByIdAndCreatorAccountId(
            UUID id,
            UUID creatorAccountId
    );

    Page<ManagedCampaignApplication> findAllByCampaignId(
            UUID campaignId,
            Pageable pageable
    );

    Page<ManagedCampaignApplication> findAllByCampaignIdAndStatus(
            UUID campaignId,
            CampaignApplicationStatus status,
            Pageable pageable
    );

    Optional<ManagedCampaignApplication> findByIdAndCampaignId(
            UUID id,
            UUID campaignId
    );

    /**
     * Caller must authorize and lock the parent campaign first.
     * Campaign ID prevents reviewing an application through the wrong campaign URL.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select application
        from ManagedCampaignApplication application
        where application.id = :applicationId
          and application.campaignId = :campaignId
        """)
    Optional<ManagedCampaignApplication> findForReview(
            @Param("applicationId") UUID applicationId,
            @Param("campaignId") UUID campaignId
    );
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\application\service\CreatorCampaignApplicationService.java
```java
package com.adrovis.influencer.campaignmanagement.application.service;

import com.adrovis.influencer.campaignmanagement.application.dto.CreatorCampaignApplicationResponse;
import com.adrovis.influencer.campaignmanagement.application.dto.SubmitCampaignApplicationRequest;
import com.adrovis.influencer.campaignmanagement.application.entity.ManagedCampaignApplication;
import com.adrovis.influencer.campaignmanagement.application.repository.CampaignApplicationDecisionRepository;
import com.adrovis.influencer.campaignmanagement.application.repository.ManagedCampaignApplicationRepository;
import com.adrovis.influencer.campaignmanagement.application.validation.CampaignApplicationAnswerValidator;
import com.adrovis.influencer.campaignmanagement.campaign.dto.CampaignDetailsRequest;
import com.adrovis.influencer.campaignmanagement.campaign.enums.CampaignStatus;
import com.adrovis.influencer.campaignmanagement.campaign.repository.ManagedCampaignRepository;
import com.adrovis.influencer.campaignmanagement.exception.CampaignManagementException;
import com.adrovis.influencer.campaignmanagement.form.dto.CreateApplicationFormVersionRequest;
import com.adrovis.influencer.campaignmanagement.form.enums.ApplicationMode;
import com.adrovis.influencer.campaignmanagement.form.repository.CampaignApplicationFormVersionRepository;
import com.adrovis.influencer.campaignmanagement.form.validation.ApplicationConsentNotice;
import com.adrovis.influencer.security.CurrentAccount;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import com.adrovis.influencer.campaignmanagement.application.dto.CreatorApplicationUpdateResponse;
import com.adrovis.influencer.campaignmanagement.application.repository.CampaignApplicationDecisionRepository;
import java.util.List;

@Service
@Validated
@RequiredArgsConstructor
@PreAuthorize("hasRole('CREATOR')")
public class CreatorCampaignApplicationService {

    private final ManagedCampaignRepository campaigns;
    private final CampaignApplicationFormVersionRepository forms;
    private final ManagedCampaignApplicationRepository applications;
    private final CampaignApplicationAnswerValidator answers;
    private final CurrentAccount currentAccount;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final CampaignApplicationDecisionRepository decisions;

    @Transactional("primaryTransactionManager")
    public CreatorCampaignApplicationResponse submit(
            @NotNull UUID campaignId,
            @NotNull @Valid SubmitCampaignApplicationRequest request
    ) {
        var account = currentAccount.require();

        var campaign = campaigns.findPublicForApplication(
                campaignId,
                CampaignStatus.PUBLISHED
        ).orElseThrow(() -> error(
                HttpStatus.NOT_FOUND,
                "CAMPAIGN_NOT_FOUND",
                "Campaign not found."
        ));

        if (campaign.getApplicationMode() != ApplicationMode.PLATFORM) {
            throw error(
                    HttpStatus.CONFLICT,
                    "EXTERNAL_APPLICATION_REQUIRED",
                    "This campaign uses an external application form."
            );
        }

        CampaignDetailsRequest details = readValidated(
                campaign.getDetails(),
                CampaignDetailsRequest.class
        );

        Instant now = Instant.now();
        var timeline = details.timeline();

        if (!timeline.closesAt().isAfter(now)
                || ("FIXED".equals(timeline.applicationMode())
                && !timeline.applicationDeadline().isAfter(now))) {
            throw error(
                    HttpStatus.CONFLICT,
                    "CAMPAIGN_NOT_OPEN",
                    "This campaign is not accepting applications."
            );
        }

        if (!request.formVersionId().equals(
                campaign.getPublishedApplicationFormVersionId()
        )) {
            throw error(
                    HttpStatus.CONFLICT,
                    "APPLICATION_FORM_CHANGED",
                    "Reload the campaign application form."
            );
        }

        if (applications.existsByCampaignIdAndCreatorAccountId(
                campaignId, account.id()
        )) {
            throw error(
                    HttpStatus.CONFLICT,
                    "APPLICATION_ALREADY_EXISTS",
                    "You have already applied to this campaign."
            );
        }

        if (!ApplicationConsentNotice.VERSION.equals(
                request.consent().noticeVersion()
        )) {
            throw error(
                    HttpStatus.CONFLICT,
                    "CONSENT_NOTICE_CHANGED",
                    "Reload and review the current application notice."
            );
        }

        var form = forms.findByIdAndCampaign_Id(
                request.formVersionId(),
                campaignId
        ).orElseThrow(this::dataUnavailable);

        CreateApplicationFormVersionRequest definition = readValidated(
                form.getDefinition(),
                CreateApplicationFormVersionRequest.class
        );

        JsonNode normalizedAnswers = answers.validate(
                definition.fields(),
                request.answers()
        );

        ManagedCampaignApplication application =
                new ManagedCampaignApplication(
                        campaignId,
                        campaign.getTitle(),
                        account.id(),
                        account.email(),
                        form.getId(),
                        form.getVersionNumber(),
                        normalizedAnswers,
                        ApplicationConsentNotice.VERSION,
                        ApplicationConsentNotice.TEXT,
                        now
                );

        return CreatorCampaignApplicationResponse.from(
                applications.saveAndFlush(application)
        );
    }

    @Transactional(
            value = "primaryTransactionManager",
            readOnly = true
    )
    public Page<CreatorCampaignApplicationResponse> listMine(
            int page,
            int size
    ) {
        var account = currentAccount.require();

        if (page < 0 || page > 1_000_000 || size < 1 || size > 100) {
            throw error(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAGINATION",
                    "Use page between 0 and 1000000 and size between 1 and 100."
            );
        }

        PageRequest pagination = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("submittedAt"),
                        Sort.Order.desc("id")
                )
        );

        return applications
                .findAllByCreatorAccountId(account.id(), pagination)
                .map(CreatorCampaignApplicationResponse::from);
    }

    @Transactional(
            value = "primaryTransactionManager",
            readOnly = true
    )
    public CreatorCampaignApplicationResponse getMine(
            @NotNull UUID applicationId
    ) {
        var account = currentAccount.require();

        return applications
                .findByIdAndCreatorAccountId(applicationId, account.id())
                .map(CreatorCampaignApplicationResponse::from)
                .orElseThrow(() -> error(
                        HttpStatus.NOT_FOUND,
                        "APPLICATION_NOT_FOUND",
                        "Application not found."
                ));
    }

    private <T> T readValidated(JsonNode snapshot, Class<T> type) {
        if (snapshot == null) {
            throw dataUnavailable();
        }

        try {
            T value = objectMapper.treeToValue(snapshot, type);

            if (value == null || !validator.validate(value).isEmpty()) {
                throw dataUnavailable();
            }

            return value;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw dataUnavailable();
        }
    }

    private CampaignManagementException dataUnavailable() {
        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "APPLICATION_DATA_UNAVAILABLE",
                "Application configuration is temporarily unavailable."
        );
    }

    private CampaignManagementException error(
            HttpStatus status,
            String code,
            String message
    ) {
        return new CampaignManagementException(status, code, message);
    }
    @Transactional(
            value = "primaryTransactionManager",
            readOnly = true
    )
    public List<CreatorApplicationUpdateResponse> updates(
            @NotNull UUID applicationId
    ) {
        var account = currentAccount.require();

        applications.findByIdAndCreatorAccountId(applicationId, account.id())
                .orElseThrow(() -> error(
                        HttpStatus.NOT_FOUND,
                        "APPLICATION_NOT_FOUND",
                        "Application not found."
                ));

        return decisions
                .findAllByApplicationIdOrderByCreatedAtAscIdAsc(applicationId)
                .stream()
                .map(CreatorApplicationUpdateResponse::from)
                .toList();
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\application\service\PublisherCampaignApplicationService.java
```java
package com.adrovis.influencer.campaignmanagement.application.service;

import com.adrovis.influencer.campaignmanagement.application.dto.ApplicationDecisionResponse;
import com.adrovis.influencer.campaignmanagement.application.dto.PublisherApplicationDetailResponse;
import com.adrovis.influencer.campaignmanagement.application.dto.PublisherCampaignApplicationResponse;
import com.adrovis.influencer.campaignmanagement.application.dto.ReviewCampaignApplicationRequest;
import com.adrovis.influencer.campaignmanagement.application.entity.CampaignApplicationDecision;
import com.adrovis.influencer.campaignmanagement.application.enums.CampaignApplicationStatus;
import com.adrovis.influencer.campaignmanagement.application.repository.CampaignApplicationDecisionRepository;
import com.adrovis.influencer.campaignmanagement.application.repository.ManagedCampaignApplicationRepository;
import com.adrovis.influencer.campaignmanagement.campaign.entity.ManagedCampaign;
import com.adrovis.influencer.campaignmanagement.campaign.enums.CampaignStatus;
import com.adrovis.influencer.campaignmanagement.campaign.repository.ManagedCampaignRepository;
import com.adrovis.influencer.campaignmanagement.exception.CampaignManagementException;
import com.adrovis.influencer.campaignmanagement.form.dto.ApplicationFormVersionResponse;
import com.adrovis.influencer.campaignmanagement.form.repository.CampaignApplicationFormVersionRepository;
import com.adrovis.influencer.security.CurrentAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('BRAND', 'AGENCY', 'ADMIN')")
public class PublisherCampaignApplicationService {

    private final ManagedCampaignRepository campaigns;
    private final ManagedCampaignApplicationRepository applications;
    private final CampaignApplicationDecisionRepository decisions;
    private final CampaignApplicationFormVersionRepository forms;
    private final CurrentAccount currentAccount;

    @Transactional(
            value = "primaryTransactionManager",
            readOnly = true
    )
    public Page<PublisherCampaignApplicationResponse> list(
            @NotNull UUID campaignId,
            CampaignApplicationStatus status,
            int page,
            int size
    ) {
        authorizeRead(campaignId);

        if (page < 0 || page > 1_000_000 || size < 1 || size > 100) {
            throw error(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAGINATION",
                    "Use page between 0 and 1000000 and size between 1 and 100."
            );
        }

        PageRequest pagination = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("submittedAt"),
                        Sort.Order.desc("id")
                )
        );

        var results = status == null
                ? applications.findAllByCampaignId(campaignId, pagination)
                : applications.findAllByCampaignIdAndStatus(
                campaignId, status, pagination
        );

        return results.map(PublisherCampaignApplicationResponse::from);
    }

    @Transactional(
            value = "primaryTransactionManager",
            readOnly = true
    )
    public PublisherApplicationDetailResponse detail(
            @NotNull UUID campaignId,
            @NotNull UUID applicationId
    ) {
        authorizeRead(campaignId);

        var application = applications
                .findByIdAndCampaignId(applicationId, campaignId)
                .orElseThrow(this::applicationNotFound);

        var form = forms.findByIdAndCampaign_Id(
                application.getFormVersionId(),
                campaignId
        ).orElseThrow(() -> error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "APPLICATION_DATA_UNAVAILABLE",
                "Application configuration is temporarily unavailable."
        ));

        var history = decisions
                .findAllByApplicationIdOrderByCreatedAtAscIdAsc(applicationId)
                .stream()
                .map(ApplicationDecisionResponse::from)
                .toList();

        return new PublisherApplicationDetailResponse(
                PublisherCampaignApplicationResponse.from(application),
                ApplicationFormVersionResponse.from(form),
                history
        );
    }

    /**
     * Always locks campaign first, application second.
     * Submission/lifecycle/review share the parent campaign lock.
     */
    @Transactional("primaryTransactionManager")
    public PublisherCampaignApplicationResponse review(
            @NotNull UUID campaignId,
            @NotNull UUID applicationId,
            @PositiveOrZero long expectedVersion,
            @NotNull @Valid ReviewCampaignApplicationRequest request
    ) {
        var account = currentAccount.require();
        boolean admin = account.roles().contains("ADMIN");

        ManagedCampaign campaign = (
                admin
                        ? campaigns.findForFormUpdate(campaignId)
                        : campaigns.findOwnedForFormUpdate(
                        campaignId, account.id()
                )
        ).orElseThrow(this::campaignNotFound);

        authorizePublisherRole(campaign, account);

        if (campaign.getPublisherProfile().getArchivedAt() != null) {
            throw error(
                    HttpStatus.CONFLICT,
                    "PUBLISHER_ARCHIVED",
                    "The campaign publisher is archived."
            );
        }

        // Closed campaigns may still need their existing applicants reviewed.
        if (campaign.getStatus() != CampaignStatus.PUBLISHED
                && campaign.getStatus() != CampaignStatus.PAUSED
                && campaign.getStatus() != CampaignStatus.CLOSED) {
            throw error(
                    HttpStatus.CONFLICT,
                    "CAMPAIGN_NOT_REVIEWABLE",
                    "Applications cannot be reviewed in this campaign state."
            );
        }

        var application = applications.findForReview(
                applicationId,
                campaignId
        ).orElseThrow(this::applicationNotFound);

        if (application.getVersion() == null
                || application.getVersion().longValue() != expectedVersion) {
            throw error(
                    HttpStatus.PRECONDITION_FAILED,
                    "VERSION_CONFLICT",
                    "Application changed. Reload it before reviewing."
            );
        }

        if (!application.canReviewAs(request.status())) {
            throw error(
                    HttpStatus.CONFLICT,
                    "INVALID_STATUS_TRANSITION",
                    "This application status transition is not allowed."
            );
        }

        var previousStatus = application.getStatus();

        application.review(request.status());

        decisions.save(new CampaignApplicationDecision(
                application.getId(),
                account.id(),
                previousStatus,
                application.getStatus(),
                request.internalNote(),
                request.creatorMessage(),
                Instant.now()
        ));

        // Both the status update and decision record commit or roll back together.
        return PublisherCampaignApplicationResponse.from(
                applications.saveAndFlush(application)
        );
    }

    /**
     * Authorized owners/admins retain historical read access after archiving.
     * No other account can obtain applicant data by knowing the campaign ID.
     */
    private void authorizeRead(UUID campaignId) {
        var account = currentAccount.require();

        ManagedCampaign campaign = campaigns.findById(campaignId)
                .orElseThrow(this::campaignNotFound);

        boolean admin = account.roles().contains("ADMIN");

        if (!admin && !campaign.getPublisherProfile()
                .getOwnerAccountId().equals(account.id())) {
            throw campaignNotFound();
        }

        authorizePublisherRole(campaign, account);
    }

    private void authorizePublisherRole(
            ManagedCampaign campaign,
            CurrentAccount.Identity account
    ) {
        String publisherRole = campaign.getPublisherProfile()
                .getPublisherType()
                .name();

        if (!account.roles().contains("ADMIN")
                && !account.roles().contains(publisherRole)) {
            throw new AccessDeniedException(
                    "Your account cannot manage this publisher."
            );
        }
    }

    private CampaignManagementException campaignNotFound() {
        return error(
                HttpStatus.NOT_FOUND,
                "CAMPAIGN_NOT_FOUND",
                "Campaign not found."
        );
    }

    private CampaignManagementException applicationNotFound() {
        return error(
                HttpStatus.NOT_FOUND,
                "APPLICATION_NOT_FOUND",
                "Application not found."
        );
    }

    private CampaignManagementException error(
            HttpStatus status,
            String code,
            String message
    ) {
        return new CampaignManagementException(status, code, message);
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\application\validation\CampaignApplicationAnswerValidator.java
```java
package com.adrovis.influencer.campaignmanagement.application.validation;

import com.adrovis.influencer.campaignmanagement.application.exception.ApplicationAnswersInvalidException;
import com.adrovis.influencer.campaignmanagement.form.dto.ApplicationFieldDefinitionRequest;
import com.adrovis.influencer.campaignmanagement.form.enums.ApplicationFieldType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import java.math.BigDecimal;
import java.net.URI;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CampaignApplicationAnswerValidator {

    private static final BigDecimal MAX_NUMBER =
            new BigDecimal("1000000000000000");

    private final Validator validator;

    /**
     * Rejects unknown fields and validates values using the pinned definition.
     * Returns a normalized snapshot safe to persist.
     */
    public ObjectNode validate(
            List<ApplicationFieldDefinitionRequest> fields,
            Map<String, JsonNode> answers
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        ObjectNode normalized = JsonNodeFactory.instance.objectNode();
        Set<String> knownKeys = new HashSet<>();

        for (var field : fields) {
            knownKeys.add(field.key());
        }

        for (String key : answers.keySet()) {
            if (!knownKeys.contains(key)) {
                errors.put(key, "This field is not part of the application form.");
            }
        }

        for (var field : fields) {
            JsonNode value = answers.get(field.key());

            if (missing(value)) {
                if (Boolean.TRUE.equals(field.required())) {
                    errors.put(field.key(), "This field is required.");
                }
                continue;
            }

            if (!valid(field, value)) {
                errors.put(
                        field.key(),
                        "Enter a valid value matching this field's requirements."
                );
                continue;
            }

            if (value.isTextual()) {
                normalized.put(field.key(), value.textValue().trim());
            } else {
                normalized.set(field.key(), value.deepCopy());
            }
        }

        if (!errors.isEmpty()) {
            throw new ApplicationAnswersInvalidException(errors);
        }

        return normalized;
    }

    private boolean missing(JsonNode value) {
        return value == null
                || value.isNull()
                || (value.isTextual() && value.textValue().isBlank())
                || (value.isArray() && value.isEmpty());
    }

    private boolean valid(
            ApplicationFieldDefinitionRequest field,
            JsonNode value
    ) {
        return switch (field.type()) {
            case SHORT_TEXT, LONG_TEXT ->
                    textWithinLimit(field, value);

            case URL ->
                    textWithinLimit(field, value)
                            && httpsUrl(value.textValue().trim());

            case EMAIL ->
                    textWithinLimit(field, value)
                            && validator.validate(
                            new EmailValue(value.textValue().trim())
                    ).isEmpty();

            case PHONE ->
                    textWithinLimit(field, value)
                            && value.textValue().trim()
                            .matches("^\\+[1-9][0-9]{7,14}$");

            case INTEGER ->
                    value.isIntegralNumber() && numeric(field, value);

            case DECIMAL ->
                    value.isNumber() && numeric(field, value);

            case BOOLEAN ->
                    value.isBoolean();

            case SINGLE_SELECT ->
                    value.isTextual()
                            && field.options().contains(value.textValue());

            case MULTI_SELECT ->
                    validMultipleSelection(field, value);
        };
    }

    private boolean textWithinLimit(
            ApplicationFieldDefinitionRequest field,
            JsonNode value
    ) {
        if (!value.isTextual()) {
            return false;
        }

        int limit = field.maxLength() == null
                ? defaultLength(field.type())
                : field.maxLength();

        return value.textValue().trim().length() <= limit;
    }

    private int defaultLength(ApplicationFieldType type) {
        return switch (type) {
            case SHORT_TEXT -> 200;
            case LONG_TEXT -> 5000;
            case URL -> 2048;
            case EMAIL -> 254;
            case PHONE -> 32;
            default -> 0;
        };
    }

    private boolean numeric(
            ApplicationFieldDefinitionRequest field,
            JsonNode value
    ) {
        try {
            BigDecimal number = value.decimalValue().stripTrailingZeros();

            if (number.abs().compareTo(MAX_NUMBER) > 0
                    || number.scale() > 6) {
                return false;
            }

            return (field.min() == null
                    || number.compareTo(field.min()) >= 0)
                    && (field.max() == null
                    || number.compareTo(field.max()) <= 0);
        } catch (ArithmeticException | NumberFormatException exception) {
            return false;
        }
    }

    private boolean validMultipleSelection(
            ApplicationFieldDefinitionRequest field,
            JsonNode value
    ) {
        if (!value.isArray() || value.size() > field.options().size()) {
            return false;
        }

        Set<String> selected = new HashSet<>();

        for (JsonNode item : value) {
            if (!item.isTextual()
                    || !field.options().contains(item.textValue())
                    || !selected.add(item.textValue())) {
                return false;
            }
        }

        return true;
    }

    private boolean httpsUrl(String value) {
        try {
            URI uri = URI.create(value);

            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getRawUserInfo() == null
                    && uri.getRawFragment() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public record EmailValue(@Email String value) {
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\campaign\controller\CampaignLifecycleController.java
```java
package com.adrovis.influencer.campaignmanagement.campaign.controller;

import com.adrovis.influencer.campaignmanagement.campaign.dto.ManagedCampaignResponse;
import com.adrovis.influencer.campaignmanagement.campaign.service.CampaignLifecycleService;
import com.adrovis.influencer.campaignmanagement.campaign.service.CampaignLifecycleService.Action;
import com.adrovis.influencer.dto.ApiResponse;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portal/campaigns")
@RequiredArgsConstructor
public class CampaignLifecycleController {

    private final CampaignLifecycleService lifecycle;

    @PostMapping("/{campaignId}/{action:publish|pause|resume|close}")
    public ResponseEntity<ApiResponse<ManagedCampaignResponse>> transition(
            @PathVariable UUID campaignId,
            @PathVariable String action,
            @RequestHeader("If-Match") String ifMatch
    ) {
        Action requestedAction = Action.valueOf(
                action.toUpperCase(Locale.ROOT)
        );

        ManagedCampaignResponse campaign = lifecycle.transition(
                campaignId,
                CampaignVersionHeader.parse(ifMatch),
                requestedAction
        );

        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .eTag("\"" + campaign.version() + "\"")
                .body(ApiResponse.ok(
                        "Campaign status updated successfully.",
                        campaign
                ));
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\campaign\controller\CampaignVersionHeader.java
```java
package com.adrovis.influencer.campaignmanagement.campaign.controller;

import com.adrovis.influencer.campaignmanagement.exception.CampaignManagementException;
import org.springframework.http.HttpStatus;

public final class CampaignVersionHeader {

    private CampaignVersionHeader() {
    }

    public static long parse(String value) {
        if (value == null || !value.matches("\"(0|[1-9][0-9]*)\"")) {
            throw invalid();
        }

        try {
            return Long.parseLong(
                    value.substring(1, value.length() - 1)
            );
        } catch (NumberFormatException exception) {
            throw invalid();
        }
    }

    private static CampaignManagementException invalid() {
        return new CampaignManagementException(
                HttpStatus.BAD_REQUEST,
                "INVALID_VERSION_HEADER",
                "If-Match must contain a quoted non-negative campaign version."
        );
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\campaign\controller\ManagedCampaignController.java
```java
package com.adrovis.influencer.campaignmanagement.campaign.controller;

import com.adrovis.influencer.campaignmanagement.campaign.dto.CreateManagedCampaignRequest;
import com.adrovis.influencer.campaignmanagement.campaign.dto.ManagedCampaignResponse;
import com.adrovis.influencer.campaignmanagement.campaign.service.ManagedCampaignService;
import com.adrovis.influencer.dto.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portal/campaigns")
@RequiredArgsConstructor
public class ManagedCampaignController {

    private final ManagedCampaignService campaigns;

    /**
     * Creates a draft under an authorized publisher profile.
     * Ownership and role checks are enforced by the service.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ManagedCampaignResponse>> create(
            @Valid @RequestBody CreateManagedCampaignRequest request
    ) {
        ManagedCampaignResponse campaign = campaigns.create(request);

        URI location = URI.create(
                "/api/v1/portal/campaigns/" + campaign.id()
        );

        return ResponseEntity.created(location)
                .body(ApiResponse.ok(
                        "Campaign draft created successfully.",
                        campaign
                ));
    }

    /**
     * Admin receives all non-archived campaigns.
     * Brand and agency accounts receive only their own campaigns.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ManagedCampaignResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
                ApiResponse.ok(campaigns.list(page, size))
        );
    }

    /**
     * Returns management details only to the campaign owner or an admin.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ManagedCampaignResponse>> detail(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                ApiResponse.ok(campaigns.getById(id))
        );
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\campaign\controller\ManagedCampaignEditController.java
```java
package com.adrovis.influencer.campaignmanagement.campaign.controller;

import com.adrovis.influencer.campaignmanagement.campaign.dto.CampaignDetailsRequest;
import com.adrovis.influencer.campaignmanagement.campaign.dto.ManagedCampaignResponse;
import com.adrovis.influencer.campaignmanagement.campaign.dto.UpdateCampaignDraftRequest;
import com.adrovis.influencer.campaignmanagement.campaign.service.ManagedCampaignEditService;
import com.adrovis.influencer.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portal/campaigns")
@RequiredArgsConstructor
public class ManagedCampaignEditController {

    private final ManagedCampaignEditService campaigns;

    @PutMapping("/{campaignId}/draft")
    public ResponseEntity<ApiResponse<ManagedCampaignResponse>> updateDraft(
            @PathVariable UUID campaignId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody UpdateCampaignDraftRequest request
    ) {
        return response(
                "Campaign draft updated successfully.",
                campaigns.updateDraft(
                        campaignId,
                        CampaignVersionHeader.parse(ifMatch),
                        request
                )
        );
    }

    @PutMapping("/{campaignId}/details")
    public ResponseEntity<ApiResponse<ManagedCampaignResponse>> updateDetails(
            @PathVariable UUID campaignId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody CampaignDetailsRequest request
    ) {
        return response(
                "Campaign details updated successfully.",
                campaigns.updateDetails(
                        campaignId,
                        CampaignVersionHeader.parse(ifMatch),
                        request
                )
        );
    }

    @DeleteMapping("/{campaignId}")
    public ResponseEntity<ApiResponse<ManagedCampaignResponse>> archive(
            @PathVariable UUID campaignId,
            @RequestHeader("If-Match") String ifMatch
    ) {
        return response(
                "Campaign archived successfully.",
                campaigns.archive(
                        campaignId,
                        CampaignVersionHeader.parse(ifMatch)
                )
        );
    }

    private ResponseEntity<ApiResponse<ManagedCampaignResponse>> response(
            String message,
            ManagedCampaignResponse campaign
    ) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .eTag("\"" + campaign.version() + "\"")
                .body(ApiResponse.ok(message, campaign));
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\campaign\controller\PublicManagedCampaignController.java
```java
package com.adrovis.influencer.campaignmanagement.campaign.controller;

import com.adrovis.influencer.campaignmanagement.campaign.dto.PublicManagedCampaignResponse;
import com.adrovis.influencer.campaignmanagement.campaign.service.PublicManagedCampaignService;
import com.adrovis.influencer.campaignmanagement.form.dto.ApplicationFormVersionResponse;
import com.adrovis.influencer.dto.ApiResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campaigns/managed")
@RequiredArgsConstructor
public class PublicManagedCampaignController {

    private final PublicManagedCampaignService campaigns;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PublicManagedCampaignResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(ApiResponse.ok(campaigns.list(page, size)));
    }

    @GetMapping("/{campaignId}")
    public ResponseEntity<ApiResponse<PublicManagedCampaignResponse>> detail(
            @PathVariable UUID campaignId
    ) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(ApiResponse.ok(campaigns.getById(campaignId)));
    }

    @GetMapping("/{campaignId}/application-form")
    public ResponseEntity<ApiResponse<ApplicationFormVersionResponse>> form(
            @PathVariable UUID campaignId
    ) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(ApiResponse.ok(
                        campaigns.getPublishedForm(campaignId)
                ));
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\campaign\dto\CampaignDetailsRequest.java
```java
package com.adrovis.influencer.campaignmanagement.campaign.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;

public record CampaignDetailsRequest(

        @NotNull @Valid
        AdvertisedBrand advertisedBrand,

        @NotNull
        @Size(min = 1, max = 10)
        List<@NotNull @Valid CampaignImage> images,

        @NotNull
        @Size(min = 1, max = 10)
        List<@NotBlank @Size(max = 80) String> categories,

        @Valid
        Product product,

        @NotNull @Valid
        Compensation compensation,

        @NotNull @Valid
        Eligibility eligibility,

        @NotNull @Valid
        Timeline timeline,

        @NotNull
        @Size(min = 1, max = 30)
        List<@NotNull @Valid Deliverable> deliverables,

        @NotNull
        @Size(max = 50)
        List<@NotBlank @Size(max = 1000) String> guidelines,

        @Size(max = 2000)
        String adRights
) {

    public record AdvertisedBrand(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2048) String logoUrl,
            @Size(max = 2048) String website,
            @Size(max = 200) String instagramUrl
    ) {
        @AssertTrue(message = "Brand URLs must be valid HTTPS URLs.")
        public boolean isUrlConfigurationValid() {
            return optionalHttps(logoUrl)
                    && optionalHttps(website)
                    && optionalHttps(instagramUrl);
        }
    }

    public record CampaignImage(
            @NotBlank @Size(max = 2048) String url,
            @NotBlank @Size(max = 200) String alt
    ) {
        @AssertTrue(message = "Image URL must use HTTPS.")
        public boolean isUrlValid() {
            return url != null && !url.isBlank() && optionalHttps(url);
        }
    }

    public record Product(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description
    ) {
    }

    public record Compensation(
            @NotBlank @Pattern(regexp = "INR") String currency,
            @NotNull Boolean productProvided,
            @DecimalMin("0.00")
            @Digits(integer = 12, fraction = 2)
            BigDecimal productValue,
            @DecimalMin("0.00")
            @Digits(integer = 12, fraction = 2)
            BigDecimal cashAmount,
            @Size(max = 2000) String description
    ) {
    }

    public record Eligibility(
            @NotNull
            @Size(min = 1, max = 3)
            List<
                    @NotBlank
                    @Pattern(regexp = "INSTAGRAM|YOUTUBE|OTHER")
                            String
                    > platforms,

            @NotNull @Min(0) @Max(1_000_000_000)
            Long minimumFollowers,

            @Min(0) @Max(1_000_000_000)
            Long maximumFollowers,

            @NotNull
            @Size(max = 4)
            List<
                    @NotBlank
                    @Pattern(regexp =
                            "MALE|FEMALE|OTHER|PREFER_NOT_TO_SAY")
                            String
                    > genders,

            @NotNull @Valid
            Locations locations
    ) {
        @AssertTrue(message =
                "Maximum followers must not be below minimum followers.")
        public boolean isFollowerRangeValid() {
            return minimumFollowers == null
                    || maximumFollowers == null
                    || maximumFollowers >= minimumFollowers;
        }
    }

    public record Locations(
            @NotBlank
            @Pattern(regexp = "COUNTRY|SELECTED_LOCATIONS")
            String scope,

            @NotNull
            @Size(min = 1, max = 20)
            List<@Pattern(regexp = "[A-Z]{2}") String> countryCodes,

            @NotNull
            @Size(max = 100)
            List<@NotBlank @Size(max = 100) String> states,

            @NotNull
            @Size(max = 200)
            List<@NotBlank @Size(max = 100) String> cities
    ) {
        @AssertTrue(message =
                "COUNTRY must not restrict states/cities; "
                        + "SELECTED_LOCATIONS requires a state or city.")
        public boolean isScopeConfigurationValid() {
            if (scope == null || states == null || cities == null) {
                return true;
            }

            return switch (scope) {
                case "COUNTRY" -> states.isEmpty() && cities.isEmpty();
                case "SELECTED_LOCATIONS" ->
                        !states.isEmpty() || !cities.isEmpty();
                default -> false;
            };
        }
    }

    public record Timeline(
            @NotBlank
            @Pattern(regexp = "ROLLING|FIXED")
            String applicationMode,

            Instant applicationDeadline,
            Instant contentSubmissionAt,
            Instant contentLiveAt,

            @NotNull
            Instant closesAt,

            @NotBlank @Size(max = 80)
            String timezone
    ) {
        @AssertTrue(message = "Timeline dates or application deadline are invalid.")
        public boolean isDateOrderValid() {
            if (applicationMode == null || closesAt == null) {
                return true;
            }

            if ("FIXED".equals(applicationMode)
                    && applicationDeadline == null) {
                return false;
            }

            if ("ROLLING".equals(applicationMode)
                    && applicationDeadline != null) {
                return false;
            }

            if (afterClose(applicationDeadline)
                    || afterClose(contentSubmissionAt)
                    || afterClose(contentLiveAt)) {
                return false;
            }

            return contentSubmissionAt == null
                    || contentLiveAt == null
                    || !contentSubmissionAt.isAfter(contentLiveAt);
        }

        @AssertTrue(message = "Enter a supported timezone.")
        public boolean isTimezoneValid() {
            if (timezone == null || timezone.isBlank()) {
                return true;
            }

            try {
                java.time.ZoneId.of(timezone);
                return true;
            } catch (java.time.DateTimeException exception) {
                return false;
            }
        }

        private boolean afterClose(Instant value) {
            return value != null && value.isAfter(closesAt);
        }
    }

    public record Deliverable(
            @NotBlank
            @Pattern(regexp = "INSTAGRAM|YOUTUBE|OTHER")
            String platform,

            @NotBlank
            @Pattern(regexp = "REEL|STORY|POST|VIDEO|SHORT|OTHER")
            String type,

            @NotNull @Min(1) @Max(100)
            Integer quantity,

            @NotBlank @Size(max = 1000)
            String description
    ) {
    }

    private static boolean optionalHttps(String value) {
        if (value == null) {
            return true;
        }

        if (value.isBlank() || !value.equals(value.trim())) {
            return false;
        }

        try {
            URI uri = URI.create(value);

            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getRawUserInfo() == null
                    && uri.getRawFragment() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\campaign\dto\CreateManagedCampaignRequest.java
```java
package com.adrovis.influencer.campaignmanagement.campaign.dto;

import com.adrovis.influencer.campaignmanagement.campaign.enums.CollaborationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateManagedCampaignRequest(

        @NotNull(message = "Publisher profile is required.")
        UUID publisherProfileId,

        @NotBlank(message = "Campaign title is required.")
        @Size(
                max = 200,
                message = "Campaign title must not exceed 200 characters."
        )
        String title,

        @Size(
                max = 10000,
                message = "Description must not exceed 10000 characters."
        )
        String description,

        @NotNull(message = "Collaboration type is required.")
        CollaborationType collaborationType
) {

    public CreateManagedCampaignRequest {
        title = title == null ? null : title.trim();
        description = description == null ? null : description.trim();
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\campaign\dto\ManagedCampaignResponse.java
```java
package com.adrovis.influencer.campaignmanagement.campaign.dto;

import com.adrovis.influencer.campaignmanagement.campaign.entity.ManagedCampaign;
import com.adrovis.influencer.campaignmanagement.campaign.enums.CampaignStatus;
import com.adrovis.influencer.campaignmanagement.campaign.enums.CollaborationType;
import com.adrovis.influencer.campaignmanagement.publisher.enums.PublisherType;
import java.time.Instant;
import java.util.UUID;
import com.adrovis.influencer.campaignmanagement.form.enums.ApplicationMode;
import com.fasterxml.jackson.databind.JsonNode;

public record ManagedCampaignResponse(
        UUID id,
        PublisherSummary publisher,
        UUID createdByAccountId,
        String title,
        String description,
        JsonNode details,
        CollaborationType collaborationType,
        CampaignStatus status,
        ApplicationMode applicationMode,
        String externalApplicationUrl,
        UUID publishedApplicationFormVersionId,
        Long version,
        Instant publishedAt,
        Instant closedAt,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Management response only.
     * Public campaign browsing will use a separate, restricted DTO.
     */
    public static ManagedCampaignResponse from(ManagedCampaign campaign) {
        var publisher = campaign.getPublisherProfile();

        return new ManagedCampaignResponse(
                campaign.getId(),
                new PublisherSummary(
                        publisher.getId(),
                        publisher.getPublisherType(),
                        publisher.getDisplayName(),
                        publisher.getOwnerAccountId()
                ),
                campaign.getCreatedByAccountId(),
                campaign.getTitle(),
                campaign.getDescription(),
                campaign.getDetails(),
                campaign.getCollaborationType(),
                campaign.getStatus(),
                campaign.getApplicationMode(),
                campaign.getExternalApplicationUrl(),
                campaign.getPublishedApplicationFormVersionId(),
                campaign.getVersion(),
                campaign.getPublishedAt(),
                campaign.getClosedAt(),
                campaign.getArchivedAt(),
                campaign.getCreatedAt(),
                campaign.getUpdatedAt()
        );
    }

    public record PublisherSummary(
            UUID id,
            PublisherType type,
            String displayName,
            UUID ownerAccountId
    ) {
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\campaign\dto\PublicManagedCampaignResponse.java
```java
package com.adrovis.influencer.campaignmanagement.campaign.dto;

import com.adrovis.influencer.campaignmanagement.campaign.enums.CollaborationType;
import com.adrovis.influencer.campaignmanagement.form.enums.ApplicationMode;
import com.adrovis.influencer.campaignmanagement.publisher.enums.PublisherType;
import java.time.Instant;
import java.util.UUID;

public record PublicManagedCampaignResponse(
        UUID id,
        String title,
        String description,
        PublisherSummary publisher,
        CollaborationType collaborationType,
        CampaignDetailsRequest details,
        ApplicationMode applicationMode,
        boolean acceptingApplications,
        Instant publishedAt
) {

    public record PublisherSummary(
            UUID id,
            PublisherType type,
            String displayName
    ) {
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\campaign\dto\UpdateCampaignDraftRequest.java
```java
package com.adrovis.influencer.campaignmanagement.campaign.dto;

import com.adrovis.influencer.campaignmanagement.campaign.enums.CollaborationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateCampaignDraftRequest(

        @NotBlank
        @Size(max = 200)
        String title,

        @Size(max = 10000)
        String description,

        @NotNull
        CollaborationType collaborationType
) {

    public UpdateCampaignDraftRequest {
        title = title == null ? null : title.trim();
        description = description == null ? null : description.trim();
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\campaign\entity\ManagedCampaign.java
```java
package com.adrovis.influencer.campaignmanagement.campaign.entity;

import com.adrovis.influencer.campaignmanagement.campaign.enums.CampaignStatus;
import com.adrovis.influencer.campaignmanagement.campaign.enums.CollaborationType;
import com.adrovis.influencer.campaignmanagement.publisher.entity.PublisherProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.adrovis.influencer.campaignmanagement.form.enums.ApplicationMode;
import com.adrovis.influencer.campaignmanagement.form.validation.GoogleFormUrl;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


@Entity
@Table(name = "managed_campaigns", schema = "public")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ManagedCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // The publisher owns the campaign.
    // No cascading deletion: deleting a campaign must not delete its publisher.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "publisher_profile_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_managed_campaign_publisher")
    )
    private PublisherProfile publisherProfile;

    // Records the actual creator, including an admin acting for a publisher.
    // Derived from authenticated identity, never trusted from request JSON.
    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    private UUID createdByAccountId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 10000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "collaboration_type", nullable = false, length = 20)
    private CollaborationType collaborationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CampaignStatus status;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Creates a draft. Publishing will require a separate validated action.
     * The service must authorize access to the selected publisher first.
     */
    public ManagedCampaign(
            PublisherProfile publisherProfile,
            UUID createdByAccountId,
            String title,
            String description,
            CollaborationType collaborationType
    ) {
        this.publisherProfile = Objects.requireNonNull(
                publisherProfile, "Publisher profile is required."
        );

        Objects.requireNonNull(
                publisherProfile.getId(), "Publisher must already exist."
        );

        if (publisherProfile.getArchivedAt() != null) {
            throw new IllegalArgumentException(
                    "An archived publisher cannot create campaigns."
            );
        }

        this.createdByAccountId = Objects.requireNonNull(
                createdByAccountId, "Creating account is required."
        );

        this.collaborationType = Objects.requireNonNull(
                collaborationType, "Collaboration type is required."
        );

        if (title == null
                || title.isBlank()
                || title.trim().length() > 200) {
            throw new IllegalArgumentException(
                    "Title must contain 1 to 200 characters."
            );
        }

        String cleanDescription = description == null
                ? null
                : description.trim();

        if (cleanDescription != null && cleanDescription.length() > 10000) {
            throw new IllegalArgumentException(
                    "Description must not exceed 10000 characters."
            );
        }

        this.title = title.trim();
        this.description = cleanDescription;
        this.status = CampaignStatus.DRAFT;
    }

    @PrePersist
    private void initializeTimestamps() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void updateTimestamp() {
        updatedAt = Instant.now();
    }

    // New campaigns use Brandcollabz applications unless configured otherwise.
    @Enumerated(EnumType.STRING)
    @Column(name = "application_mode", nullable = false, length = 20)
    private ApplicationMode applicationMode = ApplicationMode.PLATFORM;

    // Private management field. Do not expose directly in public campaign details.
    @Column(name = "external_application_url", length = 2048)
    private String externalApplicationUrl;

    /**
     * Changes application settings only while the campaign is a draft.
     * The service must authorize the caller and check the edit version first.
     */
    public void configureApplication(
            ApplicationMode mode,
            String externalUrl
    ) {
        Objects.requireNonNull(mode, "Application mode is required.");

        if (status != CampaignStatus.DRAFT || archivedAt != null) {
            throw new IllegalStateException(
                    "Application settings can only change on draft campaigns."
            );
        }

        String normalizedUrl = externalUrl == null || externalUrl.isBlank()
                ? null
                : externalUrl.trim();

        if (mode == ApplicationMode.PLATFORM && normalizedUrl != null) {
            throw new IllegalArgumentException(
                    "Platform applications must not include an external URL."
            );
        }

        if (mode == ApplicationMode.GOOGLE_FORM
                && !GoogleFormUrl.isValid(normalizedUrl)) {
            throw new IllegalArgumentException(
                    "A supported HTTPS Google Forms URL is required."
            );
        }

        applicationMode = mode;
        externalApplicationUrl = normalizedUrl;
    }
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    private JsonNode details;

    /**
     * Replaces the complete details snapshot on a draft.
     * The service validates the DTO, ownership and expected version first.
     */
    public void configureDetails(JsonNode definition) {
        if (status != CampaignStatus.DRAFT || archivedAt != null) {
            throw new IllegalStateException(
                    "Campaign details can only change on draft campaigns."
            );
        }

        if (definition == null || !definition.isObject()) {
            throw new IllegalArgumentException(
                    "Campaign details must be a JSON object."
            );
        }

        details = definition.deepCopy();
    }

    public JsonNode getDetails() {
        return details == null ? null : details.deepCopy();
    }
    /**
     * Replaces only the editable basic fields of a draft.
     * Ownership and version checks belong to the service.
     */
    public void updateDraft(
            String title,
            String description,
            CollaborationType collaborationType
    ) {
        if (status != CampaignStatus.DRAFT || archivedAt != null) {
            throw new IllegalStateException(
                    "Only draft campaigns can be edited."
            );
        }

        String cleanTitle = title == null ? null : title.trim();
        String cleanDescription = description == null
                ? null
                : description.trim();

        if (cleanTitle == null
                || cleanTitle.isBlank()
                || cleanTitle.length() > 200) {
            throw new IllegalArgumentException("Invalid campaign title.");
        }

        if (cleanDescription != null && cleanDescription.length() > 10000) {
            throw new IllegalArgumentException("Campaign description is too long.");
        }

        this.title = cleanTitle;
        this.description = cleanDescription;
        this.collaborationType = Objects.requireNonNull(collaborationType);
    }

    /**
     * Soft-deletes the campaign while preserving ownership and application history.
     */
    public void archive(Instant now) {
        Objects.requireNonNull(now);

        if (archivedAt != null || status == CampaignStatus.ARCHIVED) {
            return;
        }

        if ((status == CampaignStatus.PUBLISHED
                || status == CampaignStatus.PAUSED)
                && closedAt == null) {
            closedAt = now;
        }

        status = CampaignStatus.ARCHIVED;
        archivedAt = now;
    }
    // Pins the exact immutable form used when the campaign was published.
// Null for Google Forms campaigns.
    @Column(name = "published_application_form_version_id")
    private UUID publishedApplicationFormVersionId;

    /**
     * Publishing is permitted only after the service validates the complete
     * campaign and resolves the application form/destination.
     */
    public void publish(Instant now, UUID formVersionId) {
        Objects.requireNonNull(now);

        if (status != CampaignStatus.DRAFT || archivedAt != null) {
            throw new IllegalStateException("Only a draft can be published.");
        }

        if (applicationMode == ApplicationMode.PLATFORM
                && formVersionId == null) {
            throw new IllegalArgumentException(
                    "Platform publication requires a form version."
            );
        }

        if (applicationMode == ApplicationMode.GOOGLE_FORM
                && formVersionId != null) {
            throw new IllegalArgumentException(
                    "Google Forms publication must not pin an internal form."
            );
        }

        if (applicationMode == null) {
            throw new IllegalArgumentException(
                    "Application mode is required."
            );
        }

        publishedApplicationFormVersionId = formVersionId;
        publishedAt = now;
        status = CampaignStatus.PUBLISHED;
    }

    public void pause() {
        if (status != CampaignStatus.PUBLISHED || archivedAt != null) {
            throw new IllegalStateException(
                    "Only a published campaign can be paused."
            );
        }

        status = CampaignStatus.PAUSED;
    }

    public void resume() {
        if (status != CampaignStatus.PAUSED || archivedAt != null) {
            throw new IllegalStateException(
                    "Only a paused campaign can be resumed."
            );
        }

        // Preserve the original publication timestamp and form version.
        status = CampaignStatus.PUBLISHED;
    }

    public void close(Instant now) {
        Objects.requireNonNull(now);

        if (archivedAt != null
                || (status != CampaignStatus.PUBLISHED
                && status != CampaignStatus.PAUSED)) {
            throw new IllegalStateException(
                    "Only published or paused campaigns can be closed."
            );
        }

        closedAt = now;
        status = CampaignStatus.CLOSED;
    }


}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\campaign\enums\CampaignStatus.java
```java
package com.adrovis.influencer.campaignmanagement.campaign.enums;

public enum CampaignStatus {
    DRAFT,
    PUBLISHED,
    PAUSED,
    CLOSED,
    ARCHIVED
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\campaign\enums\CollaborationType.java
```java
package com.adrovis.influencer.campaignmanagement.campaign.enums;

public enum CollaborationType {
    BARTER,
    PAID,
    HYBRID
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\campaign\repository\ManagedCampaignRepository.java
```java
package com.adrovis.influencer.campaignmanagement.campaign.repository;

import com.adrovis.influencer.campaignmanagement.campaign.entity.ManagedCampaign;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.adrovis.influencer.campaignmanagement.campaign.enums.CampaignStatus;

public interface ManagedCampaignRepository
        extends JpaRepository<ManagedCampaign, UUID> {

    // Brand/agency dashboard: campaigns owned by this account only.
    @EntityGraph(attributePaths = "publisherProfile")
    Page<ManagedCampaign>
    findAllByPublisherProfile_OwnerAccountIdAndArchivedAtIsNull(
            UUID ownerAccountId,
            Pageable pageable
    );

    // Admin dashboard: all non-archived campaigns.
    @EntityGraph(attributePaths = "publisherProfile")
    Page<ManagedCampaign> findAllByArchivedAtIsNull(Pageable pageable);

    // Ownership-scoped retrieval for brand/agency operations.
    @EntityGraph(attributePaths = "publisherProfile")
    Optional<ManagedCampaign>
    findByIdAndPublisherProfile_OwnerAccountIdAndArchivedAtIsNull(
            UUID id,
            UUID ownerAccountId
    );

    // Admin retrieval. The service must explicitly authorize the admin first.
    @EntityGraph(attributePaths = "publisherProfile")
    Optional<ManagedCampaign> findByIdAndArchivedAtIsNull(UUID id);

    /**
     * Locks an owner's campaign while its next form version is allocated.
     * The ownership restriction is part of the database query.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select campaign
        from ManagedCampaign campaign
        where campaign.id = :campaignId
          and campaign.archivedAt is null
          and campaign.publisherProfile.ownerAccountId = :ownerAccountId
        """)
    Optional<ManagedCampaign> findOwnedForFormUpdate(
            @Param("campaignId") UUID campaignId,
            @Param("ownerAccountId") UUID ownerAccountId
    );

    /**
     * Admin-only service path: locks any non-archived campaign.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select campaign
        from ManagedCampaign campaign
        where campaign.id = :campaignId
          and campaign.archivedAt is null
        """)
    Optional<ManagedCampaign> findForFormUpdate(
            @Param("campaignId") UUID campaignId
    );


    /**
     * Public listing excludes drafts, paused/closed/archived campaigns
     * and campaigns whose publisher has been archived.
     */
    @EntityGraph(attributePaths = "publisherProfile")
    @Query("""
        select campaign
        from ManagedCampaign campaign
        where campaign.status = :status
          and campaign.archivedAt is null
          and campaign.publishedAt is not null
          and campaign.publisherProfile.archivedAt is null
        """)
    Page<ManagedCampaign> findPublicCampaigns(
            @Param("status") CampaignStatus status,
            Pageable pageable
    );

    /**
     * Public lookup must not reveal draft or unrelated management data.
     */
    @EntityGraph(attributePaths = "publisherProfile")
    @Query("""
        select campaign
        from ManagedCampaign campaign
        where campaign.id = :campaignId
          and campaign.status = :status
          and campaign.archivedAt is null
          and campaign.publishedAt is not null
          and campaign.publisherProfile.archivedAt is null
        """)
    Optional<ManagedCampaign> findPublicCampaign(
            @Param("campaignId") UUID campaignId,
            @Param("status") CampaignStatus status
    );

    /**
     * Serializes submissions with lifecycle changes on the same campaign.
     * Draft/private/archived campaigns remain concealed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select campaign
        from ManagedCampaign campaign
        where campaign.id = :campaignId
          and campaign.status = :status
          and campaign.archivedAt is null
          and campaign.publishedAt is not null
          and campaign.publisherProfile.archivedAt is null
        """)
    Optional<ManagedCampaign> findPublicForApplication(
            @Param("campaignId") UUID campaignId,
            @Param("status") CampaignStatus status
    );
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\campaign\service\CampaignLifecycleService.java
```java
package com.adrovis.influencer.campaignmanagement.campaign.service;

import com.adrovis.influencer.campaignmanagement.campaign.dto.ManagedCampaignResponse;
import com.adrovis.influencer.campaignmanagement.campaign.entity.ManagedCampaign;
import com.adrovis.influencer.campaignmanagement.campaign.enums.CampaignStatus;
import com.adrovis.influencer.campaignmanagement.campaign.repository.ManagedCampaignRepository;
import com.adrovis.influencer.campaignmanagement.exception.CampaignManagementException;
import com.adrovis.influencer.campaignmanagement.form.service.CampaignApplicationReadinessValidator;
import com.adrovis.influencer.security.CurrentAccount;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
@PreAuthorize("hasAnyRole('BRAND', 'AGENCY', 'ADMIN')")
public class CampaignLifecycleService {

    private final ManagedCampaignRepository campaigns;
    private final CurrentAccount currentAccount;
    private final CampaignPublicationValidator publicationValidator;
    private final CampaignApplicationReadinessValidator applicationValidator;

    public enum Action {
        PUBLISH,
        PAUSE,
        RESUME,
        CLOSE
    }

    /**
     * Authorization, parent lock, version check, readiness and transition
     * occur in one transaction.
     */
    @Transactional("primaryTransactionManager")
    public ManagedCampaignResponse transition(
            @NotNull UUID campaignId,
            @PositiveOrZero long expectedVersion,
            @NotNull Action action
    ) {
        var account = currentAccount.require();
        boolean admin = account.roles().contains("ADMIN");

        ManagedCampaign campaign = (
                admin
                        ? campaigns.findForFormUpdate(campaignId)
                        : campaigns.findOwnedForFormUpdate(
                        campaignId, account.id()
                )
        ).orElseThrow(() -> error(
                HttpStatus.NOT_FOUND,
                "CAMPAIGN_NOT_FOUND",
                "Campaign not found."
        ));

        String publisherRole = campaign.getPublisherProfile()
                .getPublisherType()
                .name();

        if (!admin && !account.roles().contains(publisherRole)) {
            throw new AccessDeniedException(
                    "Your account cannot manage this publisher."
            );
        }

        if (campaign.getVersion() == null
                || campaign.getVersion().longValue() != expectedVersion) {
            throw error(
                    HttpStatus.PRECONDITION_FAILED,
                    "VERSION_CONFLICT",
                    "Campaign changed. Reload it before saving."
            );
        }

        Instant now = Instant.now();

        switch (action) {
            case PUBLISH -> {
                requireStatus(campaign, CampaignStatus.DRAFT);

                var readiness = validateReadiness(campaign, now);

                campaign.publish(now, readiness.formVersionId());
            }
            case PAUSE -> {
                requireStatus(campaign, CampaignStatus.PUBLISHED);
                campaign.pause();
            }
            case RESUME -> {
                requireStatus(campaign, CampaignStatus.PAUSED);

                var readiness = validateReadiness(campaign, now);

                // Never silently change the form accepted at publication.
                if (!Objects.equals(
                        campaign.getPublishedApplicationFormVersionId(),
                        readiness.formVersionId()
                )) {
                    throw error(
                            HttpStatus.CONFLICT,
                            "PUBLISHED_FORM_CHANGED",
                            "The published application form does not match."
                    );
                }

                campaign.resume();
            }
            case CLOSE -> {
                requireStatus(
                        campaign,
                        CampaignStatus.PUBLISHED,
                        CampaignStatus.PAUSED
                );

                campaign.close(now);
            }
        }

        return ManagedCampaignResponse.from(
                campaigns.saveAndFlush(campaign)
        );
    }

    private CampaignApplicationReadinessValidator.Readiness validateReadiness(
            ManagedCampaign campaign,
            Instant now
    ) {
        publicationValidator.validate(campaign, now);

        try {
            return applicationValidator.validateForPublication(campaign);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() != 409) {
                throw exception;
            }

            throw error(
                    HttpStatus.CONFLICT,
                    "APPLICATION_NOT_READY",
                    "Configure a valid application form or Google Forms destination."
            );
        }
    }

    private void requireStatus(
            ManagedCampaign campaign,
            CampaignStatus... allowed
    ) {
        if (!List.of(allowed).contains(campaign.getStatus())) {
            throw error(
                    HttpStatus.CONFLICT,
                    "INVALID_STATUS_TRANSITION",
                    "This action is not allowed for the current campaign status."
            );
        }
    }

    private CampaignManagementException error(
            HttpStatus status,
            String code,
            String message
    ) {
        return new CampaignManagementException(status, code, message);
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\campaign\service\CampaignPublicationValidator.java
```java
package com.adrovis.influencer.campaignmanagement.campaign.service;

import com.adrovis.influencer.campaignmanagement.campaign.dto.CampaignDetailsRequest;
import com.adrovis.influencer.campaignmanagement.campaign.entity.ManagedCampaign;
import com.adrovis.influencer.campaignmanagement.exception.CampaignManagementException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CampaignPublicationValidator {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public CampaignPublicationValidator(
            ObjectMapper objectMapper,
            Validator validator
    ) {
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    /**
     * Validates campaign business details before publication or resumption.
     * Authentication, ownership, locking and lifecycle checks remain
     * the responsibility of CampaignLifecycleService.
     */
    public void validate(ManagedCampaign campaign, Instant now) {
        if (campaign == null || now == null) {
            throw new IllegalArgumentException(
                    "Campaign and validation time are required."
            );
        }

        if (campaign.getPublisherProfile() == null
                || campaign.getPublisherProfile().getArchivedAt() != null) {
            throw notReady("An active publisher profile is required.");
        }

        if (campaign.getTitle() == null
                || campaign.getTitle().isBlank()
                || campaign.getTitle().length() > 200) {
            throw notReady(
                    "A campaign title containing 1 to 200 characters is required."
            );
        }

        if (campaign.getDescription() == null
                || campaign.getDescription().isBlank()
                || campaign.getDescription().length() > 10000) {
            throw notReady(
                    "A campaign description containing 1 to 10000 characters is required."
            );
        }

        if (campaign.getCollaborationType() == null) {
            throw notReady("Collaboration type is required.");
        }

        CampaignDetailsRequest details = readAndValidateDetails(
                campaign.getDetails()
        );

        validateCompensation(campaign, details);
        validateTimeline(details.timeline(), now);
        validateDeliverablePlatforms(details);
    }

    /**
     * Converts the stored JSON snapshot and applies all nested
     * Jakarta Bean Validation rules from CampaignDetailsRequest.
     */
    private CampaignDetailsRequest readAndValidateDetails(JsonNode json) {
        if (json == null || !json.isObject()) {
            throw notReady(
                    "Complete the campaign details before publishing."
            );
        }

        CampaignDetailsRequest details;

        try {
            details = objectMapper.treeToValue(
                    json,
                    CampaignDetailsRequest.class
            );
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw notReady("Stored campaign details are invalid.");
        }

        if (details == null || !validator.validate(details).isEmpty()) {
            throw notReady(
                    "Campaign details contain missing or invalid fields."
            );
        }

        return details;
    }

    private void validateCompensation(
            ManagedCampaign campaign,
            CampaignDetailsRequest details
    ) {
        CampaignDetailsRequest.Compensation compensation =
                details.compensation();

        boolean productProvided = Boolean.TRUE.equals(
                compensation.productProvided()
        );

        boolean productHasValue = positive(
                compensation.productValue()
        );

        boolean cashHasValue = positive(
                compensation.cashAmount()
        );

        if (productProvided
                && (details.product() == null || !productHasValue)) {
            throw notReady(
                    "A gifted product requires product details and a positive value."
            );
        }

        if (!productProvided && productHasValue) {
            throw notReady(
                    "Product value cannot be set when no product is provided."
            );
        }

        switch (campaign.getCollaborationType()) {
            case BARTER -> {
                if (!productProvided || !productHasValue || cashHasValue) {
                    throw notReady(
                            "Barter campaigns require a gifted product and no cash payment."
                    );
                }
            }

            case PAID -> {
                if (!cashHasValue) {
                    throw notReady(
                            "Paid campaigns require a positive cash amount."
                    );
                }
            }

            case HYBRID -> {
                if (!cashHasValue || !productProvided || !productHasValue) {
                    throw notReady(
                            "Hybrid campaigns require cash and a gifted product."
                    );
                }
            }
        }
    }

    private void validateTimeline(
            CampaignDetailsRequest.Timeline timeline,
            Instant now
    ) {
        // Nested Bean Validation has already checked required fields.
        if (!timeline.closesAt().isAfter(now)) {
            throw notReady(
                    "The campaign closing date must be in the future."
            );
        }

        Instant deadline = timeline.applicationDeadline();
        Instant submission = timeline.contentSubmissionAt();
        Instant live = timeline.contentLiveAt();

        if ("FIXED".equals(timeline.applicationMode())
                && (deadline == null || !deadline.isAfter(now))) {
            throw notReady(
                    "A fixed application deadline must be in the future."
            );
        }

        if (submission != null && !submission.isAfter(now)) {
            throw notReady(
                    "Content submission must be in the future."
            );
        }

        if (live != null && !live.isAfter(now)) {
            throw notReady(
                    "Content publication must be in the future."
            );
        }

        if (deadline != null) {
            if (submission != null && deadline.isAfter(submission)) {
                throw notReady(
                        "Application deadline cannot follow content submission."
                );
            }

            if (live != null && deadline.isAfter(live)) {
                throw notReady(
                        "Application deadline cannot follow content publication."
                );
            }
        }
    }

    private void validateDeliverablePlatforms(
            CampaignDetailsRequest details
    ) {
        for (CampaignDetailsRequest.Deliverable deliverable
                : details.deliverables()) {

            if (!details.eligibility().platforms()
                    .contains(deliverable.platform())) {
                throw notReady(
                        "Deliverable platforms must match campaign eligibility."
                );
            }
        }
    }

    private boolean positive(BigDecimal amount) {
        return amount != null && amount.signum() > 0;
    }

    private CampaignManagementException notReady(String message) {
        return new CampaignManagementException(
                HttpStatus.CONFLICT,
                "CAMPAIGN_NOT_READY",
                message
        );
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\campaign\service\ManagedCampaignEditService.java
```java
package com.adrovis.influencer.campaignmanagement.campaign.service;

import com.adrovis.influencer.campaignmanagement.campaign.dto.CampaignDetailsRequest;
import com.adrovis.influencer.campaignmanagement.campaign.dto.ManagedCampaignResponse;
import com.adrovis.influencer.campaignmanagement.campaign.dto.UpdateCampaignDraftRequest;
import com.adrovis.influencer.campaignmanagement.campaign.entity.ManagedCampaign;
import com.adrovis.influencer.campaignmanagement.campaign.enums.CampaignStatus;
import com.adrovis.influencer.campaignmanagement.campaign.repository.ManagedCampaignRepository;
import com.adrovis.influencer.campaignmanagement.exception.CampaignManagementException;
import com.adrovis.influencer.security.CurrentAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('BRAND', 'AGENCY', 'ADMIN')")
@Transactional("primaryTransactionManager")
public class ManagedCampaignEditService {

    private final ManagedCampaignRepository campaigns;
    private final CurrentAccount currentAccount;
    private final ObjectMapper objectMapper;

    public ManagedCampaignResponse updateDraft(
            @NotNull UUID campaignId,
            @PositiveOrZero long expectedVersion,
            @NotNull @Valid UpdateCampaignDraftRequest request
    ) {
        ManagedCampaign campaign = lockAuthorized(
                campaignId, expectedVersion
        );

        requireEditableDraft(campaign);

        campaign.updateDraft(
                request.title(),
                request.description(),
                request.collaborationType()
        );

        return save(campaign);
    }

    /**
     * Replaces the entire details snapshot.
     * Does not change application mode or immutable form versions.
     */
    public ManagedCampaignResponse updateDetails(
            @NotNull UUID campaignId,
            @PositiveOrZero long expectedVersion,
            @NotNull @Valid CampaignDetailsRequest request
    ) {
        ManagedCampaign campaign = lockAuthorized(
                campaignId, expectedVersion
        );

        requireEditableDraft(campaign);

        campaign.configureDetails(objectMapper.valueToTree(request));

        return save(campaign);
    }

    /**
     * Archives instead of deleting campaign/application records.
     * An archived publisher does not prevent its owner/admin archiving a campaign.
     */
    public ManagedCampaignResponse archive(
            @NotNull UUID campaignId,
            @PositiveOrZero long expectedVersion
    ) {
        ManagedCampaign campaign = lockAuthorized(
                campaignId, expectedVersion
        );

        campaign.archive(Instant.now());

        return save(campaign);
    }

    private ManagedCampaign lockAuthorized(
            UUID campaignId,
            long expectedVersion
    ) {
        var account = currentAccount.require();
        boolean admin = account.roles().contains("ADMIN");

        ManagedCampaign campaign = (
                admin
                        ? campaigns.findForFormUpdate(campaignId)
                        : campaigns.findOwnedForFormUpdate(
                        campaignId, account.id()
                )
        ).orElseThrow(() -> new CampaignManagementException(
                HttpStatus.NOT_FOUND,
                "CAMPAIGN_NOT_FOUND",
                "Campaign not found."
        ));

        String publisherRole = campaign.getPublisherProfile()
                .getPublisherType()
                .name();

        if (!admin && !account.roles().contains(publisherRole)) {
            throw new AccessDeniedException(
                    "Your account cannot manage this publisher."
            );
        }

        if (campaign.getVersion() == null
                || campaign.getVersion().longValue() != expectedVersion) {
            throw new CampaignManagementException(
                    HttpStatus.PRECONDITION_FAILED,
                    "VERSION_CONFLICT",
                    "Campaign changed. Reload it before saving."
            );
        }

        return campaign;
    }

    private void requireEditableDraft(ManagedCampaign campaign) {
        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new CampaignManagementException(
                    HttpStatus.CONFLICT,
                    "CAMPAIGN_NOT_EDITABLE",
                    "Only draft campaigns can be edited."
            );
        }

        if (campaign.getPublisherProfile().getArchivedAt() != null) {
            throw new CampaignManagementException(
                    HttpStatus.CONFLICT,
                    "PUBLISHER_ARCHIVED",
                    "The campaign publisher is archived."
            );
        }
    }

    private ManagedCampaignResponse save(ManagedCampaign campaign) {
        return ManagedCampaignResponse.from(
                campaigns.saveAndFlush(campaign)
        );
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\campaign\service\ManagedCampaignService.java
```java
package com.adrovis.influencer.campaignmanagement.campaign.service;

import com.adrovis.influencer.campaignmanagement.campaign.dto.CreateManagedCampaignRequest;
import com.adrovis.influencer.campaignmanagement.campaign.dto.ManagedCampaignResponse;
import com.adrovis.influencer.campaignmanagement.campaign.entity.ManagedCampaign;
import com.adrovis.influencer.campaignmanagement.campaign.repository.ManagedCampaignRepository;
import com.adrovis.influencer.campaignmanagement.publisher.entity.PublisherProfile;
import com.adrovis.influencer.campaignmanagement.publisher.repository.PublisherProfileRepository;
import com.adrovis.influencer.security.CurrentAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
public class ManagedCampaignService {

    private final ManagedCampaignRepository campaigns;
    private final PublisherProfileRepository publishers;
    private final CurrentAccount currentAccount;

    /**
     * Brand/agency accounts create under their own active publisher profile.
     * Admins may create on behalf of any active publisher.
     * Every campaign starts as DRAFT.
     */
    @PreAuthorize("hasAnyRole('BRAND', 'AGENCY', 'ADMIN')")
    @Transactional("primaryTransactionManager")
    public ManagedCampaignResponse create(
            @NotNull @Valid CreateManagedCampaignRequest request
    ) {
        var account = currentAccount.require();

        PublisherProfile publisher = resolvePublisherForCreation(
                request.publisherProfileId(),
                account
        );

        ManagedCampaign campaign = new ManagedCampaign(
                publisher,
                account.id(),
                request.title(),
                request.description(),
                request.collaborationType()
        );

        ManagedCampaign saved = campaigns.saveAndFlush(campaign);

        return ManagedCampaignResponse.from(saved);
    }

    /**
     * Admins see all non-archived campaigns.
     * Other publishers see only campaigns owned by their authenticated account.
     */
    @PreAuthorize("hasAnyRole('BRAND', 'AGENCY', 'ADMIN')")
    @Transactional(
            value = "primaryTransactionManager",
            readOnly = true
    )
    public Page<ManagedCampaignResponse> list(int page, int size) {
        var account = currentAccount.require();

        PageRequest pagination = pagination(page, size);

        Page<ManagedCampaign> results;

        if (isAdmin(account)) {
            results = campaigns.findAllByArchivedAtIsNull(pagination);
        } else {
            results = campaigns
                    .findAllByPublisherProfile_OwnerAccountIdAndArchivedAtIsNull(
                            account.id(),
                            pagination
                    );
        }

        // Map inside the transaction; entities never leave the service.
        return results.map(ManagedCampaignResponse::from);
    }

    /**
     * Returns management details only to the owner or an admin.
     * Unrelated and missing campaigns both return 404.
     */
    @PreAuthorize("hasAnyRole('BRAND', 'AGENCY', 'ADMIN')")
    @Transactional(
            value = "primaryTransactionManager",
            readOnly = true
    )
    public ManagedCampaignResponse getById(@NotNull UUID campaignId) {
        var account = currentAccount.require();

        ManagedCampaign campaign;

        if (isAdmin(account)) {
            campaign = campaigns.findByIdAndArchivedAtIsNull(campaignId)
                    .orElseThrow(this::campaignNotFound);
        } else {
            campaign = campaigns
                    .findByIdAndPublisherProfile_OwnerAccountIdAndArchivedAtIsNull(
                            campaignId,
                            account.id()
                    )
                    .orElseThrow(this::campaignNotFound);
        }

        return ManagedCampaignResponse.from(campaign);
    }

    /**
     * Publisher ID is a selection, not proof of ownership.
     * Non-admin lookups include the authenticated owner in the database query.
     */
    private PublisherProfile resolvePublisherForCreation(
            UUID publisherProfileId,
            CurrentAccount.Identity account
    ) {
        if (isAdmin(account)) {
            return publishers
                    .findByIdAndArchivedAtIsNull(publisherProfileId)
                    .orElseThrow(this::publisherNotFound);
        }

        PublisherProfile publisher = publishers
                .findByIdAndOwnerAccountIdAndArchivedAtIsNull(
                        publisherProfileId,
                        account.id()
                )
                .orElseThrow(this::publisherNotFound);

        // Ownership alone does not restore a role removed from the account.
        if (!account.roles().contains(publisher.getPublisherType().name())) {
            throw new AccessDeniedException(
                    "Your account cannot create campaigns for this publisher type."
            );
        }

        return publisher;
    }

    /**
     * Uses bounded pagination and fixed sorting instead of accepting
     * arbitrary database property names from request input.
     */
    private PageRequest pagination(int page, int size) {
        if (page < 0 || page > 1_000_000 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Use page between 0 and 1000000 and size between 1 and 100."
            );
        }

        Sort sort = Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        );

        return PageRequest.of(page, size, sort);
    }

    private boolean isAdmin(CurrentAccount.Identity account) {
        return account.roles().contains("ADMIN");
    }

    private ResponseStatusException publisherNotFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Publisher profile not found."
        );
    }

    private ResponseStatusException campaignNotFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Campaign not found."
        );
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\campaign\service\PublicManagedCampaignService.java
```java
package com.adrovis.influencer.campaignmanagement.campaign.service;

import com.adrovis.influencer.campaignmanagement.campaign.dto.CampaignDetailsRequest;
import com.adrovis.influencer.campaignmanagement.campaign.dto.PublicManagedCampaignResponse;
import com.adrovis.influencer.campaignmanagement.campaign.entity.ManagedCampaign;
import com.adrovis.influencer.campaignmanagement.campaign.enums.CampaignStatus;
import com.adrovis.influencer.campaignmanagement.campaign.repository.ManagedCampaignRepository;
import com.adrovis.influencer.campaignmanagement.exception.CampaignManagementException;
import com.adrovis.influencer.campaignmanagement.form.dto.ApplicationFormVersionResponse;
import com.adrovis.influencer.campaignmanagement.form.enums.ApplicationMode;
import com.adrovis.influencer.campaignmanagement.form.repository.CampaignApplicationFormVersionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(
        value = "primaryTransactionManager",
        readOnly = true
)
public class PublicManagedCampaignService {

    private final ManagedCampaignRepository campaigns;
    private final CampaignApplicationFormVersionRepository forms;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    /**
     * Returns public published campaigns.
     * A published campaign whose deadline has passed remains viewable,
     * but acceptingApplications is false.
     */
    public Page<PublicManagedCampaignResponse> list(int page, int size) {
        if (page < 0 || page > 1_000_000 || size < 1 || size > 100) {
            throw error(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAGINATION",
                    "Use page between 0 and 1000000 and size between 1 and 100."
            );
        }

        PageRequest pagination = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("publishedAt"),
                        Sort.Order.desc("id")
                )
        );

        Instant now = Instant.now();

        return campaigns
                .findPublicCampaigns(CampaignStatus.PUBLISHED, pagination)
                .map(campaign -> toPublicResponse(campaign, now));
    }

    public PublicManagedCampaignResponse getById(
            @NotNull UUID campaignId
    ) {
        return toPublicResponse(
                findPublic(campaignId),
                Instant.now()
        );
    }

    /**
     * Creator receives the exact immutable form pinned at publication.
     * Owner/admin management endpoints still use their separate form API.
     */
    @PreAuthorize("hasRole('CREATOR')")
    public ApplicationFormVersionResponse getPublishedForm(
            @NotNull UUID campaignId
    ) {
        ManagedCampaign campaign = findPublic(campaignId);
        CampaignDetailsRequest details = readDetails(campaign);

        if (!acceptingApplications(details, Instant.now())) {
            throw error(
                    HttpStatus.CONFLICT,
                    "CAMPAIGN_NOT_OPEN",
                    "This campaign is not accepting applications."
            );
        }

        if (campaign.getApplicationMode() != ApplicationMode.PLATFORM) {
            throw error(
                    HttpStatus.CONFLICT,
                    "EXTERNAL_APPLICATION_REQUIRED",
                    "This campaign uses an external application form."
            );
        }

        UUID formId = campaign.getPublishedApplicationFormVersionId();

        if (formId == null) {
            throw applicationUnavailable();
        }

        var form = forms
                .findByIdAndCampaign_Id(formId, campaign.getId())
                .orElseThrow(this::applicationUnavailable);

        return ApplicationFormVersionResponse.from(form);
    }

    private ManagedCampaign findPublic(UUID campaignId) {
        return campaigns
                .findPublicCampaign(campaignId, CampaignStatus.PUBLISHED)
                .orElseThrow(() -> error(
                        HttpStatus.NOT_FOUND,
                        "CAMPAIGN_NOT_FOUND",
                        "Campaign not found."
                ));
    }

    private PublicManagedCampaignResponse toPublicResponse(
            ManagedCampaign campaign,
            Instant now
    ) {
        CampaignDetailsRequest details = readDetails(campaign);
        var publisher = campaign.getPublisherProfile();

        return new PublicManagedCampaignResponse(
                campaign.getId(),
                campaign.getTitle(),
                campaign.getDescription(),
                new PublicManagedCampaignResponse.PublisherSummary(
                        publisher.getId(),
                        publisher.getPublisherType(),
                        publisher.getDisplayName()
                ),
                campaign.getCollaborationType(),
                details,
                campaign.getApplicationMode(),
                acceptingApplications(details, now),
                campaign.getPublishedAt()
        );
    }

    private CampaignDetailsRequest readDetails(ManagedCampaign campaign) {
        if (campaign.getDetails() == null) {
            throw applicationUnavailable();
        }

        try {
            CampaignDetailsRequest details = objectMapper.treeToValue(
                    campaign.getDetails(),
                    CampaignDetailsRequest.class
            );

            if (details == null || !validator.validate(details).isEmpty()) {
                throw applicationUnavailable();
            }

            return details;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw applicationUnavailable();
        }
    }

    private boolean acceptingApplications(
            CampaignDetailsRequest details,
            Instant now
    ) {
        var timeline = details.timeline();

        if (!timeline.closesAt().isAfter(now)) {
            return false;
        }

        return !"FIXED".equals(timeline.applicationMode())
                || timeline.applicationDeadline().isAfter(now);
    }

    private CampaignManagementException applicationUnavailable() {
        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "CAMPAIGN_DATA_UNAVAILABLE",
                "Campaign application data is temporarily unavailable."
        );
    }

    private CampaignManagementException error(
            HttpStatus status,
            String code,
            String message
    ) {
        return new CampaignManagementException(status, code, message);
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\dashboard\controller\CampaignDashboardController.java
```java
package com.adrovis.influencer.campaignmanagement.dashboard.controller;

import com.adrovis.influencer.campaignmanagement.application.dto.PublisherCampaignApplicationResponse;
import com.adrovis.influencer.campaignmanagement.application.enums.CampaignApplicationStatus;
import com.adrovis.influencer.campaignmanagement.campaign.dto.ManagedCampaignResponse;
import com.adrovis.influencer.campaignmanagement.campaign.enums.CampaignStatus;
import com.adrovis.influencer.campaignmanagement.dashboard.dto.CampaignDashboardOverview;
import com.adrovis.influencer.campaignmanagement.dashboard.service.CampaignDashboardService;
import com.adrovis.influencer.campaignmanagement.publisher.enums.PublisherType;
import com.adrovis.influencer.dto.ApiResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portal/dashboard")
@RequiredArgsConstructor
public class CampaignDashboardController {

    private final CampaignDashboardService dashboard;

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<CampaignDashboardOverview>> overview() {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(ApiResponse.ok(dashboard.overview()));
    }

    @GetMapping("/campaigns")
    public ResponseEntity<ApiResponse<Page<ManagedCampaignResponse>>> campaigns(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CampaignStatus status,
            @RequestParam(required = false) PublisherType publisherType,
            @RequestParam(required = false) UUID publisherProfileId,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(ApiResponse.ok(dashboard.campaigns(
                        search,
                        status,
                        publisherType,
                        publisherProfileId,
                        includeArchived,
                        page,
                        size
                )));
    }

    @GetMapping("/campaigns/{campaignId}/applications")
    public ResponseEntity<ApiResponse<Page<PublisherCampaignApplicationResponse>>> applicants(
            @PathVariable UUID campaignId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CampaignApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(ApiResponse.ok(dashboard.applicants(
                        campaignId, search, status, page, size
                )));
    }

    @GetMapping("/campaigns/{campaignId}/applications/export")
    public ResponseEntity<byte[]> export(
            @PathVariable UUID campaignId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CampaignApplicationStatus status
    ) {
        var file = dashboard.export(campaignId, search, status);

        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(
                                        file.filename(),
                                        StandardCharsets.UTF_8
                                )
                                .build()
                                .toString()
                )
                .contentType(new MediaType(
                        "text", "csv", StandardCharsets.UTF_8
                ))
                .contentLength(file.content().length)
                .body(file.content());
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\dashboard\dto\ApplicantCsvExport.java
```java
package com.adrovis.influencer.campaignmanagement.dashboard.dto;

public record ApplicantCsvExport(
        String filename,
        byte[] content
) {
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\dashboard\dto\CampaignDashboardOverview.java
```java
package com.adrovis.influencer.campaignmanagement.dashboard.dto;

import com.adrovis.influencer.campaignmanagement.application.enums.CampaignApplicationStatus;
import com.adrovis.influencer.campaignmanagement.campaign.enums.CampaignStatus;
import java.util.Map;

public record CampaignDashboardOverview(
        long totalCampaigns,
        Map<CampaignStatus, Long> campaignsByStatus,
        long totalApplications,
        Map<CampaignApplicationStatus, Long> applicationsByStatus
) {
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\dashboard\repository\CampaignDashboardRepository.java
```java
package com.adrovis.influencer.campaignmanagement.dashboard.repository;

import com.adrovis.influencer.campaignmanagement.application.entity.ManagedCampaignApplication;
import com.adrovis.influencer.campaignmanagement.application.enums.CampaignApplicationStatus;
import com.adrovis.influencer.campaignmanagement.campaign.entity.ManagedCampaign;
import com.adrovis.influencer.campaignmanagement.campaign.enums.CampaignStatus;
import com.adrovis.influencer.campaignmanagement.publisher.enums.PublisherType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface CampaignDashboardRepository
        extends Repository<ManagedCampaign, UUID> {

    interface CampaignCount {
        CampaignStatus getStatus();
        long getTotal();
    }

    interface ApplicationCount {
        CampaignApplicationStatus getStatus();
        long getTotal();
    }

    /**
     * Counts include historical/archived records.
     * Ownership scope is always derived by the service.
     */
    @Query("""
            select campaign.status as status, count(campaign) as total
            from ManagedCampaign campaign
            join campaign.publisherProfile publisher
            where (
                :admin = true
                or (
                    publisher.ownerAccountId = :accountId
                    and publisher.publisherType in :publisherTypes
                )
            )
            group by campaign.status
            """)
    List<CampaignCount> countCampaigns(
            @Param("admin") boolean admin,
            @Param("accountId") UUID accountId,
            @Param("publisherTypes") List<PublisherType> publisherTypes
    );

    @Query("""
            select application.status as status, count(application) as total
            from ManagedCampaignApplication application
            join ManagedCampaign campaign
                on campaign.id = application.campaignId
            join campaign.publisherProfile publisher
            where (
                :admin = true
                or (
                    publisher.ownerAccountId = :accountId
                    and publisher.publisherType in :publisherTypes
                )
            )
            group by application.status
            """)
    List<ApplicationCount> countApplications(
            @Param("admin") boolean admin,
            @Param("accountId") UUID accountId,
            @Param("publisherTypes") List<PublisherType> publisherTypes
    );

    @EntityGraph(attributePaths = "publisherProfile")
    @Query("""
            select campaign
            from ManagedCampaign campaign
            join campaign.publisherProfile publisher
            where (
                :admin = true
                or (
                    publisher.ownerAccountId = :accountId
                    and publisher.publisherType in :publisherTypes
                )
            )
            and (:includeArchived = true or campaign.archivedAt is null)
            and (:status is null or campaign.status = :status)
            and (:publisherType is null
                or publisher.publisherType = :publisherType)
            and (:publisherId is null or publisher.id = :publisherId)
            and (
                lower(campaign.title) like :search escape '!'
                or lower(publisher.displayName) like :search escape '!'
            )
            """)
    Page<ManagedCampaign> searchCampaigns(
            @Param("admin") boolean admin,
            @Param("accountId") UUID accountId,
            @Param("publisherTypes") List<PublisherType> publisherTypes,
            @Param("includeArchived") boolean includeArchived,
            @Param("status") CampaignStatus status,
            @Param("publisherType") PublisherType publisherType,
            @Param("publisherId") UUID publisherId,
            @Param("search") String search,
            Pageable pageable
    );

    /**
     * Parent campaign access must be authorized before this query.
     * Search supports verified email, account UUID, and conventional answer keys.
     */
    @Query("""
            select application
            from ManagedCampaignApplication application
            where application.campaignId = :campaignId
            and (:status is null or application.status = :status)
            and (
                lower(application.creatorEmail) like :search escape '!'
                or lower(cast(application.creatorAccountId as string))
                    like :search escape '!'
                or lower(coalesce(
                    cast(function(
                        'jsonb_extract_path_text',
                        application.answers,
                        'fullName'
                    ) as string), ''
                )) like :search escape '!'
                or lower(coalesce(
                    cast(function(
                        'jsonb_extract_path_text',
                        application.answers,
                        'instagramUrl'
                    ) as string), ''
                )) like :search escape '!'
            )
            """)
    Page<ManagedCampaignApplication> searchApplications(
            @Param("campaignId") UUID campaignId,
            @Param("status") CampaignApplicationStatus status,
            @Param("search") String search,
            Pageable pageable
    );

    interface ExportRow {
        String getApplicationId();
        String getCreatorAccountId();
        String getCreatorEmail();
        String getStatus();
        String getSubmittedAt();
        String getSubmittedName();
        String getInstagramUrl();
        String getGender();
        String getFollowerCount();
        String getPhone();
        String getPortfolioUrl();
    }

    /**
     * Projects only export columns instead of loading complete answer documents.
     * LIMIT is bounded by the service.
     */
    @Query(value = """
            SELECT
                CAST(a.id AS varchar) AS "applicationId",
                CAST(a.creator_account_id AS varchar) AS "creatorAccountId",
                a.creator_email AS "creatorEmail",
                a.status AS "status",
                to_char(
                    a.submitted_at AT TIME ZONE 'UTC',
                    'YYYY-MM-DD"T"HH24:MI:SS"Z"'
                ) AS "submittedAt",
                a.answers ->> 'fullName' AS "submittedName",
                a.answers ->> 'instagramUrl' AS "instagramUrl",
                a.answers ->> 'gender' AS "gender",
                a.answers ->> 'followerCount' AS "followerCount",
                a.answers ->> 'phone' AS "phone",
                a.answers ->> 'portfolioUrl' AS "portfolioUrl"
            FROM public.managed_campaign_applications a
            WHERE a.campaign_id = :campaignId
              AND (:status = '' OR a.status = :status)
              AND (
                  lower(a.creator_email) LIKE :search ESCAPE '!'
                  OR lower(CAST(a.creator_account_id AS varchar))
                      LIKE :search ESCAPE '!'
                  OR lower(coalesce(a.answers ->> 'fullName', ''))
                      LIKE :search ESCAPE '!'
                  OR lower(coalesce(a.answers ->> 'instagramUrl', ''))
                      LIKE :search ESCAPE '!'
              )
            ORDER BY a.submitted_at DESC, a.id DESC
            LIMIT :rowLimit
            """, nativeQuery = true)
    List<ExportRow> exportApplications(
            @Param("campaignId") UUID campaignId,
            @Param("status") String status,
            @Param("search") String search,
            @Param("rowLimit") int rowLimit
    );
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\dashboard\service\ApplicantCsvRenderer.java
```java
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
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\dashboard\service\CampaignDashboardService.java
```java
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
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\dashboard\service\CampaignExportAudit.java
```java
package com.adrovis.influencer.campaignmanagement.dashboard.service;

import com.adrovis.influencer.campaignmanagement.application.enums.CampaignApplicationStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CampaignExportAudit {

    private final JdbcTemplate jdbc;

    public CampaignExportAudit(
            @Qualifier("primaryDataSource") DataSource dataSource
    ) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * Uses the primary transaction's datasource.
     * Does not store exported answers or raw search text.
     */
    public void record(
            UUID actorId,
            UUID campaignId,
            CampaignApplicationStatus status,
            boolean searchUsed,
            int rowCount,
            int fileBytes
    ) {
        jdbc.update("""
                INSERT INTO public.campaign_applicant_export_audit (
                    id,
                    actor_account_id,
                    campaign_id,
                    status_filter,
                    search_used,
                    row_count,
                    file_bytes,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                actorId,
                campaignId,
                status == null ? null : status.name(),
                searchUsed,
                rowCount,
                fileBytes,
                Timestamp.from(Instant.now())
        );
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\exception\CampaignManagementException.java
```java
package com.adrovis.influencer.campaignmanagement.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import com.adrovis.influencer.campaignmanagement.application.exception.ApplicationAnswersInvalidException;

@Getter
public class CampaignManagementException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public CampaignManagementException(
            HttpStatus status,
            String errorCode,
            String message
    ) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }


}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\exception\CampaignManagementExceptionHandler.java
```java
package com.adrovis.influencer.campaignmanagement.exception;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.adrovis.influencer.campaignmanagement.application.exception.ApplicationAnswersInvalidException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(
        basePackages = "com.adrovis.influencer.campaignmanagement"
)
public class CampaignManagementExceptionHandler {

    @ExceptionHandler(CampaignManagementException.class)
    public ResponseEntity<Map<String, Object>> domain(
            CampaignManagementException exception
    ) {
        return failure(
                exception.getStatus().value(),
                exception.getErrorCode(),
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalidBody(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> fields = new LinkedHashMap<>();

        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(
                        error.getField(),
                        error.getDefaultMessage() == null
                                ? "Invalid value."
                                : error.getDefaultMessage()
                )
        );

        return failure(
                400,
                "INVALID_REQUEST",
                "Please correct the request fields.",
                fields
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> invalidParameters(
            ConstraintViolationException exception
    ) {
        return failure(
                400,
                "INVALID_REQUEST",
                "The request contains missing or invalid fields.",
                Map.of()
        );
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> staleWrite(
            OptimisticLockingFailureException exception
    ) {
        return failure(
                412,
                "VERSION_CONFLICT",
                "Campaign changed. Reload it before saving.",
                Map.of()
        );
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> busy(
            PessimisticLockingFailureException exception
    ) {
        return failure(
                409,
                "CAMPAIGN_BUSY",
                "Campaign is being modified. Reload and try again.",
                Map.of()
        );
    }

    private ResponseEntity<Map<String, Object>> failure(
            int status,
            String code,
            String message,
            Map<String, String> fieldErrors
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("errorCode", code);
        body.put("message", message);

        if (!fieldErrors.isEmpty()) {
            body.put("fieldErrors", fieldErrors);
        }

        body.put("timestamp", Instant.now());

        return ResponseEntity.status(status)
                .header("Cache-Control", "no-store")
                .body(body);
    }
    @ExceptionHandler(ApplicationAnswersInvalidException.class)
    public ResponseEntity<Map<String, Object>> invalidAnswers(
            ApplicationAnswersInvalidException exception
    ) {
        return failure(
                422,
                exception.getErrorCode(),
                exception.getMessage(),
                exception.getFieldErrors()
        );
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\form\controller\CampaignApplicationFormController.java
```java
package com.adrovis.influencer.campaignmanagement.form.controller;

import com.adrovis.influencer.campaignmanagement.form.dto.ApplicationFormVersionResponse;
import com.adrovis.influencer.campaignmanagement.form.dto.CreateApplicationFormVersionRequest;
import com.adrovis.influencer.campaignmanagement.form.service.CampaignApplicationFormService;
import com.adrovis.influencer.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portal/campaigns/{campaignId}/application-form")
@RequiredArgsConstructor
public class CampaignApplicationFormController {

    private final CampaignApplicationFormService forms;

    /**
     * Creates a new immutable form version for an authorized draft campaign.
     * Nested field definitions are validated before reaching the service.
     */
    @PostMapping("/versions")
    public ResponseEntity<ApiResponse<ApplicationFormVersionResponse>> createVersion(
            @PathVariable UUID campaignId,
            @Valid @RequestBody CreateApplicationFormVersionRequest request
    ) {
        ApplicationFormVersionResponse version =
                forms.createVersion(campaignId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(
                        "Application form version created successfully.",
                        version
                ));
    }

    /**
     * Returns the latest management form definition.
     * The service verifies campaign ownership or admin authority.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<ApplicationFormVersionResponse>> latest(
            @PathVariable UUID campaignId
    ) {
        return ResponseEntity.ok(
                ApiResponse.ok(forms.getLatest(campaignId))
        );
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\form\controller\CampaignApplicationReadinessValidator.java
```java
package com.adrovis.influencer.campaignmanagement.form.service;

import com.adrovis.influencer.campaignmanagement.campaign.entity.ManagedCampaign;
import com.adrovis.influencer.campaignmanagement.form.dto.CreateApplicationFormVersionRequest;
import com.adrovis.influencer.campaignmanagement.form.enums.ApplicationMode;
import com.adrovis.influencer.campaignmanagement.form.repository.CampaignApplicationFormVersionRepository;
import com.adrovis.influencer.campaignmanagement.form.validation.GoogleFormUrl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class CampaignApplicationReadinessValidator {

    private final CampaignApplicationFormVersionRepository forms;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    /**
     * Application-specific publishing check.
     *
     * The future publication service must authorize the caller, lock the
     * campaign and call this inside the same primary database transaction.
     */
    public Readiness validateForPublication(ManagedCampaign campaign) {
        if (campaign.getApplicationMode() == ApplicationMode.GOOGLE_FORM) {
            if (!GoogleFormUrl.isValid(campaign.getExternalApplicationUrl())) {
                throw conflict(
                        "A valid Google Forms destination is required."
                );
            }

            return new Readiness(ApplicationMode.GOOGLE_FORM, null);
        }

        if (campaign.getApplicationMode() != ApplicationMode.PLATFORM) {
            throw conflict("Application mode is not configured.");
        }

        if (campaign.getExternalApplicationUrl() != null) {
            throw conflict(
                    "Platform applications must not have an external URL."
            );
        }

        var version = forms
                .findFirstByCampaign_IdOrderByVersionNumberDesc(
                        campaign.getId()
                )
                .orElseThrow(() -> conflict(
                        "Create an application form before publishing."
                ));

        CreateApplicationFormVersionRequest definition;

        try {
            definition = objectMapper.treeToValue(
                    version.getDefinition(),
                    CreateApplicationFormVersionRequest.class
            );
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw conflict("The stored application form is invalid.");
        }

        if (definition == null
                || !validator.validate(definition).isEmpty()) {
            throw conflict("The stored application form is invalid.");
        }

        // Publication must later pin this exact immutable form-version ID.
        return new Readiness(ApplicationMode.PLATFORM, version.getId());
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public record Readiness(
            ApplicationMode mode,
            UUID formVersionId
    ) {
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\form\controller\CampaignApplicationSettingsController.java
```java
package com.adrovis.influencer.campaignmanagement.form.controller;

import com.adrovis.influencer.campaignmanagement.campaign.dto.ManagedCampaignResponse;
import com.adrovis.influencer.campaignmanagement.form.dto.ConfigureCampaignApplicationRequest;
import com.adrovis.influencer.campaignmanagement.form.service.CampaignApplicationSettingsService;
import com.adrovis.influencer.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/portal/campaigns/{campaignId}/application-settings")
@RequiredArgsConstructor
public class CampaignApplicationSettingsController {

    private final CampaignApplicationSettingsService settings;

    /**
     * Requires the campaign version the browser last read.
     * Example header: If-Match: "0"
     */
    @PutMapping
    public ResponseEntity<ApiResponse<ManagedCampaignResponse>> configure(
            @PathVariable UUID campaignId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ConfigureCampaignApplicationRequest request
    ) {
        long expectedVersion = parseVersion(ifMatch);

        ManagedCampaignResponse campaign = settings.configure(
                campaignId,
                expectedVersion,
                request
        );

        return ResponseEntity.ok()
                .eTag("\"" + campaign.version() + "\"")
                .body(ApiResponse.ok(
                        "Application settings updated successfully.",
                        campaign
                ));
    }

    private long parseVersion(String value) {
        if (value == null || !value.matches("\"(0|[1-9][0-9]*)\"")) {
            throw invalidVersion();
        }

        try {
            return Long.parseLong(value.substring(1, value.length() - 1));
        } catch (NumberFormatException exception) {
            throw invalidVersion();
        }
    }

    private ResponseStatusException invalidVersion() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "If-Match must contain a quoted non-negative campaign version."
        );
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\form\dto\ApplicationFieldDefinitionRequest.java
```java
package com.adrovis.influencer.campaignmanagement.form.dto;

import com.adrovis.influencer.campaignmanagement.form.enums.ApplicationFieldType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record ApplicationFieldDefinitionRequest(

        @NotBlank
        @Pattern(
                regexp = "^[a-z][a-zA-Z0-9]{0,49}$",
                message = "Use a camelCase field key beginning with a letter."
        )
        String key,

        @NotBlank
        @Size(max = 120)
        String label,

        @NotNull
        ApplicationFieldType type,

        @NotNull
        Boolean required,

        @Size(max = 300)
        String helpText,

        @Size(max = 30)
        List<@NotBlank @Size(max = 100) String> options,

        @Min(1)
        @Max(5000)
        Integer maxLength,

        BigDecimal min,

        BigDecimal max
) {

    public ApplicationFieldDefinitionRequest {
        key = trim(key);
        label = trim(label);
        helpText = trim(helpText);

        // Preserve null elements so Bean Validation can report them.
        if (options != null) {
            options = options.stream()
                    .map(ApplicationFieldDefinitionRequest::trim)
                    .toList();
        }
    }

    /**
     * Only selection fields may declare options.
     * Options must be nonblank and unique.
     */
    @AssertTrue(message =
            "Selection fields need 1 to 30 unique options; "
                    + "other field types must not define options.")
    public boolean isOptionsConfigurationValid() {
        if (type == null) {
            return true; // @NotNull reports the missing type.
        }

        boolean selection = type == ApplicationFieldType.SINGLE_SELECT
                || type == ApplicationFieldType.MULTI_SELECT;

        if (!selection) {
            return options == null || options.isEmpty();
        }

        return options != null
                && !options.isEmpty()
                && options.size() <= 30
                && options.stream().allMatch(
                option -> option != null && !option.isBlank()
        )
                && options.stream().distinct().count() == options.size();
    }

    /**
     * Numeric bounds belong only to INTEGER/DECIMAL fields.
     * Integer fields cannot use fractional bounds.
     */
    @AssertTrue(message = "Numeric bounds are invalid for this field.")
    public boolean isNumericConfigurationValid() {
        if (type == null) {
            return true;
        }

        boolean numeric = type == ApplicationFieldType.INTEGER
                || type == ApplicationFieldType.DECIMAL;

        if (!numeric) {
            return min == null && max == null;
        }

        if (!safeNumber(min) || !safeNumber(max)) {
            return false;
        }

        if (min != null && max != null && min.compareTo(max) > 0) {
            return false;
        }

        if (type == ApplicationFieldType.INTEGER) {
            return wholeNumber(min) && wholeNumber(max);
        }

        return true;
    }

    /**
     * Length limits apply only to string-valued fields.
     * Defaults and answer validation will be enforced by the answer validator.
     */
    @AssertTrue(message =
            "Length limits are unsupported for this field type "
                    + "or exceed the permitted maximum.")
    public boolean isLengthConfigurationValid() {
        if (type == null || maxLength == null) {
            return true;
        }

        int permitted = switch (type) {
            case SHORT_TEXT -> 200;
            case LONG_TEXT -> 5000;
            case URL -> 2048;
            case EMAIL -> 254;
            case PHONE -> 32;
            default -> 0;
        };

        return maxLength >= 1 && maxLength <= permitted;
    }

    private static boolean wholeNumber(BigDecimal value) {
        return value == null || value.stripTrailingZeros().scale() <= 0;
    }

    private static boolean safeNumber(BigDecimal value) {
        if (value == null) {
            return true;
        }

        // Bound both magnitude and decimal places.
        BigDecimal normalized = value.stripTrailingZeros();

        return normalized.abs().compareTo(
                new BigDecimal("1000000000000000")
        ) <= 0 && normalized.scale() <= 6;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\form\dto\ApplicationFormVersionResponse.java
```java
package com.adrovis.influencer.campaignmanagement.form.dto;

import com.adrovis.influencer.campaignmanagement.form.entity.CampaignApplicationFormVersion;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
import com.adrovis.influencer.campaignmanagement.form.validation.ApplicationConsentNotice;

public record ApplicationFormVersionResponse(
        UUID id,
        UUID campaignId,
        int version,
        JsonNode definition,
        Instant createdAt,
        String consentNoticeVersion,
        String consentNoticeText

) {

    public static ApplicationFormVersionResponse from(
            CampaignApplicationFormVersion entity
    ) {
        return new ApplicationFormVersionResponse(
                entity.getId(),
                entity.getCampaign().getId(),
                entity.getVersionNumber(),
                entity.getDefinition(),
                entity.getCreatedAt(),
                ApplicationConsentNotice.VERSION,
                ApplicationConsentNotice.TEXT
        );
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\form\dto\ConfigureCampaignApplicationRequest.java
```java
package com.adrovis.influencer.campaignmanagement.form.dto;

import com.adrovis.influencer.campaignmanagement.form.enums.ApplicationMode;
import com.adrovis.influencer.campaignmanagement.form.validation.GoogleFormUrl;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConfigureCampaignApplicationRequest(

        @NotNull(message = "Application mode is required.")
        ApplicationMode mode,

        @Size(max = 2048)
        String externalUrl
) {

    public ConfigureCampaignApplicationRequest {
        externalUrl = externalUrl == null || externalUrl.isBlank()
                ? null
                : externalUrl.trim();
    }

    @AssertTrue(message =
            "PLATFORM must not include an external URL. "
                    + "GOOGLE_FORM requires a supported HTTPS Google Forms URL.")
    public boolean isApplicationConfigurationValid() {
        if (mode == null) {
            return true; // @NotNull handles the missing mode.
        }

        return switch (mode) {
            case PLATFORM -> externalUrl == null;
            case GOOGLE_FORM -> GoogleFormUrl.isValid(externalUrl);
        };
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\form\dto\CreateApplicationFormVersionRequest.java
```java
package com.adrovis.influencer.campaignmanagement.form.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Objects;

public record CreateApplicationFormVersionRequest(

        @NotNull
        @Size(min = 1, max = 30)
        List<@NotNull @Valid ApplicationFieldDefinitionRequest> fields
) {

    public CreateApplicationFormVersionRequest {
        if (fields != null) {
            fields = java.util.Collections.unmodifiableList(
                    new java.util.ArrayList<>(fields)
            );
        }
    }

    @AssertTrue(message = "Application field keys must be unique.")
    public boolean isFieldKeysUnique() {
        if (fields == null) {
            return true;
        }

        List<String> keys = fields.stream()
                .filter(Objects::nonNull)
                .map(ApplicationFieldDefinitionRequest::key)
                .filter(Objects::nonNull)
                .toList();

        return keys.stream().distinct().count() == keys.size();
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\form\entity\CampaignApplicationFormVersion.java
```java
package com.adrovis.influencer.campaignmanagement.form.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.adrovis.influencer.campaignmanagement.campaign.entity.ManagedCampaign;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "campaign_application_form_versions",
        schema = "public",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_campaign_application_form_version",
                columnNames = {"campaign_id", "version_number"}
        )
)
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampaignApplicationFormVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "campaign_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_application_form_campaign")
    )
    private ManagedCampaign campaign;

    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            nullable = false,
            updatable = false,
            columnDefinition = "jsonb"
    )
    private JsonNode definition;

    @Column(
            name = "created_by_account_id",
            nullable = false,
            updatable = false
    )
    private UUID createdByAccountId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Called only after service-layer authorization and form validation.
     * Version numbers are allocated by the backend, not request input.
     */
    public CampaignApplicationFormVersion(
            ManagedCampaign campaign,
            int versionNumber,
            JsonNode definition,
            UUID createdByAccountId
    ) {
        this.campaign = Objects.requireNonNull(campaign);
        Objects.requireNonNull(campaign.getId(), "Campaign must already exist.");

        if (versionNumber < 1) {
            throw new IllegalArgumentException(
                    "Form version must be positive."
            );
        }

        if (definition == null || !definition.isObject()) {
            throw new IllegalArgumentException(
                    "Form definition must be a JSON object."
            );
        }

        this.versionNumber = versionNumber;
        this.definition = definition.deepCopy();
        this.createdByAccountId = Objects.requireNonNull(
                createdByAccountId
        );
    }

    // Never expose the mutable JSON object stored inside the entity.
    public JsonNode getDefinition() {
        return definition == null ? null : definition.deepCopy();
    }

    @PrePersist
    private void initializeTimestamp() {
        createdAt = Instant.now();
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\form\enums\ApplicationFieldType.java
```java
package com.adrovis.influencer.campaignmanagement.form.enums;

public enum ApplicationFieldType {
    SHORT_TEXT,
    LONG_TEXT,
    INTEGER,
    DECIMAL,
    URL,
    EMAIL,
    PHONE,
    SINGLE_SELECT,
    MULTI_SELECT,
    BOOLEAN
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\form\enums\ApplicationMode.java
```java
package com.adrovis.influencer.campaignmanagement.form.enums;

public enum ApplicationMode {
    PLATFORM,
    GOOGLE_FORM
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\form\repository\CampaignApplicationFormVersionRepository.java
```java
package com.adrovis.influencer.campaignmanagement.form.repository;

import com.adrovis.influencer.campaignmanagement.form.entity.CampaignApplicationFormVersion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface CampaignApplicationFormVersionRepository
        extends Repository<CampaignApplicationFormVersion, UUID> {

    CampaignApplicationFormVersion save(
            CampaignApplicationFormVersion version
    );

    Optional<CampaignApplicationFormVersion>
    findFirstByCampaign_IdOrderByVersionNumberDesc(UUID campaignId);

    Optional<CampaignApplicationFormVersion>
    findByCampaign_IdAndVersionNumber(
            UUID campaignId,
            int versionNumber
    );
    Optional<CampaignApplicationFormVersion> findByIdAndCampaign_Id(
            UUID id,
            UUID campaignId
    );

}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\form\service\CampaignApplicationFormService.java
```java
package com.adrovis.influencer.campaignmanagement.form.service;

import com.adrovis.influencer.campaignmanagement.campaign.entity.ManagedCampaign;
import com.adrovis.influencer.campaignmanagement.campaign.enums.CampaignStatus;
import com.adrovis.influencer.campaignmanagement.campaign.repository.ManagedCampaignRepository;
import com.adrovis.influencer.campaignmanagement.form.dto.ApplicationFormVersionResponse;
import com.adrovis.influencer.campaignmanagement.form.dto.CreateApplicationFormVersionRequest;
import com.adrovis.influencer.campaignmanagement.form.entity.CampaignApplicationFormVersion;
import com.adrovis.influencer.campaignmanagement.form.repository.CampaignApplicationFormVersionRepository;
import com.adrovis.influencer.security.CurrentAccount;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
public class CampaignApplicationFormService {

    private final ManagedCampaignRepository campaigns;
    private final CampaignApplicationFormVersionRepository forms;
    private final CurrentAccount currentAccount;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new immutable form version.
     * Locking the parent campaign serializes version allocation.
     */
    @PreAuthorize("hasAnyRole('BRAND', 'AGENCY', 'ADMIN')")
    @Transactional("primaryTransactionManager")
    public ApplicationFormVersionResponse createVersion(
            @NotNull UUID campaignId,
            @NotNull @Valid CreateApplicationFormVersionRequest request
    ) {
        var account = currentAccount.require();

        ManagedCampaign campaign = lockAuthorizedCampaign(
                campaignId,
                account
        );

        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Application forms can only be configured on draft campaigns."
            );
        }

        if (campaign.getPublisherProfile().getArchivedAt() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An archived publisher cannot configure campaign forms."
            );
        }

        int previousVersion = forms
                .findFirstByCampaign_IdOrderByVersionNumberDesc(campaignId)
                .map(CampaignApplicationFormVersion::getVersionNumber)
                .orElse(0);

        if (previousVersion == Integer.MAX_VALUE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No further form versions can be created."
            );
        }

        // Persist a detached JSON snapshot, not the mutable request object.
        JsonNode definition = objectMapper.valueToTree(request);

        CampaignApplicationFormVersion version =
                new CampaignApplicationFormVersion(
                        campaign,
                        previousVersion + 1,
                        definition,
                        account.id()
                );

        CampaignApplicationFormVersion saved = forms.save(version);

        return ApplicationFormVersionResponse.from(saved);
    }

    /**
     * Returns the latest form configuration to the owner or admin.
     * This is a management operation, not the public creator form endpoint.
     */
    @PreAuthorize("hasAnyRole('BRAND', 'AGENCY', 'ADMIN')")
    @Transactional(
            value = "primaryTransactionManager",
            readOnly = true
    )
    public ApplicationFormVersionResponse getLatest(
            @NotNull UUID campaignId
    ) {
        var account = currentAccount.require();

        ManagedCampaign campaign = findAuthorizedCampaign(
                campaignId,
                account
        );

        CampaignApplicationFormVersion version = forms
                .findFirstByCampaign_IdOrderByVersionNumberDesc(
                        campaign.getId()
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Application form not found."
                ));

        return ApplicationFormVersionResponse.from(version);
    }

    /**
     * Uses ownership-scoped locking for non-admin accounts.
     * Missing and unrelated campaigns both return 404.
     */
    private ManagedCampaign lockAuthorizedCampaign(
            UUID campaignId,
            CurrentAccount.Identity account
    ) {
        ManagedCampaign campaign;

        if (isAdmin(account)) {
            campaign = campaigns.findForFormUpdate(campaignId)
                    .orElseThrow(this::campaignNotFound);
        } else {
            campaign = campaigns.findOwnedForFormUpdate(
                            campaignId,
                            account.id()
                    )
                    .orElseThrow(this::campaignNotFound);
        }

        verifyPublisherRole(campaign, account);

        return campaign;
    }

    private ManagedCampaign findAuthorizedCampaign(
            UUID campaignId,
            CurrentAccount.Identity account
    ) {
        ManagedCampaign campaign;

        if (isAdmin(account)) {
            campaign = campaigns.findByIdAndArchivedAtIsNull(campaignId)
                    .orElseThrow(this::campaignNotFound);
        } else {
            campaign = campaigns
                    .findByIdAndPublisherProfile_OwnerAccountIdAndArchivedAtIsNull(
                            campaignId,
                            account.id()
                    )
                    .orElseThrow(this::campaignNotFound);
        }

        verifyPublisherRole(campaign, account);

        return campaign;
    }

    /**
     * An account must still hold its publisher role.
     * Admin authority explicitly permits cross-publisher management.
     */
    private void verifyPublisherRole(
            ManagedCampaign campaign,
            CurrentAccount.Identity account
    ) {
        String publisherRole = campaign.getPublisherProfile()
                .getPublisherType()
                .name();

        if (!isAdmin(account) && !account.roles().contains(publisherRole)) {
            throw new AccessDeniedException(
                    "Your account cannot manage this publisher's forms."
            );
        }
    }

    private boolean isAdmin(CurrentAccount.Identity account) {
        return account.roles().contains("ADMIN");
    }

    private ResponseStatusException campaignNotFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Campaign not found."
        );
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\form\service\CampaignApplicationSettingsService.java
```java
package com.adrovis.influencer.campaignmanagement.form.service;

import com.adrovis.influencer.campaignmanagement.campaign.dto.ManagedCampaignResponse;
import com.adrovis.influencer.campaignmanagement.campaign.entity.ManagedCampaign;
import com.adrovis.influencer.campaignmanagement.campaign.enums.CampaignStatus;
import com.adrovis.influencer.campaignmanagement.campaign.repository.ManagedCampaignRepository;
import com.adrovis.influencer.campaignmanagement.form.dto.ConfigureCampaignApplicationRequest;
import com.adrovis.influencer.security.CurrentAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
public class CampaignApplicationSettingsService {

    private final ManagedCampaignRepository campaigns;
    private final CurrentAccount currentAccount;

    /**
     * Changes application settings on an authorized draft.
     * Uses the same campaign lock as form-version creation.
     */
    @PreAuthorize("hasAnyRole('BRAND', 'AGENCY', 'ADMIN')")
    @Transactional("primaryTransactionManager")
    public ManagedCampaignResponse configure(
            @NotNull UUID campaignId,
            @PositiveOrZero long expectedVersion,
            @NotNull @Valid ConfigureCampaignApplicationRequest request
    ) {
        var account = currentAccount.require();
        boolean admin = account.roles().contains("ADMIN");

        ManagedCampaign campaign = (
                admin
                        ? campaigns.findForFormUpdate(campaignId)
                        : campaigns.findOwnedForFormUpdate(
                        campaignId,
                        account.id()
                )
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Campaign not found."
        ));

        String publisherRole = campaign.getPublisherProfile()
                .getPublisherType()
                .name();

        if (!admin && !account.roles().contains(publisherRole)) {
            throw new AccessDeniedException(
                    "Your account cannot manage this publisher."
            );
        }

        if (campaign.getVersion() == null
                || campaign.getVersion().longValue() != expectedVersion) {
            throw new ResponseStatusException(
                    HttpStatus.PRECONDITION_FAILED,
                    "Campaign changed. Reload it before saving."
            );
        }

        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Application settings can only change on draft campaigns."
            );
        }

        if (campaign.getPublisherProfile().getArchivedAt() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The campaign publisher is archived."
            );
        }

        campaign.configureApplication(
                request.mode(),
                request.externalUrl()
        );

        // Flush so the returned DTO contains the current optimistic version.
        ManagedCampaign saved = campaigns.saveAndFlush(campaign);

        return ManagedCampaignResponse.from(saved);
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\form\validation\ApplicationConsentNotice.java
```java
package com.adrovis.influencer.campaignmanagement.form.validation;


public final class ApplicationConsentNotice {

    public static final String VERSION = "campaign-application-v1";

    public static final String TEXT =
            "By applying, I agree to share my verified email and submitted "
                    + "answers with this campaign's publisher and Brandcollabz "
                    + "administrators to process my application.";

    private ApplicationConsentNotice() {
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\form\validation\GoogleFormUrl.java
```java
package com.adrovis.influencer.campaignmanagement.form.validation;

import java.net.URI;
import java.util.Locale;

/**
 * Validates supported public Google Forms destinations.
 * Performs no network request and follows no redirects.
 */
public final class GoogleFormUrl {

    private GoogleFormUrl() {
    }

    public static boolean isValid(String value) {
        if (value == null || value.isBlank() || value.length() > 2048) {
            return false;
        }

        try {
            URI uri = URI.create(value);

            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getRawUserInfo() != null
                    || uri.getRawFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                return false;
            }

            String host = uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getRawPath();

            if (path == null) {
                return false;
            }

            if ("forms.gle".equals(host)) {
                return path.matches("/[A-Za-z0-9_-]+/?");
            }

            if ("docs.google.com".equals(host)) {
                return path.matches(
                        "/forms/(?:u/[0-9]+/)?d/(?:e/)?"
                                + "[A-Za-z0-9_-]+/viewform/?"
                );
            }

            return false;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\publisher\controller\PublisherProfileController.java
```java
package com.adrovis.influencer.campaignmanagement.publisher.controller;

import com.adrovis.influencer.campaignmanagement.publisher.dto.CreatePublisherProfileRequest;
import com.adrovis.influencer.campaignmanagement.publisher.dto.PublisherProfileResponse;
import com.adrovis.influencer.campaignmanagement.publisher.service.PublisherProfileService;
import com.adrovis.influencer.dto.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portal/publisher-profiles")
@RequiredArgsConstructor
public class PublisherProfileController {

    private final PublisherProfileService profiles;

    /**
     * Creates a profile for the authenticated account.
     * Role and ownership checks are enforced by the service.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<PublisherProfileResponse>> create(
            @Valid @RequestBody CreatePublisherProfileRequest request
    ) {
        PublisherProfileResponse profile = profiles.create(request);

        URI location = URI.create(
                "/api/v1/portal/publisher-profiles/me"
        );

        return ResponseEntity.created(location)
                .body(ApiResponse.ok(
                        "Publisher profile created successfully.",
                        profile
                ));
    }

    /**
     * Lists only the authenticated account's active publisher profiles.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<PublisherProfileResponse>>> mine() {
        return ResponseEntity.ok(
                ApiResponse.ok(profiles.getOwnProfiles())
        );
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\publisher\dto\CreatePublisherProfileRequest.java
```java
package com.adrovis.influencer.campaignmanagement.publisher.dto;

import com.adrovis.influencer.campaignmanagement.publisher.enums.PublisherType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePublisherProfileRequest(

        @NotNull(message = "Publisher type is required.")
        PublisherType type,

        @NotBlank(message = "Display name is required.")
        @Size(
                max = 120,
                message = "Display name must not exceed 120 characters."
        )
        String displayName
) {

    public CreatePublisherProfileRequest {
        displayName = displayName == null ? null : displayName.trim();
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\publisher\dto\PublisherProfileResponse.java
```java
package com.adrovis.influencer.campaignmanagement.publisher.dto;

import com.adrovis.influencer.campaignmanagement.publisher.entity.PublisherProfile;
import com.adrovis.influencer.campaignmanagement.publisher.enums.PublisherType;
import java.time.Instant;
import java.util.UUID;

public record PublisherProfileResponse(
        UUID id,
        PublisherType type,
        String displayName,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {

    public static PublisherProfileResponse from(PublisherProfile profile) {
        return new PublisherProfileResponse(
                profile.getId(),
                profile.getPublisherType(),
                profile.getDisplayName(),
                profile.getVersion(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\publisher\entity\PublisherProfile.java
```java
package com.adrovis.influencer.campaignmanagement.publisher.entity;

import com.adrovis.influencer.campaignmanagement.publisher.enums.PublisherType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "campaign_publisher_profiles",
        schema = "public",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_publisher_owner_type",
                        columnNames = {"owner_account_id", "publisher_type"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PublisherProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Identity comes from the validated JWT, never from signup/request input.
    // No database FK: the authentication account lives in a separate database.
    @Column(name = "owner_account_id", nullable = false, updatable = false)
    private UUID ownerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "publisher_type",
            nullable = false,
            updatable = false,
            length = 20
    )
    private PublisherType publisherType;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(length = 3000)
    private String description;

    @Column(name = "logo_asset_id")
    private UUID logoAssetId;

    @Column(length = 2048)
    private String website;

    @Column(name = "instagram_url", length = 200)
    private String instagramUrl;

    @Column(length = 80)
    private String industry;

    // Contact details are private management fields by default.
    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    @Column(name = "contact_phone", length = 16)
    private String contactPhone;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    // Used for optimistic locking when updating the profile.
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Archive instead of removing campaign ownership/history.
    @Column(name = "archived_at")
    private Instant archivedAt;

    public PublisherProfile(
            UUID ownerAccountId,
            PublisherType publisherType,
            String displayName
    ) {
        this.ownerAccountId = Objects.requireNonNull(
                ownerAccountId, "Owner account is required"
        );
        this.publisherType = Objects.requireNonNull(
                publisherType, "Publisher type is required"
        );

        if (displayName == null
                || displayName.isBlank()
                || displayName.trim().length() > 120) {
            throw new IllegalArgumentException(
                    "Display name must contain 1 to 120 characters"
            );
        }

        this.displayName = displayName.trim();
    }

    @PrePersist
    private void initializeTimestamps() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void updateTimestamp() {
        updatedAt = Instant.now();
    }
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\publisher\enums\PublisherType.java
```java
package com.adrovis.influencer.campaignmanagement.publisher.enums;

public enum PublisherType {
    BRAND,
    AGENCY,
    ADMIN
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\publisher\repository\PublisherProfileRepository.java
```java
package com.adrovis.influencer.campaignmanagement.publisher.repository;

import com.adrovis.influencer.campaignmanagement.publisher.entity.PublisherProfile;
import com.adrovis.influencer.campaignmanagement.publisher.enums.PublisherType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublisherProfileRepository
        extends JpaRepository<PublisherProfile, UUID> {

    // Checks whether this account already has the selected publisher type.
    boolean existsByOwnerAccountIdAndPublisherType(
            UUID ownerAccountId,
            PublisherType publisherType
    );

    // Lists only active profiles belonging to the authenticated account.
    List<PublisherProfile> findAllByOwnerAccountIdAndArchivedAtIsNull(
            UUID ownerAccountId
    );

    // Combines resource identity and ownership in the database query.
    Optional<PublisherProfile> findByIdAndOwnerAccountIdAndArchivedAtIsNull(
            UUID id,
            UUID ownerAccountId
    );

    // Admin creation can select any active publisher profile.
    Optional<PublisherProfile> findByIdAndArchivedAtIsNull(UUID id);
}
```

### .\src\main\java\com\adrovis\influencer\campaignmanagement\publisher\service\PublisherProfileService.java
```java
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
```

### .\src\main\java\com\adrovis\influencer\collector\config\CollectorDbConfig.java
```java
package com.adrovis.influencer.collector.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Secondary DataSource configuration specifically targeting the Creator Collector Supabase project.
 * All repository calls inside com.adrovis.influencer.collector.repository will route here.
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "com.adrovis.influencer.collector.repository",
        entityManagerFactoryRef = "collectorEntityManagerFactory",
        transactionManagerRef = "collectorTransactionManager"
)
public class CollectorDbConfig {

    @Bean(name = "collectorDataSourceProperties")
    @ConfigurationProperties("collector.datasource")
    public DataSourceProperties collectorDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "collectorDataSource")
    public DataSource collectorDataSource(
            @Qualifier("collectorDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean(name = "collectorEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean collectorEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("collectorDataSource") DataSource dataSource) {

        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "none");
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");

        return builder
                .dataSource(dataSource)
                .packages("com.adrovis.influencer.collector.entity")
                .persistenceUnit("collectorPU")
                .properties(properties)
                .build();
    }

    @Bean(name = "collectorTransactionManager")
    public PlatformTransactionManager collectorTransactionManager(
            @Qualifier("collectorEntityManagerFactory") LocalContainerEntityManagerFactoryBean factory) {
        return new JpaTransactionManager(Objects.requireNonNull(factory.getObject()));
    }
}
```

### .\src\main\java\com\adrovis\influencer\collector\config\PrimaryDbConfig.java
```java
package com.adrovis.influencer.collector.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Ensures existing entities and repositories under com.adrovis.influencer and com.adrovis.email
 * remain completely intact as the Primary DataSource without any interference.
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = {
                "com.adrovis.influencer.repository",
                "com.adrovis.email.repository",
                "com.adrovis.influencer.payment.repository",
                "com.adrovis.influencer.campaignmanagement.publisher.repository",
                "com.adrovis.influencer.campaignmanagement.campaign.repository",
                "com.adrovis.influencer.campaignmanagement.form.repository",
                "com.adrovis.influencer.campaignmanagement.application.repository",
                "com.adrovis.influencer.campaignmanagement.dashboard.repository"


        },
        entityManagerFactoryRef = "primaryEntityManagerFactory",
        transactionManagerRef = "primaryTransactionManager"
)

public class PrimaryDbConfig {

    @Primary
    @Bean(name = "primaryDataSourceProperties")
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean(name = "primaryDataSource")
    public DataSource primaryDataSource(
            @Qualifier("primaryDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Primary
    @Bean(name = "primaryEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean primaryEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("primaryDataSource") DataSource dataSource) {

        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "update");

        return builder
                .dataSource(dataSource)
                .packages(
                        "com.adrovis.influencer.entity",
                        "com.adrovis.email.entity",
                        "com.adrovis.influencer.payment.entity",
                        "com.adrovis.influencer.campaignmanagement.publisher.entity",
                        "com.adrovis.influencer.campaignmanagement.campaign.entity",
                        "com.adrovis.influencer.campaignmanagement.form.entity",
                        "com.adrovis.influencer.campaignmanagement.application.entity"
                )
                .persistenceUnit("primaryPU")
                .properties(properties)
                .build();
    }

    @Primary
    @Bean(name = "primaryTransactionManager")
    public PlatformTransactionManager primaryTransactionManager(
            @Qualifier("primaryEntityManagerFactory") LocalContainerEntityManagerFactoryBean factory) {
        return new JpaTransactionManager(Objects.requireNonNull(factory.getObject()));
    }



}
```

### .\src\main\java\com\adrovis\influencer\collector\controller\CollectorCreatorController.java
```java
package com.adrovis.influencer.collector.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.adrovis.influencer.collector.dto.CreatorFilterRequest;
import com.adrovis.influencer.collector.dto.CreatorTableResponse;
import com.adrovis.influencer.collector.entity.CollectorTeamMember;
import com.adrovis.influencer.collector.repository.CollectorTeamMemberRepository;
import com.adrovis.influencer.collector.service.CollectorCreatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@SecurityRequirement(name = "adrovisBearer")
@RequestMapping("/api/v1/collector/creators")
@RequiredArgsConstructor
public class CollectorCreatorController {

    private final CollectorCreatorService creatorService;
    private final CollectorTeamMemberRepository memberRepo;
    /** Preserves collector filters and pagination; the route remains Admin-only until membership is modeled. */

    @GetMapping
    public ResponseEntity<CreatorTableResponse> getCreators(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String niche,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) UUID addedBy,
            @RequestParam(required = false) String followerRange,
            @RequestParam(required = false) String timeRange,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        CreatorFilterRequest req = CreatorFilterRequest.builder()
                .search(search)
                .niche(niche)
                .location(location)
                .addedBy(addedBy)
                .followerRange(followerRange)
                .timeRange(timeRange)
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDir(sortDir)
                .build();

        return ResponseEntity.ok(creatorService.getCreatorsTable(req));
    }
    /** Preserves CSV generation while requiring Admin access to the platform-wide dataset. */

    @GetMapping("/export")
    public ResponseEntity<String> exportCsv(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String niche,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) UUID addedBy,
            @RequestParam(required = false) String followerRange,
            @RequestParam(required = false) String timeRange) {

        CreatorFilterRequest req = CreatorFilterRequest.builder()
                .search(search)
                .niche(niche)
                .location(location)
                .addedBy(addedBy)
                .followerRange(followerRange)
                .timeRange(timeRange)
                .build();

        String csvData = creatorService.exportCreatorsCsv(req);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=creators_export.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvData);
    }
    /** Returns internal collector team records only behind the Admin authorization boundary. */

    @GetMapping("/members")
    public ResponseEntity<List<CollectorTeamMember>> getMembers() {
        return ResponseEntity.ok(memberRepo.findByActiveTrue());
    }
}

```

### .\src\main\java\com\adrovis\influencer\collector\controller\CollectorDashboardController.java
```java
package com.adrovis.influencer.collector.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.adrovis.influencer.collector.dto.DashboardOverviewDto;
import com.adrovis.influencer.collector.service.CollectorDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@SecurityRequirement(name = "adrovisBearer")
@RequestMapping("/api/v1/collector/dashboard")
@RequiredArgsConstructor
public class CollectorDashboardController {

    private final CollectorDashboardService dashboardService;
    /** Returns existing aggregate dashboard data only through its Admin-protected read endpoint. */

    @GetMapping("/overview")
    public ResponseEntity<DashboardOverviewDto> getOverview(
            @RequestParam(name = "timeRange", defaultValue = "LAST_7_DAYS") String timeRange) {
        return ResponseEntity.ok(dashboardService.getOverview(timeRange));
    }
}

```

### .\src\main\java\com\adrovis\influencer\collector\dto\CreatorFilterRequest.java
```java
package com.adrovis.influencer.collector.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatorFilterRequest {
    private String search;
    private String niche;
    private String location;
    private UUID addedBy;
    private String followerRange; // e.g. "0-10k", "10k-50k", "50k-100k", "100k+"
    private String timeRange;     // "TODAY", "LAST_7_DAYS", "THIS_MONTH", "ALL"
    private int page = 0;
    private int size = 10;
    private String sortBy = "createdAt";
    private String sortDir = "desc";
}
```

### .\src\main\java\com\adrovis\influencer\collector\dto\CreatorTableResponse.java
```java
package com.adrovis.influencer.collector.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatorTableResponse {
    private CreatorStatsBar stats;
    private List<RecentCreatorDto> items;
    private int currentPage;
    private int pageSize;
    private long totalElements;
    private int totalPages;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreatorStatsBar {
        private long totalCreators;
        private String totalGrowth;
        private long withEmailCount;
        private double withEmailPercentage;
        private long withUsernameCount;
        private double withUsernamePercentage;
        private long withLocationCount;
        private double withLocationPercentage;
        private int totalNiches;
    }
}
```

### .\src\main\java\com\adrovis\influencer\collector\dto\DashboardOverviewDto.java
```java
package com.adrovis.influencer.collector.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardOverviewDto {
    private KpiMetrics kpis;
    private TeamGoal teamGoal;
    private List<TeamMemberRankDto> teamCompetition;
    private List<TrendDataPointDto> collectionTrend;
    private List<NicheDistDto> nicheDistribution;
    private List<LocationInsightDto> locationInsights;
    private List<RecentCreatorDto> recentCreators;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KpiMetrics {
        private long totalCreators;
        private String totalCreatorsGrowth; // e.g. "+12.8%"
        private long uniqueEmails;
        private String uniqueEmailsGrowth;   // e.g. "+11.4%"
        private long activeMembers;
        private long todayTargetProgress;    // e.g. 32
        private long todayTargetGoal;        // e.g. 50
        private double todayTargetPercentage; // e.g. 64.0
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TeamGoal {
        private long current;
        private long target;
        private double percentage;
    }
}
```

### .\src\main\java\com\adrovis\influencer\collector\dto\LocationInsightDto.java
```java
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
```

### .\src\main\java\com\adrovis\influencer\collector\dto\NicheDistDto.java
```java
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
```

### .\src\main\java\com\adrovis\influencer\collector\dto\RecentCreatorDto.java
```java
package com.adrovis.influencer.collector.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecentCreatorDto {
    private UUID id;
    private String username;
    private String name;
    private String email;
    private Long followers;
    private Long posts;
    private String niche;
    private String location;
    private String addedBy;
    private String addedByInitials;
    private String addedAt;
}
```

### .\src\main\java\com\adrovis\influencer\collector\dto\TeamMemberRankDto.java
```java
package com.adrovis.influencer.collector.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamMemberRankDto {
    private int rank;
    private UUID memberId;
    private String name;
    private String username;
    private String initials;
    private long creators;
    private double progress; // percentage relative to team quota
}
```

### .\src\main\java\com\adrovis\influencer\collector\dto\TrendDataPointDto.java
```java
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
```

### .\src\main\java\com\adrovis\influencer\collector\entity\CollectorCreator.java
```java
package com.adrovis.influencer.collector.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "creators")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectorCreator {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "normalized_email")
    private String normalizedEmail;

    @Column(name = "instagram_username")
    private String instagramUsername;

    @Column(name = "normalized_username")
    private String normalizedUsername;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "followers")
    private Long followers;

    @Column(name = "following")
    private Long following;

    @Column(name = "post_count")
    private Long postCount;

    @Column(name = "location")
    private String location;

    @Column(name = "niche", columnDefinition = "TEXT")
    private String niche;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "first_added_by")
    private UUID firstAddedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
```

### .\src\main\java\com\adrovis\influencer\collector\entity\CollectorSubmission.java
```java
package com.adrovis.influencer.collector.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "creator_submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectorSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "team_member_id", nullable = false)
    private UUID teamMemberId;

    @Column(name = "creator_id")
    private UUID creatorId;

    @Column(name = "telegram_message_id")
    private Long telegramMessageId;

    @Column(name = "status")
    private String status; // "new", "duplicate", "email_not_found"

    @Column(name = "extracted_username")
    private String extractedUsername;

    @Column(name = "extracted_email")
    private String extractedEmail;

    @Column(name = "extracted_data", columnDefinition = "TEXT")
    private String extractedData;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
```

### .\src\main\java\com\adrovis\influencer\collector\entity\CollectorTeamMember.java
```java
package com.adrovis.influencer.collector.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "team_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectorTeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "telegram_user_id", unique = true, nullable = false)
    private Long telegramUserId;

    @Column(name = "telegram_username")
    private String telegramUsername;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "role")
    private String role; // "admin", "member"

    @Column(name = "active")
    private Boolean active;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
```

### .\src\main\java\com\adrovis\influencer\collector\repository\CollectorCreatorRepository.java
```java
package com.adrovis.influencer.collector.repository;

import com.adrovis.influencer.collector.entity.CollectorCreator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public interface CollectorCreatorRepository extends JpaRepository<CollectorCreator, UUID>, JpaSpecificationExecutor<CollectorCreator> {

    @Query("SELECT COUNT(c) FROM CollectorCreator c")
    long countTotalCreators();

    @Query("SELECT COUNT(c) FROM CollectorCreator c WHERE c.email IS NOT NULL AND c.email != ''")
    long countUniqueEmails();

    @Query("SELECT COUNT(c) FROM CollectorCreator c WHERE c.instagramUsername IS NOT NULL AND c.instagramUsername != ''")
    long countWithUsername();

    @Query("SELECT COUNT(c) FROM CollectorCreator c WHERE c.location IS NOT NULL AND c.location != ''")
    long countWithLocation();

    @Query("SELECT COUNT(c) FROM CollectorCreator c WHERE c.createdAt >= :startDate")
    long countCreatedSince(@Param("startDate") OffsetDateTime startDate);

    @Query("SELECT COUNT(c) FROM CollectorCreator c WHERE c.createdAt >= :startDate AND c.createdAt < :endDate")
    long countCreatedBetween(@Param("startDate") OffsetDateTime startDate, @Param("endDate") OffsetDateTime endDate);

    @Query("SELECT COUNT(c) FROM CollectorCreator c WHERE c.email IS NOT NULL AND c.createdAt >= :startDate")
    long countEmailsCreatedSince(@Param("startDate") OffsetDateTime startDate);

    @Query("SELECT COUNT(c) FROM CollectorCreator c WHERE c.email IS NOT NULL AND c.createdAt >= :startDate AND c.createdAt < :endDate")
    long countEmailsCreatedBetween(@Param("startDate") OffsetDateTime startDate, @Param("endDate") OffsetDateTime endDate);

    // Distribution of Niches (PostgreSQL unnest)
    @Query(value = """
        SELECT trim(niche_item) AS nicheName, COUNT(*) AS cnt
        FROM creators c, unnest(string_to_array(c.niche, ',')) AS niche_item
        WHERE c.niche IS NOT NULL AND trim(niche_item) <> ''
        GROUP BY trim(niche_item)
        ORDER BY cnt DESC
        LIMIT 10
    """, nativeQuery = true)
    List<Map<String, Object>> findNicheDistribution();

    // Location Insights (Top Cities)
    @Query(value = """
        SELECT location AS locName, COUNT(*) AS cnt
        FROM creators
        WHERE location IS NOT NULL AND location <> ''
        GROUP BY location
        ORDER BY cnt DESC
        LIMIT 6
    """, nativeQuery = true)
    List<Map<String, Object>> findLocationDistribution();

    // Collection trend over time
    @Query(value = """
        SELECT to_char(created_at, 'DD Mon') AS dayLabel,
               date(created_at) AS rawDate,
               COUNT(*) AS totalCreators,
               COUNT(email) AS uniqueEmails
        FROM creators
        WHERE created_at >= :startDate
        GROUP BY dayLabel, rawDate
        ORDER BY rawDate ASC
    """, nativeQuery = true)
    List<Map<String, Object>> findDailyCollectionTrend(@Param("startDate") OffsetDateTime startDate);

    // Recent creators
    List<CollectorCreator> findTop5ByOrderByCreatedAtDesc();
    List<CollectorCreator> findTop8ByOrderByCreatedAtDesc();
}
```

### .\src\main\java\com\adrovis\influencer\collector\repository\CollectorSubmissionRepository.java
```java
package com.adrovis.influencer.collector.repository;

import com.adrovis.influencer.collector.entity.CollectorSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public interface CollectorSubmissionRepository extends JpaRepository<CollectorSubmission, UUID> {

    @Query("SELECT COUNT(s) FROM CollectorSubmission s WHERE s.status = 'new' AND s.createdAt >= :startDate")
    long countNewSubmissionsSince(@Param("startDate") OffsetDateTime startDate);

    // Team Competition Leaderboard
    @Query(value = """
        SELECT s.team_member_id AS memberId,
               m.display_name AS memberName,
               m.telegram_username AS tgUsername,
               COUNT(DISTINCT s.extracted_email) AS creatorsCount
        FROM creator_submissions s
        JOIN team_members m ON s.team_member_id = m.id
        WHERE s.status = 'new' AND s.created_at >= :startDate
        GROUP BY s.team_member_id, m.display_name, m.telegram_username
        ORDER BY creatorsCount DESC
    """, nativeQuery = true)
    List<Map<String, Object>> findTeamLeaderboard(@Param("startDate") OffsetDateTime startDate);

    // Today's total team unique leads
    @Query(value = """
        SELECT COUNT(DISTINCT extracted_email)
        FROM creator_submissions
        WHERE status = 'new' AND date(created_at) = date(now())
    """, nativeQuery = true)
    long countTodayTeamUniqueLeads();
}
```

### .\src\main\java\com\adrovis\influencer\collector\repository\CollectorTeamMemberRepository.java
```java
package com.adrovis.influencer.collector.repository;

import com.adrovis.influencer.collector.entity.CollectorTeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CollectorTeamMemberRepository extends JpaRepository<CollectorTeamMember, UUID> {
    List<CollectorTeamMember> findByActiveTrue();
    Optional<CollectorTeamMember> findByTelegramUserId(Long telegramUserId);
    long countByActiveTrue();
}
```

### .\src\main\java\com\adrovis\influencer\collector\service\CollectorCreatorService.java
```java
package com.adrovis.influencer.collector.service;

import org.springframework.security.access.prepost.PreAuthorize;
import com.adrovis.influencer.collector.dto.CreatorFilterRequest;
import com.adrovis.influencer.collector.dto.CreatorTableResponse;
import com.adrovis.influencer.collector.dto.RecentCreatorDto;
import com.adrovis.influencer.collector.entity.CollectorCreator;
import com.adrovis.influencer.collector.entity.CollectorTeamMember;
import com.adrovis.influencer.collector.repository.CollectorCreatorRepository;
import com.adrovis.influencer.collector.repository.CollectorTeamMemberRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class CollectorCreatorService {

    private final CollectorCreatorRepository creatorRepo;
    private final CollectorTeamMemberRepository memberRepo;

    private static final DateTimeFormatter UI_DATE_FMT = DateTimeFormatter.ofPattern("dd MMM, hh:mm a");

    @Transactional(readOnly = true, transactionManager = "collectorTransactionManager")
    public CreatorTableResponse getCreatorsTable(CreatorFilterRequest req) {
        Sort.Direction direction = "asc".equalsIgnoreCase(req.getSortDir()) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortProperty = req.getSortBy() != null ? req.getSortBy() : "createdAt";
        Pageable pageable = PageRequest.of(Math.max(0, req.getPage()), Math.max(1, req.getSize()), Sort.by(direction, sortProperty));

        Specification<CollectorCreator> spec = buildSpecification(req);
        Page<CollectorCreator> page = creatorRepo.findAll(spec, pageable);

        Map<UUID, String> memberNameMap = memberRepo.findAll().stream()
                .collect(Collectors.toMap(CollectorTeamMember::getId, CollectorTeamMember::getDisplayName, (a, b) -> a));

        List<RecentCreatorDto> items = page.getContent().stream().map(c -> {
            String addedByName = c.getFirstAddedBy() != null ? memberNameMap.getOrDefault(c.getFirstAddedBy(), "Admin") : "Admin";
            return RecentCreatorDto.builder()
                    .id(c.getId())
                    .username(c.getInstagramUsername() != null ? "@" + c.getInstagramUsername() : "â€”")
                    .name(c.getFullName() != null ? c.getFullName() : "UNKNOWN")
                    .email(c.getEmail())
                    .followers(c.getFollowers())
                    .posts(c.getPostCount())
                    .niche(c.getNiche() != null ? c.getNiche().split(",")[0].trim() : "â€”")
                    .location(c.getLocation())
                    .addedBy(addedByName)
                    .addedByInitials(getInitials(addedByName))
                    .addedAt(c.getCreatedAt() != null ? c.getCreatedAt().format(UI_DATE_FMT) : "â€”")
                    .build();
        }).collect(Collectors.toList());

        long totalCreators = creatorRepo.countTotalCreators();
        long withEmail = creatorRepo.countUniqueEmails();
        long withUser = creatorRepo.countWithUsername();
        long withLoc = creatorRepo.countWithLocation();

        CreatorTableResponse.CreatorStatsBar statsBar = CreatorTableResponse.CreatorStatsBar.builder()
                .totalCreators(totalCreators)
                .totalGrowth("+12.8%")
                .withEmailCount(withEmail)
                .withEmailPercentage(totalCreators > 0 ? Math.round((withEmail * 100.0 / totalCreators) * 10.0) / 10.0 : 0.0)
                .withUsernameCount(withUser)
                .withUsernamePercentage(totalCreators > 0 ? Math.round((withUser * 100.0 / totalCreators) * 10.0) / 10.0 : 0.0)
                .withLocationCount(withLoc)
                .withLocationPercentage(totalCreators > 0 ? Math.round((withLoc * 100.0 / totalCreators) * 10.0) / 10.0 : 0.0)
                .totalNiches(7)
                .build();

        return CreatorTableResponse.builder()
                .stats(statsBar)
                .items(items)
                .currentPage(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true, transactionManager = "collectorTransactionManager")
    public String exportCreatorsCsv(CreatorFilterRequest req) {
        Specification<CollectorCreator> spec = buildSpecification(req);
        List<CollectorCreator> creators = creatorRepo.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        pw.println("ID,Instagram Username,Full Name,Email,Followers,Following,Posts,Location,Niche,Added At");

        for (CollectorCreator c : creators) {
            pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",%s,%s,%s,\"%s\",\"%s\",\"%s\"%n",
                    c.getId(),
                    safe(c.getInstagramUsername()),
                    safe(c.getFullName()),
                    safe(c.getEmail()),
                    c.getFollowers() != null ? c.getFollowers() : "",
                    c.getFollowing() != null ? c.getFollowing() : "",
                    c.getPostCount() != null ? c.getPostCount() : "",
                    safe(c.getLocation()),
                    safe(c.getNiche()),
                    c.getCreatedAt() != null ? c.getCreatedAt().toString() : ""
            );
        }
        pw.flush();
        return sw.toString();
    }

    private Specification<CollectorCreator> buildSpecification(CreatorFilterRequest req) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (req.getSearch() != null && !req.getSearch().isBlank()) {
                String term = "%" + req.getSearch().trim().toLowerCase() + "%";
                Predicate searchEmail = cb.like(cb.lower(root.get("email")), term);
                Predicate searchUsername = cb.like(cb.lower(root.get("instagramUsername")), term);
                Predicate searchName = cb.like(cb.lower(root.get("fullName")), term);
                predicates.add(cb.or(searchEmail, searchUsername, searchName));
            }

            if (req.getNiche() != null && !req.getNiche().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("niche")), "%" + req.getNiche().trim().toLowerCase() + "%"));
            }

            if (req.getLocation() != null && !req.getLocation().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("location")), "%" + req.getLocation().trim().toLowerCase() + "%"));
            }

            if (req.getAddedBy() != null) {
                predicates.add(cb.equal(root.get("firstAddedBy"), req.getAddedBy()));
            }

            if (req.getFollowerRange() != null && !req.getFollowerRange().isBlank()) {
                switch (req.getFollowerRange()) {
                    case "0-10k" -> predicates.add(cb.lessThanOrEqualTo(root.get("followers"), 10000L));
                    case "10k-50k" -> predicates.add(cb.between(root.get("followers"), 10000L, 50000L));
                    case "50k-100k" -> predicates.add(cb.between(root.get("followers"), 50000L, 100000L));
                    case "100k+" -> predicates.add(cb.greaterThan(root.get("followers"), 100000L));
                }
            }

            if (req.getTimeRange() != null && !req.getTimeRange().isBlank()) {
                OffsetDateTime now = OffsetDateTime.now();
                if ("TODAY".equalsIgnoreCase(req.getTimeRange())) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), now.toLocalDate().atStartOfDay().atOffset(now.getOffset())));
                } else if ("LAST_7_DAYS".equalsIgnoreCase(req.getTimeRange())) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), now.minusDays(7)));
                } else if ("THIS_MONTH".equalsIgnoreCase(req.getTimeRange())) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), now.withDayOfMonth(1).toLocalDate().atStartOfDay().atOffset(now.getOffset())));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String safe(String str) {
        return str != null ? str.replace("\"", "\"\"") : "";
    }

    private String getInitials(String name) {
        if (name == null || name.isBlank()) return "TM";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
    }
}
```

### .\src\main\java\com\adrovis\influencer\collector\service\CollectorDashboardService.java
```java
package com.adrovis.influencer.collector.service;

import org.springframework.security.access.prepost.PreAuthorize;
import com.adrovis.influencer.collector.dto.*;
import com.adrovis.influencer.collector.entity.CollectorTeamMember;
import com.adrovis.influencer.collector.repository.CollectorCreatorRepository;
import com.adrovis.influencer.collector.repository.CollectorSubmissionRepository;
import com.adrovis.influencer.collector.repository.CollectorTeamMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class CollectorDashboardService {

    private final CollectorCreatorRepository creatorRepo;
    private final CollectorSubmissionRepository submissionRepo;
    private final CollectorTeamMemberRepository memberRepo;

    private static final DateTimeFormatter UI_DATE_FMT = DateTimeFormatter.ofPattern("dd MMM, hh:mm a");

    @Transactional(readOnly = true, transactionManager = "collectorTransactionManager")
    public DashboardOverviewDto getOverview(String timeRange) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startDate = determineStartDate(timeRange, now);
        OffsetDateTime priorStartDate = startDate.minusDays(7);

        // 1. Core KPIs
        long totalCreators = creatorRepo.countTotalCreators();
        long uniqueEmails = creatorRepo.countUniqueEmails();
        long activeMembers = memberRepo.countByActiveTrue();
        long todayTargetProgress = submissionRepo.countTodayTeamUniqueLeads();
        long todayTargetGoal = activeMembers * 50;
        double todayPercentage = todayTargetGoal > 0 ? (todayTargetProgress * 100.0 / todayTargetGoal) : 0.0;

        // Growth metrics
        long recentCreated = creatorRepo.countCreatedSince(startDate);
        long priorCreated = creatorRepo.countCreatedBetween(priorStartDate, startDate);
        String creatorsGrowth = calculateGrowth(recentCreated, priorCreated);

        long recentEmails = creatorRepo.countEmailsCreatedSince(startDate);
        long priorEmails = creatorRepo.countEmailsCreatedBetween(priorStartDate, startDate);
        String emailsGrowth = calculateGrowth(recentEmails, priorEmails);

        DashboardOverviewDto.KpiMetrics kpis = DashboardOverviewDto.KpiMetrics.builder()
                .totalCreators(totalCreators)
                .totalCreatorsGrowth(creatorsGrowth)
                .uniqueEmails(uniqueEmails)
                .uniqueEmailsGrowth(emailsGrowth)
                .activeMembers(activeMembers)
                .todayTargetProgress(todayTargetProgress)
                .todayTargetGoal(todayTargetGoal > 0 ? todayTargetGoal : 50)
                .todayTargetPercentage(Math.round(todayPercentage * 10.0) / 10.0)
                .build();

        // 2. Team Goal
        DashboardOverviewDto.TeamGoal teamGoal = DashboardOverviewDto.TeamGoal.builder()
                .current(recentCreated)
                .target(2000)
                .percentage(Math.min(100.0, Math.round((recentCreated * 100.0 / 2000.0) * 10.0) / 10.0))
                .build();

        // 3. Team Competition Leaderboard
        List<Map<String, Object>> rawLeaderboard = submissionRepo.findTeamLeaderboard(startDate);
        List<TeamMemberRankDto> leaderboard = new ArrayList<>();
        int rank = 1;
        for (Map<String, Object> row : rawLeaderboard) {
            String name = (String) row.get("memberName");
            String username = (String) row.get("tgUsername");
            Number cntNum = (Number) row.get("creatorsCount");
            long cnt = cntNum != null ? cntNum.longValue() : 0L;
            UUID memberId = (UUID) row.get("memberId");

            leaderboard.add(TeamMemberRankDto.builder()
                    .rank(rank++)
                    .memberId(memberId)
                    .name(name != null ? name : "Member")
                    .username(username != null ? "@" + username : null)
                    .initials(getInitials(name))
                    .creators(cnt)
                    .progress(Math.round((cnt * 100.0 / 350.0) * 10.0) / 10.0) // against weekly 350 target
                    .build());
        }

        // 4. Collection Trend
        List<Map<String, Object>> trendRows = creatorRepo.findDailyCollectionTrend(startDate);
        List<TrendDataPointDto> trend = trendRows.stream().map(row -> TrendDataPointDto.builder()
                .date((String) row.get("dayLabel"))
                .totalCreators(((Number) row.get("totalCreators")).longValue())
                .uniqueEmails(((Number) row.get("uniqueEmails")).longValue())
                .build()).collect(Collectors.toList());

        // 5. Niche Distribution
        List<Map<String, Object>> nicheRows = creatorRepo.findNicheDistribution();
        long nicheTotal = nicheRows.stream().mapToLong(r -> ((Number) r.get("cnt")).longValue()).sum();
        List<NicheDistDto> nicheDist = nicheRows.stream().map(r -> {
            long count = ((Number) r.get("cnt")).longValue();
            double pct = nicheTotal > 0 ? Math.round((count * 100.0 / nicheTotal) * 10.0) / 10.0 : 0.0;
            return NicheDistDto.builder()
                    .niche((String) r.get("nicheName"))
                    .count(count)
                    .percentage(pct)
                    .build();
        }).collect(Collectors.toList());

        // 6. Location Insights
        List<Map<String, Object>> locRows = creatorRepo.findLocationDistribution();
        long locTotal = locRows.stream().mapToLong(r -> ((Number) r.get("cnt")).longValue()).sum();
        List<LocationInsightDto> locDist = locRows.stream().map(r -> {
            long count = ((Number) r.get("cnt")).longValue();
            double pct = locTotal > 0 ? Math.round((count * 100.0 / locTotal) * 10.0) / 10.0 : 0.0;
            return LocationInsightDto.builder()
                    .location((String) r.get("locName"))
                    .count(count)
                    .percentage(pct)
                    .build();
        }).collect(Collectors.toList());

        // 7. Recent Creators
        Map<UUID, String> memberNameMap = memberRepo.findAll().stream()
                .collect(Collectors.toMap(CollectorTeamMember::getId, CollectorTeamMember::getDisplayName, (a, b) -> a));

        List<RecentCreatorDto> recent = creatorRepo.findTop5ByOrderByCreatedAtDesc().stream().map(c -> {
            String addedByName = c.getFirstAddedBy() != null ? memberNameMap.getOrDefault(c.getFirstAddedBy(), "Admin") : "Admin";
            return RecentCreatorDto.builder()
                    .id(c.getId())
                    .username(c.getInstagramUsername() != null ? "@" + c.getInstagramUsername() : "â€”")
                    .name(c.getFullName() != null ? c.getFullName() : "UNKNOWN")
                    .email(c.getEmail())
                    .followers(c.getFollowers())
                    .posts(c.getPostCount())
                    .niche(c.getNiche() != null ? c.getNiche().split(",")[0].trim() : "â€”")
                    .location(c.getLocation())
                    .addedBy(addedByName)
                    .addedByInitials(getInitials(addedByName))
                    .addedAt(c.getCreatedAt() != null ? c.getCreatedAt().format(UI_DATE_FMT) : "â€”")
                    .build();
        }).collect(Collectors.toList());

        return DashboardOverviewDto.builder()
                .kpis(kpis)
                .teamGoal(teamGoal)
                .teamCompetition(leaderboard)
                .collectionTrend(trend)
                .nicheDistribution(nicheDist)
                .locationInsights(locDist)
                .recentCreators(recent)
                .build();
    }

    private OffsetDateTime determineStartDate(String timeRange, OffsetDateTime now) {
        if ("TODAY".equalsIgnoreCase(timeRange)) {
            return now.toLocalDate().atStartOfDay().atOffset(now.getOffset());
        } else if ("THIS_MONTH".equalsIgnoreCase(timeRange)) {
            return now.withDayOfMonth(1).toLocalDate().atStartOfDay().atOffset(now.getOffset());
        }
        return now.minusDays(7); // default: LAST_7_DAYS
    }

    private String calculateGrowth(long current, long prior) {
        if (prior == 0) return current > 0 ? "+100%" : "0.0%";
        double growth = ((double) (current - prior) / prior) * 100.0;
        return (growth >= 0 ? "+" : "") + String.format(Locale.US, "%.1f%%", growth);
    }

    private String getInitials(String name) {
        if (name == null || name.isBlank()) return "TM";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
    }
}
```

### .\src\main\java\com\adrovis\influencer\config\CorsConfig.java
```java
package com.adrovis.influencer.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    /** Applies one exact browser-origin policy; controller wildcards must not broaden it. */
    @Bean
    public CorsConfigurationSource coreCorsSource(
            @Value("${adrovis.frontend-origin}") String configured, Environment environment) {
        List<String> origins = new ArrayList<>();
        for (String value : configured.split(",", -1)) {
            String origin = value.trim();
            URI uri = exactOrigin(origin);
            boolean local = isLocalHttp(uri);
            if ((!"https".equals(uri.getScheme()) && !local)
                    || (environment.acceptsProfiles(Profiles.of("prod")) && local)) {
                throw new IllegalStateException("Configure exact HTTPS production frontend origins");
            }
            if (!origins.contains(origin)) {
                origins.add(origin);
            }
        }
        // Explicit opt-in permits local frontend testing against a deployed production backend.
        // This separate list cannot grant arbitrary HTTP hosts or wildcard access.
        String development = environment.getProperty("adrovis.local-development-origins", "");
        for (String value : development.split(",")) {
            String origin = value.trim();
            if (origin.isEmpty()) {
                continue;
            }
            if (!isLocalHttp(exactOrigin(origin))) {
                throw new IllegalStateException("Local development origins must use HTTP localhost or 127.0.0.1");
            }
            if (!origins.contains(origin)) {
                origins.add(origin);
            }
        }
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowCredentials(true);
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-XSRF-TOKEN", "X-Admin-Key","If-Match"));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setExposedHeaders(List.of("Content-Disposition", "Retry-After","ETag"));
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
    /** Rejects paths, wildcards and ambiguous values before registering credentialed browser access. */
    private URI exactOrigin(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null || value.contains("*") || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getRawPath() == null || !uri.getRawPath().isEmpty()
                    || uri.getPort() == 0 || uri.getPort() > 65535) {
                throw new IllegalArgumentException("Not an exact origin");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Configure exact frontend origins without paths or wildcards");
        }
    }

    /** Matches explicit loopback names only; similarly named public domains are never development origins. */
    private boolean isLocalHttp(URI uri) {
        return "http".equals(uri.getScheme())
                && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
    }

}

```

### .\src\main\java\com\adrovis\influencer\config\OpenApiConfig.java
```java
package com.adrovis.influencer.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /** Documents Bearer authentication without placing secrets or account data into the public schema. */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes("adrovisBearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .info(new Info()
                        .title("Influencer Campaign Core API")
                        .version("1.0.0")
                        .description("REST API service for campaign discovery, smart filtering, and agency CRM management.")
                        .contact(new Contact()
                                .name("ADROVIS Technologies")
                                .email("contact@adrovis.com")));
    }
}
```

### .\src\main\java\com\adrovis\influencer\controller\AgencyController.java
```java
package com.adrovis.influencer.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.adrovis.influencer.dto.AgencyResponse;
import com.adrovis.influencer.dto.ApiResponse;
import com.adrovis.influencer.service.AgencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "adrovisBearer")
@RequestMapping("/api/v1/agencies")
@RequiredArgsConstructor
public class AgencyController {

    private final AgencyService agencyService;
    /** Reads the existing agency list behind the current Admin-only policy. */

    @GetMapping
    public ResponseEntity<ApiResponse<List<AgencyResponse>>> listAgencies() {
        return ResponseEntity.ok(ApiResponse.ok(agencyService.getAllAgencies()));
    }
}

```

### .\src\main\java\com\adrovis\influencer\controller\AnalyticsController.java
```java
package com.adrovis.influencer.controller;

import com.adrovis.influencer.dto.ApiResponse;
import com.adrovis.influencer.entity.AnalyticsSessionEntity;
import com.adrovis.influencer.repository.AnalyticsRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/analytics")
@Slf4j
public class AnalyticsController {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Set<String> SORTABLE = Set.of(
            "createdAt", "lastActiveAt", "durationSeconds", "activeDwellSeconds",
            "pageViews", "city", "channel", "maxScrollDepth", "rageClicks");
    private static final Map<String, String> DEVICES = Map.of(
            "mobile", "Mobile", "desktop", "Desktop", "tablet", "Tablet");

    private static final String[] ADJECTIVES = {
            "Swift", "Calm", "Bright", "Bold", "Clever", "Noble", "Keen", "Steady",
            "Vivid", "Agile", "Silent", "Cosmic", "Golden", "Silver", "Amber", "Crimson",
            "Emerald", "Royal", "Brisk", "Radiant", "Prime", "Lucid", "Valiant", "Stellar",
            "Gentle", "Fierce", "Rapid", "Serene", "Daring", "Loyal", "Sharp", "Witty"
    };
    private static final String[] ANIMALS = {
            "Falcon", "Tiger", "Phoenix", "Panther", "Eagle", "Wolf", "Dolphin", "Lynx",
            "Hawk", "Otter", "Fox", "Raven", "Lion", "Bear", "Heron", "Stag",
            "Leopard", "Condor", "Jaguar", "Osprey", "Cobra", "Mustang", "Orca", "Puma",
            "Griffin", "Cheetah", "Badger", "Crane", "Gazelle", "Marlin", "Owl", "Viper"
    };
    // NOTE: all signatures are lower-case because the user agent is lower-cased before matching.
    private static final List<String> BOT_SIGNATURES = List.of(
            "bot", "spider", "crawl", "slurp", "ahrefs", "semrush", "mj12bot", "dotbot",
            "bytespider", "claudebot", "gptbot", "ccbot", "headlesschrome", "phantomjs",
            "puppeteer", "playwright", "selenium", "webdriver", "curl/", "wget/",
            "python-requests", "python-urllib", "aiohttp", "httpx", "go-http-client",
            "postmanruntime", "insomnia", "uptimerobot", "pingdom", "statuscake",
            "lighthouse", "pagespeed", "gtmetrix", "facebookexternalhit", "facebot",
            "twitterbot", "linkedinbot", "discordbot", "telegrambot", "applebot"
    );
    // Short words like "tor", "pia", "aws" were removed: they matched normal ISP names (e.g. "...operator...").
    private static final List<String> DATACENTER_KEYWORDS = List.of(
            "amazon", "digitalocean", "hetzner", "ovh", "linode", "akamai",
            "google cloud", "google llc", "microsoft corporation", "azure", "choopa",
            "vultr", "m247", "datacamp", "cdn77", "leaseweb", "contabo", "scaleway",
            "hostinger", "godaddy", "cloudflare", "fastly", "zscaler", "nordvpn",
            "expressvpn", "proton", "surfshark", "mullvad", "cyberghost"
    );

    private final AnalyticsRepository analyticsRepository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /** Cache real GeoIP lookups by /24 subnet so free GeoIP APIs stay fast and under rate limits. */
    private final Map<String, GeoData> geoCache = new ConcurrentHashMap<>();

    @Value("${analytics.visitor-salt:change-me-in-production}")
    private String visitorSalt;

    @Value("${analytics.excluded-ips:}")
    private String excludedIpsConfig;

    @Value("${analytics.excluded-emails:}")
    private String excludedEmailsConfig;

    @Value("${analytics.telegram-bot-token:}")
    private String telegramBotToken;

    @Value("${analytics.telegram-chat-id:}")
    private String telegramChatId;

    public AnalyticsController(
            AnalyticsRepository analyticsRepository,
            @Qualifier("primaryDataSource") DataSource dataSource,
            ObjectMapper objectMapper) {
        this.analyticsRepository = analyticsRepository;
        this.jdbc = new JdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(1500))
                .build();
    }

    /** Automatically ensures all analytics tables and columns exist without losing historical data. */
    @PostConstruct
    public void ensureAnalyticsSchema() {
        try {
            jdbc.execute("""
                ALTER TABLE public.analytics_sessions
                    ADD COLUMN IF NOT EXISTS person_id VARCHAR(64),
                    ADD COLUMN IF NOT EXISTS account_id UUID,
                    ADD COLUMN IF NOT EXISTS user_email VARCHAR(254),
                    ADD COLUMN IF NOT EXISTS user_name VARCHAR(120),
                    ADD COLUMN IF NOT EXISTS user_type VARCHAR(32) DEFAULT 'GUEST',
                    ADD COLUMN IF NOT EXISTS friendly_name VARCHAR(64),
                    ADD COLUMN IF NOT EXISTS display_label VARCHAR(160),
                    ADD COLUMN IF NOT EXISTS masked_ip VARCHAR(48),
                    ADD COLUMN IF NOT EXISTS network_group_id VARCHAR(32),
                    ADD COLUMN IF NOT EXISTS isp VARCHAR(128),
                    ADD COLUMN IF NOT EXISTS asn VARCHAR(64),
                    ADD COLUMN IF NOT EXISTS is_vpn_or_proxy BOOLEAN NOT NULL DEFAULT FALSE,
                    ADD COLUMN IF NOT EXISTS is_datacenter BOOLEAN NOT NULL DEFAULT FALSE,
                    ADD COLUMN IF NOT EXISTS timezone_mismatch BOOLEAN NOT NULL DEFAULT FALSE,
                    ADD COLUMN IF NOT EXISTS location_confidence VARCHAR(24) DEFAULT 'UNKNOWN',
                    ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION,
                    ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION,
                    ADD COLUMN IF NOT EXISTS device_fingerprint VARCHAR(32),
                    ADD COLUMN IF NOT EXISTS max_scroll_depth INTEGER NOT NULL DEFAULT 0,
                    ADD COLUMN IF NOT EXISTS rage_clicks INTEGER NOT NULL DEFAULT 0,
                    ADD COLUMN IF NOT EXISTS engaged BOOLEAN NOT NULL DEFAULT FALSE
                """);
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS public.analytics_visitors (
                    person_id VARCHAR(64) PRIMARY KEY,
                    visitor_hashes TEXT NOT NULL DEFAULT '',
                    account_id UUID,
                    user_email VARCHAR(254),
                    user_name VARCHAR(120),
                    user_type VARCHAR(32) NOT NULL DEFAULT 'GUEST',
                    friendly_name VARCHAR(64) NOT NULL,
                    display_label VARCHAR(160),
                    first_channel VARCHAR(32),
                    first_utm_source VARCHAR(128),
                    first_referrer VARCHAR(512),
                    last_channel VARCHAR(32),
                    last_utm_source VARCHAR(128),
                    last_referrer VARCHAR(512),
                    source_path TEXT DEFAULT '',
                    country VARCHAR(8),
                    region VARCHAR(128),
                    city VARCHAR(128),
                    latitude DOUBLE PRECISION,
                    longitude DOUBLE PRECISION,
                    location_confidence VARCHAR(24) DEFAULT 'UNKNOWN',
                    masked_ip VARCHAR(48),
                    isp VARCHAR(128),
                    asn VARCHAR(64),
                    network_group_id VARCHAR(32),
                    is_vpn_or_proxy BOOLEAN NOT NULL DEFAULT FALSE,
                    is_datacenter BOOLEAN NOT NULL DEFAULT FALSE,
                    timezone_mismatch BOOLEAN NOT NULL DEFAULT FALSE,
                    device_fingerprint VARCHAR(32),
                    devices_used VARCHAR(128) DEFAULT '',
                    total_sessions INTEGER NOT NULL DEFAULT 1,
                    total_page_views INTEGER NOT NULL DEFAULT 1,
                    total_active_seconds INTEGER NOT NULL DEFAULT 0,
                    campaign_views_count INTEGER NOT NULL DEFAULT 0,
                    apply_clicks_count INTEGER NOT NULL DEFAULT 0,
                    rage_clicks_count INTEGER NOT NULL DEFAULT 0,
                    max_scroll_depth INTEGER NOT NULL DEFAULT 0,
                    lead_score INTEGER NOT NULL DEFAULT 0,
                    applied BOOLEAN NOT NULL DEFAULT FALSE,
                    first_applied_at TIMESTAMPTZ,
                    admin_notes TEXT,
                    admin_tags VARCHAR(256),
                    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """);
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS public.analytics_events (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    session_id UUID NOT NULL,
                    visitor_hash VARCHAR(64) NOT NULL,
                    person_id VARCHAR(64) NOT NULL,
                    event_type VARCHAR(32) NOT NULL,
                    path VARCHAR(512) NOT NULL,
                    previous_path VARCHAR(512),
                    campaign_id UUID,
                    dwell_seconds INTEGER DEFAULT 0,
                    scroll_depth INTEGER DEFAULT 0,
                    element_target VARCHAR(256),
                    search_query VARCHAR(256),
                    filter_summary VARCHAR(512),
                    metadata JSONB,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """);
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS public.analytics_campaign_spend (
                    campaign_id UUID PRIMARY KEY,
                    spend_inr NUMERIC(12, 2) NOT NULL DEFAULT 0,
                    notes VARCHAR(256),
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """);
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS public.analytics_daily_summary (
                    day_ist DATE PRIMARY KEY,
                    unique_people BIGINT NOT NULL DEFAULT 0,
                    total_sessions BIGINT NOT NULL DEFAULT 0,
                    engaged_sessions BIGINT NOT NULL DEFAULT 0,
                    total_page_views BIGINT NOT NULL DEFAULT 0,
                    apply_clicks BIGINT NOT NULL DEFAULT 0,
                    applications_submitted BIGINT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """);
        } catch (Exception e) {
            log.warn("Analytics schema auto-check skipped or partial: {}", e.getMessage());
        }
    }

    // =====================================================================
    // DTOs
    // =====================================================================

    @Data
    public static class TrackVisitRequest {
        @NotBlank(message = "visitorId is required")
        @Size(max = 100)
        private String visitorId;

        @NotNull(message = "sessionId is required")
        private UUID sessionId;

        @NotBlank(message = "path is required")
        @Size(max = 2048)
        private String path;

        @Size(max = 2048) private String previousPath;
        @Size(max = 2048) private String referrer;
        @Size(max = 256)  private String utmSource;
        @Size(max = 256)  private String utmMedium;
        @Size(max = 256)  private String utmCampaign;
        @Size(max = 256)  private String utmContent;
        @Size(max = 256)  private String utmTerm;
        private UUID campaignId;
        @Size(max = 32)   private String screenResolution;
        @Size(max = 64)   private String timezone;

        // Optional identity fields if user is logged in on the frontend
        private UUID accountId;
        @Size(max = 254)  private String email;
        @Size(max = 120)  private String fullName;
        @Size(max = 32)   private String userType;
        @Min(0) @Max(86400) private Integer previousPageDwellSeconds;
    }

    @Data
    public static class HeartbeatRequest {
        @NotNull(message = "sessionId is required")
        private UUID sessionId;

        @Min(0) @Max(86_400)
        private int durationSeconds;

        @Min(0) @Max(86_400)
        private int activeDwellSeconds;

        @Size(max = 512)
        private String currentPath;

        @Min(0) @Max(100)
        private Integer maxScrollDepth;
    }

    @Data
    public static class ConversionRequest {
        @NotNull(message = "sessionId is required")
        private UUID sessionId;

        private UUID campaignId;
    }

    @Data
    public static class LeadRequest {
        @NotNull(message = "sessionId is required")
        private UUID sessionId;

        @NotBlank(message = "email is required")
        @Email(message = "email is invalid")
        @Size(max = 254)
        private String email;

        @Size(max = 120)
        private String fullName;

        @Pattern(regexp = "^[+0-9 ()-]{7,20}$", message = "phone is invalid")
        private String phone;

        @Pattern(regexp = "^(BRAND|INFLUENCER|CREATOR|AGENCY|OTHER)$", message = "userType is invalid")
        private String userType;

        @AssertTrue(message = "consent is required")
        private boolean consent;

        @Size(max = 512)
        private String consentText;
    }

    @Data
    public static class TrackEventRequest {
        @NotBlank @Size(max = 100)
        private String visitorId;

        @NotNull
        private UUID sessionId;

        @NotBlank @Size(max = 32)
        private String eventType; // CLICK, SCROLL, APPLY_CLICK, FORM_START, FORM_SUBMIT, SEARCH, FILTER, CAMPAIGN_VIEW, SHARE, RAGE_CLICK

        @NotBlank @Size(max = 512)
        private String path;

        @Size(max = 512) private String previousPath;
        private UUID campaignId;
        @Min(0) @Max(86400) private Integer dwellSeconds;
        @Min(0) @Max(100)   private Integer scrollDepth;
        @Size(max = 256)    private String elementTarget;
        @Size(max = 256)    private String searchQuery;
        @Size(max = 512)    private String filterSummary;
        private Map<String, Object> metadata;
    }

    @Data
    public static class IdentifyVisitorRequest {
        @NotBlank @Size(max = 100)
        private String visitorId;

        private UUID sessionId;
        private UUID accountId;

        @Email @Size(max = 254)
        private String email;

        @Size(max = 120)
        private String fullName;

        @Size(max = 32)
        private String userType; // CREATOR, BRAND, AGENCY
    }

    @Data
    public static class VisitorAdminMetaRequest {
        @Size(max = 2000)
        private String adminNotes;

        @Size(max = 256)
        private String adminTags;
    }

    @Data
    public static class CampaignSpendRequest {
        @NotNull @Min(0)
        private BigDecimal spendInr;

        @Size(max = 256)
        private String notes;
    }

    private record GeoData(
            String country,
            String region,
            String city,
            Double lat,
            Double lon,
            String isp,
            String asn,
            boolean vpnOrProxy,
            boolean datacenter,
            String ipTimezone,
            String confidence
    ) {}

    private record AuthIdentity(UUID accountId, String email, String fullName, String userType) {}

    // =====================================================================
    // PUBLIC INGESTION ENDPOINTS
    // =====================================================================

    @PostMapping("/track")
    @Transactional
    public ResponseEntity<ApiResponse<String>> trackVisit(
            @Valid @RequestBody TrackVisitRequest req,
            HttpServletRequest http) {

        String ua = http.getHeader("User-Agent");
        String rawIp = extractClientIp(http);

        if (shouldIgnoreTraffic(http, ua, rawIp, req.getEmail())) {
            return ResponseEntity.ok(ApiResponse.ok("Ignored (bot/internal)"));
        }

        // Hash ONLY visitorId (stable across WiFi & mobile data). IP is NEVER part of identity.
        String visitorHash = hashStableVisitor(req.getVisitorId());

        AuthIdentity auth = resolveIdentity(req.getAccountId(), req.getEmail(), req.getFullName(), req.getUserType());
        String personId = resolveCanonicalPersonId(visitorHash, auth);

        String path = cleanPath(req.getPath());
        String prevPath = req.getPreviousPath() != null ? cleanPath(req.getPreviousPath()) : null;
        String referrer = clip(req.getReferrer(), 1024);
        String referrerDomain = extractDomain(referrer);

        String deviceType = detectDeviceType(ua);
        String browser = detectBrowser(ua);
        String os = detectOs(ua);
        String inAppSource = detectInAppSource(ua, referrer, deviceType);

        String utmSource = clip(req.getUtmSource(), 128);
        String utmMedium = clip(req.getUtmMedium(), 128);
        String channel = classifyChannel(utmSource, utmMedium, referrerDomain, inAppSource);

        // Real location: CF headers -> free GeoIP backup -> never fake data
        String browserTz = clip(req.getTimezone(), 64);
        GeoData geo = resolveRealLocation(http, rawIp, browserTz);
        boolean tzMismatch = isTimezoneMismatch(browserTz, geo.country(), geo.ipTimezone());
        String confidence = tzMismatch && !"UNKNOWN".equals(geo.confidence()) ? "LOW_VPN" : geo.confidence();

        String maskedIp = maskIp(rawIp);
        String networkGroupId = hashIdentifier("net|" + extractSubnetPrefix(rawIp) + "|" + Objects.toString(geo.isp(), "")).substring(0, 16);
        String language = extractLanguage(http);
        String screenRes = clip(req.getScreenResolution(), 24);
        String fingerprint = hashIdentifier(deviceType + "|" + os + "|" + Objects.toString(screenRes, "") + "|" + Objects.toString(browserTz, "") + "|" + Objects.toString(language, "")).substring(0, 16);

        String friendlyName = generateFriendlyName(personId);
        String placeLabel = (geo.city() != null && !"Unknown".equalsIgnoreCase(geo.city()))
                ? geo.city()
                : (geo.country() != null && !"XX".equalsIgnoreCase(geo.country()) ? geo.country() : "Unknown Place");
        String displayLabel = clip(placeLabel + " Â· " + browser + " Â· " + os + " " + shortTagFor(personId), 160);

        // 1. Idempotent daily unique visitor
        analyticsRepository.recordDailyUniqueVisitor(personId);

        // 2. Atomic session upsert
        analyticsRepository.upsertSession(
                req.getSessionId(),
                visitorHash,
                path,
                referrer,
                referrerDomain,
                utmSource,
                utmMedium,
                clip(req.getUtmCampaign(), 128),
                clip(req.getUtmContent(), 128),
                clip(req.getUtmTerm(), 128),
                req.getCampaignId() != null ? req.getCampaignId().toString() : null,
                channel,
                clip(geo.country(), 8),
                clip(geo.region(), 128),
                clip(geo.city(), 128),
                deviceType,
                browser,
                os,
                inAppSource != null,
                inAppSource,
                screenRes,
                browserTz,
                language);

        // 3. Enrich session with identity / location / network columns
        jdbc.update("""
            UPDATE public.analytics_sessions
            SET person_id = ?,
                account_id = COALESCE(?, account_id),
                user_email = COALESCE(?, user_email),
                user_name = COALESCE(?, user_name),
                user_type = COALESCE(?, user_type, 'GUEST'),
                friendly_name = COALESCE(friendly_name, ?),
                display_label = ?,
                masked_ip = COALESCE(?, masked_ip),
                network_group_id = COALESCE(?, network_group_id),
                isp = COALESCE(?, isp),
                asn = COALESCE(?, asn),
                is_vpn_or_proxy = (is_vpn_or_proxy OR ?),
                is_datacenter = (is_datacenter OR ?),
                timezone_mismatch = (timezone_mismatch OR ?),
                location_confidence = CASE WHEN location_confidence IS NULL OR location_confidence = 'UNKNOWN' THEN ? ELSE location_confidence END,
                latitude = COALESCE(?, latitude),
                longitude = COALESCE(?, longitude),
                device_fingerprint = COALESCE(?, device_fingerprint)
            WHERE session_id = ?
            """,
                personId, auth.accountId(), auth.email(), auth.fullName(), auth.userType(),
                friendlyName, displayLabel, maskedIp, networkGroupId, geo.isp(), geo.asn(),
                geo.vpnOrProxy(), geo.datacenter(), tzMismatch, confidence,
                geo.lat(), geo.lon(), fingerprint, req.getSessionId());

        // 4. Update previous page dwell time if supplied on navigation
        if (req.getPreviousPageDwellSeconds() != null && req.getPreviousPageDwellSeconds() > 0 && prevPath != null) {
            jdbc.update("""
                UPDATE public.analytics_events
                SET dwell_seconds = GREATEST(COALESCE(dwell_seconds, 0), ?)
                WHERE id = (
                    SELECT id FROM public.analytics_events
                    WHERE session_id = ? AND path = ? AND event_type = 'PAGE_VIEW'
                    ORDER BY created_at DESC LIMIT 1
                )
                """, Math.min(req.getPreviousPageDwellSeconds(), 86400), req.getSessionId(), prevPath);
        }

        // 5. Log PAGE_VIEW (and CAMPAIGN_VIEW) in analytics_events
        insertEvent(req.getSessionId(), visitorHash, personId, "PAGE_VIEW", path, prevPath,
                req.getCampaignId(), 0, 0, null, null, null, null);

        if (req.getCampaignId() != null) {
            insertEvent(req.getSessionId(), visitorHash, personId, "CAMPAIGN_VIEW", path, prevPath,
                    req.getCampaignId(), 0, 0, null, null, null, null);
        }

        // 6. Upsert master visitor profile & recalculate lead score
        upsertVisitorProfile(personId, visitorHash, auth, friendlyName, displayLabel, channel,
                utmSource, referrer, geo, confidence, maskedIp, networkGroupId, tzMismatch,
                fingerprint, deviceType, req.getCampaignId() != null);

        return ResponseEntity.ok(ApiResponse.ok("Tracked"));
    }

    @PostMapping("/heartbeat")
    @Transactional
    public ResponseEntity<ApiResponse<String>> heartbeat(@Valid @RequestBody HeartbeatRequest req) {
        analyticsRepository.updateHeartbeat(
                req.getSessionId(),
                Math.max(0, req.getDurationSeconds()),
                Math.max(0, req.getActiveDwellSeconds()));

        if (req.getMaxScrollDepth() != null && req.getMaxScrollDepth() > 0) {
            jdbc.update("""
                UPDATE public.analytics_sessions
                SET max_scroll_depth = GREATEST(max_scroll_depth, ?)
                WHERE session_id = ?
                """, Math.min(100, req.getMaxScrollDepth()), req.getSessionId());
        }

        // Sync active dwell time and last_seen_at to analytics_visitors
        jdbc.update("""
            UPDATE public.analytics_visitors v
            SET last_seen_at = NOW(),
                max_scroll_depth = GREATEST(v.max_scroll_depth, COALESCE(s.max_scroll_depth, 0)),
                total_active_seconds = COALESCE((
                    SELECT SUM(active_dwell_seconds) FROM public.analytics_sessions WHERE person_id = v.person_id
                ), v.total_active_seconds),
                lead_score = LEAST(100,
                    LEAST(25, v.total_page_views * 3) +
                    LEAST(25, COALESCE((SELECT SUM(active_dwell_seconds) FROM public.analytics_sessions WHERE person_id = v.person_id), 0) / 15) +
                    LEAST(20, (v.total_sessions - 1) * 10) +
                    LEAST(15, v.campaign_views_count * 5) +
                    (CASE WHEN v.apply_clicks_count > 0 THEN 15 ELSE 0 END) +
                    (CASE WHEN v.applied THEN 20 ELSE 0 END)
                )
            FROM public.analytics_sessions s
            WHERE s.session_id = ? AND v.person_id = s.person_id
            """, req.getSessionId());

        return ResponseEntity.ok(ApiResponse.ok("Alive"));
    }

    @PostMapping("/conversion")
    @Transactional
    public ResponseEntity<ApiResponse<String>> recordConversion(@Valid @RequestBody ConversionRequest req) {
        analyticsRepository.markApplicationConverted(req.getSessionId());

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT visitor_hash, COALESCE(person_id, visitor_hash) AS person_id,
                   COALESCE(last_path, entry_path) AS path, campaign_id
            FROM public.analytics_sessions WHERE session_id = ?
            """, req.getSessionId());

        for (Map<String, Object> row : rows) {
            String vHash = String.valueOf(row.get("visitor_hash"));
            String pId = String.valueOf(row.get("person_id"));
            String path = String.valueOf(row.get("path"));
            UUID cId = req.getCampaignId();
            if (cId == null) {
                Object raw = row.get("campaign_id");
                if (raw instanceof UUID u) {
                    cId = u;
                } else if (raw != null) {
                    cId = UUID.fromString(raw.toString());
                }
            }

            insertEvent(req.getSessionId(), vHash, pId, "APPLY_CLICK", path, null, cId, 0, 0, null, null, null, null);

            jdbc.update("""
                UPDATE public.analytics_visitors
                SET applied = TRUE,
                    apply_clicks_count = apply_clicks_count + 1,
                    first_applied_at = COALESCE(first_applied_at, NOW()),
                    last_seen_at = NOW(),
                    lead_score = LEAST(100, lead_score + 20)
                WHERE person_id = ?
                """, pId);
        }

        return ResponseEntity.ok(ApiResponse.ok("Conversion recorded"));
    }

    @PostMapping("/lead")
    @Transactional
    public ResponseEntity<ApiResponse<String>> captureLead(@Valid @RequestBody LeadRequest req) {
        String normalizedEmail = req.getEmail().trim().toLowerCase(Locale.ROOT);
        String normalizedType = req.getUserType() != null ? req.getUserType().trim().toUpperCase(Locale.ROOT) : "CREATOR";
        if ("INFLUENCER".equals(normalizedType)) normalizedType = "CREATOR";

        int rows = analyticsRepository.upsertLead(
                req.getSessionId(),
                normalizedEmail,
                clip(req.getFullName(), 120),
                clip(req.getPhone(), 20),
                normalizedType.matches("BRAND|OTHER") ? normalizedType : "INFLUENCER",
                clip(req.getConsentText(), 512));
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown session");
        }

        // Link the visitor's anonymous sessions to their real email/name
        List<Map<String, Object>> sessionRows = jdbc.queryForList(
                "SELECT visitor_hash, COALESCE(last_path, entry_path) AS path FROM public.analytics_sessions WHERE session_id = ?",
                req.getSessionId());
        for (Map<String, Object> row : sessionRows) {
            String vHash = String.valueOf(row.get("visitor_hash"));
            String path = String.valueOf(row.get("path"));
            AuthIdentity identity = new AuthIdentity(null, normalizedEmail, clip(req.getFullName(), 120), normalizedType);
            String canonicalPersonId = stitchIdentity(vHash, identity);
            insertEvent(req.getSessionId(), vHash, canonicalPersonId, "FORM_SUBMIT", path, null, null, 0, 0, "lead_form", null, null, null);
        }

        return ResponseEntity.ok(ApiResponse.ok("Lead saved"));
    }

    /** Records granular journey actions (clicks, scroll depth, search, filter, rage clicks, shares). */
    @PostMapping("/event")
    @Transactional
    public ResponseEntity<ApiResponse<String>> recordEvent(
            @Valid @RequestBody TrackEventRequest req,
            HttpServletRequest http) {

        String ua = http.getHeader("User-Agent");
        String rawIp = extractClientIp(http);
        if (shouldIgnoreTraffic(http, ua, rawIp, null)) {
            return ResponseEntity.ok(ApiResponse.ok("Ignored"));
        }

        String visitorHash = hashStableVisitor(req.getVisitorId());
        String personId = resolveCanonicalPersonId(visitorHash, resolveIdentity(null, null, null, null));
        String eventType = req.getEventType().trim().toUpperCase(Locale.ROOT);
        String path = cleanPath(req.getPath());
        String prevPath = req.getPreviousPath() != null ? cleanPath(req.getPreviousPath()) : null;
        int scroll = req.getScrollDepth() != null ? Math.min(100, Math.max(0, req.getScrollDepth())) : 0;
        int dwell = req.getDwellSeconds() != null ? Math.min(86400, Math.max(0, req.getDwellSeconds())) : 0;

        String metaJson = null;
        if (req.getMetadata() != null && !req.getMetadata().isEmpty()) {
            try {
                metaJson = objectMapper.writeValueAsString(req.getMetadata());
            } catch (Exception ignored) {
                metaJson = null;
            }
        }

        insertEvent(req.getSessionId(), visitorHash, personId, eventType, path, prevPath,
                req.getCampaignId(), dwell, scroll,
                clip(req.getElementTarget(), 256),
                clip(req.getSearchQuery(), 256),
                clip(req.getFilterSummary(), 512),
                metaJson);

        if ("SCROLL".equals(eventType) && scroll > 0) {
            jdbc.update("UPDATE public.analytics_sessions SET max_scroll_depth = GREATEST(max_scroll_depth, ?), last_active_at = NOW() WHERE session_id = ?", scroll, req.getSessionId());
            jdbc.update("UPDATE public.analytics_visitors SET max_scroll_depth = GREATEST(max_scroll_depth, ?), last_seen_at = NOW() WHERE person_id = ?", scroll, personId);
        } else if ("RAGE_CLICK".equals(eventType)) {
            jdbc.update("UPDATE public.analytics_sessions SET rage_clicks = rage_clicks + 1, last_active_at = NOW() WHERE session_id = ?", req.getSessionId());
            jdbc.update("UPDATE public.analytics_visitors SET rage_clicks_count = rage_clicks_count + 1, last_seen_at = NOW() WHERE person_id = ?", personId);
        } else if ("APPLY_CLICK".equals(eventType) || "FORM_SUBMIT".equals(eventType)) {
            analyticsRepository.markApplicationConverted(req.getSessionId());
            boolean isSubmit = "FORM_SUBMIT".equals(eventType);
            jdbc.update("""
                UPDATE public.analytics_visitors
                SET apply_clicks_count = apply_clicks_count + 1,
                    applied = (applied OR ?),
                    first_applied_at = CASE WHEN ? THEN COALESCE(first_applied_at, NOW()) ELSE first_applied_at END,
                    lead_score = LEAST(100, lead_score + 15),
                    last_seen_at = NOW()
                WHERE person_id = ?
                """, isSubmit, isSubmit, personId);
        } else if ("CAMPAIGN_VIEW".equals(eventType)) {
            jdbc.update("UPDATE public.analytics_visitors SET campaign_views_count = campaign_views_count + 1, lead_score = LEAST(100, lead_score + 5), last_seen_at = NOW() WHERE person_id = ?", personId);
        }

        return ResponseEntity.ok(ApiResponse.ok("Event recorded"));
    }

    /** Links anonymous visitor history to a logged-in account and merges phone/laptop visits into one person. */
    @PostMapping("/identify")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> identifyVisitor(
            @Valid @RequestBody IdentifyVisitorRequest req) {

        String visitorHash = hashStableVisitor(req.getVisitorId());
        AuthIdentity auth = resolveIdentity(req.getAccountId(), req.getEmail(), req.getFullName(), req.getUserType());
        String canonicalPersonId = stitchIdentity(visitorHash, auth);

        if (req.getSessionId() != null) {
            insertEvent(req.getSessionId(), visitorHash, canonicalPersonId, "LOGIN", "/", null, null, 0, 0, null, null, null, null);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("personId", canonicalPersonId);
        body.put("status", "Identity linked and historical sessions merged");
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    // =====================================================================
    // ADMIN ENDPOINTS
    // =====================================================================

    @GetMapping("/overview")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOverview(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Boolean excludeVpn) {

        int window = Math.min(Math.max(days, 1), 365);

        long liveUsers = analyticsRepository.countLiveActiveUsers();
        long liveCampaigns = analyticsRepository.countLiveViewingCampaigns();
        long todayVisitors = analyticsRepository.countTodayUniqueVisitors();
        long weekVisitors = analyticsRepository.countThisWeekUniqueVisitors();
        long monthVisitors = analyticsRepository.countThisMonthUniqueVisitors();
        long allTimeVisitors = analyticsRepository.countTotalAllTimeUniqueVisitors();

        Map<String, Object> s = analyticsRepository.getSessionSummary(window);
        long totalSessions = (long) num(s, "total_sessions", 0);
        long uniquePeople = (long) num(s, "unique_people", 0);
        long appliedSessions = (long) num(s, "applied_sessions", 0);
        long appliedPeople = (long) num(s, "applied_people", 0);
        double avgDuration = num(s, "avg_duration", 0);
        double avgActive = num(s, "avg_active", 0);
        double avgPages = num(s, "avg_pages", 0);
        double avgScroll = num(s, "avg_scroll_depth", 0);
        double bounce = num(s, "bounce_rate", 0);
        double engagedRate = num(s, "engaged_rate", 0);
        double mobilePct = num(s, "mobile_pct", 0);
        double desktopPct = num(s, "desktop_pct", 0);
        double tabletPct = num(s, "tablet_pct", 0);
        double inAppPct = num(s, "in_app_pct", 0);
        double returningPct = num(s, "returning_pct", 0);
        double vpnPct = num(s, "vpn_pct", 0);

        long leads = analyticsRepository.countLeadsSince(window);
        double convRate = totalSessions > 0 ? round1(appliedSessions * 100.0 / totalSessions) : 0.0;
        double personConvRate = uniquePeople > 0 ? round1(appliedPeople * 100.0 / uniquePeople) : 0.0;
        double leadRate = totalSessions > 0 ? round1(leads * 100.0 / totalSessions) : 0.0;

        int durMin = (int) (avgDuration / 60);
        int durSec = (int) (avgDuration % 60);
        int activeMin = (int) (avgActive / 60);
        int activeSec = (int) (avgActive % 60);

        List<Map<String, Object>> topChannels = safe(analyticsRepository.getTopTrafficChannels(window));
        List<Map<String, Object>> topCities = safe(analyticsRepository.getTopCities(window));

        long todayReturning = queryLong("""
            SELECT COUNT(DISTINCT COALESCE(person_id, visitor_hash))
            FROM public.analytics_sessions
            WHERE (created_at AT TIME ZONE 'Asia/Kolkata')::date = (NOW() AT TIME ZONE 'Asia/Kolkata')::date
              AND returning_visitor = TRUE
            """);
        String topSourceToday = topChannels.isEmpty() ? "Direct" : String.valueOf(topChannels.get(0).get("channel"));
        String topCityNow = topCities.isEmpty() ? "India" : String.valueOf(topCities.get(0).get("city"));
        String plainEnglishSummary = String.format(Locale.ROOT,
                "%d unique %s today (%d came back). %d live right now. Most traffic arrives via %s from %s.",
                todayVisitors, todayVisitors == 1 ? "person" : "people", todayReturning, liveUsers, topSourceToday, topCityNow);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("windowDays", window);
        out.put("plainEnglishSummary", plainEnglishSummary);

        Map<String, Object> live = new LinkedHashMap<>();
        live.put("activeUsersRightNow", liveUsers);
        live.put("currentlyViewingCampaigns", liveCampaigns);
        live.put("activeVisitorsList", getLiveVisitorsNow());
        out.put("live", live);

        Map<String, Object> traffic = new LinkedHashMap<>();
        traffic.put("todayUniqueVisitors", todayVisitors);
        traffic.put("thisWeekUniqueVisitors", weekVisitors);
        traffic.put("thisMonthUniqueVisitors", monthVisitors);
        traffic.put("allTimeUniqueVisitors", allTimeVisitors);
        traffic.put("uniquePeopleInWindow", uniquePeople);
        traffic.put("sessionsInWindow", totalSessions);
        out.put("trafficVolume", traffic);

        Map<String, Object> engagement = new LinkedHashMap<>();
        engagement.put("averageSessionDurationSeconds", Math.round(avgDuration));
        engagement.put("averageEngagementDisplay", String.format(Locale.ROOT, "%dm %02ds", durMin, durSec));
        engagement.put("averageActiveDwellSeconds", Math.round(avgActive));
        engagement.put("averageActiveDwellDisplay", String.format(Locale.ROOT, "%dm %02ds", activeMin, activeSec));
        engagement.put("bounceRatePercentage", round1(bounce));
        engagement.put("engagedVisitPercentage", round1(engagedRate));
        engagement.put("averagePageViewsPerSession", round1(avgPages));
        engagement.put("averageScrollDepthPercentage", round1(avgScroll));
        out.put("engagement", engagement);

        Map<String, Object> env = new LinkedHashMap<>();
        env.put("mobilePercentage", round1(mobilePct));
        env.put("desktopPercentage", round1(desktopPct));
        env.put("tabletPercentage", round1(tabletPct));
        env.put("inAppBrowserPercentage", round1(inAppPct));
        env.put("returningVisitorPercentage", round1(returningPct));
        env.put("newVisitorPercentage", totalSessions > 0 ? round1(100.0 - returningPct) : 0.0);
        env.put("vpnOrDatacenterPercentage", round1(vpnPct));
        out.put("environment", env);

        Map<String, Object> funnel = new LinkedHashMap<>();
        funnel.put("sessions", totalSessions);
        funnel.put("uniquePeople", uniquePeople);
        funnel.put("applyButtonClicks", appliedSessions);
        funnel.put("uniquePeopleApplied", appliedPeople);
        funnel.put("overallConversionRate", convRate);
        funnel.put("personConversionRate", personConvRate);
        funnel.put("optInLeads", leads);
        funnel.put("leadCaptureRate", leadRate);
        out.put("funnelConversion", funnel);

        out.put("topTrafficChannels", topChannels);
        out.put("topCampaigns", safe(analyticsRepository.getTopCampaigns(window)));
        out.put("topReferrers", safe(analyticsRepository.getTopReferrers(window)));
        out.put("topLandingPages", safe(analyticsRepository.getTopLandingPages(window)));
        out.put("topCities", topCities);
        out.put("dailyVisitorTrend", safe(analyticsRepository.getDailyVisitorTrend(window)));

        out.put("visitFrequencyGroups", getVisitFrequencyGroups(window));
        out.put("userTypeSplit", getUserTypeSplit(window));
        out.put("firstVsLastSource", getFirstVsLastAttribution(window));
        out.put("bestTimeToPostHeatmapIst", getIstHourlyHeatmap(window));
        out.put("timeToConvert", getTimeToConvertStats(window));
        out.put("hotVisitors", getHotVisitors(10));
        out.put("smartAlerts", checkTrafficAnomalies());

        Map<String, Object> definitions = new LinkedHashMap<>();
        definitions.put("activeUsersRightNow", "Distinct real people who opened a page or scrolled in the last 5 minutes.");
        definitions.put("uniquePeopleInWindow", "Every person counted once across mobile data, WiFi, and linked devices.");
        definitions.put("engagedVisitPercentage", "Visits where the person spent 15+ active seconds, viewed 2+ pages, or clicked Apply.");
        definitions.put("bounceRatePercentage", "Visits that left in under 15 active seconds after seeing only 1 page.");
        definitions.put("leadScore", "0-100 score based on pages viewed, active time, return visits, campaign views, and apply clicks.");
        definitions.put("locationConfidence", "How sure we are of the place: EXACT_CITY, STATE_ONLY, COUNTRY_ONLY, LOW_VPN, or UNKNOWN.");
        out.put("metricDefinitions", definitions);

        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    /** Visitors directory (one row per person). */
    @GetMapping("/visitors")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVisitors(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Boolean applied,
            @RequestParam(required = false) Boolean hideVpn,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "last_seen_at") String sortBy) {

        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        int offset = safePage * safeSize;

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            where.append("""
                 AND (LOWER(COALESCE(user_name, '')) LIKE ?
                   OR LOWER(COALESCE(user_email, '')) LIKE ?
                   OR LOWER(COALESCE(friendly_name, '')) LIKE ?
                   OR LOWER(COALESCE(display_label, '')) LIKE ?
                   OR LOWER(COALESCE(city, '')) LIKE ?
                   OR LOWER(COALESCE(isp, '')) LIKE ?
                   OR LOWER(COALESCE(admin_notes, '')) LIKE ?
                   OR LOWER(COALESCE(admin_tags, '')) LIKE ?)
                """);
            for (int i = 0; i < 8; i++) params.add(like);
        }
        if (country != null && !country.isBlank()) {
            where.append(" AND UPPER(COALESCE(country, '')) = ? ");
            params.add(country.trim().toUpperCase(Locale.ROOT));
        }
        if (state != null && !state.isBlank()) {
            where.append(" AND LOWER(COALESCE(region, '')) = ? ");
            params.add(state.trim().toLowerCase(Locale.ROOT));
        }
        if (city != null && !city.isBlank()) {
            where.append(" AND LOWER(COALESCE(city, '')) = ? ");
            params.add(city.trim().toLowerCase(Locale.ROOT));
        }
        if (userType != null && !userType.isBlank()) {
            where.append(" AND UPPER(COALESCE(user_type, 'GUEST')) = ? ");
            params.add(userType.trim().toUpperCase(Locale.ROOT));
        }
        if (channel != null && !channel.isBlank()) {
            where.append(" AND (LOWER(COALESCE(first_channel, '')) = ? OR LOWER(COALESCE(last_channel, '')) = ?) ");
            params.add(channel.trim().toLowerCase(Locale.ROOT));
            params.add(channel.trim().toLowerCase(Locale.ROOT));
        }
        if (applied != null) {
            where.append(" AND applied = ? ");
            params.add(applied);
        }
        if (Boolean.TRUE.equals(hideVpn)) {
            where.append(" AND is_vpn_or_proxy = FALSE AND is_datacenter = FALSE ");
        }

        String orderCol = switch (sortBy) {
            case "lead_score", "leadScore" -> "lead_score DESC, last_seen_at DESC";
            case "total_sessions", "visits" -> "total_sessions DESC, last_seen_at DESC";
            case "total_active_seconds", "activeTime" -> "total_active_seconds DESC, last_seen_at DESC";
            case "first_seen_at", "firstSeen" -> "first_seen_at DESC";
            default -> "last_seen_at DESC";
        };

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM public.analytics_visitors " + where, Long.class, params.toArray());
        long totalElements = total != null ? total : 0L;

        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safeSize);
        queryParams.add(offset);

        List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT * FROM public.analytics_visitors " + where + " ORDER BY " + orderCol + " LIMIT ? OFFSET ?",
                queryParams.toArray());

        for (Map<String, Object> row : items) {
            row.put("primaryDisplayName", resolvePrimaryName(row));
            row.put("quickTags", buildQuickTags(row));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", items);
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("totalElements", totalElements);
        result.put("totalPages", (int) Math.ceil((double) totalElements / safeSize));
        result.put("sameNetworkGroups", getSameNetworkGroups());
        result.put("possibleDuplicateHints", getPossibleDuplicateHints());

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /** One-person full story timeline + cross-device sessions. */
    @GetMapping("/visitors/{personId}/timeline")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVisitorTimeline(
            @PathVariable String personId) {

        List<Map<String, Object>> profiles = jdbc.queryForList(
                "SELECT * FROM public.analytics_visitors WHERE person_id = ?", personId);
        Map<String, Object> profile = profiles.isEmpty() ? new LinkedHashMap<>() : new LinkedHashMap<>(profiles.get(0));
        if (!profile.isEmpty()) {
            profile.put("primaryDisplayName", resolvePrimaryName(profile));
            profile.put("quickTags", buildQuickTags(profile));
        }

        List<Map<String, Object>> sessions = jdbc.queryForList("""
            SELECT session_id, visitor_hash, device_type, browser, os, in_app_browser, in_app_source,
                   channel, utm_source, utm_campaign, referrer_domain, entry_path, last_path,
                   city, region, country, location_confidence, masked_ip, isp,
                   is_vpn_or_proxy, is_datacenter, timezone_mismatch,
                   page_views, duration_seconds, active_dwell_seconds, max_scroll_depth,
                   rage_clicks, engaged, applied, applied_at, created_at, last_active_at
            FROM public.analytics_sessions
            WHERE person_id = ? OR visitor_hash = ?
            ORDER BY created_at DESC
            """, personId, personId);

        List<Map<String, Object>> events = jdbc.queryForList("""
            SELECT id, session_id, event_type, path, previous_path, campaign_id,
                   dwell_seconds, scroll_depth, element_target, search_query, filter_summary,
                   metadata, created_at
            FROM public.analytics_events
            WHERE person_id = ? OR visitor_hash = ?
            ORDER BY created_at ASC
            LIMIT 500
            """, personId, personId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("visitor", profile);
        out.put("sessions", sessions);
        out.put("timelineEvents", events);
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    /** Save admin notes and labels on a visitor. */
    @PatchMapping("/visitors/{personId}/meta")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @Transactional
    public ResponseEntity<ApiResponse<String>> updateVisitorAdminMeta(
            @PathVariable String personId,
            @Valid @RequestBody VisitorAdminMetaRequest req) {

        int updated = jdbc.update("""
            UPDATE public.analytics_visitors
            SET admin_notes = ?,
                admin_tags = ?
            WHERE person_id = ?
            """, clip(req.getAdminNotes(), 2000), clip(req.getAdminTags(), 256), personId);

        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Visitor not found");
        }
        return ResponseEntity.ok(ApiResponse.ok("Visitor notes and tags saved"));
    }

    /** Privacy data-delete option for any person. */
    @DeleteMapping("/visitors/{personId}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @Transactional
    public ResponseEntity<ApiResponse<String>> deleteVisitorData(@PathVariable String personId) {
        jdbc.update("DELETE FROM public.analytics_leads WHERE visitor_hash = ? OR session_id IN (SELECT session_id FROM public.analytics_sessions WHERE person_id = ?)", personId, personId);
        jdbc.update("DELETE FROM public.analytics_events WHERE person_id = ? OR visitor_hash = ?", personId, personId);
        jdbc.update("DELETE FROM public.analytics_sessions WHERE person_id = ? OR visitor_hash = ?", personId, personId);
        jdbc.update("DELETE FROM public.analytics_visitors WHERE person_id = ?", personId);
        return ResponseEntity.ok(ApiResponse.ok("All analytics data for this person has been permanently deleted"));
    }

    /** Journeys, top paths, drop-off report, before/after page flow, scroll depth, rage clicks, search/filter insights. */
    @GetMapping("/journeys")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getJourneys(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) String inspectPath) {

        int window = Math.min(Math.max(days, 1), 365);
        Map<String, Object> out = new LinkedHashMap<>();

        out.put("pageAttentionAndScroll", jdbc.queryForList("""
            SELECT
                path,
                COUNT(*) FILTER (WHERE event_type = 'PAGE_VIEW') AS views,
                COUNT(DISTINCT person_id) AS unique_people,
                ROUND(AVG(NULLIF(dwell_seconds, 0)) FILTER (WHERE event_type = 'PAGE_VIEW'), 1) AS avg_dwell_seconds,
                ROUND(AVG(NULLIF(scroll_depth, 0)) FILTER (WHERE event_type = 'SCROLL'), 0) AS avg_scroll_depth,
                COUNT(*) FILTER (WHERE event_type = 'SCROLL' AND scroll_depth >= 75) AS read_75_pct_count
            FROM public.analytics_events
            WHERE created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            GROUP BY path
            ORDER BY views DESC
            LIMIT 25
            """, window));

        out.put("mostCommonPaths", jdbc.queryForList("""
            WITH session_routes AS (
                SELECT
                    e.session_id,
                    STRING_AGG(e.path, ' â†’ ' ORDER BY e.created_at ASC) AS route,
                    BOOL_OR(s.applied) AS converted
                FROM (
                    SELECT session_id, path, created_at,
                           ROW_NUMBER() OVER (PARTITION BY session_id ORDER BY created_at ASC) AS step_no
                    FROM public.analytics_events
                    WHERE event_type = 'PAGE_VIEW'
                      AND created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
                ) e
                JOIN public.analytics_sessions s ON s.session_id = e.session_id
                WHERE e.step_no <= 5
                GROUP BY e.session_id
            )
            SELECT
                route,
                COUNT(*) AS sessions,
                COUNT(*) FILTER (WHERE converted = TRUE) AS conversions,
                ROUND(100.0 * COUNT(*) FILTER (WHERE converted = TRUE) / NULLIF(COUNT(*), 0), 1) AS conversion_rate
            FROM session_routes
            GROUP BY route
            ORDER BY sessions DESC
            LIMIT 20
            """, window));

        out.put("pathFlowTransitions", jdbc.queryForList("""
            SELECT
                previous_path AS from_page,
                path AS to_page,
                COUNT(*) AS transitions
            FROM public.analytics_events
            WHERE event_type = 'PAGE_VIEW'
              AND previous_path IS NOT NULL
              AND previous_path <> path
              AND created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            GROUP BY previous_path, path
            ORDER BY transitions DESC
            LIMIT 30
            """, window));

        out.put("dropOffPages", jdbc.queryForList("""
            SELECT
                COALESCE(last_path, entry_path) AS exit_page,
                COUNT(*) AS total_exits,
                COUNT(*) FILTER (WHERE applied = FALSE) AS unconverted_exits,
                ROUND(100.0 * COUNT(*) FILTER (WHERE applied = FALSE) / NULLIF(COUNT(*), 0), 1) AS drop_off_rate_pct,
                ROUND(AVG(active_dwell_seconds), 1) AS avg_active_seconds_before_exit
            FROM public.analytics_sessions
            WHERE created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            GROUP BY COALESCE(last_path, entry_path)
            ORDER BY unconverted_exits DESC
            LIMIT 20
            """, window));

        String targetPage = (inspectPath != null && !inspectPath.isBlank()) ? cleanPath(inspectPath) : "/campaigns";
        Map<String, Object> beforeAfter = new LinkedHashMap<>();
        beforeAfter.put("inspectedPath", targetPage);
        beforeAfter.put("viewedBefore", jdbc.queryForList("""
            SELECT previous_path AS page, COUNT(*) AS count
            FROM public.analytics_events
            WHERE event_type = 'PAGE_VIEW' AND path = ? AND previous_path IS NOT NULL
              AND created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            GROUP BY previous_path ORDER BY count DESC LIMIT 10
            """, targetPage, window));
        beforeAfter.put("viewedAfter", jdbc.queryForList("""
            SELECT path AS page, COUNT(*) AS count
            FROM public.analytics_events
            WHERE event_type = 'PAGE_VIEW' AND previous_path = ? AND path IS NOT NULL
              AND created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            GROUP BY path ORDER BY count DESC LIMIT 10
            """, targetPage, window));
        out.put("pageBeforeAndAfter", beforeAfter);

        out.put("frustrationSignals", jdbc.queryForList("""
            SELECT
                path,
                COALESCE(element_target, 'Unknown element') AS element,
                COUNT(*) AS rage_click_events,
                COUNT(DISTINCT person_id) AS affected_people
            FROM public.analytics_events
            WHERE event_type = 'RAGE_CLICK'
              AND created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            GROUP BY path, COALESCE(element_target, 'Unknown element')
            ORDER BY rage_click_events DESC
            LIMIT 20
            """, window));

        out.put("campaignFunnels", getCampaignLevelFunnels(window));

        out.put("topSearches", jdbc.queryForList("""
            SELECT LOWER(TRIM(search_query)) AS query, COUNT(*) AS searches, COUNT(DISTINCT person_id) AS unique_people
            FROM public.analytics_events
            WHERE event_type = 'SEARCH' AND search_query IS NOT NULL AND TRIM(search_query) <> ''
              AND created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            GROUP BY LOWER(TRIM(search_query))
            ORDER BY searches DESC LIMIT 20
            """, window));

        out.put("topFiltersUsed", jdbc.queryForList("""
            SELECT filter_summary AS filter_used, COUNT(*) AS uses, COUNT(DISTINCT person_id) AS unique_people
            FROM public.analytics_events
            WHERE event_type = 'FILTER' AND filter_summary IS NOT NULL AND TRIM(filter_summary) <> ''
              AND created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            GROUP BY filter_summary
            ORDER BY uses DESC LIMIT 20
            """, window));

        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    /** Custom funnel builder (any sequence of paths or action types). */
    @GetMapping("/funnel")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> evaluateFunnel(
            @RequestParam(defaultValue = "/,/campaigns,CAMPAIGN_VIEW,APPLY_CLICK,FORM_SUBMIT") String steps,
            @RequestParam(defaultValue = "30") int days) {

        int window = Math.min(Math.max(days, 1), 365);
        List<String> stepList = Arrays.stream(steps.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .limit(8)
                .toList();

        Set<String> previousStepPeople = null;
        long firstStepCount = 0;
        List<Map<String, Object>> funnelOut = new ArrayList<>();

        for (int i = 0; i < stepList.size(); i++) {
            String step = stepList.get(i);
            List<String> matchingPeople;
            if ("APPLIED".equalsIgnoreCase(step)) {
                matchingPeople = jdbc.queryForList("""
                    SELECT DISTINCT COALESCE(person_id, visitor_hash)
                    FROM public.analytics_sessions
                    WHERE applied = TRUE AND created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
                    """, String.class, window);
            } else if (step.startsWith("/")) {
                matchingPeople = jdbc.queryForList("""
                    SELECT DISTINCT person_id FROM public.analytics_events
                    WHERE event_type = 'PAGE_VIEW' AND (path = ? OR path LIKE ?)
                      AND created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
                    """, String.class, step, step + "%", window);
            } else {
                matchingPeople = jdbc.queryForList("""
                    SELECT DISTINCT person_id FROM public.analytics_events
                    WHERE UPPER(event_type) = ?
                      AND created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
                    """, String.class, step.toUpperCase(Locale.ROOT), window);
            }

            Set<String> currentSet = new HashSet<>(matchingPeople);
            if (previousStepPeople != null) {
                currentSet.retainAll(previousStepPeople);
            }
            long count = currentSet.size();
            long prevCount = previousStepPeople == null ? count : previousStepPeople.size();
            if (i == 0) firstStepCount = count;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stepNumber", i + 1);
            row.put("step", step);
            row.put("people", count);
            row.put("retainedFromPreviousPct", prevCount > 0 ? round1(count * 100.0 / prevCount) : 0.0);
            row.put("dropOffFromPrevious", Math.max(0, prevCount - count));
            row.put("dropOffFromPreviousPct", prevCount > 0 ? round1((prevCount - count) * 100.0 / prevCount) : 0.0);
            row.put("overallConversionFromStartPct", firstStepCount > 0 ? round1(count * 100.0 / firstStepCount) : 0.0);
            funnelOut.add(row);

            previousStepPeople = currentSet;
        }

        return ResponseEntity.ok(ApiResponse.ok(funnelOut));
    }

    /** World map dots, state -> city drill-down, ISP/network breakdown, VPN & confidence stats. */
    @GetMapping("/locations")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLocationAnalytics(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "IN") String country,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "false") boolean excludeVpn) {

        int window = Math.min(Math.max(days, 1), 365);
        String vpnFilter = excludeVpn ? " AND is_vpn_or_proxy = FALSE AND is_datacenter = FALSE " : "";
        String countryCode = country.trim().toUpperCase(Locale.ROOT);

        Map<String, Object> out = new LinkedHashMap<>();

        String cityDotsSql = """
            SELECT
                city, region AS state, country,
                ROUND(CAST(AVG(latitude) AS NUMERIC), 4) AS lat,
                ROUND(CAST(AVG(longitude) AS NUMERIC), 4) AS lon,
                COUNT(DISTINCT COALESCE(person_id, visitor_hash)) AS unique_people,
                COUNT(*) AS sessions,
                COUNT(*) FILTER (WHERE applied = TRUE) AS conversions
            FROM public.analytics_sessions
            WHERE latitude IS NOT NULL AND longitude IS NOT NULL
              AND created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            """ + vpnFilter + """
            GROUP BY city, region, country
            ORDER BY unique_people DESC
            LIMIT 100
            """;
        out.put("cityMapDots", jdbc.queryForList(cityDotsSql, window));

        String statesSql = """
            SELECT
                COALESCE(NULLIF(region, 'Unknown'), 'Unknown State') AS state,
                COUNT(DISTINCT COALESCE(person_id, visitor_hash)) AS unique_people,
                COUNT(*) AS sessions,
                COUNT(*) FILTER (WHERE applied = TRUE) AS conversions
            FROM public.analytics_sessions
            WHERE UPPER(COALESCE(country, 'IN')) = ?
              AND created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            """ + vpnFilter + """
            GROUP BY COALESCE(NULLIF(region, 'Unknown'), 'Unknown State')
            ORDER BY unique_people DESC
            """;
        out.put("statesBreakdown", jdbc.queryForList(statesSql, countryCode, window));

        if (state != null && !state.isBlank()) {
            String citiesSql = """
                SELECT
                    COALESCE(NULLIF(city, 'Unknown'), 'Unknown City') AS city,
                    COUNT(DISTINCT COALESCE(person_id, visitor_hash)) AS unique_people,
                    COUNT(*) AS sessions,
                    COUNT(*) FILTER (WHERE applied = TRUE) AS conversions
                FROM public.analytics_sessions
                WHERE UPPER(COALESCE(country, 'IN')) = ?
                  AND LOWER(COALESCE(region, '')) = ?
                  AND created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
                """ + vpnFilter + """
                GROUP BY COALESCE(NULLIF(city, 'Unknown'), 'Unknown City')
                ORDER BY unique_people DESC
                """;
            out.put("citiesInSelectedState", jdbc.queryForList(citiesSql, countryCode, state.trim().toLowerCase(Locale.ROOT), window));
        }

        out.put("topIspsAndNetworks", jdbc.queryForList("""
            SELECT
                COALESCE(isp, 'Unknown Provider') AS isp,
                COUNT(DISTINCT COALESCE(person_id, visitor_hash)) AS unique_people,
                COUNT(*) AS sessions,
                COUNT(*) FILTER (WHERE device_type = 'Mobile') AS mobile_sessions,
                COUNT(*) FILTER (WHERE device_type = 'Desktop') AS desktop_sessions
            FROM public.analytics_sessions
            WHERE created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            GROUP BY COALESCE(isp, 'Unknown Provider')
            ORDER BY unique_people DESC
            LIMIT 20
            """, window));

        out.put("locationConfidenceSummary", jdbc.queryForList("""
            SELECT
                COALESCE(location_confidence, 'UNKNOWN') AS confidence,
                COUNT(*) AS sessions,
                COUNT(DISTINCT COALESCE(person_id, visitor_hash)) AS unique_people
            FROM public.analytics_sessions
            WHERE created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            GROUP BY COALESCE(location_confidence, 'UNKNOWN')
            ORDER BY sessions DESC
            """, window));

        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    /** Set campaign spend so the dashboard calculates Cost Per Apply (CPA). */
    @PutMapping("/campaigns/{campaignId}/spend")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @Transactional
    public ResponseEntity<ApiResponse<String>> updateCampaignSpend(
            @PathVariable UUID campaignId,
            @Valid @RequestBody CampaignSpendRequest req) {

        jdbc.update("""
            INSERT INTO public.analytics_campaign_spend (campaign_id, spend_inr, notes, updated_at)
            VALUES (?, ?, ?, NOW())
            ON CONFLICT (campaign_id) DO UPDATE SET
                spend_inr = EXCLUDED.spend_inr,
                notes = EXCLUDED.notes,
                updated_at = NOW()
            """, campaignId, req.getSpendInr(), clip(req.getNotes(), 256));

        return ResponseEntity.ok(ApiResponse.ok("Campaign spend saved"));
    }

    /** Export Visitors, Sessions, Journeys, or Leads as CSV. */
    @GetMapping("/export/{type}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportCsv(
            @PathVariable String type,
            @RequestParam(defaultValue = "30") int days) {

        int window = Math.min(Math.max(days, 1), 365);
        StringBuilder csv = new StringBuilder("\uFEFF");

        switch (type.toLowerCase(Locale.ROOT)) {
            case "visitors" -> {
                csv.append("Person ID,Name,Email,User Type,City,State,Country,Confidence,ISP,Masked IP,First Source,Last Source,Source Path,Visits,Page Views,Active Seconds,Lead Score,Applied,Admin Tags,Admin Notes,First Seen,Last Seen\r\n");
                List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM public.analytics_visitors ORDER BY last_seen_at DESC LIMIT 5000");
                for (Map<String, Object> r : rows) {
                    csv.append(csvLine(
                            r.get("person_id"), resolvePrimaryName(r), r.get("user_email"), r.get("user_type"),
                            r.get("city"), r.get("region"), r.get("country"), r.get("location_confidence"),
                            r.get("isp"), r.get("masked_ip"), r.get("first_channel"), r.get("last_channel"),
                            r.get("source_path"), r.get("total_sessions"), r.get("total_page_views"),
                            r.get("total_active_seconds"), r.get("lead_score"), r.get("applied"),
                            r.get("admin_tags"), r.get("admin_notes"), r.get("first_seen_at"), r.get("last_seen_at")
                    ));
                }
            }
            case "journeys" -> {
                csv.append("Event Time,Person ID,Session ID,Event Type,Path,Previous Path,Dwell Seconds,Scroll Depth,Element Target,Search Query,Filter Summary\r\n");
                List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT created_at, person_id, session_id, event_type, path, previous_path, dwell_seconds, scroll_depth, element_target, search_query, filter_summary
                    FROM public.analytics_events
                    WHERE created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
                    ORDER BY created_at DESC LIMIT 5000
                    """, window);
                for (Map<String, Object> r : rows) {
                    csv.append(csvLine(
                            r.get("created_at"), r.get("person_id"), r.get("session_id"), r.get("event_type"),
                            r.get("path"), r.get("previous_path"), r.get("dwell_seconds"), r.get("scroll_depth"),
                            r.get("element_target"), r.get("search_query"), r.get("filter_summary")
                    ));
                }
            }
            case "leads" -> {
                csv.append("Created At,Email,Full Name,Phone,User Type,Channel,UTM Source,City,State,Country,Device\r\n");
                for (Map<String, Object> r : safe(analyticsRepository.getRecentLeads())) {
                    csv.append(csvLine(
                            r.get("created_at"), r.get("email"), r.get("full_name"), r.get("phone"),
                            r.get("user_type"), r.get("channel"), r.get("utm_source"), r.get("city"),
                            r.get("region"), r.get("country"), r.get("device_type")
                    ));
                }
            }
            default -> {
                csv.append("Session ID,Person ID,Display Label,Channel,Entry Path,Last Path,City,State,Country,ISP,Masked IP,Device,Browser,OS,Page Views,Active Seconds,Scroll Depth,Engaged,Applied,Created At\r\n");
                List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT session_id, person_id, display_label, channel, entry_path, last_path, city, region, country, isp, masked_ip,
                           device_type, browser, os, page_views, active_dwell_seconds, max_scroll_depth, engaged, applied, created_at
                    FROM public.analytics_sessions
                    WHERE created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
                    ORDER BY created_at DESC LIMIT 5000
                    """, window);
                for (Map<String, Object> r : rows) {
                    csv.append(csvLine(
                            r.get("session_id"), r.get("person_id"), r.get("display_label"), r.get("channel"),
                            r.get("entry_path"), r.get("last_path"), r.get("city"), r.get("region"), r.get("country"),
                            r.get("isp"), r.get("masked_ip"), r.get("device_type"), r.get("browser"), r.get("os"),
                            r.get("page_views"), r.get("active_dwell_seconds"), r.get("max_scroll_depth"),
                            r.get("engaged"), r.get("applied"), r.get("created_at")
                    ));
                }
            }
        }

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("brandcollabz-" + type + "-export.csv", StandardCharsets.UTF_8).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .contentLength(bytes.length)
                .body(bytes);
    }

    @GetMapping("/sessions")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Page<AnalyticsSessionEntity>>> getSessions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Boolean applied,
            @RequestParam(required = false) Boolean excludeVpn,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Boolean activeNow,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        final String device;
        if (deviceType == null || deviceType.isBlank()) {
            device = null;
        } else {
            device = DEVICES.get(deviceType.trim().toLowerCase(Locale.ROOT));
            if (device == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "deviceType must be Mobile, Desktop or Tablet");
            }
        }

        final String search = (q == null || q.isBlank()) ? null : q.trim().toLowerCase(Locale.ROOT);
        final String channelFilter = (channel == null || channel.isBlank()) ? null : channel.trim();

        Specification<AnalyticsSessionEntity> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();

            if (search != null) {
                String like = "%" + escapeLike(search) + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.<String>get("city")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("region")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("friendlyName")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("displayLabel")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("userName")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("userEmail")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("isp")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("entryPath")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("lastPath")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("referrer")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("utmSource")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("utmMedium")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("utmCampaign")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("utmContent")), like, '\\')));
            }
            if (device != null) {
                p.add(cb.equal(root.get("deviceType"), device));
            }
            if (channelFilter != null) {
                p.add(cb.equal(cb.lower(root.<String>get("channel")), channelFilter.toLowerCase(Locale.ROOT)));
            }
            if (country != null && !country.isBlank()) {
                p.add(cb.equal(cb.upper(root.<String>get("country")), country.trim().toUpperCase(Locale.ROOT)));
            }
            if (city != null && !city.isBlank()) {
                p.add(cb.equal(cb.lower(root.<String>get("city")), city.trim().toLowerCase(Locale.ROOT)));
            }
            if (applied != null) {
                p.add(cb.equal(root.get("applied"), applied));
            }
            if (Boolean.TRUE.equals(excludeVpn)) {
                p.add(cb.isFalse(root.<Boolean>get("vpnOrProxy")));
                p.add(cb.isFalse(root.<Boolean>get("datacenter")));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.<OffsetDateTime>get("createdAt"),
                        from.atStartOfDay(IST).toOffsetDateTime()));
            }
            if (to != null) {
                p.add(cb.lessThan(root.<OffsetDateTime>get("createdAt"),
                        to.plusDays(1).atStartOfDay(IST).toOffsetDateTime()));
            }
            if (Boolean.TRUE.equals(activeNow)) {
                p.add(cb.greaterThanOrEqualTo(root.<OffsetDateTime>get("lastActiveAt"),
                        OffsetDateTime.now().minusMinutes(5)));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };

        String sortField = SORTABLE.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(dir, sortField).and(Sort.by(Sort.Direction.DESC, "sessionId"));

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort);
        Page<AnalyticsSessionEntity> result = analyticsRepository.findAll(spec, pageable);

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/sessions/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<AnalyticsSessionEntity>> getSession(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(analyticsRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"))));
    }

    @GetMapping("/leads")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getLeads() {
        return ResponseEntity.ok(ApiResponse.ok(safe(analyticsRepository.getRecentLeads())));
    }

    // =====================================================================
    // INTERNAL ANALYTICS ENGINES (IDENTITY, LOCATION, FUNNELS, SCORING)
    // =====================================================================

    /** Extracts identity from Spring Security JWT if logged in, or from explicit request parameters. */
    private AuthIdentity resolveIdentity(UUID reqAccountId, String reqEmail, String reqName, String reqType) {
        UUID accId = reqAccountId;
        String email = reqEmail != null && !reqEmail.isBlank() ? reqEmail.trim().toLowerCase(Locale.ROOT) : null;
        String name = clip(reqName, 120);
        String role = reqType != null && !reqType.isBlank() ? reqType.trim().toUpperCase(Locale.ROOT) : null;

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
                if (accId == null && jwt.getSubject() != null) {
                    try {
                        accId = UUID.fromString(jwt.getSubject());
                    } catch (Exception ignored) {
                        // subject is not a UUID
                    }
                }
                if (email == null && jwt.hasClaim("email")) {
                    email = jwt.getClaimAsString("email").trim().toLowerCase(Locale.ROOT);
                }
                if (name == null && jwt.hasClaim("name")) {
                    name = clip(jwt.getClaimAsString("name"), 120);
                }
                if (role == null && jwt.hasClaim("role")) {
                    role = jwt.getClaimAsString("role").toUpperCase(Locale.ROOT);
                }
            }
        } catch (Exception ignored) {
            // anonymous request
        }

        if ("INFLUENCER".equals(role)) role = "CREATOR";
        if (role != null && !Set.of("CREATOR", "BRAND", "AGENCY", "ADMIN").contains(role)) {
            role = "GUEST";
        }
        return new AuthIdentity(accId, email, name, role);
    }

    private String resolveCanonicalPersonId(String visitorHash, AuthIdentity auth) {
        if (auth.accountId() != null || auth.email() != null) {
            return stitchIdentity(visitorHash, auth);
        }
        List<String> existing = jdbc.queryForList(
                "SELECT person_id FROM public.analytics_sessions WHERE visitor_hash = ? AND person_id IS NOT NULL ORDER BY last_active_at DESC LIMIT 1",
                String.class, visitorHash);
        return existing.isEmpty() ? visitorHash : existing.get(0);
    }

    /** Connects anonymous visits to real accounts and merges phone + laptop duplicates. */
    private String stitchIdentity(String visitorHash, AuthIdentity auth) {
        String canonicalId = visitorHash;
        if (auth.accountId() != null) {
            canonicalId = "acc_" + hashIdentifier(auth.accountId().toString()).substring(0, 48);
        } else if (auth.email() != null) {
            canonicalId = "usr_" + hashIdentifier(auth.email()).substring(0, 48);
        }

        jdbc.update("""
            UPDATE public.analytics_sessions
            SET person_id = ?,
                account_id = COALESCE(CAST(? AS UUID), account_id),
                user_email = COALESCE(CAST(? AS VARCHAR), user_email),
                user_name = COALESCE(CAST(? AS VARCHAR), user_name),
                user_type = COALESCE(CAST(? AS VARCHAR), user_type)
            WHERE visitor_hash = ?
               OR (CAST(? AS UUID) IS NOT NULL AND account_id = CAST(? AS UUID))
               OR (CAST(? AS VARCHAR) IS NOT NULL AND user_email = CAST(? AS VARCHAR))
            """,
                canonicalId, auth.accountId(), auth.email(), auth.fullName(), auth.userType(),
                visitorHash, auth.accountId(), auth.accountId(), auth.email(), auth.email());

        jdbc.update("UPDATE public.analytics_events SET person_id = ? WHERE visitor_hash = ?", canonicalId, visitorHash);

        // Merge any earlier anonymous visitor row into the canonical person_id
        if (!canonicalId.equals(visitorHash)) {
            jdbc.update("""
                UPDATE public.analytics_visitors
                SET person_id = ?,
                    account_id = COALESCE(CAST(? AS UUID), account_id),
                    user_email = COALESCE(CAST(? AS VARCHAR), user_email),
                    user_name = COALESCE(CAST(? AS VARCHAR), user_name),
                    user_type = COALESCE(CAST(? AS VARCHAR), user_type)
                WHERE person_id = ?
                  AND NOT EXISTS (SELECT 1 FROM public.analytics_visitors WHERE person_id = ?)
                """, canonicalId, auth.accountId(), auth.email(), auth.fullName(), auth.userType(), visitorHash, canonicalId);

            jdbc.update("DELETE FROM public.analytics_visitors WHERE person_id = ?", visitorHash);
        }

        return canonicalId;
    }

    private void upsertVisitorProfile(
            String personId, String visitorHash, AuthIdentity auth,
            String friendlyName, String displayLabel, String channel,
            String utmSource, String referrer, GeoData geo, String confidence,
            String maskedIp, String networkGroupId, boolean tzMismatch,
            String fingerprint, String deviceType, boolean campaignViewed) {

        jdbc.update("""
            INSERT INTO public.analytics_visitors (
                person_id, visitor_hashes, account_id, user_email, user_name, user_type,
                friendly_name, display_label, first_channel, first_utm_source, first_referrer,
                last_channel, last_utm_source, last_referrer, source_path,
                country, region, city, latitude, longitude, location_confidence,
                masked_ip, isp, asn, network_group_id, is_vpn_or_proxy, is_datacenter,
                timezone_mismatch, device_fingerprint, devices_used,
                total_sessions, total_page_views, campaign_views_count, lead_score,
                first_seen_at, last_seen_at
            )
            VALUES (
                ?, ?, ?, ?, ?, COALESCE(?, 'GUEST'),
                ?, ?, ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?,
                ?, ?, ?,
                1, 1, ?, ?,
                NOW(), NOW()
            )
            ON CONFLICT (person_id) DO UPDATE SET
                visitor_hashes = CASE
                    WHEN POSITION(? IN public.analytics_visitors.visitor_hashes) > 0 THEN public.analytics_visitors.visitor_hashes
                    ELSE LEFT(public.analytics_visitors.visitor_hashes || ',' || ?, 500)
                END,
                account_id = COALESCE(EXCLUDED.account_id, public.analytics_visitors.account_id),
                user_email = COALESCE(EXCLUDED.user_email, public.analytics_visitors.user_email),
                user_name = COALESCE(EXCLUDED.user_name, public.analytics_visitors.user_name),
                user_type = CASE WHEN EXCLUDED.user_type <> 'GUEST' THEN EXCLUDED.user_type ELSE public.analytics_visitors.user_type END,
                display_label = EXCLUDED.display_label,
                last_channel = EXCLUDED.last_channel,
                last_utm_source = COALESCE(EXCLUDED.last_utm_source, public.analytics_visitors.last_utm_source),
                last_referrer = COALESCE(EXCLUDED.last_referrer, public.analytics_visitors.last_referrer),
                source_path = CASE
                    WHEN public.analytics_visitors.source_path LIKE '%' || EXCLUDED.last_channel THEN public.analytics_visitors.source_path
                    ELSE LEFT(public.analytics_visitors.source_path || ' â†’ ' || EXCLUDED.last_channel, 500)
                END,
                country = CASE WHEN EXCLUDED.country IS NOT NULL AND EXCLUDED.country <> 'XX' THEN EXCLUDED.country ELSE public.analytics_visitors.country END,
                region = CASE WHEN EXCLUDED.region IS NOT NULL AND EXCLUDED.region <> 'Unknown' THEN EXCLUDED.region ELSE public.analytics_visitors.region END,
                city = CASE WHEN EXCLUDED.city IS NOT NULL AND EXCLUDED.city <> 'Unknown' THEN EXCLUDED.city ELSE public.analytics_visitors.city END,
                latitude = COALESCE(EXCLUDED.latitude, public.analytics_visitors.latitude),
                longitude = COALESCE(EXCLUDED.longitude, public.analytics_visitors.longitude),
                location_confidence = CASE WHEN EXCLUDED.location_confidence <> 'UNKNOWN' THEN EXCLUDED.location_confidence ELSE public.analytics_visitors.location_confidence END,
                masked_ip = COALESCE(EXCLUDED.masked_ip, public.analytics_visitors.masked_ip),
                isp = COALESCE(EXCLUDED.isp, public.analytics_visitors.isp),
                asn = COALESCE(EXCLUDED.asn, public.analytics_visitors.asn),
                network_group_id = COALESCE(EXCLUDED.network_group_id, public.analytics_visitors.network_group_id),
                is_vpn_or_proxy = (public.analytics_visitors.is_vpn_or_proxy OR EXCLUDED.is_vpn_or_proxy),
                is_datacenter = (public.analytics_visitors.is_datacenter OR EXCLUDED.is_datacenter),
                timezone_mismatch = (public.analytics_visitors.timezone_mismatch OR EXCLUDED.timezone_mismatch),
                device_fingerprint = COALESCE(EXCLUDED.device_fingerprint, public.analytics_visitors.device_fingerprint),
                devices_used = CASE
                    WHEN POSITION(? IN COALESCE(public.analytics_visitors.devices_used, '')) > 0 THEN public.analytics_visitors.devices_used
                    ELSE LEFT(COALESCE(public.analytics_visitors.devices_used, '') || CASE WHEN COALESCE(public.analytics_visitors.devices_used, '') = '' THEN '' ELSE ',' END || ?, 128)
                END,
                total_sessions = (SELECT COUNT(*) FROM public.analytics_sessions WHERE person_id = ?),
                total_page_views = public.analytics_visitors.total_page_views + 1,
                campaign_views_count = public.analytics_visitors.campaign_views_count + (CASE WHEN ? THEN 1 ELSE 0 END),
                lead_score = LEAST(100,
                    LEAST(25, (public.analytics_visitors.total_page_views + 1) * 3) +
                    LEAST(25, public.analytics_visitors.total_active_seconds / 15) +
                    LEAST(20, GREATEST(0, (SELECT COUNT(*) FROM public.analytics_sessions WHERE person_id = ?) - 1) * 10) +
                    LEAST(15, (public.analytics_visitors.campaign_views_count + (CASE WHEN ? THEN 1 ELSE 0 END)) * 5) +
                    (CASE WHEN public.analytics_visitors.apply_clicks_count > 0 THEN 15 ELSE 0 END) +
                    (CASE WHEN public.analytics_visitors.applied THEN 20 ELSE 0 END)
                ),
                last_seen_at = NOW()
            """,
                personId, visitorHash, auth.accountId(), auth.email(), auth.fullName(), auth.userType(),
                friendlyName, displayLabel, channel, utmSource, clip(referrer, 512),
                channel, utmSource, clip(referrer, 512), channel,
                clip(geo.country(), 8), clip(geo.region(), 128), clip(geo.city(), 128), geo.lat(), geo.lon(), confidence,
                maskedIp, geo.isp(), geo.asn(), networkGroupId, geo.vpnOrProxy(), geo.datacenter(),
                tzMismatch, fingerprint, deviceType,
                campaignViewed ? 1 : 0, campaignViewed ? 8 : 3,
                visitorHash, visitorHash,
                deviceType, deviceType,
                personId, campaignViewed, personId, campaignViewed);
    }

    private void insertEvent(
            UUID sessionId, String visitorHash, String personId, String eventType,
            String path, String previousPath, UUID campaignId, int dwellSeconds,
            int scrollDepth, String elementTarget, String searchQuery,
            String filterSummary, String metadataJson) {

        jdbc.update("""
            INSERT INTO public.analytics_events (
                session_id, visitor_hash, person_id, event_type, path, previous_path,
                campaign_id, dwell_seconds, scroll_depth, element_target,
                search_query, filter_summary, metadata, created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), NOW())
            """,
                sessionId, visitorHash, personId, eventType, path, previousPath,
                campaignId, dwellSeconds, scrollDepth, elementTarget,
                searchQuery, filterSummary, metadataJson);
    }

    // =====================================================================
    // REAL LOCATION RESOLUTION (CF HEADERS + FREE GEOIP BACKUP)
    // =====================================================================

    private GeoData resolveRealLocation(HttpServletRequest req, String ip, String browserTz) {
        String cfCountry = headerOrNull(req, "CF-IPCountry", "X-Vercel-IP-Country", "X-Country-Code");
        String cfRegion = headerOrNull(req, "CF-Region", "X-Vercel-IP-Country-Region", "X-Region-Name");
        String cfCity = headerOrNull(req, "CF-IPCity", "X-Vercel-IP-City", "X-City-Name");
        Double cfLat = parseDoubleOrNull(headerOrNull(req, "CF-IPLatitude", "X-Vercel-IP-Latitude", null));
        Double cfLon = parseDoubleOrNull(headerOrNull(req, "CF-IPLongitude", "X-Vercel-IP-Longitude", null));

        boolean hasValidCfCity = cfCity != null && !"Unknown".equalsIgnoreCase(cfCity)
                && cfCountry != null && !"XX".equalsIgnoreCase(cfCountry);

        // Private/loopback IP (local dev) -> honest UNKNOWN, never fake data
        if (isPrivateOrLoopbackIp(ip)) {
            if (hasValidCfCity) {
                return new GeoData(cfCountry, cfRegion != null ? cfRegion : "Unknown", cfCity, cfLat, cfLon, null, null, false, false, null, "EXACT_CITY");
            }
            return new GeoData(
                    cfCountry != null ? cfCountry : "XX",
                    cfRegion != null ? cfRegion : "Unknown",
                    cfCity != null ? cfCity : "Unknown",
                    cfLat, cfLon, "Local Network", null, false, false, null, "UNKNOWN");
        }

        String subnetKey = extractSubnetPrefix(ip);
        GeoData cached = geoCache.get(subnetKey);
        if (cached != null) {
            return mergeHeaderWithGeo(cfCountry, cfRegion, cfCity, cfLat, cfLon, cached);
        }

        GeoData lookedUp = fetchFromIpApi(ip);
        if (lookedUp == null) {
            lookedUp = fetchFromIpWhoIs(ip);
        }

        if (lookedUp != null) {
            if (geoCache.size() > 10_000) geoCache.clear();
            geoCache.put(subnetKey, lookedUp);
            return mergeHeaderWithGeo(cfCountry, cfRegion, cfCity, cfLat, cfLon, lookedUp);
        }

        String conf = determineConfidence(cfCountry, cfRegion, cfCity, false);
        return new GeoData(
                cfCountry != null ? cfCountry : "XX",
                cfRegion != null ? cfRegion : "Unknown",
                cfCity != null ? cfCity : "Unknown",
                cfLat, cfLon, null, null, false, false, null, conf);
    }

    private GeoData fetchFromIpApi(String ip) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://ip-api.com/json/" + ip + "?fields=status,countryCode,regionName,city,lat,lon,isp,org,as,proxy,hosting,timezone"))
                    .timeout(Duration.ofMillis(1500))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode n = objectMapper.readTree(response.body());
                if ("success".equalsIgnoreCase(n.path("status").asText())) {
                    String country = textOrDefault(n, "countryCode", "XX");
                    String region = textOrDefault(n, "regionName", "Unknown");
                    String city = textOrDefault(n, "city", "Unknown");
                    Double lat = n.hasNonNull("lat") ? n.get("lat").asDouble() : null;
                    Double lon = n.hasNonNull("lon") ? n.get("lon").asDouble() : null;
                    String isp = simplifyIspName(textOrDefault(n, "isp", textOrDefault(n, "org", null)));
                    String asn = textOrDefault(n, "as", null);
                    boolean proxy = n.path("proxy").asBoolean(false);
                    boolean hosting = n.path("hosting").asBoolean(false) || isDatacenterIsp(isp, asn);
                    String tz = textOrDefault(n, "timezone", null);
                    String conf = determineConfidence(country, region, city, proxy || hosting);
                    return new GeoData(country, region, city, lat, lon, isp, asn, proxy, hosting, tz, conf);
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
            // fall through to next provider
        }
        return null;
    }

    private GeoData fetchFromIpWhoIs(String ip) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://ipwho.is/" + ip))
                    .timeout(Duration.ofMillis(1500))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode n = objectMapper.readTree(response.body());
                if (n.path("success").asBoolean(false)) {
                    String country = textOrDefault(n, "country_code", "XX");
                    String region = textOrDefault(n, "region", "Unknown");
                    String city = textOrDefault(n, "city", "Unknown");
                    Double lat = n.hasNonNull("latitude") ? n.get("latitude").asDouble() : null;
                    Double lon = n.hasNonNull("longitude") ? n.get("longitude").asDouble() : null;
                    JsonNode conn = n.path("connection");
                    String isp = simplifyIspName(textOrDefault(conn, "isp", textOrDefault(conn, "org", null)));
                    String asn = conn.hasNonNull("asn") ? "AS" + conn.get("asn").asText() : null;
                    boolean datacenter = isDatacenterIsp(isp, asn);
                    String tz = textOrDefault(n.path("timezone"), "id", null);
                    String conf = determineConfidence(country, region, city, datacenter);
                    return new GeoData(country, region, city, lat, lon, isp, asn, false, datacenter, tz, conf);
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
            // no provider answered
        }
        return null;
    }

    private GeoData mergeHeaderWithGeo(String cfCountry, String cfRegion, String cfCity, Double cfLat, Double cfLon, GeoData geo) {
        String country = (cfCountry != null && !"XX".equalsIgnoreCase(cfCountry)) ? cfCountry : geo.country();
        String region = (cfRegion != null && !"Unknown".equalsIgnoreCase(cfRegion)) ? cfRegion : geo.region();
        String city = (cfCity != null && !"Unknown".equalsIgnoreCase(cfCity)) ? cfCity : geo.city();
        Double lat = cfLat != null ? cfLat : geo.lat();
        Double lon = cfLon != null ? cfLon : geo.lon();
        String conf = determineConfidence(country, region, city, geo.vpnOrProxy() || geo.datacenter());
        return new GeoData(country, region, city, lat, lon, geo.isp(), geo.asn(), geo.vpnOrProxy(), geo.datacenter(), geo.ipTimezone(), conf);
    }

    private String determineConfidence(String country, String region, String city, boolean suspectVpn) {
        if (suspectVpn) return "LOW_VPN";
        boolean hasCountry = country != null && !"XX".equalsIgnoreCase(country) && !"Unknown".equalsIgnoreCase(country);
        boolean hasRegion = region != null && !"Unknown".equalsIgnoreCase(region);
        boolean hasCity = city != null && !"Unknown".equalsIgnoreCase(city);
        if (hasCity && hasRegion && hasCountry) return "EXACT_CITY";
        if (hasRegion && hasCountry) return "STATE_ONLY";
        if (hasCountry) return "COUNTRY_ONLY";
        return "UNKNOWN";
    }

    private boolean isTimezoneMismatch(String browserTz, String countryCode, String ipTz) {
        if (browserTz == null || browserTz.isBlank() || countryCode == null || "XX".equalsIgnoreCase(countryCode)) {
            return false;
        }
        String bTz = browserTz.trim().toLowerCase(Locale.ROOT);
        boolean browserIsIndian = bTz.contains("kolkata") || bTz.contains("calcutta") || bTz.contains("asia/colombo");
        if (browserIsIndian && !"IN".equalsIgnoreCase(countryCode) && !"LK".equalsIgnoreCase(countryCode)) {
            return true;
        }
        if ("IN".equalsIgnoreCase(countryCode) && !browserIsIndian && !bTz.startsWith("asia/")) {
            return true;
        }
        if (ipTz != null && !ipTz.isBlank()) {
            String iTz = ipTz.trim().toLowerCase(Locale.ROOT);
            String bContinent = bTz.contains("/") ? bTz.split("/")[0] : bTz;
            String iContinent = iTz.contains("/") ? iTz.split("/")[0] : iTz;
            return !bContinent.equals(iContinent);
        }
        return false;
    }

    private String simplifyIspName(String rawIsp) {
        if (rawIsp == null || rawIsp.isBlank()) return null;
        String l = rawIsp.toLowerCase(Locale.ROOT);
        if (l.contains("jio")) return "Reliance Jio";
        if (l.contains("bharti") || l.contains("airtel")) return "Airtel";
        if (l.contains("vodafone") || l.contains("idea")) return "Vi (Vodafone Idea)";
        if (l.contains("atria") || l.contains("act fibernet") || l.contains("beam telecom")) return "ACT Fibernet";
        if (l.contains("bsnl") || l.contains("bharat sanchar")) return "BSNL";
        if (l.contains("tata")) return "Tata Communications / Fiber";
        if (l.contains("hathway")) return "Hathway";
        if (l.contains("excitel")) return "Excitel";
        if (l.contains("you broadband")) return "YOU Broadband";
        return clip(rawIsp, 128);
    }

    private boolean isDatacenterIsp(String isp, String asn) {
        String combined = ((isp != null ? isp : "") + " " + (asn != null ? asn : "")).toLowerCase(Locale.ROOT);
        for (String kw : DATACENTER_KEYWORDS) {
            if (combined.contains(kw)) return true;
        }
        return false;
    }

    // =====================================================================
    // DEEPER ANALYTICS HELPERS
    // =====================================================================

    private List<Map<String, Object>> getLiveVisitorsNow() {
        return jdbc.queryForList("""
            SELECT
                COALESCE(s.person_id, s.visitor_hash) AS person_id,
                COALESCE(s.user_name, s.user_email, s.friendly_name, 'Visitor') AS name,
                s.display_label,
                s.user_type,
                COALESCE(s.last_path, s.entry_path) AS current_page,
                s.city,
                s.region,
                s.country,
                s.isp,
                s.device_type,
                s.browser,
                s.active_dwell_seconds,
                s.last_active_at
            FROM public.analytics_sessions s
            WHERE s.last_active_at >= NOW() - INTERVAL '5 minutes'
            ORDER BY s.last_active_at DESC
            LIMIT 25
            """);
    }

    private List<Map<String, Object>> getVisitFrequencyGroups(int days) {
        return jdbc.queryForList("""
            WITH person_counts AS (
                SELECT COALESCE(person_id, visitor_hash) AS pid, COUNT(*) AS visits
                FROM public.analytics_sessions
                WHERE created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
                GROUP BY COALESCE(person_id, visitor_hash)
            )
            SELECT
                CASE
                    WHEN visits = 1 THEN '1 visit'
                    WHEN visits BETWEEN 2 AND 3 THEN '2 to 3 visits'
                    ELSE '4+ visits'
                END AS frequency_group,
                COUNT(*) AS people
            FROM person_counts
            GROUP BY 1
            ORDER BY MIN(visits) ASC
            """, days);
    }

    private List<Map<String, Object>> getUserTypeSplit(int days) {
        return jdbc.queryForList("""
            SELECT
                COALESCE(user_type, 'GUEST') AS user_type,
                COUNT(DISTINCT COALESCE(person_id, visitor_hash)) AS unique_people,
                COUNT(*) AS sessions,
                ROUND(AVG(active_dwell_seconds), 1) AS avg_active_seconds,
                COUNT(*) FILTER (WHERE applied = TRUE) AS conversions
            FROM public.analytics_sessions
            WHERE created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            GROUP BY COALESCE(user_type, 'GUEST')
            ORDER BY unique_people DESC
            """, days);
    }

    private List<Map<String, Object>> getFirstVsLastAttribution(int days) {
        return jdbc.queryForList("""
            SELECT
                COALESCE(first_channel, 'Direct') AS first_source,
                COALESCE(last_channel, 'Direct') AS last_source,
                COUNT(*) AS people,
                COUNT(*) FILTER (WHERE applied = TRUE) AS converted_people
            FROM public.analytics_visitors
            WHERE last_seen_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            GROUP BY COALESCE(first_channel, 'Direct'), COALESCE(last_channel, 'Direct')
            ORDER BY converted_people DESC, people DESC
            LIMIT 15
            """, days);
    }

    private List<Map<String, Object>> getIstHourlyHeatmap(int days) {
        return jdbc.queryForList("""
            SELECT
                EXTRACT(DOW FROM (created_at AT TIME ZONE 'Asia/Kolkata'))::int AS day_of_week,
                to_char(created_at AT TIME ZONE 'Asia/Kolkata', 'Dy') AS day_name,
                EXTRACT(HOUR FROM (created_at AT TIME ZONE 'Asia/Kolkata'))::int AS hour_ist,
                COUNT(DISTINCT COALESCE(person_id, visitor_hash)) AS unique_people,
                COUNT(*) AS sessions,
                COUNT(*) FILTER (WHERE applied = TRUE) AS conversions
            FROM public.analytics_sessions
            WHERE created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            GROUP BY 1, 2, 3
            ORDER BY 1 ASC, 3 ASC
            """, days);
    }

    private Map<String, Object> getTimeToConvertStats(int days) {
        return jdbc.queryForMap("""
            SELECT
                COUNT(*) FILTER (WHERE applied = TRUE AND first_applied_at IS NOT NULL) AS total_converted_people,
                ROUND(CAST(COALESCE(AVG(EXTRACT(EPOCH FROM (first_applied_at - first_seen_at)) / 86400.0) FILTER (WHERE applied = TRUE AND first_applied_at >= first_seen_at), 0) AS NUMERIC), 2) AS avg_days_to_convert,
                COUNT(*) FILTER (WHERE applied = TRUE AND (first_applied_at - first_seen_at) < INTERVAL '1 day') AS same_day_conversions,
                COUNT(*) FILTER (WHERE applied = TRUE AND (first_applied_at - first_seen_at) >= INTERVAL '1 day' AND (first_applied_at - first_seen_at) <= INTERVAL '3 days') AS days_1_to_3,
                COUNT(*) FILTER (WHERE applied = TRUE AND (first_applied_at - first_seen_at) > INTERVAL '3 days') AS days_4_plus
            FROM public.analytics_visitors
            WHERE last_seen_at >= NOW() - CAST(? || ' days' AS INTERVAL)
            """, days);
    }

    private List<Map<String, Object>> getHotVisitors(int limit) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
                person_id, user_name, user_email, user_type, friendly_name, display_label,
                city, region, country, isp, first_channel, last_channel,
                total_sessions, total_page_views, total_active_seconds,
                campaign_views_count, apply_clicks_count, lead_score,
                applied, admin_notes, admin_tags, last_seen_at
            FROM public.analytics_visitors
            ORDER BY lead_score DESC, last_seen_at DESC
            LIMIT ?
            """, limit);
        for (Map<String, Object> r : rows) {
            r.put("primaryDisplayName", resolvePrimaryName(r));
            r.put("quickTags", buildQuickTags(r));
        }
        return rows;
    }

    private List<Map<String, Object>> getCampaignLevelFunnels(int days) {
        return jdbc.queryForList("""
            WITH camp_events AS (
                SELECT
                    campaign_id,
                    COUNT(DISTINCT person_id) FILTER (WHERE event_type IN ('PAGE_VIEW', 'CAMPAIGN_VIEW')) AS viewed_people,
                    COUNT(DISTINCT person_id) FILTER (WHERE event_type = 'APPLY_CLICK') AS apply_clicked_people,
                    COUNT(DISTINCT person_id) FILTER (WHERE event_type = 'FORM_SUBMIT') AS submitted_people
                FROM public.analytics_events
                WHERE campaign_id IS NOT NULL
                  AND created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
                GROUP BY campaign_id
            ),
            camp_sessions AS (
                SELECT
                    campaign_id,
                    COUNT(DISTINCT COALESCE(person_id, visitor_hash)) AS session_visitors,
                    COUNT(DISTINCT COALESCE(person_id, visitor_hash)) FILTER (WHERE applied = TRUE) AS session_applied
                FROM public.analytics_sessions
                WHERE campaign_id IS NOT NULL
                  AND created_at >= NOW() - CAST(? || ' days' AS INTERVAL)
                GROUP BY campaign_id
            )
            SELECT
                CAST(COALESCE(cs.campaign_id, ce.campaign_id) AS VARCHAR) AS campaign_id,
                COALESCE(mc.title, c.campaign_title, 'Campaign ' || SUBSTRING(CAST(COALESCE(cs.campaign_id, ce.campaign_id) AS VARCHAR), 1, 8)) AS campaign_title,
                GREATEST(COALESCE(cs.session_visitors, 0), COALESCE(ce.viewed_people, 0)) AS viewed_count,
                GREATEST(COALESCE(ce.apply_clicked_people, 0), COALESCE(cs.session_applied, 0)) AS apply_clicked_count,
                GREATEST(COALESCE(ce.submitted_people, 0), COALESCE(cs.session_applied, 0)) AS applied_count,
                COALESCE(sp.spend_inr, 0) AS spend_inr,
                CASE
                    WHEN GREATEST(COALESCE(ce.submitted_people, 0), COALESCE(cs.session_applied, 0)) > 0 AND COALESCE(sp.spend_inr, 0) > 0
                    THEN ROUND(sp.spend_inr / GREATEST(COALESCE(ce.submitted_people, 0), COALESCE(cs.session_applied, 0)), 2)
                    ELSE 0
                END AS cost_per_apply_inr
            FROM camp_sessions cs
            FULL OUTER JOIN camp_events ce ON ce.campaign_id = cs.campaign_id
            LEFT JOIN public.managed_campaigns mc ON mc.id = COALESCE(cs.campaign_id, ce.campaign_id)
            LEFT JOIN public.campaigns c ON c.id = COALESCE(cs.campaign_id, ce.campaign_id)
            LEFT JOIN public.analytics_campaign_spend sp ON sp.campaign_id = COALESCE(cs.campaign_id, ce.campaign_id)
            ORDER BY viewed_count DESC
            LIMIT 25
            """, days, days);
    }

    private List<Map<String, Object>> getSameNetworkGroups() {
        return jdbc.queryForList("""
            SELECT
                network_group_id,
                COALESCE(isp, 'Shared Network') AS isp,
                COALESCE(city, 'Unknown') AS city,
                MAX(masked_ip) AS masked_ip,
                COUNT(*) AS distinct_visitors,
                SUM(total_sessions) AS total_sessions
            FROM public.analytics_visitors
            WHERE network_group_id IS NOT NULL
            GROUP BY network_group_id, COALESCE(isp, 'Shared Network'), COALESCE(city, 'Unknown')
            HAVING COUNT(*) >= 2
            ORDER BY distinct_visitors DESC
            LIMIT 15
            """);
    }

    private List<Map<String, Object>> getPossibleDuplicateHints() {
        return jdbc.queryForList("""
            SELECT
                v1.person_id AS person_a,
                v1.friendly_name AS name_a,
                v1.display_label AS label_a,
                v2.person_id AS person_b,
                v2.friendly_name AS name_b,
                v2.display_label AS label_b,
                v1.city,
                v1.isp,
                'Same device screen, OS, timezone & network within 24h (likely In-App to Browser switch)' AS reason
            FROM public.analytics_visitors v1
            JOIN public.analytics_visitors v2
              ON v1.device_fingerprint = v2.device_fingerprint
             AND v1.network_group_id = v2.network_group_id
             AND v1.person_id < v2.person_id
             AND ABS(EXTRACT(EPOCH FROM (v1.last_seen_at - v2.last_seen_at))) <= 86400
            WHERE v1.device_fingerprint IS NOT NULL
            ORDER BY v1.last_seen_at DESC
            LIMIT 10
            """);
    }

    private List<Map<String, Object>> checkTrafficAnomalies() {
        List<Map<String, Object>> alerts = new ArrayList<>();
        long lastHour = queryLong("SELECT COUNT(DISTINCT COALESCE(person_id, visitor_hash)) FROM public.analytics_sessions WHERE created_at >= NOW() - INTERVAL '1 hour'");
        long prev7dHourlyAvg = Math.max(1, queryLong("SELECT COUNT(DISTINCT COALESCE(person_id, visitor_hash)) FROM public.analytics_sessions WHERE created_at >= NOW() - INTERVAL '7 days'") / (7 * 24));

        if (lastHour >= prev7dHourlyAvg * 3 && lastHour >= 10) {
            String msg = "Traffic spike: " + lastHour + " unique people in the last hour (normal hourly avg: " + prev7dHourlyAvg + ").";
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("type", "TRAFFIC_SPIKE");
            alert.put("severity", "HIGH");
            alert.put("message", msg);
            alerts.add(alert);
            sendOptionalTelegramAlert(msg);
        }
        return alerts;
    }

    private void sendOptionalTelegramAlert(String text) {
        if (telegramBotToken == null || telegramBotToken.isBlank() || telegramChatId == null || telegramChatId.isBlank()) {
            return;
        }
        try {
            String url = "https://api.telegram.org/bot" + telegramBotToken.trim() + "/sendMessage";
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("chat_id", telegramChatId.trim());
            payload.put("text", "ðŸ“Š BrandCollabz Analytics: " + text);
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(1500))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            httpClient.sendAsync(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // alerts are best-effort
        }
    }

    // =====================================================================
    // HELPERS: BOT FILTER, FRIENDLY NAMES, IP MASKING, HASHING
    // =====================================================================

    private boolean shouldIgnoreTraffic(HttpServletRequest req, String ua, String ip, String email) {
        if ("true".equalsIgnoreCase(req.getHeader("X-Analytics-Ignore"))) {
            return true;
        }
        if (ua == null || ua.isBlank()) {
            return true;
        }
        String lowerUa = ua.toLowerCase(Locale.ROOT);
        for (String bot : BOT_SIGNATURES) {
            if (lowerUa.contains(bot)) return true;
        }
        if (excludedIpsConfig != null && !excludedIpsConfig.isBlank() && ip != null) {
            for (String exIp : excludedIpsConfig.split(",")) {
                if (!exIp.isBlank() && ip.trim().equalsIgnoreCase(exIp.trim())) return true;
            }
        }
        if (excludedEmailsConfig != null && !excludedEmailsConfig.isBlank() && email != null) {
            for (String exEmail : excludedEmailsConfig.split(",")) {
                if (!exEmail.isBlank() && email.trim().equalsIgnoreCase(exEmail.trim())) return true;
            }
        }
        return false;
    }

    /** personId can start with "acc_"/"usr_", so we hash it first to always get valid hex for the name/tag. */
    private String generateFriendlyName(String personId) {
        String h = hashIdentifier("fn|" + personId);
        int adjIdx = Integer.parseInt(h.substring(0, 2), 16) % ADJECTIVES.length;
        int aniIdx = Integer.parseInt(h.substring(2, 4), 16) % ANIMALS.length;
        return ADJECTIVES[adjIdx] + " " + ANIMALS[aniIdx] + " " + shortTagFor(personId);
    }

    private String shortTagFor(String personId) {
        return "#" + hashIdentifier("fn|" + personId).substring(0, 3).toUpperCase(Locale.ROOT);
    }

    private String resolvePrimaryName(Map<String, Object> row) {
        Object name = row.get("user_name");
        Object email = row.get("user_email");
        Object type = row.get("user_type");
        String roleSuffix = (type != null && !"GUEST".equalsIgnoreCase(String.valueOf(type)))
                ? " (" + String.valueOf(type).toLowerCase(Locale.ROOT) + ")" : "";
        if (name != null && !String.valueOf(name).isBlank()) {
            return name + roleSuffix;
        }
        if (email != null && !String.valueOf(email).isBlank()) {
            return email + roleSuffix;
        }
        Object friendly = row.get("friendly_name");
        return friendly != null ? String.valueOf(friendly) : "Anonymous Visitor";
    }

    private List<String> buildQuickTags(Map<String, Object> row) {
        List<String> tags = new ArrayList<>();
        int sessions = row.get("total_sessions") instanceof Number n ? n.intValue() : 1;
        int score = row.get("lead_score") instanceof Number n2 ? n2.intValue() : 0;
        boolean applied = Boolean.TRUE.equals(row.get("applied"));
        boolean vpn = Boolean.TRUE.equals(row.get("is_vpn_or_proxy"))
                || Boolean.TRUE.equals(row.get("is_datacenter"))
                || Boolean.TRUE.equals(row.get("timezone_mismatch"));

        tags.add(sessions > 1 ? "RETURNING" : "NEW");
        if (applied) tags.add("APPLIED");
        if (score >= 60) tags.add("HIGH_INTEREST");
        if (vpn) tags.add("VPN_OR_PROXY");
        return tags;
    }

    private String maskIp(String ip) {
        if (ip == null || ip.isBlank()) return "0.0.x.x";
        String clean = ip.trim();
        if (clean.contains(".")) {
            String[] parts = clean.split("\\.");
            if (parts.length == 4) {
                return parts[0] + "." + parts[1] + ".x.x";
            }
        }
        if (clean.contains(":")) {
            String[] parts = clean.split(":");
            if (parts.length >= 2) {
                return parts[0] + ":" + parts[1] + ":x:x";
            }
        }
        return "hidden";
    }

    private String extractSubnetPrefix(String ip) {
        if (ip == null || ip.isBlank()) return "0.0.0";
        String clean = ip.trim();
        if (clean.contains(".")) {
            String[] parts = clean.split("\\.");
            if (parts.length == 4) return parts[0] + "." + parts[1] + "." + parts[2];
        }
        if (clean.contains(":")) {
            String[] parts = clean.split(":");
            if (parts.length >= 3) return parts[0] + ":" + parts[1] + ":" + parts[2];
        }
        return clean;
    }

    private boolean isPrivateOrLoopbackIp(String ip) {
        if (ip == null || ip.isBlank()) return true;
        String clean = ip.trim();
        return "127.0.0.1".equals(clean) || "0:0:0:0:0:0:0:1".equals(clean) || "::1".equals(clean)
                || clean.startsWith("10.") || clean.startsWith("192.168.")
                || clean.matches("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*");
    }

    /** Hashes ONLY visitorId (never mixes in IP), so one person across WiFi & mobile data has 1 stable ID. */
    private String hashStableVisitor(String visitorId) {
        return hashIdentifier("vid|" + visitorId.trim());
    }

    private String hashIdentifier(String raw) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(visitorSalt.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash identifier", e);
        }
    }

    private long queryLong(String sql, Object... args) {
        Long val = jdbc.queryForObject(sql, Long.class, args);
        return val != null ? val : 0L;
    }

    private static double num(Map<String, Object> map, String key, double defaultValue) {
        if (map == null) return defaultValue;
        Object v = map.get(key);
        return v instanceof Number n ? n.doubleValue() : defaultValue;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static <T> List<T> safe(List<T> list) {
        return list != null ? list : Collections.emptyList();
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static String cleanPath(String raw) {
        String p = raw == null ? "/" : raw.trim();
        int q = p.indexOf('?');
        if (q >= 0) p = p.substring(0, q);
        int h = p.indexOf('#');
        if (h >= 0) p = p.substring(0, h);
        if (p.isEmpty()) p = "/";
        return p.length() > 512 ? p.substring(0, 512) : p;
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String extractDomain(String url) {
        if (url == null) return null;
        try {
            String host = URI.create(url).getHost();
            if (host == null) return null;
            host = host.toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) host = host.substring(4);
            return clip(host, 255);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String headerOrNull(HttpServletRequest req, String h1, String h2, String h3) {
        for (String h : new String[]{h1, h2, h3}) {
            if (h == null) continue;
            String v = req.getHeader(h);
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }

    private Double parseDoubleOrNull(String val) {
        if (val == null || val.isBlank()) return null;
        try {
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String textOrDefault(JsonNode node, String field, String fallback) {
        if (node != null && node.hasNonNull(field)) {
            String v = node.get(field).asText().trim();
            if (!v.isEmpty()) return v;
        }
        return fallback;
    }

    private String extractLanguage(HttpServletRequest req) {
        String h = req.getHeader("Accept-Language");
        if (h == null || h.isBlank()) return null;
        String first = h.split(",")[0].split(";")[0].trim();
        return clip(first, 16);
    }

    private String extractClientIp(HttpServletRequest request) {
        String ip = request.getHeader("CF-Connecting-IP");
        if (ip == null || ip.isBlank()) {
            ip = request.getHeader("True-Client-IP");
        }
        if (ip == null || ip.isBlank()) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank() && !"unknown".equalsIgnoreCase(xff)) {
                ip = xff.split(",")[0].trim();
            }
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        return ip != null ? ip : "0.0.0.0";
    }

    private String detectDeviceType(String ua) {
        if (ua == null) return "Desktop";
        String l = ua.toLowerCase(Locale.ROOT);
        if (l.contains("ipad") || l.contains("tablet")) return "Tablet";
        if (l.contains("android") && !l.contains("mobile")) return "Tablet";
        if (l.contains("mobile") || l.contains("android") || l.contains("iphone")) return "Mobile";
        return "Desktop";
    }

    private String detectOs(String ua) {
        if (ua == null) return "Other";
        String l = ua.toLowerCase(Locale.ROOT);
        if (l.contains("iphone") || l.contains("ipad") || l.contains("ipod")) return "iOS";
        if (l.contains("android")) return "Android";
        if (l.contains("windows")) return "Windows";
        if (l.contains("mac os x") || l.contains("macintosh")) return "macOS";
        if (l.contains("linux")) return "Linux";
        return "Other";
    }

    private String detectBrowser(String ua) {
        if (ua == null) return "Other";
        String l = ua.toLowerCase(Locale.ROOT);
        if (l.contains("instagram")) return "Instagram In-App";
        if (l.contains("fban") || l.contains("fbav") || l.contains("fb_iab")) return "Facebook In-App";
        if (l.contains("edg/") || l.contains("edga/") || l.contains("edgios/")) return "Edge";
        if (l.contains("opr/") || l.contains("opera")) return "Opera";
        if (l.contains("samsungbrowser")) return "Samsung Internet";
        if (l.contains("firefox") || l.contains("fxios")) return "Firefox";
        if (l.contains("chrome") || l.contains("crios")) return "Chrome";
        if (l.contains("safari")) return "Safari";
        return "Other";
    }

    private String detectInAppSource(String ua, String referrer, String deviceType) {
        String l = ua == null ? "" : ua.toLowerCase(Locale.ROOT);
        if (l.contains("instagram")) return "Instagram";
        if (l.contains("fban") || l.contains("fbav") || l.contains("fb_iab")) return "Facebook";
        if (l.contains("whatsapp")) return "WhatsApp";
        if (l.contains("linkedinapp")) return "LinkedIn";
        if (l.contains("snapchat")) return "Snapchat";
        if ("Mobile".equals(deviceType) && referrer != null) {
            String r = referrer.toLowerCase(Locale.ROOT);
            if (r.contains("instagram.com")) return "Instagram";
            if (r.contains("whatsapp") || r.contains("wa.me")) return "WhatsApp";
        }
        return null;
    }

    private String classifyChannel(String utmSource, String utmMedium, String referrerDomain, String inAppSource) {
        String src = utmSource == null ? "" : utmSource.toLowerCase(Locale.ROOT);
        String med = utmMedium == null ? "" : utmMedium.toLowerCase(Locale.ROOT);

        if (containsAny(med, "cpc", "ppc", "paid", "display", "cpm")) return "Paid";
        if (containsAny(med, "email", "newsletter") || containsAny(src, "email", "newsletter")) return "Email";
        if (containsAny(med, "influencer", "creator", "affiliate")) return "Influencer";
        if (containsAny(med, "social") || containsAny(src,
                "instagram", "facebook", "whatsapp", "linkedin", "twitter", "youtube", "snapchat", "pinterest", "reddit", "threads")) {
            return "Social";
        }
        if (inAppSource != null) return "Social";

        if (referrerDomain != null) {
            String d = referrerDomain;
            if (containsAny(d, "google.", "bing.", "duckduckgo.", "yahoo.", "ecosia.", "baidu.", "yandex.")) {
                return "Organic Search";
            }
            if (containsAny(d, "instagram.", "facebook.", "fb.com", "t.co", "twitter.", "x.com", "linkedin.",
                    "lnkd.in", "youtube.", "youtu.be", "pinterest.", "reddit.", "snapchat.", "threads.", "wa.me", "whatsapp.")) {
                return "Social";
            }
            return "Referral";
        }
        if (!src.isEmpty() || !med.isEmpty()) return "Other Campaign";
        return "Direct";
    }

    private static boolean containsAny(String value, String... needles) {
        if (value == null || value.isEmpty()) return false;
        for (String n : needles) {
            if (value.contains(n)) return true;
        }
        return false;
    }

    private String csvLine(Object... cols) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) sb.append(',');
            String raw = cols[i] == null ? "" : String.valueOf(cols[i]).replace('\r', ' ').replace('\n', ' ');
            if (!raw.isEmpty() && "=+-@".indexOf(raw.charAt(0)) >= 0) {
                raw = "'" + raw;
            }
            sb.append('"').append(raw.replace("\"", "\"\"")).append('"');
        }
        return sb.append("\r\n").toString();
    }
}
```

### .\src\main\java\com\adrovis\influencer\controller\CampaignController.java
```java
package com.adrovis.influencer.controller;

import com.adrovis.influencer.dto.ApiResponse;
import com.adrovis.influencer.dto.CampaignFilterRequest;
import com.adrovis.influencer.dto.PublicCampaignResponse;
import com.adrovis.influencer.service.CampaignService;
import com.adrovis.influencer.campaign.access.CampaignApplyAccessService;
import com.adrovis.influencer.campaign.access.GuestApplyCookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;
    private final CampaignApplyAccessService applyAccess;
    private final GuestApplyCookie guestCookies;
    /** Preserves public campaign discovery and the existing filter contract. */

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PublicCampaignResponse>>> getPublicCampaigns(
            @ModelAttribute CampaignFilterRequest filter) {
        Page<PublicCampaignResponse> campaigns = campaignService.getFilteredCampaigns(filter);
        return ResponseEntity.ok(ApiResponse.ok(campaigns));
    }
    /** Preserves public campaign details without exposing the direct application destination. */

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PublicCampaignResponse>> getCampaignDetails(@PathVariable UUID id) {
        PublicCampaignResponse campaign = campaignService.getCampaignById(id);
        return ResponseEntity.ok(ApiResponse.ok(campaign));
    }

    /**
     * Application Link Proxy:
     * Logs the click-through / analytics and securely redirects the creator
     * to the actual Google Form without exposing destination links up-front.
     */
    @GetMapping("/{id}/apply")
    public ResponseEntity<Void> applyToCampaign(
            @PathVariable UUID id,
            @CookieValue(name = GuestApplyCookie.NAME, required = false) String guest,
            @AuthenticationPrincipal Jwt account,
            HttpServletResponse response) {
        String destination = grant(id, guest, account, response);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(destination))
                .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    // JSON alternative lets the frontend display signup before navigating to an external form.
    /** Returns a gated JSON destination so the frontend can show signup before navigating. */
    @PostMapping("/{id}/apply-access")
    public ResponseEntity<Map<String, Object>> applyAccess(
            @PathVariable UUID id,
            @CookieValue(name = GuestApplyCookie.NAME, required = false) String guest,
            @AuthenticationPrincipal Jwt account,
            HttpServletResponse response) {
        String destination = grant(id, guest, account, response);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("success", true, "applicationUrl", destination));
    }
    /** Validates the destination and atomically enforces the selected guest policy before recording a click. */

    private String grant(UUID id, String raw, Jwt account, HttpServletResponse response) {
        if (account != null) {
            return applyAccess.grant(id, null, true);
        }
        String identity = guestCookies.identity(raw);
        String destination = applyAccess.grant(id, guestCookies.hash(identity), false);
        if (!identity.equals(raw)) {
            guestCookies.write(response, identity);
        }
        return destination;
    }
}

```

### .\src\main\java\com\adrovis\influencer\controller\HealthController.java
```java
package com.adrovis.influencer.controller;

import com.adrovis.influencer.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> checkHealth() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "status", "UP",
                "service", "influencer-core-api",
                "version", "1.0.0"
        )));
    }
}

```

### .\src\main\java\com\adrovis\influencer\controller\InquiryController.java
```java
package com.adrovis.influencer.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.access.prepost.PreAuthorize;
import com.adrovis.influencer.dto.ApiResponse;
import com.adrovis.influencer.entity.InquiryEntity;
import com.adrovis.influencer.exception.ResourceNotFoundException;
import com.adrovis.influencer.repository.InquiryRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inquiries")
@RequiredArgsConstructor
@Validated
public class InquiryController {

    private final InquiryRepository inquiryRepository;

    @Data
    public static class CreateInquiryRequest {
        @NotBlank(message = "Name is required")
        private String name;

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;

        // OPTIONAL: Phone number
        private String phone;

        // OPTIONAL: Default is CREATOR if not sent
        private String role; // "BRAND", "CREATOR", "AGENCY"

        // STRICTLY OPTIONAL: Creators do not have companies
        private String companyName;

        // OPTIONAL: Only brands/agencies typically fill this
        private String budget;

        @NotBlank(message = "Message or requirement details required")
        private String message;
    }

    @Data
    public static class UpdateStatusRequest {
        @NotBlank(message = "Status is required")
        private String status; // "NEW", "IN_REVIEW", "CONTACTED", "RESOLVED"
    }

    /**
     * POST /api/v1/inquiries
     * Public submission endpoint for Creators, Brands, and Agencies
     */
    @PostMapping
    public ResponseEntity<ApiResponse<String>> submitInquiry(@Valid @RequestBody CreateInquiryRequest request) {
        String cleanRole = request.getRole() != null ? request.getRole().toUpperCase().trim() : "CREATOR";
        if (!cleanRole.matches("BRAND|CREATOR|AGENCY")) {
            cleanRole = "CREATOR";
        }

        InquiryEntity inquiry = InquiryEntity.builder()
                .name(request.getName().trim())
                .email(request.getEmail().trim().toLowerCase())
                .phone(request.getPhone() != null && !request.getPhone().isBlank() ? request.getPhone().trim() : null)
                .role(cleanRole)
                .companyName(request.getCompanyName() != null && !request.getCompanyName().isBlank() ? request.getCompanyName().trim() : null)
                .budget(request.getBudget() != null && !request.getBudget().isBlank() ? request.getBudget().trim() : null)
                .message(request.getMessage().trim())
                .status("NEW")
                .build();

        InquiryEntity saved = inquiryRepository.save(inquiry);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Your inquiry has been received. Our team will contact you shortly.", saved.getId().toString()));
    }

    /**
     * GET /api/v1/inquiries
     * Paginated list with filtering by role, status, and search query
     */
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<InquiryEntity>>> listInquiries(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)));

        String cleanRole = (role != null && !role.isBlank()) ? role.toUpperCase().trim() : null;
        String cleanStatus = (status != null && !status.isBlank()) ? status.toUpperCase().trim() : null;
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;

        Page<InquiryEntity> results = inquiryRepository.findFilteredInquiries(cleanRole, cleanStatus, cleanSearch, pageable);
        return ResponseEntity.ok(ApiResponse.ok(results));
    }

    /**
     * GET /api/v1/inquiries/{id}
     * Get specific inquiry details
     */
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InquiryEntity>> getInquiryDetails(@PathVariable UUID id) {
        InquiryEntity inquiry = inquiryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inquiry not found with ID: " + id));
        return ResponseEntity.ok(ApiResponse.ok(inquiry));
    }

    /**
     * PATCH /api/v1/inquiries/{id}/status
     * Update status (NEW -> IN_REVIEW -> CONTACTED -> RESOLVED)
     */
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<InquiryEntity>> updateInquiryStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request
    ) {
        InquiryEntity inquiry = inquiryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inquiry not found with ID: " + id));

        inquiry.setStatus(request.getStatus().toUpperCase().trim());
        InquiryEntity updated = inquiryRepository.save(inquiry);

        return ResponseEntity.ok(ApiResponse.ok("Status updated successfully", updated));
    }
}

```

### .\src\main\java\com\adrovis\influencer\controller\NewsletterController.java
```java
package com.adrovis.influencer.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.access.prepost.PreAuthorize;
import com.adrovis.influencer.dto.ApiResponse;
import com.adrovis.influencer.security.CurrentAccount;
import org.springframework.security.access.AccessDeniedException;
import com.adrovis.influencer.entity.NewsletterSubscriptionEntity;
import com.adrovis.influencer.repository.NewsletterSubscriptionRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/newsletter")
@RequiredArgsConstructor
@Validated
public class NewsletterController {

    private final NewsletterSubscriptionRepository subscriptionRepository;
    private final CurrentAccount accounts;

    @Data
    public static class SubscribeRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;
    }

    /**
     * POST /api/v1/newsletter/subscribe
     * Idempotent subscription for the signed-in account's verified email; prevents subscribing another person.
     */
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "adrovisBearer")
    @PostMapping("/subscribe")
    public ResponseEntity<ApiResponse<String>> subscribe(@Valid @RequestBody SubscribeRequest request) {
        String cleanEmail = request.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        if (!accounts.require().email().equals(cleanEmail)) {
            throw new AccessDeniedException("Subscribe using your verified account email");
        }

        if (!subscriptionRepository.existsByEmailIgnoreCase(cleanEmail)) {
            NewsletterSubscriptionEntity sub = NewsletterSubscriptionEntity.builder()
                    .email(cleanEmail)
                    .isActive(true)
                    .build();
            subscriptionRepository.save(sub);
        }

        return ResponseEntity.ok(ApiResponse.ok("Subscribed successfully! Welcome to Brand Collabz.", cleanEmail));
    }

    /**
     * GET /api/v1/newsletter/subscribers
     * Paginated list of subscribers with optional email search and active-status filtering
     */
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @GetMapping("/subscribers")
    public ResponseEntity<ApiResponse<Page<NewsletterSubscriptionEntity>>> getSubscribers(
            @RequestParam(required = false) Boolean activeOnly,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)));
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;

        Page<NewsletterSubscriptionEntity> subscribers = subscriptionRepository.findFilteredSubscriptions(activeOnly, cleanSearch, pageable);
        return ResponseEntity.ok(ApiResponse.ok(subscribers));
    }
}

```

### .\src\main\java\com\adrovis\influencer\dto\AgencyResponse.java
```java
package com.adrovis.influencer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgencyResponse {
    private UUID id;
    private String agencyName;
    private String contactPhone;
    private String contactEmail;
    private String instagramHandle;
    private String notes;
    private Integer activeCampaignsCount;
    private OffsetDateTime createdAt;
}

```

### .\src\main\java\com\adrovis\influencer\dto\ApiResponse.java
```java
package com.adrovis.influencer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    @Builder.Default
    private OffsetDateTime timestamp = OffsetDateTime.now();

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message("Operation successful")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> fail(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .data(null)
                .build();
    }
}

```

### .\src\main\java\com\adrovis\influencer\dto\CampaignFilterRequest.java
```java
package com.adrovis.influencer.dto;

import lombok.Data;

@Data
public class CampaignFilterRequest {
    private String campaignType;
    private Integer maxFollowers;
    private String brand;
    private String category;
    private String location;
    private Double minPayout;
    private Integer page = 0;
    private Integer size = 12;
    private String sortBy = "created_at";
    private String sortDirection = "DESC";
}

```

### .\src\main\java\com\adrovis\influencer\dto\PublicCampaignResponse.java
```java
package com.adrovis.influencer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicCampaignResponse {
    private UUID id;
    private String brandName;
    private String brandInstagramUrl;
    private String campaignTitle;
    private String campaignType;
    private String applyProxyUrl;
    private List<String> images;
    private Map<String, Object> compensation;
    private List<String> deliverables;
    private String adRights;
    private List<String> categories;
    private String productName;
    private Map<String, Object> eligibility;
    private Map<String, Object> schedule;
    private List<String> importantRequirements;
    private List<String> referenceLinks;
    private OffsetDateTime createdAt;
    private OffsetDateTime expiresAt;
    private Long viewsCount;
    private Long clicksCount;
}

```

### .\src\main\java\com\adrovis\influencer\entity\AgencyEntity.java
```java
package com.adrovis.influencer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "agencies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgencyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_name", nullable = false)
    private String agencyName;

    @Column(name = "contact_phone", unique = true)
    private String contactPhone;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "instagram_handle")
    private String instagramHandle;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "agency", fetch = FetchType.LAZY)
    @Builder.Default
    private List<CampaignEntity> campaigns = new ArrayList<>();
}

```

### .\src\main\java\com\adrovis\influencer\entity\AnalyticsSessionEntity.java
```java
package com.adrovis.influencer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "analytics_sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsSessionEntity {

    @Id
    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "visitor_hash", nullable = false, length = 64)
    private String visitorHash;

    @Column(name = "person_id", length = 64)
    private String personId;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "user_email", length = 254)
    private String userEmail;

    @Column(name = "user_name", length = 120)
    private String userName;

    @Column(name = "user_type", length = 32)
    @Builder.Default
    private String userType = "GUEST";

    @Column(name = "friendly_name", length = 64)
    private String friendlyName;

    @Column(name = "display_label", length = 160)
    private String displayLabel;

    @Column(name = "entry_path", nullable = false, length = 512)
    private String entryPath;

    @Column(name = "last_path", length = 512)
    private String lastPath;

    @Column(name = "referrer", length = 1024)
    private String referrer;

    @Column(name = "referrer_domain", length = 255)
    private String referrerDomain;

    @Column(name = "utm_source", length = 128)
    private String utmSource;

    @Column(name = "utm_medium", length = 128)
    private String utmMedium;

    @Column(name = "utm_campaign", length = 128)
    private String utmCampaign;

    @Column(name = "utm_content", length = 128)
    private String utmContent;

    @Column(name = "utm_term", length = 128)
    private String utmTerm;

    @Column(name = "campaign_id")
    private UUID campaignId;

    @Column(name = "channel", nullable = false, length = 32)
    @Builder.Default
    private String channel = "Direct";

    @Column(name = "country", length = 8)
    private String country;

    @Column(name = "region", length = 128)
    private String region;

    @Column(name = "city", length = 128)
    private String city;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "location_confidence", length = 24)
    @Builder.Default
    private String locationConfidence = "UNKNOWN";

    @Column(name = "masked_ip", length = 48)
    private String maskedIp;

    @Column(name = "network_group_id", length = 32)
    private String networkGroupId;

    @Column(name = "isp", length = 128)
    private String isp;

    @Column(name = "asn", length = 64)
    private String asn;

    @Column(name = "is_vpn_or_proxy", nullable = false)
    private boolean vpnOrProxy;

    @Column(name = "is_datacenter", nullable = false)
    private boolean datacenter;

    @Column(name = "timezone_mismatch", nullable = false)
    private boolean timezoneMismatch;

    @Column(name = "device_fingerprint", length = 32)
    private String deviceFingerprint;

    @Column(name = "device_type", nullable = false, length = 16)
    @Builder.Default
    private String deviceType = "Desktop";

    @Column(name = "browser", length = 48)
    private String browser;

    @Column(name = "os", length = 48)
    private String os;

    @Column(name = "in_app_browser", nullable = false)
    private boolean inAppBrowser;

    @Column(name = "in_app_source", length = 32)
    private String inAppSource;

    @Column(name = "screen_resolution", length = 24)
    private String screenResolution;

    @Column(name = "timezone", length = 64)
    private String timezone;

    @Column(name = "language", length = 16)
    private String language;

    @Column(name = "returning_visitor", nullable = false)
    private boolean returningVisitor;

    @Column(name = "page_views", nullable = false)
    @Builder.Default
    private int pageViews = 1;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(name = "active_dwell_seconds", nullable = false)
    private int activeDwellSeconds;

    @Column(name = "max_scroll_depth", nullable = false)
    private int maxScrollDepth;

    @Column(name = "rage_clicks", nullable = false)
    private int rageClicks;

    @Column(name = "engaged", nullable = false)
    private boolean engaged;

    @Column(name = "applied", nullable = false)
    private boolean applied;

    @Column(name = "applied_at")
    private OffsetDateTime appliedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "last_active_at", nullable = false)
    private OffsetDateTime lastActiveAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (lastActiveAt == null) lastActiveAt = now;
        if (personId == null) personId = visitorHash;
    }
}
```

### .\src\main\java\com\adrovis\influencer\entity\CampaignEntity.java
```java
package com.adrovis.influencer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "campaigns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "brand_name", nullable = false)
    private String brandName;

    @Column(name = "campaign_title", nullable = false)
    private String campaignTitle;

    @Column(name = "campaign_type", nullable = false)
    private String campaignType;

    @Column(name = "application_form_url", nullable = false, unique = true)
    private String applicationFormUrl;

    @Column(name = "brand_instagram_url")
    private String brandInstagramUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "canonical_data", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> canonicalData;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "views_count")
    @Builder.Default
    private Long viewsCount = 0L;

    @Column(name = "clicks_count")
    @Builder.Default
    private Long clicksCount = 0L;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agency_id")
    private AgencyEntity agency;

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("imageOrder ASC")
    @Builder.Default
    private List<CampaignImageEntity> images = new ArrayList<>();
}

```

### .\src\main\java\com\adrovis\influencer\entity\CampaignImageEntity.java
```java
package com.adrovis.influencer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "campaign_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignImageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private CampaignEntity campaign;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    @Column(name = "public_url", nullable = false)
    private String publicUrl;

    @Column(name = "image_order", nullable = false)
    @Builder.Default
    private Integer imageOrder = 1;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}

```

### .\src\main\java\com\adrovis\influencer\entity\InquiryEntity.java
```java
package com.adrovis.influencer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inquiries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InquiryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "role", nullable = false)
    private String role; // "BRAND", "CREATOR", "AGENCY"

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "budget")
    private String budget;

    @Column(name = "message", columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "status")
    @Builder.Default
    private String status = "NEW"; // "NEW", "IN_REVIEW", "CONTACTED"

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
```

### .\src\main\java\com\adrovis\influencer\entity\NewsletterSubscriptionEntity.java
```java
package com.adrovis.influencer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "newsletter_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsletterSubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "subscribed_at", updatable = false)
    private OffsetDateTime subscribedAt;
}
```

### .\src\main\java\com\adrovis\influencer\enums\CampaignType.java
```java
package com.adrovis.influencer.enums;

public enum CampaignType {
    PAID,
    BARTER,
    PRODUCT_EXCHANGE,
    UNKNOWN
}

```

### .\src\main\java\com\adrovis\influencer\enums\GenderRequirement.java
```java
package com.adrovis.influencer.enums;

public enum GenderRequirement {
    ANY,
    MALE_ONLY,
    FEMALE_ONLY,
    NOT_SPECIFIED
}

```

### .\src\main\java\com\adrovis\influencer\enums\ProductProvided.java
```java
package com.adrovis.influencer.enums;

public enum ProductProvided {
    YES,
    NO,
    NOT_SPECIFIED,
    UNKNOWN
}

```

### .\src\main\java\com\adrovis\influencer\exception\GlobalExceptionHandler.java
```java
package com.adrovis.influencer.exception;

import com.adrovis.influencer.dto.ApiResponse;
import com.adrovis.influencer.payment.security.PaymentOperationException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Preserves the existing not-found envelope without exposing storage details. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException exception) {
        return ResponseEntity.status(404).body(ApiResponse.fail(exception.getMessage()));
    }

    /** Method-level authorization must produce 403, not be swallowed by the generic 500 handler. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> forbidden(AccessDeniedException exception) {
        return failure(403, "FORBIDDEN", "You do not have permission to perform this action.");
    }

    /** Distinguishes missing identity from an unavailable trusted identity check. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> authentication(AuthenticationException exception) {
        return exception instanceof AuthenticationServiceException
                ? failure(503, "AUTHENTICATION_UNAVAILABLE", "Account validation is temporarily unavailable.")
                : failure(401, "UNAUTHORIZED", "A valid account access token is required.");
    }

    /** Payment services provide deliberately sanitized domain errors, never provider exception text. */
    @ExceptionHandler(PaymentOperationException.class)
    public ResponseEntity<Map<String, Object>> payment(PaymentOperationException exception) {
        return failure(exception.status(), exception.code(), exception.getMessage());
    }

    /** Invalid JSON, DTO fields and parameter types are client errors; rejected values are not echoed. */
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            HttpMessageNotReadableException.class, ServletRequestBindingException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> invalidRequest(Exception exception) {
        return failure(400, "INVALID_REQUEST", "The request contains missing or invalid fields.");
    }

    /** Preserves existing explicit controller statuses such as an unknown analytics session. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> explicitStatus(ResponseStatusException exception) {
        return failure(exception.getStatusCode().value(), "REQUEST_REJECTED", "The request could not be completed.");
    }

    /** SQL statements, parameters and connection details never appear in public API responses. */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> database(DataAccessException exception) {
        return failure(503, "DATA_UNAVAILABLE", "Data is temporarily unavailable. Please retry later.");
    }

    /** Replaces the old exception-message echo because it could expose SQL or provider details. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception exception) {
        String reference = java.util.UUID.randomUUID().toString();
        LOG.error("Unexpected request failure: reference={}, type={}", reference, exception.getClass().getSimpleName());
        return failure(500, "INTERNAL_ERROR", "An unexpected error occurred. Reference: " + reference);
    }

    /** Provides a consistent minimal error envelope and prevents caching security failures. */
    private ResponseEntity<Map<String, Object>> failure(int status, String code, String message) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store")
                .body(Map.of("success", false, "errorCode", code, "message", message));
    }
}

```

### .\src\main\java\com\adrovis\influencer\exception\ResourceNotFoundException.java
```java
package com.adrovis.influencer.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

```

### .\src\main\java\com\adrovis\influencer\payment\client\RazorpayClientWrapper.java
```java
package com.adrovis.influencer.payment.client;

import com.adrovis.influencer.payment.config.RazorpayProperties;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class RazorpayClientWrapper {

    private final RazorpayProperties razorpayProperties;
    private RazorpayClient client;
    /** Initializes the payment SDK and fails startup instead of continuing with a missing client. */

    @PostConstruct
    public void init() {
        try {
            this.client = new RazorpayClient(
                    razorpayProperties.getKeyId(),
                    razorpayProperties.getKeySecret()
            );
            log.info("Initialized RazorpayClient successfully.");
        } catch (RazorpayException e) {
            // Do not continue with a null SDK client or expose credentials in a provider exception.
            throw new IllegalStateException("Unable to initialize the configured payment provider");
        }
    }
    /** Creates an authenticated account-owned order after DTO and role checks; pricing contract is unchanged. */

    public Order createOrder(BigDecimal amountInInr, String receiptId) throws RazorpayException {
        JSONObject orderRequest = new JSONObject();
        // Exact conversion prevents silently rounding a user-supplied amount to a different charge.
        long amountInPaise = amountInInr.multiply(BigDecimal.valueOf(100)).longValueExact();
        orderRequest.put("amount", amountInPaise);
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", receiptId);
        orderRequest.put("payment_capture", 1);

        return client.orders.create(orderRequest);
    }
}

```

### .\src\main\java\com\adrovis\influencer\payment\config\PaymentProperties.java
```java
package com.adrovis.influencer.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.payment")
public class PaymentProperties {
    private String currency = "INR";
}

```

### .\src\main\java\com\adrovis\influencer\payment\config\RazorpayProperties.java
```java
package com.adrovis.influencer.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "razorpay")
public class RazorpayProperties {
    private String keyId;
    private String keySecret;
    private String webhookSecret;
}

```

### .\src\main\java\com\adrovis\influencer\payment\controller\AdminPaymentController.java
```java
package com.adrovis.influencer.payment.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.adrovis.influencer.dto.ApiResponse;
import com.adrovis.influencer.payment.dto.response.AdminPaymentStatsResponse;
import com.adrovis.influencer.payment.dto.response.AdminPaymentTransactionResponse;
import com.adrovis.influencer.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "adrovisBearer")
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor

public class AdminPaymentController {

    private final PaymentService paymentService;
    /** Returns platform-wide payment statistics only to Admin accounts. */

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<AdminPaymentStatsResponse>> getStats() {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.getAdminStats()));
    }
    /** Keeps existing search/pagination while mapping internal records to a signature-free Admin DTO. */

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<Page<AdminPaymentTransactionResponse>>> listTransactions(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AdminPaymentTransactionResponse> transactions = paymentService.getAllTransactions(search, pageable)
                .map(AdminPaymentTransactionResponse::from);
        return ResponseEntity.ok(ApiResponse.ok(transactions));
    }
}

```

### .\src\main\java\com\adrovis\influencer\payment\controller\PaymentController.java
```java
package com.adrovis.influencer.payment.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.adrovis.influencer.dto.ApiResponse;
import com.adrovis.influencer.payment.dto.request.CreateOrderRequest;
import com.adrovis.influencer.payment.dto.request.VerifyPaymentRequest;
import com.adrovis.influencer.payment.dto.response.PaymentResponse;
import com.adrovis.influencer.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "adrovisBearer")
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor

public class PaymentController {

    private final PaymentService paymentService;
    /** Creates an authenticated account-owned order after DTO and role checks; pricing contract is unchanged. */

    @PostMapping("/create-order")
    public ResponseEntity<ApiResponse<PaymentResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request
    ) {
        PaymentResponse response = paymentService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Payment order created successfully", response));
    }
    /** Allows only the account owner to verify an existing order through the signed checkout callback. */

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<PaymentResponse>> verifyPayment(
            @Valid @RequestBody VerifyPaymentRequest request
    ) {
        PaymentResponse response = paymentService.verifyPayment(request);
        return ResponseEntity.ok(ApiResponse.ok("Payment verified successfully", response));
    }
}

```

### .\src\main\java\com\adrovis\influencer\payment\controller\RazorpayWebhookController.java
```java
package com.adrovis.influencer.payment.controller;

import com.adrovis.influencer.dto.ApiResponse;
import com.adrovis.influencer.payment.service.PaymentWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks/razorpay")
@RequiredArgsConstructor
public class RazorpayWebhookController {

    private final PaymentWebhookService webhookService;
    /** Accepts provider-signed callbacks; user Bearer authentication is intentionally not the provider protocol. */

    @PostMapping
    public ResponseEntity<ApiResponse<String>> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("X-Razorpay-Signature") String signature
    ) {
        webhookService.processWebhook(payload, signature);
        return ResponseEntity.ok(ApiResponse.ok("Webhook processed successfully", "OK"));
    }
}

```

### .\src\main\java\com\adrovis\influencer\payment\dto\request\CreateOrderRequest.java
```java
package com.adrovis.influencer.payment.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateOrderRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 150)
    private String email;

    @Size(max = 150)
    private String fullName;

    @NotBlank(message = "Plan ID is required")
    @Size(max = 50)
    private String planId;

    @NotBlank(message = "User role is required")
    @Pattern(regexp = "(?i)ADMIN|CREATOR|AGENCY|BRAND", message = "Select a supported account role")
    private String userRole;

    @NotNull(message = "Amount is required")
    // Pricing remains frontend supplied by explicit product decision; only numeric safety is enforced here.
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    @Digits(integer = 10, fraction = 2, message = "Amount must use at most two decimal places")
    private BigDecimal amount;
}

```

### .\src\main\java\com\adrovis\influencer\payment\dto\request\VerifyPaymentRequest.java
```java
package com.adrovis.influencer.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VerifyPaymentRequest {
    @NotBlank(message = "Razorpay order ID is required")
    @Size(max = 100)
    private String razorpayOrderId;

    @NotBlank(message = "Razorpay payment ID is required")
    @Size(max = 100)
    private String razorpayPaymentId;

    @NotBlank(message = "Razorpay signature is required")
    @Pattern(regexp = "[0-9a-fA-F]{64}", message = "Payment signature must be a SHA-256 hexadecimal value")
    private String razorpaySignature;
}

```

### .\src\main\java\com\adrovis\influencer\payment\dto\response\AdminPaymentStatsResponse.java
```java
package com.adrovis.influencer.payment.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AdminPaymentStatsResponse {
    private long totalTransactions;
    private long successTransactions;
    private long initiatedTransactions;
    private long failedTransactions;
    private BigDecimal totalRevenue;
}

```

### .\src\main\java\com\adrovis\influencer\payment\dto\response\AdminPaymentTransactionResponse.java
```java
package com.adrovis.influencer.payment.dto.response;

import com.adrovis.influencer.payment.entity.PaymentTransaction;
import com.adrovis.influencer.payment.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Preserves admin table fields while excluding the gateway signature and exposing reviewed account ownership. */
public record AdminPaymentTransactionResponse(UUID id, UUID accountId, String referenceId, String email,
        String fullName, String planId, String userRole, BigDecimal amount, String currency, PaymentStatus status,
        String razorpayOrderId, String razorpayPaymentId, Instant createdAt, Instant updatedAt) {

    /** Maps internal persistence state to a deliberately limited API contract. */
    public static AdminPaymentTransactionResponse from(PaymentTransaction payment) {
        return new AdminPaymentTransactionResponse(payment.getId(), payment.getAccountId(), payment.getReferenceId(),
                payment.getEmail(), payment.getFullName(), payment.getPlanId(), payment.getUserRole(),
                payment.getAmount(), payment.getCurrency(), payment.getStatus(), payment.getRazorpayOrderId(),
                payment.getRazorpayPaymentId(), payment.getCreatedAt(), payment.getUpdatedAt());
    }
}

```

### .\src\main\java\com\adrovis\influencer\payment\dto\response\PaymentResponse.java
```java
package com.adrovis.influencer.payment.dto.response;

import com.adrovis.influencer.payment.entity.PaymentTransaction;
import com.adrovis.influencer.payment.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class PaymentResponse {
    private UUID id;
    private String referenceId;
    private String email;
    private String planId;
    private String userRole;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String razorpayKeyId;
    private Instant createdAt;
    /** Maps persistence state into the explicitly defined response fields. */

    public static PaymentResponse from(PaymentTransaction tx, String razorpayKeyId) {
        return PaymentResponse.builder()
                .id(tx.getId())
                .referenceId(tx.getReferenceId())
                .email(tx.getEmail())
                .planId(tx.getPlanId())
                .userRole(tx.getUserRole())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .status(tx.getStatus())
                .razorpayOrderId(tx.getRazorpayOrderId())
                .razorpayPaymentId(tx.getRazorpayPaymentId())
                .razorpayKeyId(razorpayKeyId)
                .createdAt(tx.getCreatedAt())
                .build();
    }
}

```

### .\src\main\java\com\adrovis\influencer\payment\entity\PaymentTransaction.java
```java
package com.adrovis.influencer.payment.entity;

import com.adrovis.influencer.payment.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "payment_transactions",
        indexes = {
                @Index(name = "idx_payment_order_id", columnList = "razorpay_order_id"),
                @Index(name = "idx_payment_status", columnList = "status"),
                @Index(name = "idx_payment_email", columnList = "email")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Stable security-account owner; nullable only for historical records requiring reviewed linking. */
    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "reference_id", nullable = false, unique = true, length = 64)
    private String referenceId;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "full_name", length = 150)
    private String fullName;

    @Column(name = "plan_id", nullable = false, length = 50)
    private String planId; // e.g. "brand_pro", "brand_unlimited", "creator_pro", "creator_elite"

    @Column(name = "user_role", nullable = false, length = 30)
    private String userRole; // "BRAND" or "CREATOR"

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount; // in INR (e.g. 250.00)

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.INITIATED;

    @Column(name = "razorpay_order_id", unique = true, length = 100)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 100)
    private String razorpayPaymentId;

    @Column(name = "razorpay_signature", length = 255)
    private String razorpaySignature;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    /** Records the original payment success state after callers have checked signature, owner and lifecycle. */

    public void markSuccess(String paymentId, String signature) {
        this.status = PaymentStatus.SUCCESS;
        this.razorpayPaymentId = paymentId;
        this.razorpaySignature = signature;
    }
    /** Retains the existing domain helper; untrusted signature failures no longer invoke it. */

    public void markFailed() {
        this.status = PaymentStatus.FAILED;
    }
}

```

### .\src\main\java\com\adrovis\influencer\payment\entity\PaymentWebhookEvent.java
```java
package com.adrovis.influencer.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "payment_webhook_events",
        indexes = {
                @Index(name = "idx_webhook_event_id", columnList = "event_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", unique = true, length = 100)
    private String eventId;

    @Column(name = "event_type", length = 100)
    private String eventType;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;
}

```

### .\src\main\java\com\adrovis\influencer\payment\enums\PaymentStatus.java
```java
package com.adrovis.influencer.payment.enums;

public enum PaymentStatus {
    INITIATED,
    SUCCESS,
    FAILED,
    REFUNDED
}

```

### .\src\main\java\com\adrovis\influencer\payment\repository\PaymentTransactionRepository.java
```java
package com.adrovis.influencer.payment.repository;

import com.adrovis.influencer.payment.entity.PaymentTransaction;
import com.adrovis.influencer.payment.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    Optional<PaymentTransaction> findByRazorpayOrderId(String razorpayOrderId);
    /** Serializes checkout and webhook updates so a late callback cannot overwrite another payment. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentTransaction p WHERE p.razorpayOrderId = :orderId")
    Optional<PaymentTransaction> findLockedByRazorpayOrderId(@Param("orderId") String orderId);

    Optional<PaymentTransaction> findByReferenceId(String referenceId);

    long countByStatus(PaymentStatus status);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentTransaction p WHERE p.status = 'SUCCESS'")
    BigDecimal sumTotalRevenue();

    Page<PaymentTransaction> findByEmailContainingIgnoreCaseOrPlanIdContainingIgnoreCase(
            String email, String planId, Pageable pageable
    );
}

```

### .\src\main\java\com\adrovis\influencer\payment\repository\PaymentWebhookEventRepository.java
```java
package com.adrovis.influencer.payment.repository;

import com.adrovis.influencer.payment.entity.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, UUID> {
    boolean existsByEventId(String eventId);

    /** A database unique constraint claims retries atomically; concurrent delivery cannot process twice. */
    @Modifying
    @Query(value = """
            INSERT INTO public.payment_webhook_events (id, event_id, event_type, payload, received_at)
            VALUES (:id, :eventId, :type, :payload, CURRENT_TIMESTAMP)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int claim(@Param("id") UUID id, @Param("eventId") String eventId,
              @Param("type") String type, @Param("payload") String payload);
}

```

### .\src\main\java\com\adrovis\influencer\payment\security\PaymentOperationException.java
```java
package com.adrovis.influencer.payment.security;

/** Carries only safe payment-domain messages; never wrap secrets or gateway response bodies. */
public class PaymentOperationException extends RuntimeException {

    private final int status;
    private final String code;
    /** Initializes the explicit dependencies and validates this component's configuration. */

    public PaymentOperationException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /** Returns the intentional HTTP status for this payment outcome. */
    public int status() {
        return status;
    }

    /** Returns the stable frontend error identifier. */
    public String code() {
        return code;
    }
}

```

### .\src\main\java\com\adrovis\influencer\payment\security\RazorpayWebhookVerifier.java
```java
package com.adrovis.influencer.payment.security;

import com.adrovis.influencer.payment.config.RazorpayProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RazorpayWebhookVerifier {

    private final RazorpayProperties razorpayProperties;

    /** Authenticates the exact raw body; missing secrets/signatures fail closed, never bypass verification. */
    public boolean verifySignature(String payload, String signature) {
        String secret = razorpayProperties.getWebhookSecret();
        if (secret == null || secret.isBlank() || payload == null || signature == null
                || !signature.matches("[0-9a-fA-F]{64}")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(expected, HexFormat.of().parseHex(signature));
        } catch (java.security.GeneralSecurityException | IllegalArgumentException exception) {
            return false;
        }
    }
}

```

### .\src\main\java\com\adrovis\influencer\payment\service\PaymentService.java
```java
package com.adrovis.influencer.payment.service;

import com.adrovis.influencer.payment.dto.request.CreateOrderRequest;
import com.adrovis.influencer.payment.dto.request.VerifyPaymentRequest;
import com.adrovis.influencer.payment.dto.response.AdminPaymentStatsResponse;
import com.adrovis.influencer.payment.dto.response.PaymentResponse;
import com.adrovis.influencer.payment.entity.PaymentTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentService {
    /** Requires a verified caller; ownership and contact identity come from server authentication. */
    PaymentResponse createOrder(CreateOrderRequest request);
    /** Verifies only a caller-owned order; another account cannot submit its payment callback. */
    PaymentResponse verifyPayment(VerifyPaymentRequest request);
    /** Platform-wide financial reporting requires ADMIN at the implementation boundary. */
    AdminPaymentStatsResponse getAdminStats();
    /** Internal read model for Admin reporting; controllers map it to a signature-free DTO. */
    Page<PaymentTransaction> getAllTransactions(String search, Pageable pageable);
}

```

### .\src\main\java\com\adrovis\influencer\payment\service\PaymentWebhookService.java
```java
package com.adrovis.influencer.payment.service;

public interface PaymentWebhookService {
    void processWebhook(String payload, String signature);
}

```

### .\src\main\java\com\adrovis\influencer\payment\service\impl\PaymentServiceImpl.java
```java
package com.adrovis.influencer.payment.service.impl;

import com.adrovis.influencer.payment.client.RazorpayClientWrapper;
import com.adrovis.influencer.payment.config.RazorpayProperties;
import com.adrovis.influencer.payment.dto.request.CreateOrderRequest;
import com.adrovis.influencer.payment.dto.request.VerifyPaymentRequest;
import com.adrovis.influencer.payment.dto.response.AdminPaymentStatsResponse;
import com.adrovis.influencer.payment.dto.response.PaymentResponse;
import com.adrovis.influencer.payment.entity.PaymentTransaction;
import com.adrovis.influencer.payment.enums.PaymentStatus;
import com.adrovis.influencer.payment.repository.PaymentTransactionRepository;
import com.adrovis.influencer.payment.service.PaymentService;
import com.adrovis.influencer.payment.security.PaymentOperationException;
import com.adrovis.influencer.security.CurrentAccount;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import com.razorpay.Order;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentTransactionRepository transactionRepository;
    private final RazorpayClientWrapper razorpayClient;
    private final RazorpayProperties razorpayProperties;
    private final CurrentAccount accounts;

    /** Preserves frontend pricing while binding every new order to a verified account and permitted role. */
    @Override
    @PreAuthorize("isAuthenticated()")
    @Transactional("primaryTransactionManager")
    public PaymentResponse createOrder(CreateOrderRequest request) {
        var account = accounts.require();
        if (!account.email().equals(request.getEmail().trim().toLowerCase(java.util.Locale.ROOT))) {
            throw new AccessDeniedException("Payment email must match the verified account");
        }
        String role = request.getUserRole().toUpperCase(java.util.Locale.ROOT);
        if (!account.roles().contains(role)) {
            throw new AccessDeniedException("The account does not have the requested role");
        }
        if (request.getAmount() == null || request.getAmount().signum() <= 0
                || request.getAmount().stripTrailingZeros().scale() > 2) {
            throw new PaymentOperationException(400, "INVALID_AMOUNT", "Use a positive amount with two decimal places.");
        }
        String referenceId = "TXN-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(java.util.Locale.ROOT);
        Order razorpayOrder;
        try {
            razorpayOrder = razorpayClient.createOrder(request.getAmount(), referenceId);
        } catch (com.razorpay.RazorpayException exception) {
            // Removed provider-message echo and email logging; those may contain credentials or personal data.
            log.warn("Payment provider did not create an order.");
            throw new PaymentOperationException(502, "PAYMENT_PROVIDER_UNAVAILABLE", "Unable to create the payment order.");
        }
        String orderId = razorpayOrder.get("id");
        if (orderId == null || orderId.isBlank()) {
            throw new PaymentOperationException(502, "PAYMENT_PROVIDER_UNAVAILABLE", "Unable to create the payment order.");
        }
        PaymentTransaction transaction = PaymentTransaction.builder()
                .accountId(account.id()).referenceId(referenceId).email(account.email())
                .fullName(request.getFullName()).planId(request.getPlanId()).userRole(role)
                .amount(request.getAmount()).currency("INR").status(PaymentStatus.INITIATED)
                .razorpayOrderId(orderId).build();
        transactionRepository.save(transaction);
        log.info("Created payment order: reference={}", referenceId);
        return PaymentResponse.from(transaction, razorpayProperties.getKeyId());
    }

    /** Checks ownership before signature processing; an invalid signature never changes payment state. */
    @Override
    @PreAuthorize("isAuthenticated()")
    @Transactional("primaryTransactionManager")
    public PaymentResponse verifyPayment(VerifyPaymentRequest request) {
        var account = accounts.require();
        PaymentTransaction transaction = transactionRepository
                .findLockedByRazorpayOrderId(request.getRazorpayOrderId())
                .filter(payment -> account.id().equals(payment.getAccountId()))
                .orElseThrow(() -> new PaymentOperationException(404, "PAYMENT_NOT_FOUND", "Payment order not found."));
        org.json.JSONObject options = new org.json.JSONObject();
        // Use the stored order identifier, not a different client-provided order, in the signature check.
        options.put("razorpay_order_id", transaction.getRazorpayOrderId());
        options.put("razorpay_payment_id", request.getRazorpayPaymentId());
        options.put("razorpay_signature", request.getRazorpaySignature());
        boolean valid;
        try {
            valid = Utils.verifyPaymentSignature(options, razorpayProperties.getKeySecret());
        } catch (com.razorpay.RazorpayException exception) {
            valid = false;
        }
        if (!valid) {
            throw new PaymentOperationException(400, "INVALID_PAYMENT_SIGNATURE", "Payment signature is invalid.");
        }
        if (transaction.getStatus() == PaymentStatus.REFUNDED
                || (transaction.getStatus() == PaymentStatus.SUCCESS
                && !request.getRazorpayPaymentId().equals(transaction.getRazorpayPaymentId()))) {
            throw new PaymentOperationException(409, "PAYMENT_STATE_CONFLICT", "Payment state does not permit this update.");
        }
        if (transaction.getStatus() != PaymentStatus.SUCCESS) {
            // Preserve the existing checkout verification lifecycle; settlement reconciliation remains the webhook's job.
            transaction.markSuccess(request.getRazorpayPaymentId(), request.getRazorpaySignature());
            transactionRepository.save(transaction);
        }
        return PaymentResponse.from(transaction, razorpayProperties.getKeyId());
    }

    /** Platform-wide payment reporting is restricted in the service as well as the HTTP filter. */
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(value = "primaryTransactionManager", readOnly = true)
    public AdminPaymentStatsResponse getAdminStats() {
        long total = transactionRepository.count();
        long success = transactionRepository.countByStatus(PaymentStatus.SUCCESS);
        long initiated = transactionRepository.countByStatus(PaymentStatus.INITIATED);
        long failed = transactionRepository.countByStatus(PaymentStatus.FAILED);
        BigDecimal revenue = transactionRepository.sumTotalRevenue();

        return AdminPaymentStatsResponse.builder()
                .totalTransactions(total)
                .successTransactions(success)
                .initiatedTransactions(initiated)
                .failedTransactions(failed)
                .totalRevenue(revenue != null ? revenue : BigDecimal.ZERO)
                .build();
    }

    /** Platform-wide payment reporting is restricted in the service as well as the HTTP filter. */
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(value = "primaryTransactionManager", readOnly = true)
    public Page<PaymentTransaction> getAllTransactions(String search, Pageable pageable) {
        if (search == null || search.isBlank()) {
            return transactionRepository.findAll(pageable);
        }
        return transactionRepository.findByEmailContainingIgnoreCaseOrPlanIdContainingIgnoreCase(search.trim(), search.trim(), pageable);
    }
}

```

### .\src\main\java\com\adrovis\influencer\payment\service\impl\PaymentWebhookServiceImpl.java
```java
package com.adrovis.influencer.payment.service.impl;

import com.adrovis.influencer.payment.enums.PaymentStatus;
import com.adrovis.influencer.payment.repository.PaymentTransactionRepository;
import com.adrovis.influencer.payment.repository.PaymentWebhookEventRepository;
import com.adrovis.influencer.payment.security.PaymentOperationException;
import com.adrovis.influencer.payment.security.RazorpayWebhookVerifier;
import com.adrovis.influencer.payment.service.PaymentWebhookService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentWebhookServiceImpl implements PaymentWebhookService {

    private final PaymentWebhookEventRepository webhookEventRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final RazorpayWebhookVerifier webhookVerifier;
    private final ObjectMapper objectMapper;

    @Value("${app.payment.max-webhook-bytes:262144}")
    private int maxWebhookBytes = 262144;

    /** Provider callbacks use signed raw bodies, not browser JWTs; all state changes remain transactional. */
    @Override
    @Transactional("primaryTransactionManager")
    public void processWebhook(String payload, String signature) {
        if (payload == null || payload.getBytes(StandardCharsets.UTF_8).length > maxWebhookBytes) {
            throw new PaymentOperationException(413, "WEBHOOK_TOO_LARGE", "Webhook payload exceeds the allowed size.");
        }
        if (!webhookVerifier.verifySignature(payload, signature)) {
            throw new PaymentOperationException(401, "INVALID_WEBHOOK_SIGNATURE", "Webhook signature is invalid.");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            throw new PaymentOperationException(400, "INVALID_WEBHOOK", "Webhook payload is invalid.");
        }
        if (root == null || !root.isObject() || !root.path("event").isTextual()
                || root.path("event").asText().isBlank() || root.path("event").asText().length() > 100) {
            throw new PaymentOperationException(400, "INVALID_WEBHOOK", "Webhook event type is missing or invalid.");
        }
        String eventType = root.path("event").asText();
        // Hash the signed body, not an unsigned request ID; identical retries remain idempotent across instances.
        String eventId = "sha256:" + hash(payload);
        if (webhookEventRepository.claim(UUID.randomUUID(), eventId, eventType, payload) == 0) {
            return;
        }
        if (!"payment.captured".equals(eventType)) {
            return; // Preserve the original supported event scope; other signed events are retained for audit.
        }
        JsonNode payment = root.path("payload").path("payment").path("entity");
        String orderId = payment.path("order_id").asText();
        String paymentId = payment.path("id").asText();
        // Do not coerce numeric/structured identifiers into order lookups or persist unbounded provider IDs.
        if (!payment.path("order_id").isTextual() || !payment.path("id").isTextual()
                || orderId.isBlank() || paymentId.isBlank() || orderId.length() > 100 || paymentId.length() > 100
                || !payment.path("amount").isIntegralNumber()) {
            throw new PaymentOperationException(400, "INVALID_WEBHOOK", "Captured payment fields are invalid.");
        }
        var transaction = transactionRepository.findLockedByRazorpayOrderId(orderId)
                .orElseThrow(() -> new PaymentOperationException(503, "PAYMENT_NOT_READY", "Payment order is not ready."));
        // A valid provider signature does not justify crediting a different amount, currency or order.
        BigDecimal paidPaise = new BigDecimal(payment.path("amount").asText());
        if (transaction.getAmount().multiply(BigDecimal.valueOf(100)).compareTo(paidPaise) != 0
                || !transaction.getCurrency().equals(payment.path("currency").asText())) {
            throw new PaymentOperationException(409, "PAYMENT_AMOUNT_MISMATCH", "Payment details do not match the order.");
        }
        if (transaction.getStatus() == PaymentStatus.REFUNDED) {
            return; // A delayed captured notification must never undo a recorded refund.
        }
        if (transaction.getStatus() == PaymentStatus.SUCCESS) {
            if (!paymentId.equals(transaction.getRazorpayPaymentId())) {
                throw new PaymentOperationException(409, "PAYMENT_STATE_CONFLICT", "Payment state conflicts with the event.");
            }
            return;
        }
        transaction.markSuccess(paymentId, "WEBHOOK_VERIFIED");
        transactionRepository.save(transaction);
    }

    /** Creates a bounded deterministic retry key without logging or exposing the provider payload. */
    private String hash(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required");
        }
    }
}

```

### .\src\main\java\com\adrovis\influencer\repository\AgencyRepository.java
```java
package com.adrovis.influencer.repository;

import com.adrovis.influencer.entity.AgencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgencyRepository extends JpaRepository<AgencyEntity, UUID> {
    Optional<AgencyEntity> findByContactPhone(String contactPhone);
}

```

### .\src\main\java\com\adrovis\influencer\repository\AnalyticsRepository.java
```java
package com.adrovis.influencer.repository;

import com.adrovis.influencer.entity.AnalyticsSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public interface AnalyticsRepository
        extends JpaRepository<AnalyticsSessionEntity, UUID>, JpaSpecificationExecutor<AnalyticsSessionEntity> {

    // =========================================================================
    // 1. INGESTION & ATOMIC UPSERTS
    // =========================================================================

    @Modifying
    @Query(value = """
        INSERT INTO public.analytics_daily_visitors (id, visitor_hash, visit_date, first_seen_at)
        SELECT gen_random_uuid(), :visitorHash, (NOW() AT TIME ZONE 'Asia/Kolkata')::date, NOW()
        WHERE NOT EXISTS (
            SELECT 1 FROM public.analytics_daily_visitors d
            WHERE d.visitor_hash = :visitorHash
              AND d.visit_date = (NOW() AT TIME ZONE 'Asia/Kolkata')::date
        )
        """, nativeQuery = true)
    void recordDailyUniqueVisitor(@Param("visitorHash") String visitorHash);

    @Modifying
    @Query(value = """
        INSERT INTO public.analytics_sessions (
            session_id, visitor_hash, person_id, entry_path, last_path,
            referrer, referrer_domain, utm_source, utm_medium, utm_campaign,
            utm_content, utm_term, campaign_id, channel, country, region, city,
            device_type, browser, os, in_app_browser, in_app_source,
            screen_resolution, timezone, language, returning_visitor,
            page_views, duration_seconds, active_dwell_seconds, applied,
            created_at, last_active_at
        )
        VALUES (
            :sessionId, :visitorHash, :visitorHash, :path, :path,
            :referrer, :referrerDomain, :utmSource, :utmMedium, :utmCampaign,
            :utmContent, :utmTerm, CAST(:campaignId AS UUID), :channel, :country, :region, :city,
            :deviceType, :browser, :os, :inAppBrowser, :inAppSource,
            :screenResolution, :timezone, :language,
            EXISTS (SELECT 1 FROM public.analytics_sessions s WHERE s.visitor_hash = :visitorHash AND s.session_id <> :sessionId),
            1, 0, 0, FALSE, NOW(), NOW()
        )
        ON CONFLICT (session_id) DO UPDATE SET
            last_path = EXCLUDED.last_path,
            page_views = public.analytics_sessions.page_views + 1,
            engaged = TRUE,
            campaign_id = COALESCE(EXCLUDED.campaign_id, public.analytics_sessions.campaign_id),
            country = CASE WHEN public.analytics_sessions.country IS NULL OR public.analytics_sessions.country = 'XX' THEN EXCLUDED.country ELSE public.analytics_sessions.country END,
            region = CASE WHEN public.analytics_sessions.region IS NULL OR public.analytics_sessions.region = 'Unknown' THEN EXCLUDED.region ELSE public.analytics_sessions.region END,
            city = CASE WHEN public.analytics_sessions.city IS NULL OR public.analytics_sessions.city = 'Unknown' THEN EXCLUDED.city ELSE public.analytics_sessions.city END,
            last_active_at = NOW()
        """, nativeQuery = true)
    void upsertSession(
            @Param("sessionId") UUID sessionId,
            @Param("visitorHash") String visitorHash,
            @Param("path") String path,
            @Param("referrer") String referrer,
            @Param("referrerDomain") String referrerDomain,
            @Param("utmSource") String utmSource,
            @Param("utmMedium") String utmMedium,
            @Param("utmCampaign") String utmCampaign,
            @Param("utmContent") String utmContent,
            @Param("utmTerm") String utmTerm,
            @Param("campaignId") String campaignId,
            @Param("channel") String channel,
            @Param("country") String country,
            @Param("region") String region,
            @Param("city") String city,
            @Param("deviceType") String deviceType,
            @Param("browser") String browser,
            @Param("os") String os,
            @Param("inAppBrowser") boolean inAppBrowser,
            @Param("inAppSource") String inAppSource,
            @Param("screenResolution") String screenResolution,
            @Param("timezone") String timezone,
            @Param("language") String language
    );

    @Modifying
    @Query(value = """
        UPDATE public.analytics_sessions
        SET duration_seconds = GREATEST(duration_seconds, :durationSeconds),
            active_dwell_seconds = GREATEST(active_dwell_seconds, :activeDwellSeconds),
            engaged = (engaged OR :activeDwellSeconds >= 15 OR page_views >= 2 OR applied),
            last_active_at = NOW()
        WHERE session_id = :sessionId
        """, nativeQuery = true)
    void updateHeartbeat(
            @Param("sessionId") UUID sessionId,
            @Param("durationSeconds") int durationSeconds,
            @Param("activeDwellSeconds") int activeDwellSeconds
    );

    @Modifying
    @Query(value = """
        UPDATE public.analytics_sessions
        SET applied = TRUE,
            engaged = TRUE,
            applied_at = COALESCE(applied_at, NOW()),
            last_active_at = NOW()
        WHERE session_id = :sessionId
        """, nativeQuery = true)
    void markApplicationConverted(@Param("sessionId") UUID sessionId);

    @Modifying
    @Query(value = """
        INSERT INTO public.analytics_leads (
            session_id, visitor_hash, email, full_name, phone, user_type, consent_text, created_at
        )
        SELECT s.session_id, s.visitor_hash, :email, :fullName, :phone, :userType, :consentText, NOW()
        FROM public.analytics_sessions s
        WHERE s.session_id = :sessionId
        ON CONFLICT (session_id) DO UPDATE SET
            email = EXCLUDED.email,
            full_name = COALESCE(EXCLUDED.full_name, public.analytics_leads.full_name),
            phone = COALESCE(EXCLUDED.phone, public.analytics_leads.phone),
            user_type = COALESCE(EXCLUDED.user_type, public.analytics_leads.user_type)
        """, nativeQuery = true)
    int upsertLead(
            @Param("sessionId") UUID sessionId,
            @Param("email") String email,
            @Param("fullName") String fullName,
            @Param("phone") String phone,
            @Param("userType") String userType,
            @Param("consentText") String consentText
    );

    // =========================================================================
    // 2. REAL-TIME & UNIQUE PERSON COUNTERS
    // =========================================================================

    /** Counts distinct real people active in the last 5 minutes, not raw sessions. */
    @Query(value = """
        SELECT COUNT(DISTINCT COALESCE(person_id, visitor_hash))
        FROM public.analytics_sessions
        WHERE last_active_at >= NOW() - INTERVAL '5 minutes'
        """, nativeQuery = true)
    long countLiveActiveUsers();

    @Query(value = """
        SELECT COUNT(DISTINCT COALESCE(person_id, visitor_hash))
        FROM public.analytics_sessions
        WHERE last_active_at >= NOW() - INTERVAL '5 minutes'
          AND (last_path LIKE '/campaigns%' OR campaign_id IS NOT NULL)
        """, nativeQuery = true)
    long countLiveViewingCampaigns();

    @Query(value = """
        SELECT COUNT(DISTINCT COALESCE(person_id, visitor_hash))
        FROM public.analytics_sessions
        WHERE (created_at AT TIME ZONE 'Asia/Kolkata')::date = (NOW() AT TIME ZONE 'Asia/Kolkata')::date
        """, nativeQuery = true)
    long countTodayUniqueVisitors();

    @Query(value = """
        SELECT COUNT(DISTINCT COALESCE(person_id, visitor_hash))
        FROM public.analytics_sessions
        WHERE (created_at AT TIME ZONE 'Asia/Kolkata')::date >= (NOW() AT TIME ZONE 'Asia/Kolkata')::date - 6
        """, nativeQuery = true)
    long countThisWeekUniqueVisitors();

    @Query(value = """
        SELECT COUNT(DISTINCT COALESCE(person_id, visitor_hash))
        FROM public.analytics_sessions
        WHERE date_trunc('month', created_at AT TIME ZONE 'Asia/Kolkata') = date_trunc('month', NOW() AT TIME ZONE 'Asia/Kolkata')
        """, nativeQuery = true)
    long countThisMonthUniqueVisitors();

    @Query(value = """
        SELECT COUNT(DISTINCT COALESCE(person_id, visitor_hash))
        FROM public.analytics_sessions
        """, nativeQuery = true)
    long countTotalAllTimeUniqueVisitors();

    // =========================================================================
    // 3. OVERVIEW AGGREGATES & ENGAGEMENT
    // =========================================================================

    @Query(value = """
        SELECT
            COUNT(*) AS total_sessions,
            COUNT(DISTINCT COALESCE(person_id, visitor_hash)) AS unique_people,
            COUNT(*) FILTER (WHERE applied = TRUE) AS applied_sessions,
            COUNT(DISTINCT COALESCE(person_id, visitor_hash)) FILTER (WHERE applied = TRUE) AS applied_people,
            COALESCE(AVG(duration_seconds), 0) AS avg_duration,
            COALESCE(AVG(active_dwell_seconds), 0) AS avg_active,
            COALESCE(AVG(page_views), 0) AS avg_pages,
            COALESCE(AVG(max_scroll_depth), 0) AS avg_scroll_depth,
            COALESCE(100.0 * COUNT(*) FILTER (WHERE NOT (active_dwell_seconds >= 15 OR page_views >= 2 OR applied = TRUE)) / NULLIF(COUNT(*), 0), 0) AS bounce_rate,
            COALESCE(100.0 * COUNT(*) FILTER (WHERE active_dwell_seconds >= 15 OR page_views >= 2 OR applied = TRUE) / NULLIF(COUNT(*), 0), 0) AS engaged_rate,
            COALESCE(100.0 * COUNT(*) FILTER (WHERE device_type = 'Mobile') / NULLIF(COUNT(*), 0), 0) AS mobile_pct,
            COALESCE(100.0 * COUNT(*) FILTER (WHERE device_type = 'Desktop') / NULLIF(COUNT(*), 0), 0) AS desktop_pct,
            COALESCE(100.0 * COUNT(*) FILTER (WHERE device_type = 'Tablet') / NULLIF(COUNT(*), 0), 0) AS tablet_pct,
            COALESCE(100.0 * COUNT(*) FILTER (WHERE in_app_browser = TRUE) / NULLIF(COUNT(*), 0), 0) AS in_app_pct,
            COALESCE(100.0 * COUNT(*) FILTER (WHERE returning_visitor = TRUE) / NULLIF(COUNT(*), 0), 0) AS returning_pct,
            COALESCE(100.0 * COUNT(*) FILTER (WHERE is_vpn_or_proxy = TRUE OR is_datacenter = TRUE OR timezone_mismatch = TRUE) / NULLIF(COUNT(*), 0), 0) AS vpn_pct
        FROM public.analytics_sessions
        WHERE created_at >= NOW() - CAST(:days || ' days' AS INTERVAL)
        """, nativeQuery = true)
    Map<String, Object> getSessionSummary(@Param("days") int days);

    @Query(value = """
        SELECT COUNT(*)
        FROM public.analytics_leads
        WHERE created_at >= NOW() - CAST(:days || ' days' AS INTERVAL)
        """, nativeQuery = true)
    long countLeadsSince(@Param("days") int days);

    @Query(value = """
        SELECT
            channel,
            COUNT(*) AS sessions,
            COUNT(DISTINCT COALESCE(person_id, visitor_hash)) AS unique_visitors,
            COUNT(*) FILTER (WHERE applied = TRUE) AS conversions,
            ROUND(100.0 * COUNT(*) FILTER (WHERE applied = TRUE) / NULLIF(COUNT(*), 0), 1) AS conversion_rate
        FROM public.analytics_sessions
        WHERE created_at >= NOW() - CAST(:days || ' days' AS INTERVAL)
        GROUP BY channel
        ORDER BY unique_visitors DESC, sessions DESC
        """, nativeQuery = true)
    List<Map<String, Object>> getTopTrafficChannels(@Param("days") int days);

    @Query(value = """
        SELECT
            CAST(s.campaign_id AS VARCHAR) AS campaign_id,
            COALESCE(mc.title, c.campaign_title, 'Campaign ' || SUBSTRING(CAST(s.campaign_id AS VARCHAR), 1, 8)) AS campaign_title,
            COUNT(*) AS sessions,
            COUNT(DISTINCT COALESCE(s.person_id, s.visitor_hash)) AS unique_visitors,
            COUNT(*) FILTER (WHERE s.applied = TRUE) AS conversions,
            ROUND(100.0 * COUNT(*) FILTER (WHERE s.applied = TRUE) / NULLIF(COUNT(*), 0), 1) AS conversion_rate
        FROM public.analytics_sessions s
        LEFT JOIN public.managed_campaigns mc ON mc.id = s.campaign_id
        LEFT JOIN public.campaigns c ON c.id = s.campaign_id
        WHERE s.campaign_id IS NOT NULL
          AND s.created_at >= NOW() - CAST(:days || ' days' AS INTERVAL)
        GROUP BY s.campaign_id, mc.title, c.campaign_title
        ORDER BY unique_visitors DESC
        LIMIT 15
        """, nativeQuery = true)
    List<Map<String, Object>> getTopCampaigns(@Param("days") int days);

    @Query(value = """
        SELECT
            COALESCE(referrer_domain, 'Direct / None') AS referrer_domain,
            COUNT(*) AS sessions,
            COUNT(DISTINCT COALESCE(person_id, visitor_hash)) AS unique_visitors,
            COUNT(*) FILTER (WHERE applied = TRUE) AS conversions
        FROM public.analytics_sessions
        WHERE created_at >= NOW() - CAST(:days || ' days' AS INTERVAL)
        GROUP BY COALESCE(referrer_domain, 'Direct / None')
        ORDER BY unique_visitors DESC
        LIMIT 15
        """, nativeQuery = true)
    List<Map<String, Object>> getTopReferrers(@Param("days") int days);

    @Query(value = """
        SELECT
            entry_path AS path,
            COUNT(*) AS sessions,
            COUNT(DISTINCT COALESCE(person_id, visitor_hash)) AS unique_visitors,
            ROUND(AVG(active_dwell_seconds), 1) AS avg_active_seconds,
            ROUND(AVG(max_scroll_depth), 0) AS avg_scroll_depth,
            COUNT(*) FILTER (WHERE applied = TRUE) AS conversions
        FROM public.analytics_sessions
        WHERE created_at >= NOW() - CAST(:days || ' days' AS INTERVAL)
        GROUP BY entry_path
        ORDER BY unique_visitors DESC
        LIMIT 15
        """, nativeQuery = true)
    List<Map<String, Object>> getTopLandingPages(@Param("days") int days);

    @Query(value = """
        SELECT
            COALESCE(NULLIF(city, 'Unknown'), 'Unknown') AS city,
            COALESCE(NULLIF(region, 'Unknown'), 'Unknown') AS region,
            COALESCE(NULLIF(country, 'XX'), 'Unknown') AS country,
            AVG(latitude) AS lat,
            AVG(longitude) AS lon,
            COUNT(*) AS sessions,
            COUNT(DISTINCT COALESCE(person_id, visitor_hash)) AS unique_visitors,
            COUNT(*) FILTER (WHERE applied = TRUE) AS conversions
        FROM public.analytics_sessions
        WHERE created_at >= NOW() - CAST(:days || ' days' AS INTERVAL)
        GROUP BY COALESCE(NULLIF(city, 'Unknown'), 'Unknown'),
                 COALESCE(NULLIF(region, 'Unknown'), 'Unknown'),
                 COALESCE(NULLIF(country, 'XX'), 'Unknown')
        ORDER BY unique_visitors DESC
        LIMIT 25
        """, nativeQuery = true)
    List<Map<String, Object>> getTopCities(@Param("days") int days);

    @Query(value = """
        SELECT
            to_char((created_at AT TIME ZONE 'Asia/Kolkata')::date, 'YYYY-MM-DD') AS day,
            COUNT(DISTINCT COALESCE(person_id, visitor_hash)) AS unique_visitors,
            COUNT(*) AS sessions,
            COUNT(*) FILTER (WHERE active_dwell_seconds >= 15 OR page_views >= 2 OR applied = TRUE) AS engaged_sessions,
            COUNT(*) FILTER (WHERE applied = TRUE) AS conversions
        FROM public.analytics_sessions
        WHERE created_at >= NOW() - CAST(:days || ' days' AS INTERVAL)
        GROUP BY (created_at AT TIME ZONE 'Asia/Kolkata')::date
        ORDER BY (created_at AT TIME ZONE 'Asia/Kolkata')::date ASC
        """, nativeQuery = true)
    List<Map<String, Object>> getDailyVisitorTrend(@Param("days") int days);

    @Query(value = """
        SELECT
            l.session_id,
            COALESCE(s.person_id, l.visitor_hash) AS person_id,
            l.email,
            l.full_name,
            l.phone,
            l.user_type,
            l.consent_text,
            l.created_at,
            s.channel,
            s.utm_source,
            s.utm_campaign,
            s.city,
            s.region,
            s.country,
            s.device_type,
            s.browser,
            s.os
        FROM public.analytics_leads l
        LEFT JOIN public.analytics_sessions s ON s.session_id = l.session_id
        ORDER BY l.created_at DESC
        LIMIT 200
        """, nativeQuery = true)
    List<Map<String, Object>> getRecentLeads();
}
```

### .\src\main\java\com\adrovis\influencer\repository\CampaignImageRepository.java
```java
package com.adrovis.influencer.repository;

import com.adrovis.influencer.entity.CampaignImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CampaignImageRepository extends JpaRepository<CampaignImageEntity, UUID> {
    List<CampaignImageEntity> findByCampaignIdOrderByImageOrderAsc(UUID campaignId);
}

```

### .\src\main\java\com\adrovis\influencer\repository\CampaignRepository.java
```java
package com.adrovis.influencer.repository;

import com.adrovis.influencer.entity.CampaignEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CampaignRepository extends JpaRepository<CampaignEntity, UUID> {

    @Query("SELECT c FROM CampaignEntity c LEFT JOIN FETCH c.images WHERE c.id = :id AND c.isActive = true")
    Optional<CampaignEntity> findActiveByIdWithImages(@Param("id") UUID id);

    Optional<CampaignEntity> findByApplicationFormUrl(String applicationFormUrl);

    @Modifying
    @Query("UPDATE CampaignEntity c SET c.viewsCount = c.viewsCount + 1 WHERE c.id = :id")
    void incrementViewsCount(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE CampaignEntity c SET c.clicksCount = c.clicksCount + 1 WHERE c.id = :id")
    void incrementClicksCount(@Param("id") UUID id);

    @Query(value = """
        SELECT * FROM campaigns c
        WHERE c.is_active = true
          AND (:campaignType IS NULL OR c.campaign_type = :campaignType)
          AND (:maxFollowers IS NULL OR (
                c.canonical_data->'eligibility'->>'minimum_followers' IS NOT NULL 
                AND CAST(c.canonical_data->'eligibility'->>'minimum_followers' AS INTEGER) <= :maxFollowers
              ))
          AND (:brand IS NULL OR LOWER(c.brand_name) LIKE LOWER(CONCAT('%', :brand, '%')) 
               OR LOWER(c.campaign_title) LIKE LOWER(CONCAT('%', :brand, '%')))
          AND (:category IS NULL OR LOWER(CAST(c.canonical_data AS text)) LIKE LOWER(CONCAT('%', :category, '%')))
          AND (:location IS NULL OR LOWER(CAST(c.canonical_data->'eligibility'->'locations' AS text)) LIKE LOWER(CONCAT('%', :location, '%')))
          AND (:minPayout IS NULL OR (
                (c.canonical_data->'compensation'->>'payout_amount_inr' IS NOT NULL 
                 AND CAST(c.canonical_data->'compensation'->>'payout_amount_inr' AS NUMERIC) >= :minPayout)
                OR (c.canonical_data->'compensation'->'payout_tiers'->0->>'payout_inr' IS NOT NULL 
                 AND CAST(c.canonical_data->'compensation'->'payout_tiers'->0->>'payout_inr' AS NUMERIC) >= :minPayout)
              ))
        ORDER BY c.created_at DESC
        """,
            countQuery = """
        SELECT count(*) FROM campaigns c
        WHERE c.is_active = true
          AND (:campaignType IS NULL OR c.campaign_type = :campaignType)
          AND (:maxFollowers IS NULL OR (
                c.canonical_data->'eligibility'->>'minimum_followers' IS NOT NULL 
                AND CAST(c.canonical_data->'eligibility'->>'minimum_followers' AS INTEGER) <= :maxFollowers
              ))
          AND (:brand IS NULL OR LOWER(c.brand_name) LIKE LOWER(CONCAT('%', :brand, '%'))
               OR LOWER(c.campaign_title) LIKE LOWER(CONCAT('%', :brand, '%')))
          AND (:category IS NULL OR LOWER(CAST(c.canonical_data AS text)) LIKE LOWER(CONCAT('%', :category, '%')))
          AND (:location IS NULL OR LOWER(CAST(c.canonical_data->'eligibility'->'locations' AS text)) LIKE LOWER(CONCAT('%', :location, '%')))
          AND (:minPayout IS NULL OR (
                (c.canonical_data->'compensation'->>'payout_amount_inr' IS NOT NULL 
                 AND CAST(c.canonical_data->'compensation'->>'payout_amount_inr' AS NUMERIC) >= :minPayout)
                OR (c.canonical_data->'compensation'->'payout_tiers'->0->>'payout_inr' IS NOT NULL 
                 AND CAST(c.canonical_data->'compensation'->'payout_tiers'->0->>'payout_inr' AS NUMERIC) >= :minPayout)
              ))
        """,
            nativeQuery = true)
    Page<CampaignEntity> findFilteredCampaigns(
            @Param("campaignType") String campaignType,
            @Param("maxFollowers") Integer maxFollowers,
            @Param("brand") String brand,
            @Param("category") String category,
            @Param("location") String location,
            @Param("minPayout") Double minPayout,
            Pageable pageable
    );
}
```

### .\src\main\java\com\adrovis\influencer\repository\InquiryRepository.java
```java
package com.adrovis.influencer.repository;

import com.adrovis.influencer.entity.InquiryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InquiryRepository extends JpaRepository<InquiryEntity, UUID> {

    @Query(value = """
        SELECT * FROM inquiries i
        WHERE (:role IS NULL OR i.role = :role)
          AND (:status IS NULL OR i.status = :status)
          AND (:search IS NULL OR (
                LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(i.email) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(i.company_name, '')) LIKE LOWER(CONCAT('%', :search, '%'))
              ))
        ORDER BY i.created_at DESC
        """,
            countQuery = """
        SELECT count(*) FROM inquiries i
        WHERE (:role IS NULL OR i.role = :role)
          AND (:status IS NULL OR i.status = :status)
          AND (:search IS NULL OR (
                LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(i.email) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(i.company_name, '')) LIKE LOWER(CONCAT('%', :search, '%'))
              ))
        """,
            nativeQuery = true)
    Page<InquiryEntity> findFilteredInquiries(
            @Param("role") String role,
            @Param("status") String status,
            @Param("search") String search,
            Pageable pageable
    );
}
```

### .\src\main\java\com\adrovis\influencer\repository\NewsletterSubscriptionRepository.java
```java
package com.adrovis.influencer.repository;

import com.adrovis.influencer.entity.NewsletterSubscriptionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NewsletterSubscriptionRepository extends JpaRepository<NewsletterSubscriptionEntity, UUID> {

    Optional<NewsletterSubscriptionEntity> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query(value = """
        SELECT * FROM newsletter_subscriptions n
        WHERE (:activeOnly IS NULL OR n.is_active = :activeOnly)
          AND (:search IS NULL OR LOWER(n.email) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY n.subscribed_at DESC
        """,
            countQuery = """
        SELECT count(*) FROM newsletter_subscriptions n
        WHERE (:activeOnly IS NULL OR n.is_active = :activeOnly)
          AND (:search IS NULL OR LOWER(n.email) LIKE LOWER(CONCAT('%', :search, '%')))
        """,
            nativeQuery = true)
    Page<NewsletterSubscriptionEntity> findFilteredSubscriptions(
            @Param("activeOnly") Boolean activeOnly,
            @Param("search") String search,
            Pageable pageable
    );
}
```

### .\src\main\java\com\adrovis\influencer\security\AccessTokenValidator.java
```java
package com.adrovis.influencer.security;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

final class AccessTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AccessTokenValidator.class);
    static final Set<String> ROLES = Set.of("CREATOR", "BRAND", "AGENCY", "ADMIN");
    private final String audience;

    AccessTokenValidator(String audience) { this.audience = audience; }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String reason = check(jwt);
        if (reason == null) return OAuth2TokenValidatorResult.success();
        log.warn("Access token rejected: {}", reason);
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid access token", null));
    }

    private String check(Jwt jwt) {
        try {
            String sub = jwt.getSubject();
            if (sub == null || !UUID.fromString(sub).toString().equalsIgnoreCase(sub)) return "sub is not a full UUID: " + sub;
            if (jwt.getAudience() == null || !jwt.getAudience().contains(audience)) return "aud mismatch: " + jwt.getAudience();
            String use = jwt.getClaimAsString("token_use");
            if (use != null && !"access".equals(use)) return "token_use=" + use;   // blocks onboarding tokens if you issue the claim
            if (jwt.getIssuedAt() == null || jwt.getExpiresAt() == null) return "missing iat/exp";
            if (Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).compareTo(Duration.ofDays(30)) > 0)
                return "lifetime over 30 days";
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null || roles.isEmpty()) return "roles claim missing";
            if (!ROLES.containsAll(roles)) return "unknown roles: " + roles;
            return null;
        } catch (RuntimeException e) {
            return "malformed claims: " + e.getMessage();
        }
    }
}

```

### .\src\main\java\com\adrovis\influencer\security\AccountConnectionController.java
```java
package com.adrovis.influencer.security;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "adrovisBearer")
public class AccountConnectionController {
    /** Returns only the authenticated account identity, never an identity supplied in query parameters. */

    @GetMapping("/api/v1/account/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("success", true, "accountId", jwt.getSubject(), "roles", jwt.getClaimAsStringList("roles"));
    }
}

```

### .\src\main\java\com\adrovis\influencer\security\AccountStatusClient.java
```java
package com.adrovis.influencer.security;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

final class AccountStatusClient {

    private static final Logger log = LoggerFactory.getLogger(AccountStatusClient.class);

    private final RestClient client;
    private final AdrovisSecurityProperties properties;

    AccountStatusClient(AdrovisSecurityProperties properties) {
        this(properties, builder(properties));
    }

    AccountStatusClient(AdrovisSecurityProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.client = builder.build();
    }

    /** Uses bounded HTTPS timeouts and disables redirects so Bearer credentials cannot follow another host. */
    private static RestClient.Builder builder(AdrovisSecurityProperties properties) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(properties.statusTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(properties.statusTimeout());
        return RestClient.builder().requestFactory(factory);
    }

    /** Validates current account status and returns the verified email. */
    String verify(Jwt jwt) {
        Map<?, ?> body;
        try {
            body = client.get().uri(properties.statusUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getTokenValue())
                    .retrieve().body(Map.class);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw rejected("auth/me returned HTTP " + status
                        + " (status URL host may not accept this token: " + properties.statusUrl().getHost() + ")");
            }
            throw unavailable("auth/me returned HTTP " + status);
        } catch (RestClientException exception) {
            throw unavailable("auth/me call failed: " + exception.getClass().getSimpleName()
                    + " - " + exception.getMessage());
        }

        if (body == null || !Boolean.TRUE.equals(body.get("success"))) {
            throw unavailable("auth/me body missing or success != true: " + body);
        }
        if (!(body.get("user") instanceof Map<?, ?> user)) {
            throw unavailable("auth/me response has no 'user' object");
        }

        // Subject must match the account the status service reports.
        if (jwt.getSubject() == null || !jwt.getSubject().equals(String.valueOf(user.get("id")))) {
            throw rejected("user.id (" + user.get("id") + ") != token sub (" + jwt.getSubject() + ")");
        }

        // Accept either field name for the email-verified flag.
        Object verifiedFlag = user.containsKey("isEmailVerified")
                ? user.get("isEmailVerified") : user.get("emailVerified");
        if (!Boolean.TRUE.equals(verifiedFlag)) {
            throw rejected("email not verified (flag=" + verifiedFlag + ")");
        }

        if (!(user.get("roles") instanceof List<?> currentRoles)) {
            throw rejected("user.roles missing in auth/me response");
        }
        Set<String> statusRoles = currentRoles.stream()
                .filter(String.class::isInstance).map(String.class::cast)
                .map(role -> role.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());

        List<String> tokenRolesRaw = jwt.getClaimAsStringList("roles");
        Set<String> tokenRoles = tokenRolesRaw == null ? Set.of()
                : tokenRolesRaw.stream().map(role -> role.toUpperCase(Locale.ROOT)).collect(Collectors.toSet());

        if (statusRoles.isEmpty() || !AccessTokenValidator.ROLES.containsAll(statusRoles)) {
            throw rejected("unknown or empty roles from auth/me: " + statusRoles);
        }
        if (!statusRoles.equals(tokenRoles)) {
            throw rejected("roles differ: auth/me=" + statusRoles + " token=" + tokenRoles);
        }

        if (!(user.get("email") instanceof String email) || email.isBlank()) {
            throw unavailable("user.email missing in auth/me response");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Invalid-token outcome (401). The reason is logged server-side only. */
    private JwtValidationException rejected(String reason) {
        log.warn("Account status check rejected token: {}", reason);
        return new JwtValidationException("Account credentials are no longer valid",
                List.of(new OAuth2Error("invalid_token", "Account credentials are no longer valid", null)));
    }

    /** Retryable failure. The reason is logged server-side only. */
    private JwtException unavailable(String reason) {
        log.warn("Account status check unavailable: {}", reason);
        return new JwtException("Account validation is unavailable");
    }
}
```

### .\src\main\java\com\adrovis\influencer\security\AdrovisSecurityProperties.java
```java
package com.adrovis.influencer.security;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties("adrovis.auth")
public record AdrovisSecurityProperties(String issuer, String audience, Resource publicKeyLocation,
                                        URI statusUrl, Duration statusTimeout) {

    public AdrovisSecurityProperties {
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()
                || publicKeyLocation == null || statusUrl == null) {
            throw new IllegalStateException("Configure Adrovis issuer, audience, public key and status URL");
        }
        URI issuerUri = URI.create(issuer);
        requireHttps(issuerUri);
        requireHttps(statusUrl);
        if (!"/api/v1/auth/me".equals(statusUrl.getPath())) {
            throw new IllegalStateException("The account status URL must point to /api/v1/auth/me");
        }
        if (statusTimeout == null) {
            statusTimeout = Duration.ofSeconds(10);
        }
        if (statusTimeout.isNegative() || statusTimeout.isZero() || statusTimeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalStateException("Account status timeout must be greater than zero and at most 30 seconds");
        }
        if (!(publicKeyLocation instanceof org.springframework.core.io.ByteArrayResource)
                && !(publicKeyLocation instanceof org.springframework.core.io.ClassPathResource)
                && !publicKeyLocation.getDescription().startsWith("URL [file:")
                && !(publicKeyLocation instanceof org.springframework.core.io.FileSystemResource)) {
            throw new IllegalStateException("Load the trusted public key from a local resource, not a remote URL");
        }
    }
    /** Rejects insecure endpoints and credential-bearing or ambiguous URLs. */

    private static void requireHttps(URI uri) {
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException("Adrovis endpoints must use HTTPS without credentials, query or fragment");
        }
    }
}

```

### .\src\main\java\com\adrovis\influencer\security\CurrentAccount.java
```java
package com.adrovis.influencer.security;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Reads identity exclusively from the JWT validated and enriched by the account-service check. */
@Component
public class CurrentAccount {

    /** Supplies trusted identity to business services; request bodies never establish ownership. */
    public Identity require() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("An account access token is required");
        }
        String email = jwt.getToken().getClaimAsString("account_email");
        if (email == null || email.isBlank()) {
            throw new AuthenticationServiceException("Verified account metadata is unavailable");
        }
        Set<String> roles = jwt.getAuthorities().stream().map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring(5)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new Identity(UUID.fromString(jwt.getToken().getSubject()), email.trim().toLowerCase(Locale.ROOT), roles);
    }

    /** Account UUID is the ownership key; verified email is used only for contact/consent checks. */
    public record Identity(UUID id, String email, Set<String> roles) { }
}

```

### .\src\main\java\com\adrovis\influencer\security\JwtValidationConfig.java
```java
package com.adrovis.influencer.security;

import java.io.InputStream;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
@EnableConfigurationProperties(AdrovisSecurityProperties.class)
public class JwtValidationConfig {
    /** Verifies signed access tokens and checks current account state before exposing trusted identity. */

    @Bean
    public JwtDecoder jwtDecoder(AdrovisSecurityProperties properties) {
        NimbusJwtDecoder local = signatureDecoder(properties);
        AccountStatusClient status = new AccountStatusClient(properties);
        return token -> {
            var jwt = local.decode(token);
            String verifiedEmail = status.verify(jwt);
            // This local metadata is overwritten from the trusted account response, never trusted from JWT input.
            java.util.Map<String, Object> claims = new java.util.LinkedHashMap<>(jwt.getClaims());
            claims.put("account_email", verifiedEmail);
            return new org.springframework.security.oauth2.jwt.Jwt(jwt.getTokenValue(), jwt.getIssuedAt(),
                    jwt.getExpiresAt(), jwt.getHeaders(), claims);
        };
    }

    static NimbusJwtDecoder signatureDecoder(AdrovisSecurityProperties properties) {
        NimbusJwtDecoder local;
        try (InputStream input = properties.publicKeyLocation().getInputStream()) {
            var publicKey = RsaKeyConverters.x509().convert(input);
            if (publicKey == null || publicKey.getModulus().bitLength() < 2048) {
                throw new IllegalStateException("A trusted RSA public key of at least 2048 bits is required");
            }
            local = NimbusJwtDecoder.withPublicKey(publicKey).signatureAlgorithm(SignatureAlgorithm.RS256).build();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load the Adrovis public key; check its configured resource", exception);
        }
        local.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(30)), new JwtIssuerValidator(properties.issuer()),
                new AccessTokenValidator(properties.audience())));
        return local;
    }
}

```

### .\src\main\java\com\adrovis\influencer\security\SecurityConfig.java
```java
package com.adrovis.influencer.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    // ADDED: logger used by unauthorized()
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, @Qualifier("coreCorsSource") CorsConfigurationSource coreCorsSource)
            throws Exception {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return http
                .cors(cors -> cors.configurationSource(coreCorsSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) -> unauthorized(response, error))
                        .accessDeniedHandler((request, response, error) -> error(response, 403, "FORBIDDEN")))
                .oauth2ResourceServer(resource -> resource
                        .withObjectPostProcessor(new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                            /** Returns sanitized 503 for identity-service outages instead of leaking an authentication exception. */
                            @Override
                            public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
                                filter.setAuthenticationFailureHandler(
                                        (request, response, error) -> unauthorized(response, error));
                                return filter;
                            }
                        })
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((request, response, error) -> unauthorized(response, error))
                        .accessDeniedHandler((request, response, error) -> error(response, 403, "FORBIDDEN")))
                .authorizeHttpRequests(routes -> routes
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/api/v1/health",
                                "/api/v1/campaigns", "/api/v1/campaigns/{id}",
                                "/api/v1/campaigns/{id}/apply").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/inquiries",
                                "/api/v1/analytics/track", "/api/v1/analytics/heartbeat",
                                "/api/v1/analytics/conversion", "/api/v1/analytics/lead",
                                "/api/v1/analytics/event", "/api/v1/analytics/identify",
                                "/api/v1/campaigns/{id}/apply-access").permitAll()
                        .requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs", "/v3/api-docs/**").permitAll()
                        // Razorpay cannot send user Bearer tokens; its controller requires a verified HMAC signature.
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/razorpay").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/create-order",
                                "/api/v1/payments/verify", "/api/v1/newsletter/subscribe").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/account/me").authenticated()
                        .requestMatchers("/api/v1/collector/**", "/api/v1/inquiries/**", "/api/v1/inquiries",
                                "/api/v1/newsletter/subscribers", "/api/v1/analytics/**",
                                "/api/v1/admin/**", "/api/v1/agencies", "/api/v1/agencies/**").hasRole("ADMIN")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/portal/publisher-profiles"
                        ).hasAnyRole("BRAND", "AGENCY", "ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/portal/publisher-profiles/me"
                        ).hasAnyRole("BRAND", "AGENCY", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/portal/campaigns"
                        ).hasAnyRole("BRAND", "AGENCY", "ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/portal/campaigns",
                                "/api/v1/portal/campaigns/{id}"
                        ).hasAnyRole("BRAND", "AGENCY", "ADMIN")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/portal/campaigns/{campaignId}/application-form/versions"
                        ).hasAnyRole("BRAND", "AGENCY", "ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/portal/campaigns/{campaignId}/application-form"
                        ).hasAnyRole("BRAND", "AGENCY", "ADMIN")
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/v1/portal/campaigns/{campaignId}/application-settings"
                        ).hasAnyRole("BRAND", "AGENCY", "ADMIN")
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/v1/portal/campaigns/{campaignId}/draft",
                                "/api/v1/portal/campaigns/{campaignId}/details"
                        ).hasAnyRole("BRAND", "AGENCY", "ADMIN")

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/v1/portal/campaigns/{campaignId}"
                        ).hasAnyRole("BRAND", "AGENCY", "ADMIN")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/portal/campaigns/{campaignId}/publish",
                                "/api/v1/portal/campaigns/{campaignId}/pause",
                                "/api/v1/portal/campaigns/{campaignId}/resume",
                                "/api/v1/portal/campaigns/{campaignId}/close"
                        ).hasAnyRole("BRAND", "AGENCY", "ADMIN")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/campaigns/managed",
                                "/api/v1/campaigns/managed/{campaignId}"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/campaigns/managed/{campaignId}/application-form"
                        ).hasRole("CREATOR")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/campaigns/managed/{campaignId}/applications"
                        ).hasRole("CREATOR")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/users/me/campaign-applications",
                                "/api/v1/users/me/campaign-applications/{applicationId}"
                        ).hasRole("CREATOR")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/portal/campaigns/{campaignId}/applications",
                                "/api/v1/portal/campaigns/{campaignId}/applications/{applicationId}"
                        ).hasAnyRole("BRAND", "AGENCY", "ADMIN")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/portal/campaigns/{campaignId}/applications/{applicationId}/status"
                        ).hasAnyRole("BRAND", "AGENCY", "ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/users/me/campaign-applications/{applicationId}/updates"
                        ).hasRole("CREATOR")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/portal/dashboard/overview",
                                "/api/v1/portal/dashboard/campaigns",
                                "/api/v1/portal/dashboard/campaigns/{campaignId}/applications",
                                "/api/v1/portal/dashboard/campaigns/{campaignId}/applications/export"
                        ).hasAnyRole("BRAND", "AGENCY", "ADMIN")
                        .anyRequest().denyAll())
                .build();
    }

    /** Separates invalid credentials from an unavailable trusted account check. */
    private static void unauthorized(HttpServletResponse response, Exception exception) throws IOException {
        log.warn("Auth rejected: {} - {}", exception.getClass().getSimpleName(), exception.getMessage());
        if (exception instanceof AuthenticationServiceException) {
            error(response, 503, "AUTHENTICATION_UNAVAILABLE");
        } else {
            response.setHeader("WWW-Authenticate", "Bearer");
            error(response, 401, "UNAUTHORIZED");
        }
    }

    /** Writes a minimal security error without token, exception or infrastructure details. */
    private static void error(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"errorCode\":\"" + code + "\"}");
    }
}
```

### .\src\main\java\com\adrovis\influencer\service\AgencyService.java
```java
package com.adrovis.influencer.service;

import org.springframework.security.access.prepost.PreAuthorize;
import com.adrovis.influencer.dto.AgencyResponse;
import com.adrovis.influencer.entity.AgencyEntity;
import com.adrovis.influencer.repository.AgencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AgencyService {

    private final AgencyRepository agencyRepository;
    /** Preserves agency retrieval/mapping while enforcing the current Admin-only service boundary. */

    @Transactional(readOnly = true)
    public List<AgencyResponse> getAllAgencies() {
        return agencyRepository.findAll().stream()
                .map(agency -> AgencyResponse.builder()
                        .id(agency.getId())
                        .agencyName(agency.getAgencyName())
                        .contactPhone(agency.getContactPhone())
                        .contactEmail(agency.getContactEmail())
                        .instagramHandle(agency.getInstagramHandle())
                        .notes(agency.getNotes())
                        .activeCampaignsCount(agency.getCampaigns() != null ? agency.getCampaigns().size() : 0)
                        .createdAt(agency.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }
}

```

### .\src\main\java\com\adrovis\influencer\service\CampaignService.java
```java
package com.adrovis.influencer.service;

import com.adrovis.influencer.dto.CampaignFilterRequest;
import com.adrovis.influencer.dto.PublicCampaignResponse;
import com.adrovis.influencer.entity.CampaignEntity;
import com.adrovis.influencer.entity.CampaignImageEntity;
import com.adrovis.influencer.exception.ResourceNotFoundException;
import com.adrovis.influencer.repository.CampaignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignService {

    private final CampaignRepository campaignRepository;

    @Value("${app.backend-base-url:https://adrovis-influencer-backend.onrender.com}")
    private String backendBaseUrl;
    /** Preserves campaign search filters, paging and public response mapping. */

    @Transactional(readOnly = true)
    public Page<PublicCampaignResponse> getFilteredCampaigns(CampaignFilterRequest filter) {
        int page = Math.max(0, filter.getPage() != null ? filter.getPage() : 0);
        int size = Math.max(1, Math.min(100, filter.getSize() != null ? filter.getSize() : 12));
        Pageable pageable = PageRequest.of(page, size);

        Page<CampaignEntity> entities = campaignRepository.findFilteredCampaigns(
                filter.getCampaignType(),
                filter.getMaxFollowers(),
                filter.getBrand(),
                filter.getCategory(),
                filter.getLocation(),
                filter.getMinPayout(),
                pageable
        );
        return entities.map(this::mapToPublicResponse);
    }
    /** Preserves active campaign lookup and view tracking for public discovery. */

    @Transactional
    public PublicCampaignResponse getCampaignById(UUID id) {
        CampaignEntity campaign = campaignRepository.findActiveByIdWithImages(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found with ID: " + id));

        campaignRepository.incrementViewsCount(id);
        return mapToPublicResponse(campaign);
    }
    /** Rejects missing or invalid external links before any guest allowance is consumed. */

    @Transactional(readOnly = true)
    public String getApplicationDestination(UUID id) {
        CampaignEntity campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found with ID: " + id));
        String destination = campaign.getApplicationFormUrl();
        try {
            java.net.URI uri = java.net.URI.create(destination);
            if (!Set.of("https", "http").contains(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Invalid application URL");
            }
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResourceNotFoundException("Campaign application link is unavailable");
        }
        return destination;
    }
    /** Records only allowed application clicks within the primary transaction. */

    @Transactional
    public String trackAndGetApplicationUrl(UUID id) {
        String destination = getApplicationDestination(id);
        campaignRepository.incrementClicksCount(id);
        log.info("Application click recorded for campaign ID: {}", id);
        return destination;
    }
    /** Preserves the public campaign DTO and proxy link rather than returning persistence entities. */

    @SuppressWarnings("unchecked")
    private PublicCampaignResponse mapToPublicResponse(CampaignEntity entity) {
        Map<String, Object> data = entity.getCanonicalData();
        if (data == null) {
            data = Collections.emptyMap();
        }

        // 1. Resolve Images: strictly deduplicated, max 4 images
        List<String> imageUrls = entity.getImages().stream()
                .map(CampaignImageEntity::getPublicUrl)
                .filter(url -> url != null && !url.isBlank())
                .distinct()
                .limit(4)
                .collect(Collectors.toList());

        if (imageUrls.isEmpty() && data.containsKey("images")) {
            Object imgObj = data.get("images");
            if (imgObj instanceof List<?>) {
                imageUrls = ((List<?>) imgObj).stream()
                        .map(Object::toString)
                        .filter(url -> url != null && !url.isBlank())
                        .distinct()
                        .limit(4)
                        .collect(Collectors.toList());
            }
        }

        // 2. Resolve nested campaign_details (fixes deliverables, categories, adRights, productName)
        Map<String, Object> campaignDetails = (Map<String, Object>) data.getOrDefault("campaign_details", Collections.emptyMap());

        List<String> deliverables = (List<String>) data.get("deliverables");
        if (deliverables == null || deliverables.isEmpty()) {
            deliverables = (List<String>) campaignDetails.getOrDefault("deliverables", Collections.emptyList());
        }

        List<String> categories = (List<String>) data.get("categories");
        if (categories == null || categories.isEmpty()) {
            categories = (List<String>) campaignDetails.getOrDefault("creator_categories", Collections.emptyList());
        }

        String adRights = (String) data.get("ad_rights");
        if (adRights == null || adRights.isBlank()) {
            adRights = (String) campaignDetails.get("ad_rights");
        }

        String productName = (String) data.get("product_name");
        if (productName == null || productName.isBlank()) {
            productName = (String) campaignDetails.get("product_details");
        }

        // 3. Resolve Compensation & Eligibility
        Map<String, Object> comp = (Map<String, Object>) data.getOrDefault("compensation", Collections.emptyMap());
        Map<String, Object> eligibility = (Map<String, Object>) data.getOrDefault("eligibility", Collections.emptyMap());
        Map<String, Object> schedule = (Map<String, Object>) data.getOrDefault("schedule", Collections.emptyMap());
        List<String> reqs = (List<String>) data.getOrDefault("important_requirements", Collections.emptyList());
        List<String> refs = (List<String>) data.getOrDefault("reference_links", Collections.emptyList());

        // 4. Dynamic Proxy URL Resolution (Stripping local host and trailing slashes)
        String baseUrl = backendBaseUrl != null ? backendBaseUrl.replaceAll("/+$", "") : "https://adrovis-influencer-backend.onrender.com";
        String proxyUrl = baseUrl + "/api/v1/campaigns/" + entity.getId() + "/apply";

        return PublicCampaignResponse.builder()
                .id(entity.getId())
                .brandName(entity.getBrandName())
                .brandInstagramUrl(entity.getBrandInstagramUrl())
                .campaignTitle(entity.getCampaignTitle())
                .campaignType(entity.getCampaignType())
                .applyProxyUrl(proxyUrl)
                .images(imageUrls)
                .compensation(comp)
                .deliverables(deliverables)
                .adRights(adRights)
                .categories(categories)
                .productName(productName)
                .eligibility(eligibility)
                .schedule(schedule)
                .importantRequirements(reqs)
                .referenceLinks(refs)
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .viewsCount(entity.getViewsCount())
                .clicksCount(entity.getClicksCount())
                .build();
    }
}

```

### .\src\main\resources\application-dev.yml
```yml
spring:
  datasource:
    # 1. Stockholm IPv4 Connection Pooler URL
    url: jdbc:postgresql://aws-0-eu-north-1.pooler.supabase.com:5432/postgres?sslmode=require

    # 2. Pooler username format: postgres.[project-ref]
    username: postgres.dhfstnonquoyfgnpswsw

    # 3. Your actual Supabase database password
    password: ${SUPABASE_DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true

# ==========================================
# 2. NEW CREATOR COLLECTOR DATABASE (SUPABASE)
# ==========================================
collector:
  datasource:
    # Replace with your Collector Supabase project pooler URL (dxclgwqkxmmxgeyovagd)
    url: jdbc:postgresql://aws-0-eu-west-1.pooler.supabase.com:5432/postgres?sslmode=require
    username: postgres.dxclgwqkxmmxgeyovagd
    password: ${COLLECTOR_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver

logging:
  level:
    com.adrovis.influencer: DEBUG
    org.hibernate.SQL: DEBUG
    org.springframework.security: DEBUG   # ADDED

razorpay:
  key-id: ${RAZORPAY_KEY_ID:rzp_test_your_key_id}
  key-secret: ${RAZORPAY_KEY_SECRET}
  webhook-secret: ${RAZORPAY_WEBHOOK_SECRET}

app:
  payment:
    currency: INR

adrovis:
  frontend-origin: "http://localhost:5173,http://localhost:3000,https://brandcollabz.in"
```

### .\src\main\resources\application-prod.yml
```yml
spring:
  config:
    activate:
      on-profile: prod

  datasource:
    # Port 6543 (Transaction Mode) + prepareThreshold=0 completely removes the 15-connection limit
    url: jdbc:postgresql://aws-0-eu-north-1.pooler.supabase.com:6543/postgres?sslmode=require&prepareThreshold=0
    username: postgres.dhfstnonquoyfgnpswsw
    password: ${SUPABASE_DB_PASSWORD}
    hikari:
      maximum-pool-size: 2
      minimum-idle: 1

  jpa:
    hibernate:
      ddl-auto: none
    show-sql: false

collector:
  datasource:
    url: ${COLLECTOR_DB_URL:jdbc:postgresql://aws-0-eu-west-1.pooler.supabase.com:6543/postgres?sslmode=require&prepareThreshold=0}
    username: ${COLLECTOR_DB_USER:postgres.dxclgwqkxmmxgeyovagd}
    password: ${COLLECTOR_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 2
      minimum-idle: 1

razorpay:
  key-id: ${RAZORPAY_KEY_ID:rzp_test_your_key_id}
  key-secret: ${RAZORPAY_KEY_SECRET}
  webhook-secret: ${RAZORPAY_WEBHOOK_SECRET}

app:
  payment:
    currency: INR

adrovis:
  frontend-origin: "https://brandcollabz.in"
  auth:
    public-key-location: ${ADROVIS_AUTH_PUBLIC_KEY_LOCATION:file:/etc/secrets/adrovis-public.pem}
```

### .\src\main\resources\application.yml
```yml
server:
  port: ${PORT:8080}
  servlet:
    context-path: /

spring:
  application:
    name: influencer-core-api
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}

  datasource:
    driver-class-name: org.postgresql.Driver
    hikari:
      pool-name: BrandCollabzPrimaryPool
      maximum-pool-size: 2
      minimum-idle: 1
      idle-timeout: 20000
      max-lifetime: 300000
      connection-timeout: 15000
      leak-detection-threshold: 20000

  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: none
    show-sql: false

# Collector Database (Creator Collector Supabase Project)
collector:
  datasource:
    url: jdbc:postgresql://aws-0-eu-west-1.pooler.supabase.com:5432/postgres?sslmode=require
    username: postgres.dxclgwqkxmmxgeyovagd
    password: ${COLLECTOR_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      pool-name: BrandCollabzCollectorPool
      maximum-pool-size: 2
      minimum-idle: 1
      idle-timeout: 20000
      max-lifetime: 300000
      connection-timeout: 15000

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never

app:
  # Keep existing app properties here.

  campaign-dashboard:
    export-max-rows: ${CAMPAIGN_APPLICANT_EXPORT_MAX_ROWS:500}
    export-max-bytes: ${CAMPAIGN_APPLICANT_EXPORT_MAX_BYTES:2097152}
  payment:
    currency: INR
    max-webhook-bytes: ${PAYMENT_MAX_WEBHOOK_BYTES:262144}
  guest-apply:
    # Set 3 or 5 as required. CLICKS counts every allowed Apply request, including the same campaign.
    limit: ${GUEST_APPLY_LIMIT:3}
    counting-mode: ${GUEST_APPLY_COUNTING_MODE:CLICKS}
    cookie-secure: ${GUEST_APPLY_COOKIE_SECURE:true}
    cookie-same-site: ${GUEST_APPLY_COOKIE_SAME_SITE:Lax}
  backend-base-url: ${BACKEND_BASE_URL:https://adrovis-influencer-backend.onrender.com}
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:*}

analytics:
  visitor-salt: ${ANALYTICS_VISITOR_SALT:bc_secret_salt_2026_brand_collabz}
  allowed-origins: ${CORS_ALLOWED_ORIGINS:*}

logging:
  level:
    root: INFO
    com.adrovis.influencer: INFO


razorpay:
  key-id: ${RAZORPAY_KEY_ID:rzp_live_TPDJ7j4wIp0eFJ}
  key-secret: ${RAZORPAY_KEY_SECRET:rVcBoOnGloPpisD0mqg2ezLC}
  webhook-secret: ${RAZORPAY_WEBHOOK_SECRET:Jr.gykGbU48_EUZ}


# Separate authentication service: no private signing key or auth database access is needed here.
adrovis:
  auth:
    issuer: ${ADROVIS_AUTH_ISSUER:https://brandcollabz-security.onrender.com}
    audience: ${ADROVIS_AUTH_AUDIENCE:adrovis-platform}
    public-key-location: ${ADROVIS_AUTH_PUBLIC_KEY_LOCATION:file:.secrets/adrovis-public.pem}
    status-url: ${ADROVIS_AUTH_STATUS_URL:https://auth.brandcollabz.in/api/v1/auth/me}
    status-timeout: ${ADROVIS_AUTH_STATUS_TIMEOUT:10s}
  frontend-origin: ${ADROVIS_FRONTEND_ORIGIN:http://localhost:5173}

# Guest allowance is enforced by the core database, not by frontend storage.
# Local HTTP testing needs GUEST_APPLY_COOKIE_SECURE=false; keep true in production.
# With cross-site frontend/API hosting, None requires Secure and browser third-party cookie support.
```

### .\src\main\resources\db\manual\001_guest_campaign_allowance.sql
```sql
-- Run once on the core backend PRIMARY database before deploying the guest allowance.
-- This project has no migration runner; this file is not applied automatically.
BEGIN;
CREATE TABLE IF NOT EXISTS public.guest_apply_sessions (
    guest_hash varchar(64) PRIMARY KEY CHECK (guest_hash ~ '^[0-9a-f]{64}$'),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS public.guest_campaign_access (
    guest_hash varchar(64) NOT NULL REFERENCES public.guest_apply_sessions(guest_hash) ON DELETE CASCADE,
    campaign_id uuid NOT NULL REFERENCES public.campaigns(id) ON DELETE CASCADE,
    granted_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (guest_hash, campaign_id)
);
CREATE INDEX IF NOT EXISTS guest_campaign_access_campaign_idx ON public.guest_campaign_access(campaign_id);
ALTER TABLE public.guest_apply_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.guest_campaign_access ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.guest_apply_sessions, public.guest_campaign_access FROM PUBLIC;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON public.guest_apply_sessions, public.guest_campaign_access FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON public.guest_apply_sessions, public.guest_campaign_access FROM authenticated;
    END IF;
END $$;
COMMIT;
-- Run as the backend table owner. If the runtime DB role is separate, grant it backend-only
-- access with an explicit RLS policy; never grant browser roles access to these tables.

```

### .\src\main\resources\db\manual\002_payment_account_ownership.sql
```sql
-- Apply to the CORE primary database before deploying payment ownership enforcement.
-- Existing payment tables are owned by the payment module; no existing records are deleted or auto-linked.
BEGIN;
ALTER TABLE public.payment_transactions ADD COLUMN IF NOT EXISTS account_id uuid;
CREATE INDEX IF NOT EXISTS idx_payment_account_id ON public.payment_transactions(account_id);
-- NULL historical owners remain admin-readable; client verification requires a reviewed UUID link.
-- Required by the webhook's atomic ON CONFLICT retry claim. Existing duplicates need reviewed remediation.
CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_webhook_event_id ON public.payment_webhook_events(event_id);
-- Browser credentials must not bypass the API's financial authorization through direct table access.
ALTER TABLE public.payment_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.payment_webhook_events ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.payment_transactions, public.payment_webhook_events FROM PUBLIC;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON public.payment_transactions, public.payment_webhook_events FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON public.payment_transactions, public.payment_webhook_events FROM authenticated;
    END IF;
END $$;
-- Run as the table owner. A distinct backend role requires reviewed grants and a backend-only RLS policy.
COMMIT;

```

### .\src\main\resources\db\manual\003_guest_apply_click_counter.sql
```sql
-- Additive upgrade: never edit/reapply the original guest-table script to change existing databases.
BEGIN;
ALTER TABLE public.guest_apply_sessions
    ADD COLUMN IF NOT EXISTS apply_count integer NOT NULL DEFAULT 0 CHECK (apply_count >= 0);
-- Old history recorded distinct grants only, so conservatively seed at least that count without resetting newer clicks.
UPDATE public.guest_apply_sessions AS session
SET apply_count = GREATEST(session.apply_count,
    (SELECT count(*)::integer FROM public.guest_campaign_access AS access WHERE access.guest_hash = session.guest_hash));
COMMIT;

```

### .\src\test\java\com\adrovis\influencer\campaign\access\CampaignApplyControllerTests.java
```java
package com.adrovis.influencer.campaign.access;

import com.adrovis.influencer.config.CorsConfig;
import com.adrovis.influencer.controller.CampaignController;
import com.adrovis.influencer.security.SecurityConfig;
import com.adrovis.influencer.service.CampaignService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CampaignController.class, properties = {
        "spring.profiles.active=test", "adrovis.frontend-origin=https://app.example.com",
        "app.guest-apply.cookie-secure=false"})
@Import({SecurityConfig.class, CorsConfig.class, GuestApplyCookie.class, GuestApplyExceptionHandler.class})
class CampaignApplyControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private CampaignService campaigns;

    @MockBean
    private CampaignApplyAccessService access;

    @MockBean
    private JwtDecoder decoder;

    private final UUID id = UUID.randomUUID();
    private final String destination = "https://forms.example.com/apply";

    @Test
    void existingApplyRouteRedirectsAndIssuesGuestIdentity() throws Exception {
        when(access.grant(eq(id), any(), eq(false))).thenReturn(destination);
        mvc.perform(get("/api/v1/campaigns/{id}/apply", id))
                .andExpect(status().isFound()).andExpect(header().string("Location", destination))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(cookie().exists(GuestApplyCookie.NAME));
    }

    @Test
    void existingGuestIdentityIsHashedAndReused() throws Exception {
        String raw = "a".repeat(43);
        when(access.grant(eq(id), any(), eq(false))).thenReturn(destination);
        mvc.perform(get("/api/v1/campaigns/{id}/apply", id).cookie(new Cookie(GuestApplyCookie.NAME, raw)))
                .andExpect(status().isFound()).andExpect(cookie().doesNotExist(GuestApplyCookie.NAME));
        GuestApplyCookie cookies = new GuestApplyCookie(false, "Lax", new org.springframework.mock.env.MockEnvironment());
        verify(access).grant(id, cookies.hash(raw), false);
    }

    @Test
    void bothRoutesRejectExhaustedGuestsWithoutReturningTheExternalLink() throws Exception {
        when(access.grant(eq(id), any(), eq(false))).thenThrow(new SignupRequiredException());
        mvc.perform(get("/api/v1/campaigns/{id}/apply", id))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("SIGNUP_REQUIRED"))
                .andExpect(header().doesNotExist("Location"));
        mvc.perform(post("/api/v1/campaigns/{id}/apply-access", id))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("SIGNUP_REQUIRED"))
                .andExpect(jsonPath("$.applicationUrl").doesNotExist());
    }

    @Test
    void jsonRouteReturnsTheLinkAndSupportsCredentialedFrontendRequests() throws Exception {
        when(access.grant(eq(id), any(), eq(false))).thenReturn(destination);
        mvc.perform(post("/api/v1/campaigns/{id}/apply-access", id).header("Origin", "https://app.example.com"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.applicationUrl").value(destination))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void validatedAccountsUseTheirTokenRatherThanAFrontendFlag() throws Exception {
        Jwt token = Jwt.withTokenValue("valid").header("alg", "RS256").subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
                .claim("roles", List.of("CREATOR")).build();
        when(decoder.decode("valid")).thenReturn(token);
        when(access.grant(eq(id), isNull(), eq(true))).thenReturn(destination);
        mvc.perform(post("/api/v1/campaigns/{id}/apply-access", id).header("Authorization", "Bearer valid"))
                .andExpect(status().isOk()).andExpect(cookie().doesNotExist(GuestApplyCookie.NAME));
        verify(access).grant(id, null, true);
    }

    @Test
    void invalidBearerDoesNotFallBackToGuestAccess() throws Exception {
        when(decoder.decode("invalid")).thenThrow(new BadJwtException("Invalid"));
        mvc.perform(get("/api/v1/campaigns/{id}/apply", id).header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(access);
    }
    @Test
    void databaseFailuresDoNotExposeQueriesOrDestinations() throws Exception {
        when(access.grant(eq(id), any(), eq(false))).thenThrow(
                new org.springframework.dao.DataAccessResourceFailureException("private database details"));
        mvc.perform(post("/api/v1/campaigns/{id}/apply-access", id))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("APPLICATION_ACCESS_UNAVAILABLE"))
                .andExpect(jsonPath("$.applicationUrl").doesNotExist());
    }
}

```

### .\src\test\java\com\adrovis\influencer\campaign\access\GuestApplyCookieTests.java
```java
package com.adrovis.influencer.campaign.access;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuestApplyCookieTests {

    @Test
    void generatesRandomIdentitiesAndStoresOnlyTheirHash() {
        GuestApplyCookie cookies = new GuestApplyCookie(true, "Lax", new MockEnvironment());
        String first = cookies.identity(null);
        assertThat(first).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(cookies.identity(null));
        assertThat(cookies.identity(first)).isEqualTo(first);
        assertThat(cookies.identity("malformed")).isNotEqualTo("malformed");
        assertThat(cookies.hash(first)).matches("[0-9a-f]{64}").isNotEqualTo(first);
    }

    @Test
    void writesAScopedSecureHttpOnlyCookie() {
        GuestApplyCookie cookies = new GuestApplyCookie(true, "None", new MockEnvironment());
        MockHttpServletResponse response = new MockHttpServletResponse();
        cookies.write(response, cookies.identity(null));
        assertThat(response.getHeader("Set-Cookie")).contains("HttpOnly", "Secure", "SameSite=None",
                "Path=/api/v1/campaigns", "Max-Age=2592000");
    }

    @Test
    void rejectsInsecureProductionAndCrossSiteCookies() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThatThrownBy(() -> new GuestApplyCookie(false, "Lax", prod))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new GuestApplyCookie(false, "None", new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class);
    }
}

```

### .\src\test\java\com\adrovis\influencer\campaign\access\GuestApplyRepositoryTests.java
```java
package com.adrovis.influencer.campaign.access;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "GUEST_APPLY_TEST_DATABASE_URL", matches = ".+")
class GuestApplyRepositoryTests {

    private GuestApplyRepository repository;
    private JdbcTemplate jdbc;
    private final List<UUID> campaigns = new ArrayList<>();
    private final String guest = "a".repeat(64);

    @BeforeEach
    void setup() throws Exception {
        String url = System.getenv("GUEST_APPLY_TEST_DATABASE_URL");
        if (!url.startsWith("jdbc:postgresql://127.0.0.1:") || !url.endsWith("/guest_apply_test")) {
            throw new IllegalStateException("Use only the disposable local guest_apply_test database");
        }
        DriverManagerDataSource source = new DriverManagerDataSource(url, "quota_test",
                System.getenv().getOrDefault("GUEST_APPLY_TEST_DATABASE_PASSWORD", ""));
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE IF NOT EXISTS public.campaigns (id uuid PRIMARY KEY)");
        String sql;
        try (var stream = new ClassPathResource("db/manual/001_guest_campaign_allowance.sql").getInputStream()) {
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        jdbc.execute(sql);
        try (var stream = new ClassPathResource("db/manual/003_guest_apply_click_counter.sql").getInputStream()) {
            jdbc.execute(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
        jdbc.execute("TRUNCATE public.guest_campaign_access, public.guest_apply_sessions, public.campaigns CASCADE");
        for (int i = 0; i < 12; i++) {
            UUID id = UUID.randomUUID();
            campaigns.add(id);
            jdbc.update("INSERT INTO public.campaigns VALUES (?)", id);
        }
        repository = new GuestApplyRepository(source, new org.springframework.jdbc.datasource.DataSourceTransactionManager(source));
    }

    @Test
    void allowsThreeDistinctCampaignsRejectsTheFourthAndPreservesRetries() {
        for (int i = 0; i < 3; i++) {
            assertThat(allow(guest, campaigns.get(i), 3)).isTrue();
        }
        assertThat(allow(guest, campaigns.get(3), 3)).isFalse();
        assertThat(allow(guest, campaigns.get(0), 3)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.guest_campaign_access", Integer.class)).isEqualTo(3);
    }

    @Test
    void simultaneousDifferentCampaignsCannotExceedTheAllowance() throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> tasks = campaigns.stream()
                    .<Callable<Boolean>>map(id -> () -> allow(guest, id, 3)).toList();
            int allowed = 0;
            for (var future : executor.invokeAll(tasks)) {
                if (future.get()) {
                    allowed++;
                }
            }
            assertThat(allowed).isEqualTo(3);
        } finally {
            // Java 17 executors are not AutoCloseable; always release worker threads.
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.guest_campaign_access", Integer.class)).isEqualTo(3);
    }

    @Test
    void simultaneousRetriesConsumeOnlyOneSlot() throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                tasks.add(() -> allow(guest, campaigns.get(0), 3));
            }
            for (var future : executor.invokeAll(tasks)) {
                assertThat(future.get()).isTrue();
            }
        } finally {
            // Java 17 executors are not AutoCloseable; always release worker threads.
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.guest_campaign_access", Integer.class)).isEqualTo(1);
    }

    @Test
    void eachGuestGetsTheirOwnAllowance() {
        for (int i = 0; i < 3; i++) {
            assertThat(allow(guest, campaigns.get(i), 3)).isTrue();
        }
        assertThat(allow("b".repeat(64), campaigns.get(3), 3)).isTrue();
    }

    @Test
    void failedGrantsRollBackTheSessionAndUsage() {
        assertThatThrownBy(() -> allow(guest, UUID.randomUUID(), 3))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.guest_apply_sessions", Integer.class)).isZero();
    }
    /** Existing distinct-campaign assertions remain to verify the selectable compatibility mode. */
    private boolean allow(String hash, UUID id, int limit) {
        return repository.allow(hash, id, limit, GuestApplyCountingMode.DISTINCT_CAMPAIGNS);
    }

    @Test
    void clickModeCountsRepeatedCampaignsAndHonorsFiveAsAConfiguredLimit() {
        for (int i = 0; i < 5; i++) {
            assertThat(repository.allow(guest, campaigns.get(0), 5, GuestApplyCountingMode.CLICKS)).isTrue();
        }
        assertThat(repository.allow(guest, campaigns.get(0), 5, GuestApplyCountingMode.CLICKS)).isFalse();
        assertThat(jdbc.queryForObject("SELECT apply_count FROM public.guest_apply_sessions", Integer.class)).isEqualTo(5);
    }

    @Test
    void simultaneousClickRequestsCannotExceedThreeEvenOnTheSameCampaign() throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                tasks.add(() -> repository.allow(guest, campaigns.get(0), 3, GuestApplyCountingMode.CLICKS));
            }
            int granted = 0;
            for (var result : executor.invokeAll(tasks)) {
                if (result.get()) {
                    granted++;
                }
            }
            assertThat(granted).isEqualTo(3);
        } finally {
            // Java 17 executors are not AutoCloseable; always release worker threads.
            executor.shutdownNow();
        }
    }
}

```

### .\src\test\java\com\adrovis\influencer\campaign\access\GuestApplyServiceTests.java
```java
package com.adrovis.influencer.campaign.access;

import com.adrovis.influencer.exception.ResourceNotFoundException;
import com.adrovis.influencer.service.CampaignService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GuestApplyServiceTests {

    private final CampaignService campaigns = mock(CampaignService.class);
    private final GuestApplyRepository guests = mock(GuestApplyRepository.class);
    private final CampaignApplyAccessService service = new CampaignApplyAccessService(campaigns, guests, 3, GuestApplyCountingMode.CLICKS);
    private final UUID campaign = UUID.randomUUID();

    @Test
    void allowsAGuestAndRecordsOnlyAcceptedClicks() {
        when(guests.allow("hash", campaign, 3, GuestApplyCountingMode.CLICKS)).thenReturn(true);
        when(campaigns.trackAndGetApplicationUrl(campaign)).thenReturn("https://forms.example.com/apply");
        assertThat(service.grant(campaign, "hash", false)).isEqualTo("https://forms.example.com/apply");
        verify(guests).allow("hash", campaign, 3, GuestApplyCountingMode.CLICKS);
    }

    @Test
    void blocksTheFourthGuestCampaignBeforeTrackingOrReturningADestination() {
        when(guests.allow("hash", campaign, 3, GuestApplyCountingMode.CLICKS)).thenReturn(false);
        assertThatThrownBy(() -> service.grant(campaign, "hash", false))
                .isInstanceOf(SignupRequiredException.class);
        verify(campaigns, never()).trackAndGetApplicationUrl(any());
    }

    @Test
    void validatedAccountsBypassGuestUsage() {
        when(campaigns.trackAndGetApplicationUrl(campaign)).thenReturn("https://forms.example.com/apply");
        assertThat(service.grant(campaign, null, true)).isEqualTo("https://forms.example.com/apply");
        verifyNoInteractions(guests);
    }

    @Test
    void missingCampaignsDoNotConsumeTheAllowance() {
        when(campaigns.getApplicationDestination(campaign)).thenThrow(new ResourceNotFoundException("Not found"));
        assertThatThrownBy(() -> service.grant(campaign, "hash", false))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(guests, never()).allow(anyString(), any(), anyInt(), any());
    }
}

```

### .\src\test\java\com\adrovis\influencer\payment\PaymentMethodAuthorizationTests.java
```java
package com.adrovis.influencer.payment;

import com.adrovis.influencer.payment.client.RazorpayClientWrapper;
import com.adrovis.influencer.payment.config.RazorpayProperties;
import com.adrovis.influencer.payment.repository.PaymentTransactionRepository;
import com.adrovis.influencer.payment.service.PaymentService;
import com.adrovis.influencer.payment.service.impl.PaymentServiceImpl;
import com.adrovis.influencer.security.CurrentAccount;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(PaymentMethodAuthorizationTests.Guards.class)
class PaymentMethodAuthorizationTests {

    @Configuration
    @EnableMethodSecurity
    static class Guards {
        @Bean
        PaymentService service() {
            return new PaymentServiceImpl(mock(PaymentTransactionRepository.class), mock(RazorpayClientWrapper.class),
                    new RazorpayProperties(), mock(CurrentAccount.class));
        }
    }

    @Autowired
    private PaymentService service;

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void directNonAdminServiceCallsCannotReadPlatformFinancialStatistics() {
        authenticate("BRAND");
        assertThatThrownBy(service::getAdminStats).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getAllTransactions(null, org.springframework.data.domain.Pageable.unpaged()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void theAdminServiceGuardAllowsLegitimateReporting() {
        authenticate("ADMIN");
        assertThatCode(service::getAdminStats).doesNotThrowAnyException();
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "test-account", "not-a-real-credential", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}

```

### .\src\test\java\com\adrovis\influencer\payment\PaymentPersistenceTests.java
```java
package com.adrovis.influencer.payment;

import com.adrovis.influencer.payment.entity.PaymentTransaction;
import com.adrovis.influencer.payment.enums.PaymentStatus;
import com.adrovis.influencer.payment.repository.PaymentTransactionRepository;
import com.adrovis.influencer.payment.repository.PaymentWebhookEventRepository;
import com.adrovis.influencer.payment.config.RazorpayProperties;
import com.adrovis.influencer.payment.security.RazorpayWebhookVerifier;
import com.adrovis.influencer.payment.security.PaymentOperationException;
import com.adrovis.influencer.payment.service.impl.PaymentWebhookServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "GUEST_APPLY_TEST_DATABASE_URL", matches = ".+")
class PaymentPersistenceTests {

    private LocalContainerEntityManagerFactoryBean factory;
    private PaymentTransactionRepository payments;
    private PaymentWebhookEventRepository events;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private PaymentWebhookServiceImpl webhook;

    @BeforeAll
    void setupDatabase() throws Exception {
        String url = System.getenv("GUEST_APPLY_TEST_DATABASE_URL");
        if (!url.startsWith("jdbc:postgresql://127.0.0.1:") || !url.endsWith("/guest_apply_test")) {
            throw new IllegalStateException("Only the disposable local test database is permitted");
        }
        DriverManagerDataSource source = new DriverManagerDataSource(url, "quota_test",
                System.getenv().getOrDefault("GUEST_APPLY_TEST_DATABASE_PASSWORD", ""));
        jdbc = new JdbcTemplate(source);
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(source);
        factory.setPackagesToScan("com.adrovis.influencer.payment.entity");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
        factory.afterPropertiesSet();
        EntityManagerFactory emf = factory.getObject();
        JpaRepositoryFactory repositories = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf));
        payments = repositories.getRepository(PaymentTransactionRepository.class);
        events = repositories.getRepository(PaymentWebhookEventRepository.class);
        transactions = new TransactionTemplate(new JpaTransactionManager(emf));
        try (var stream = new ClassPathResource("db/manual/002_payment_account_ownership.sql").getInputStream()) {
            jdbc.execute(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
        RazorpayProperties properties = new RazorpayProperties();
        properties.setWebhookSecret("database-test-webhook-secret");
        webhook = new PaymentWebhookServiceImpl(events, payments, new RazorpayWebhookVerifier(properties), new ObjectMapper());
    }

    @BeforeEach
    void clearOnlyDisposablePaymentFixtures() {
        jdbc.execute("TRUNCATE public.payment_webhook_events, public.payment_transactions");
    }

    @AfterAll
    void closeDatabase() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    void accountOwnershipRoundTripsThroughTheActualJpaMappingAndLockedQuery() {
        UUID owner = UUID.randomUUID();
        transactions.executeWithoutResult(status -> payments.save(payment(owner)));
        PaymentTransaction loaded = transactions.execute(status -> payments.findLockedByRazorpayOrderId("order_test").orElseThrow());
        assertThat(loaded.getAccountId()).isEqualTo(owner);
    }

    @Test
    void concurrentIdenticalSignedBodiesAreClaimedExactlyOnce() throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                tasks.add(() -> transactions.execute(status -> events.claim(UUID.randomUUID(), "same-body-hash",
                        "payment.captured", "{}")));
            }
            int claimed = 0;
            for (var result : executor.invokeAll(tasks)) {
                claimed += result.get();
            }
            assertThat(claimed).isEqualTo(1);
        } finally {
            // Java 17 executors are not AutoCloseable; always release worker threads.
            executor.shutdownNow();
        }
        assertThat(transactions.<Long>execute(status -> events.count())).isEqualTo(1);
    }

    @Test
    void aRejectedCaptureRollsBackBothTheEventClaimAndThePaymentUpdate() throws Exception {
        transactions.executeWithoutResult(status -> payments.save(payment(UUID.randomUUID())));
        String body = WebhookSecurityTests.payload(1, "INR");
        String signature = PaymentSecurityTests.signature(body, "database-test-webhook-secret");
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> webhook.processWebhook(body, signature)))
                .isInstanceOf(PaymentOperationException.class);
        assertThat(transactions.<Long>execute(status -> events.count())).isZero();
        assertThat(transactions.<PaymentStatus>execute(status -> payments.findByRazorpayOrderId("order_test").orElseThrow().getStatus()))
                .isEqualTo(PaymentStatus.INITIATED);
    }

    @Test
    void successfulCaptureAndRetryPreserveOneEventAndOneSuccessfulPayment() throws Exception {
        transactions.executeWithoutResult(status -> payments.save(payment(UUID.randomUUID())));
        String body = WebhookSecurityTests.payload(10000, "INR");
        String signature = PaymentSecurityTests.signature(body, "database-test-webhook-secret");
        transactions.executeWithoutResult(status -> webhook.processWebhook(body, signature));
        transactions.executeWithoutResult(status -> webhook.processWebhook(body, signature));
        assertThat(transactions.<Long>execute(status -> events.count())).isEqualTo(1);
        assertThat(transactions.<PaymentStatus>execute(status -> payments.findByRazorpayOrderId("order_test").orElseThrow().getStatus()))
                .isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void browserRolesCannotReadPaymentRowsEvenWhenSelectIsGranted() {
        transactions.executeWithoutResult(status -> payments.save(payment(UUID.randomUUID())));
        jdbc.execute("""
                DO $$ BEGIN
                  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'payment_browser_probe') THEN
                    CREATE ROLE payment_browser_probe NOLOGIN;
                  END IF;
                END $$;
                GRANT USAGE ON SCHEMA public TO payment_browser_probe;
                GRANT SELECT ON public.payment_transactions TO payment_browser_probe;
                """);
        Integer visible = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Integer>) connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("SET ROLE payment_browser_probe");
                try (var result = statement.executeQuery("SELECT count(*) FROM public.payment_transactions")) {
                    result.next();
                    return result.getInt(1);
                }
            }
        });
        assertThat(visible).isZero();
    }

    private PaymentTransaction payment(UUID account) {
        return PaymentTransaction.builder().accountId(account).referenceId("TEST-" + UUID.randomUUID())
                .email("test@example.com").planId("test-plan").userRole("CREATOR")
                .amount(new BigDecimal("100.00")).currency("INR").status(PaymentStatus.INITIATED)
                .razorpayOrderId("order_test").build();
    }
}

```

### .\src\test\java\com\adrovis\influencer\payment\PaymentSecurityTests.java
```java
package com.adrovis.influencer.payment;

import com.adrovis.influencer.payment.client.RazorpayClientWrapper;
import com.adrovis.influencer.payment.config.RazorpayProperties;
import com.adrovis.influencer.payment.dto.request.CreateOrderRequest;
import com.adrovis.influencer.payment.dto.request.VerifyPaymentRequest;
import com.adrovis.influencer.payment.entity.PaymentTransaction;
import com.adrovis.influencer.payment.enums.PaymentStatus;
import com.adrovis.influencer.payment.repository.PaymentTransactionRepository;
import com.adrovis.influencer.payment.security.PaymentOperationException;
import com.adrovis.influencer.payment.service.impl.PaymentServiceImpl;
import com.adrovis.influencer.security.CurrentAccount;
import com.razorpay.Order;
import com.razorpay.RazorpayException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PaymentSecurityTests {

    private final PaymentTransactionRepository repository = mock(PaymentTransactionRepository.class);
    private final RazorpayClientWrapper gateway = mock(RazorpayClientWrapper.class);
    private final CurrentAccount accounts = mock(CurrentAccount.class);
    private final RazorpayProperties properties = new RazorpayProperties();
    private final UUID owner = UUID.randomUUID();
    private PaymentServiceImpl service;

    @BeforeEach
    void setup() {
        properties.setKeyId("rzp_test_not_real");
        properties.setKeySecret("test-only-signing-secret");
        when(accounts.require()).thenReturn(new CurrentAccount.Identity(owner, "user@example.com", Set.of("CREATOR")));
        service = new PaymentServiceImpl(repository, gateway, properties, accounts);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "CREATOR", "AGENCY", "BRAND"})
    void ordersAreOwnedByTheAuthenticatedAccountAndPreserveFrontendPrices(String role) throws Exception {
        when(accounts.require()).thenReturn(new CurrentAccount.Identity(owner, "user@example.com", Set.of(role)));
        when(gateway.createOrder(any(), anyString())).thenReturn(new Order(new org.json.JSONObject().put("id", "order_test")));
        CreateOrderRequest request = order(role);
        service.createOrder(request);
        var captor = org.mockito.ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getAccountId()).isEqualTo(owner);
        assertThat(captor.getValue().getUserRole()).isEqualTo(role);
        assertThat(captor.getValue().getEmail()).isEqualTo("user@example.com");
        verify(gateway).createOrder(eq(new BigDecimal("100.00")), anyString());
    }

    @Test
    void rejectsEmailAndRoleSpoofingBeforeCallingTheGateway() {
        CreateOrderRequest email = order("CREATOR");
        email.setEmail("victim@example.com");
        assertThatThrownBy(() -> service.createOrder(email)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.createOrder(order("ADMIN"))).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(gateway);
    }

    @Test
    void rejectsUnsafeNumericAmountsBeforeCallingTheGateway() {
        for (String amount : new String[]{"0", "-1", "0.001"}) {
            CreateOrderRequest request = order("CREATOR");
            request.setAmount(new BigDecimal(amount));
            assertThatThrownBy(() -> service.createOrder(request)).isInstanceOf(PaymentOperationException.class);
        }
        verifyNoInteractions(gateway);
    }

    @Test
    void anotherUserAndUnlinkedHistoricalOrdersCannotBeVerified() {
        PaymentTransaction payment = payment();
        payment.setAccountId(UUID.randomUUID());
        when(repository.findLockedByRazorpayOrderId("order_test")).thenReturn(Optional.of(payment));
        assertThatThrownBy(() -> service.verifyPayment(verification("pay_test")))
                .isInstanceOf(PaymentOperationException.class)
                .satisfies(error -> assertThat(((PaymentOperationException) error).status()).isEqualTo(404));
        payment.setAccountId(null);
        assertThatThrownBy(() -> service.verifyPayment(verification("pay_test")))
                .isInstanceOf(PaymentOperationException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void invalidSignatureCannotMarkAnOrderFailedOrSuccessful() throws Exception {
        PaymentTransaction payment = payment();
        when(repository.findLockedByRazorpayOrderId("order_test")).thenReturn(Optional.of(payment));
        VerifyPaymentRequest request = verification("pay_test");
        request.setRazorpaySignature("invalid-signature");
        assertThatThrownBy(() -> service.verifyPayment(request)).isInstanceOf(PaymentOperationException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.INITIATED);
        verify(repository, never()).save(any());
    }

    @Test
    void validSignatureIsAcceptedAndIdenticalRetriesDoNotRewriteTheTransaction() throws Exception {
        PaymentTransaction payment = payment();
        when(repository.findLockedByRazorpayOrderId("order_test")).thenReturn(Optional.of(payment));
        VerifyPaymentRequest request = verification("pay_test");
        request.setRazorpaySignature(signature("order_test|pay_test", properties.getKeySecret()));
        service.verifyPayment(request);
        service.verifyPayment(request);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(repository, times(1)).save(payment);
    }

    @Test
    void aSecondPaymentCannotOverwriteSuccessAndRefundsCannotBeUndone() throws Exception {
        PaymentTransaction payment = payment();
        payment.markSuccess("pay_original", "already-verified");
        when(repository.findLockedByRazorpayOrderId("order_test")).thenReturn(Optional.of(payment));
        VerifyPaymentRequest request = verification("pay_different");
        request.setRazorpaySignature(signature("order_test|pay_different", properties.getKeySecret()));
        assertThatThrownBy(() -> service.verifyPayment(request)).isInstanceOf(PaymentOperationException.class);
        assertThat(payment.getRazorpayPaymentId()).isEqualTo("pay_original");
        payment.setStatus(PaymentStatus.REFUNDED);
        assertThatThrownBy(() -> service.verifyPayment(request)).isInstanceOf(PaymentOperationException.class);
    }

    @Test
    void providerErrorsAreNotReturnedToTheCaller() throws Exception {
        when(gateway.createOrder(any(), anyString())).thenThrow(new RazorpayException("private-provider-information"));
        assertThatThrownBy(() -> service.createOrder(order("CREATOR")))
                .isInstanceOf(PaymentOperationException.class).hasMessageNotContaining("private-provider-information")
                .hasNoCause();
    }

    private CreateOrderRequest order(String role) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setEmail("user@example.com");
        request.setPlanId("existing-plan");
        request.setUserRole(role);
        request.setAmount(new BigDecimal("100.00"));
        return request;
    }

    private PaymentTransaction payment() {
        return PaymentTransaction.builder().accountId(owner).razorpayOrderId("order_test")
                .status(PaymentStatus.INITIATED).amount(new BigDecimal("100.00")).currency("INR").build();
    }

    private VerifyPaymentRequest verification(String paymentId) {
        VerifyPaymentRequest request = new VerifyPaymentRequest();
        request.setRazorpayOrderId("order_test");
        request.setRazorpayPaymentId(paymentId);
        request.setRazorpaySignature("placeholder");
        return request;
    }

    static String signature(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}

```

### .\src\test\java\com\adrovis\influencer\payment\WebhookSecurityTests.java
```java
package com.adrovis.influencer.payment;

import com.adrovis.influencer.payment.config.RazorpayProperties;
import com.adrovis.influencer.payment.entity.PaymentTransaction;
import com.adrovis.influencer.payment.enums.PaymentStatus;
import com.adrovis.influencer.payment.repository.PaymentTransactionRepository;
import com.adrovis.influencer.payment.repository.PaymentWebhookEventRepository;
import com.adrovis.influencer.payment.security.PaymentOperationException;
import com.adrovis.influencer.payment.security.RazorpayWebhookVerifier;
import com.adrovis.influencer.payment.service.impl.PaymentWebhookServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class WebhookSecurityTests {

    private static final String SECRET = "test-only-webhook-secret";
    private final RazorpayProperties properties = new RazorpayProperties();
    private final PaymentTransactionRepository payments = mock(PaymentTransactionRepository.class);
    private final PaymentWebhookEventRepository events = mock(PaymentWebhookEventRepository.class);
    private RazorpayWebhookVerifier verifier;
    private PaymentWebhookServiceImpl service;

    @BeforeEach
    void setup() {
        properties.setWebhookSecret(SECRET);
        verifier = new RazorpayWebhookVerifier(properties);
        service = new PaymentWebhookServiceImpl(events, payments, verifier, new ObjectMapper());
        when(events.claim(any(), anyString(), anyString(), anyString())).thenReturn(1);
    }

    @Test
    void onlyTheExactSignedBodyPassesAndBlankSecretsNeverBypassValidation() throws Exception {
        String signature = PaymentSecurityTests.signature(payload(10000, "INR"), SECRET);
        assertThat(verifier.verifySignature(payload(10000, "INR"), signature)).isTrue();
        assertThat(verifier.verifySignature(payload(10000, "INR") + " ", signature)).isFalse();
        assertThat(verifier.verifySignature(payload(10000, "INR"), null)).isFalse();
        properties.setWebhookSecret("");
        assertThat(verifier.verifySignature(payload(10000, "INR"), signature)).isFalse();
    }

    @Test
    void unsignedRequestsCannotClaimEventsOrModifyPayments() {
        assertThatThrownBy(() -> service.processWebhook(payload(10000, "INR"), "invalid"))
                .isInstanceOf(PaymentOperationException.class);
        verifyNoInteractions(events, payments);
    }

    @Test
    void validCapturedPaymentMustMatchStoredAmountAndCurrency() throws Exception {
        PaymentTransaction transaction = transaction();
        when(payments.findLockedByRazorpayOrderId("order_test")).thenReturn(Optional.of(transaction));
        String body = payload(10000, "INR");
        service.processWebhook(body, PaymentSecurityTests.signature(body, SECRET));
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(payments).save(transaction);
    }

    @Test
    void signedAmountOrCurrencyMismatchDoesNotCreditAnOrder() throws Exception {
        PaymentTransaction transaction = transaction();
        when(payments.findLockedByRazorpayOrderId("order_test")).thenReturn(Optional.of(transaction));
        for (String body : new String[]{payload(1, "INR"), payload(10000, "USD")}) {
            String signature = PaymentSecurityTests.signature(body, SECRET);
            assertThatThrownBy(() -> service.processWebhook(body, signature))
                    .isInstanceOf(PaymentOperationException.class);
        }
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.INITIATED);
        verify(payments, never()).save(any());
    }

    @Test
    void duplicateDeliveryDoesNotReprocessPayment() throws Exception {
        when(events.claim(any(), anyString(), anyString(), anyString())).thenReturn(0);
        String body = payload(10000, "INR");
        service.processWebhook(body, PaymentSecurityTests.signature(body, SECRET));
        verifyNoInteractions(payments);
    }

    @Test
    void unknownOrdersRemainRetryableRatherThanAcknowledgedAsProcessed() throws Exception {
        when(payments.findLockedByRazorpayOrderId("order_test")).thenReturn(Optional.empty());
        String body = payload(10000, "INR");
        String signature = PaymentSecurityTests.signature(body, SECRET);
        assertThatThrownBy(() -> service.processWebhook(body, signature))
                .isInstanceOf(PaymentOperationException.class)
                .satisfies(error -> assertThat(((PaymentOperationException) error).status()).isEqualTo(503));
    }

    @Test
    void lateCapturedEventsCannotUndoRefunds() throws Exception {
        PaymentTransaction transaction = transaction();
        transaction.setStatus(PaymentStatus.REFUNDED);
        when(payments.findLockedByRazorpayOrderId("order_test")).thenReturn(Optional.of(transaction));
        String body = payload(10000, "INR");
        service.processWebhook(body, PaymentSecurityTests.signature(body, SECRET));
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(payments, never()).save(any());
    }

    @Test
    void oversizedUnsignedBodiesAreRejectedBeforeAnyDatabaseWork() {
        assertThatThrownBy(() -> service.processWebhook("x".repeat(262145), "invalid"))
                .isInstanceOf(PaymentOperationException.class)
                .satisfies(error -> assertThat(((PaymentOperationException) error).status()).isEqualTo(413));
        verifyNoInteractions(events, payments);
    }

    @Test
    void signedMalformedJsonAndMissingEventNeverReachPersistence() throws Exception {
        for (String body : new String[]{"{", "{}", "null", "[]"}) {
            String signature = PaymentSecurityTests.signature(body, SECRET);
            assertThatThrownBy(() -> service.processWebhook(body, signature))
                    .isInstanceOf(PaymentOperationException.class)
                    .satisfies(error -> assertThat(((PaymentOperationException) error).status()).isEqualTo(400));
        }
        verifyNoInteractions(events, payments);
    }

    @Test
    void signedNumericOrOversizedIdentifiersAreRejectedBeforeOrderLookup() throws Exception {
        for (String value : new String[]{"123", "\"" + "x".repeat(101) + "\""}) {
            String body = payload(10000, "INR").replace("\"order_test\"", value);
            String signature = PaymentSecurityTests.signature(body, SECRET);
            assertThatThrownBy(() -> service.processWebhook(body, signature))
                    .isInstanceOf(PaymentOperationException.class)
                    .satisfies(error -> assertThat(((PaymentOperationException) error).status()).isEqualTo(400));
        }
        verifyNoInteractions(payments);
    }

    private PaymentTransaction transaction() {
        return PaymentTransaction.builder().razorpayOrderId("order_test").status(PaymentStatus.INITIATED)
                .amount(new BigDecimal("100.00")).currency("INR").build();
    }

    static String payload(long paise, String currency) {
        return "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{"
                + "\"order_id\":\"order_test\",\"id\":\"pay_test\",\"amount\":" + paise
                + ",\"currency\":\"" + currency + "\"}}}}";
    }
}

```

### .\src\test\java\com\adrovis\influencer\security\AccountStatusClientTests.java
```java
package com.adrovis.influencer.security;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AccountStatusClientTests {

    private MockRestServiceServer server;
    private AccountStatusClient client;
    private Jwt jwt;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AccountStatusClient(TokenFixtures.properties(), builder);
        jwt = JwtValidationConfig.signatureDecoder(TokenFixtures.properties())
                .decode(TokenFixtures.sign(TokenFixtures.claims()));
    }

    @Test
    void forwardsOnlyTheValidatedBearerToTheConfiguredEndpoint() {
        server.expect(requestTo(TokenFixtures.ISSUER + "/api/v1/auth/me"))
                .andExpect(header("Authorization", "Bearer " + jwt.getTokenValue()))
                .andRespond(withSuccess(body(TokenFixtures.ACCOUNT, "creator"), MediaType.APPLICATION_JSON));
        assertThatCode(() -> client.verify(jwt)).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void rejectsRevokedAndSuspendedCredentials() {
        server.expect(requestTo(TokenFixtures.ISSUER + "/api/v1/auth/me"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> client.verify(jwt)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void rejectsAnAccountMismatch() {
        server.expect(requestTo(TokenFixtures.ISSUER + "/api/v1/auth/me"))
                .andRespond(withSuccess(body("different-user", "creator"), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.verify(jwt)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void rejectsChangedAuthoritiesInsteadOfUsingStaleAdminClaims() {
        server.expect(requestTo(TokenFixtures.ISSUER + "/api/v1/auth/me"))
                .andRespond(withSuccess(body(TokenFixtures.ACCOUNT, "brand"), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.verify(jwt)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void failsClosedOnNetworkAndProviderFailures() {
        server.expect(requestTo(TokenFixtures.ISSUER + "/api/v1/auth/me"))
                .andRespond(withException(new IOException("private transport details")));
        assertThatThrownBy(() -> client.verify(jwt)).isInstanceOf(JwtException.class)
                .hasMessage("Account validation is unavailable").hasNoCause();
    }

    @Test
    void rejectsMalformedResponses() {
        server.expect(requestTo(TokenFixtures.ISSUER + "/api/v1/auth/me"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.verify(jwt)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsRedirectsWithoutForwardingCredentialsToAnotherHost() {
        server.expect(requestTo(TokenFixtures.ISSUER + "/api/v1/auth/me"))
                .andRespond(withStatus(HttpStatus.FOUND).header("Location", "https://attacker.example.com"));
        assertThatThrownBy(() -> client.verify(jwt)).isInstanceOf(JwtException.class);
        server.verify();
    }

    @Test
    void rejectsForbiddenResponses() {
        server.expect(requestTo(TokenFixtures.ISSUER + "/api/v1/auth/me"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> client.verify(jwt)).isInstanceOf(JwtValidationException.class);
    }

    private String body(String id, String role) {
        return "{\"success\":true,\"user\":{\"id\":\"" + id
                + "\",\"email\":\"user@example.com\",\"isEmailVerified\":true,\"roles\":[\"" + role + "\"]}}";
    }
}

```

### .\src\test\java\com\adrovis\influencer\security\ApiAuthorizationMatrixTests.java
```java
package com.adrovis.influencer.security;

import com.adrovis.influencer.config.CorsConfig;
import com.adrovis.influencer.controller.*;
import com.adrovis.influencer.collector.controller.*;
import com.adrovis.influencer.collector.service.CollectorCreatorService;
import com.adrovis.influencer.collector.service.CollectorDashboardService;
import com.adrovis.influencer.collector.repository.CollectorTeamMemberRepository;
import com.adrovis.influencer.campaign.access.CampaignApplyAccessService;
import com.adrovis.influencer.campaign.access.GuestApplyCookie;
import com.adrovis.influencer.payment.controller.*;
import com.adrovis.influencer.payment.dto.response.PaymentResponse;
import com.adrovis.influencer.payment.entity.PaymentTransaction;
import com.adrovis.influencer.payment.enums.PaymentStatus;
import com.adrovis.influencer.payment.security.PaymentOperationException;
import com.adrovis.influencer.payment.service.PaymentService;
import com.adrovis.influencer.payment.service.PaymentWebhookService;
import com.adrovis.influencer.entity.InquiryEntity;
import com.adrovis.influencer.repository.*;
import com.adrovis.influencer.service.AgencyService;
import com.adrovis.influencer.service.CampaignService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {CampaignController.class, AgencyController.class, InquiryController.class,
        NewsletterController.class, AnalyticsController.class, CollectorCreatorController.class,
        CollectorDashboardController.class, PaymentController.class, AdminPaymentController.class,
        RazorpayWebhookController.class}, properties = {"spring.profiles.active=test",
        "adrovis.frontend-origin=https://app.example.com", "app.guest-apply.cookie-secure=false"})
@Import({SecurityConfig.class, CorsConfig.class, CurrentAccount.class, GuestApplyCookie.class})
class ApiAuthorizationMatrixTests {

    @Autowired private MockMvc mvc;
    @MockBean private JwtDecoder decoder;
    @MockBean private CampaignService campaigns;
    @MockBean private CampaignApplyAccessService apply;
    @MockBean private AgencyService agencies;
    @MockBean private InquiryRepository inquiries;
    @MockBean private NewsletterSubscriptionRepository subscribers;
    @MockBean private AnalyticsRepository analytics;
    @MockBean private CollectorCreatorService creators;
    @MockBean private CollectorDashboardService dashboard;
    @MockBean private CollectorTeamMemberRepository members;
    @MockBean private PaymentService payments;
    @MockBean private PaymentWebhookService webhooks;

    private static final UUID ID = UUID.fromString("ad9f1111-2222-4333-8444-555566667777");
    private static final List<String> ADMIN_GETS = List.of(
            "/api/v1/agencies", "/api/v1/collector/creators", "/api/v1/collector/creators/export",
            "/api/v1/collector/creators/members", "/api/v1/collector/dashboard/overview",
            "/api/v1/inquiries", "/api/v1/inquiries/" + ID, "/api/v1/newsletter/subscribers",
            "/api/v1/analytics/overview", "/api/v1/analytics/sessions", "/api/v1/analytics/sessions/" + ID,
            "/api/v1/analytics/leads", "/api/v1/admin/payments/stats", "/api/v1/admin/payments/transactions");

    @BeforeEach
    void setup() {
        for (String role : List.of("ADMIN", "CREATOR", "BRAND", "AGENCY")) {
            when(decoder.decode(role)).thenReturn(Jwt.withTokenValue(role).header("alg", "RS256")
                    .subject(ID.toString()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                    .claim("roles", List.of(role)).claim("account_email", "user@example.com").build());
        }
        when(campaigns.getFilteredCampaigns(any())).thenReturn(Page.empty());
        when(inquiries.save(any())).thenReturn(InquiryEntity.builder().id(ID).build());
        when(inquiries.findFilteredInquiries(isNull(), isNull(), isNull(), any())).thenReturn(Page.empty());
        when(inquiries.findById(any())).thenReturn(java.util.Optional.of(InquiryEntity.builder().id(ID).build()));
        when(subscribers.existsByEmailIgnoreCase("user@example.com")).thenReturn(true);
        when(subscribers.findFilteredSubscriptions(any(), any(), any())).thenReturn(Page.empty());
        when(analytics.findById(any())).thenReturn(java.util.Optional.of(
                com.adrovis.influencer.entity.AnalyticsSessionEntity.builder().sessionId(ID).build()));
        when(analytics.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class))).thenReturn(Page.empty());
        when(analytics.upsertLead(any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(creators.exportCreatorsCsv(any())).thenReturn("id,email\n");
        when(payments.createOrder(any())).thenReturn(PaymentResponse.builder().status(PaymentStatus.INITIATED).build());
        when(payments.verifyPayment(any())).thenReturn(PaymentResponse.builder().status(PaymentStatus.SUCCESS).build());
        PaymentTransaction payment = PaymentTransaction.builder().id(ID).accountId(ID)
                .razorpaySignature("not-for-api-responses").amount(BigDecimal.TEN).build();
        when(payments.getAllTransactions(any(), any())).thenReturn(new PageImpl<>(List.of(payment)));
    }

    @Test
    void everyManagementGetRequiresABearer() throws Exception {
        for (String route : ADMIN_GETS) {
            mvc.perform(get(route)).andExpect(status().isUnauthorized());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATOR", "BRAND", "AGENCY"})
    void nonAdminRolesCannotReadAnyManagementCollection(String role) throws Exception {
        for (String route : ADMIN_GETS) {
            mvc.perform(get(route).header("Authorization", "Bearer " + role)).andExpect(status().isForbidden());
        }
    }

    @Test
    void adminReadsRemainAvailableWithoutExposingGatewaySignatures() throws Exception {
        for (String route : ADMIN_GETS) {
            mvc.perform(get(route).header("Authorization", "Bearer ADMIN")).andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/admin/payments/transactions").header("Authorization", "Bearer ADMIN"))
                .andExpect(jsonPath("$.data.content[0].accountId").value(ID.toString()))
                .andExpect(jsonPath("$.data.content[0].razorpaySignature").doesNotExist());
    }

    @Test
    void campaignBrowsingAndItsFiltersRemainPublic() throws Exception {
        mvc.perform(get("/api/v1/campaigns").param("brand", "Example").param("location", "Mumbai")
                        .param("page", "0").param("size", "12"))
                .andExpect(status().isOk());
    }

    @Test
    void inquiriesRemainPublicButNewsletterRequiresSignup() throws Exception {
        mvc.perform(post("/api/v1/inquiries").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Test","email":"guest@example.com","message":"An inquiry"}
                """)).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/newsletter/subscribe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "CREATOR", "BRAND", "AGENCY"})
    void allAccountRolesMaySubscribeOnlyTheirOwnVerifiedEmail(String role) throws Exception {
        mvc.perform(post("/api/v1/newsletter/subscribe").header("Authorization", "Bearer " + role)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/newsletter/subscribe").header("Authorization", "Bearer " + role)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"other@example.com\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void noClientPaymentOperationCanBeCalledWithoutBearer() throws Exception {
        mvc.perform(post("/api/v1/payments/create-order").contentType(MediaType.APPLICATION_JSON)
                        .content(order("CREATOR"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/payments/verify").contentType(MediaType.APPLICATION_JSON).content(verification()))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "CREATOR", "BRAND", "AGENCY"})
    void authenticatedPaymentRoutesReachTheirServices(String role) throws Exception {
        mvc.perform(post("/api/v1/payments/create-order").header("Authorization", "Bearer " + role)
                        .contentType(MediaType.APPLICATION_JSON).content(order(role)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/payments/verify").header("Authorization", "Bearer " + role)
                        .contentType(MediaType.APPLICATION_JSON).content(verification()))
                .andExpect(status().isOk());
    }

    @Test
    void visitorEventsStillWorkForGuestsWhileTheirReadApisStayPrivate() throws Exception {
        String session = "{\"sessionId\":\"" + ID + "\"}";
        mvc.perform(post("/api/v1/analytics/track").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"visitorId\":\"visitor\",\"sessionId\":\"" + ID + "\",\"path\":\"/campaigns\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/analytics/heartbeat").contentType(MediaType.APPLICATION_JSON).content(session))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/analytics/conversion").contentType(MediaType.APPLICATION_JSON).content(session))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/analytics/lead").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"sessionId\":\"" + ID + "\",\"email\":\"guest@example.com\",\"consent\":true}"))
                .andExpect(status().isOk());
    }

    @Test
    void signedProviderCallbacksUseHmacRatherThanCustomerBearer() throws Exception {
        doThrow(new PaymentOperationException(401, "INVALID_WEBHOOK_SIGNATURE", "Webhook signature is invalid."))
                .when(webhooks).processWebhook(anyString(), eq("invalid"));
        mvc.perform(post("/api/v1/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", "invalid").content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wildcardPaymentCorsCannotOverrideTheCentralAllowlist() throws Exception {
        mvc.perform(options("/api/v1/payments/create-order").header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedInvalidPaymentAmountsAreRejectedBeforeTheService() throws Exception {
        for (String amount : new String[]{"0", "-1", "0.001"}) {
            mvc.perform(post("/api/v1/payments/create-order").header("Authorization", "Bearer CREATOR")
                            .contentType(MediaType.APPLICATION_JSON).content(order("CREATOR").replace("100.00", amount)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }
        org.mockito.Mockito.verify(payments, org.mockito.Mockito.never()).createOrder(any());
    }

    private String order(String role) {
        return "{\"email\":\"user@example.com\",\"planId\":\"existing-plan\",\"userRole\":\"" + role
                + "\",\"amount\":100.00}";
    }

    private String verification() {
        return "{\"razorpayOrderId\":\"order_test\",\"razorpayPaymentId\":\"pay_test\","
                + "\"razorpaySignature\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}";
    }
}

```

### .\src\test\java\com\adrovis\influencer\security\CorsDeploymentTests.java
```java
package com.adrovis.influencer.security;

import com.adrovis.influencer.config.CorsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorsDeploymentTests {
    private MockEnvironment production(String local) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("adrovis.local-development-origins", local);
        return environment;
    }

    @Test
    void productionPreflightAllowsConfiguredWebsiteAndLocalFrontend() throws Exception {
        var source = new CorsConfig().coreCorsSource(
                "https://brandcollabz.in,https://www.brandcollabz.in",
                production("http://localhost:5173"));
        for (String origin : new String[]{"https://brandcollabz.in", "https://www.brandcollabz.in", "http://localhost:5173"}) {
            for (String path : new String[]{"/api/v1/analytics/track", "/api/v1/analytics/conversion",
                    "/api/v1/newsletter/subscribe", "/api/v1/campaigns/id/apply-access"}) {
                var response = preflight(source, origin, path, "authorization,content-type");
                assertThat(response.getStatus()).isEqualTo(200);
                assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo(origin);
                assertThat(response.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
            }
        }
    }

    @Test
    void unconfiguredOriginsAndHeadersStayRejected() throws Exception {
        var source = new CorsConfig().coreCorsSource("https://brandcollabz.in", production("http://localhost:5173"));
        for (String origin : new String[]{"http://localhost:3000", "http://localhost:5174",
                "https://attacker.example", "http://localhost.attacker.example:5173", "null"}) {
            assertThat(preflight(source, origin, "/api/v1/analytics/track", "content-type").getStatus()).isEqualTo(403);
        }
        assertThat(preflight(source, "http://localhost:5173", "/api/v1/analytics/track", "x-unapproved").getStatus())
                .isEqualTo(403);
    }

    @Test
    void emptyDevelopmentListDisablesLocalAccess() throws Exception {
        var source = new CorsConfig().coreCorsSource("https://brandcollabz.in", production(""));
        assertThat(preflight(source, "http://localhost:5173", "/api/v1/analytics/track", "content-type").getStatus())
                .isEqualTo(403);
    }

    @Test
    void malformedOrNonLoopbackDevelopmentOriginsFailStartup() {
        for (String origin : new String[]{"*", "http://example.com:5173", "http://localhost:5173/",
                "http://localhost:5173/path", "http://localhost:5173?x=1", "http://localhost:99999",
                "https://brandcollabz.in", "http://localhost.attacker.example:5173"}) {
            assertThatThrownBy(() -> new CorsConfig().coreCorsSource("https://brandcollabz.in", production(origin)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    private MockHttpServletResponse preflight(CorsConfigurationSource source, String origin, String path,
                                               String headers) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", path);
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", headers);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new DefaultCorsProcessor().processRequest(source.getCorsConfiguration(request), request, response);
        return response;
    }
}

```

### .\src\test\java\com\adrovis\influencer\security\JwtValidationTests.java
```java
package com.adrovis.influencer.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWTClaimsSet;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtValidationTests {

    private final JwtDecoder decoder = JwtValidationConfig.signatureDecoder(TokenFixtures.properties());

    @Test
    void acceptsARealSignedAccessToken() {
        assertThat(decoder.decode(TokenFixtures.sign(TokenFixtures.claims())).getSubject())
                .isEqualTo(TokenFixtures.ACCOUNT);
    }

    @Test
    void rejectsWrongIssuerAndAudience() {
        rejects(TokenFixtures.claims().issuer("https://other.example.com"));
        rejects(TokenFixtures.claims().audience("another-platform"));
    }

    @Test
    void rejectsExpiredMissingAndFutureTimestamps() {
        rejects(TokenFixtures.claims().issueTime(Date.from(Instant.now().minusSeconds(900)))
                .expirationTime(Date.from(Instant.now().minusSeconds(100))));
        rejects(TokenFixtures.claims().expirationTime(null));
        rejects(TokenFixtures.claims().issueTime(Date.from(Instant.now().plusSeconds(120))));
    }

    @Test
    void rejectsOnboardingUnknownRolesAndMalformedIdentity() {
        rejects(TokenFixtures.claims().claim("token_use", "onboarding"));
        rejects(TokenFixtures.claims().claim("roles", List.of("ONBOARDING")));
        rejects(TokenFixtures.claims().claim("roles", List.of("SUPER_ADMIN")));
        rejects(TokenFixtures.claims().subject("someone-else"));
        rejects(TokenFixtures.claims().claim("ver", -1));
    }

    @Test
    void rejectsWrongKeyAlgorithmAndMalformedToken() {
        assertThatThrownBy(() -> decoder.decode(TokenFixtures.sign(TokenFixtures.claims(),
                TokenFixtures.keys(), JWSAlgorithm.RS256))).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(TokenFixtures.sign(TokenFixtures.claims(),
                TokenFixtures.KEYS, JWSAlgorithm.RS512))).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode("invalid")).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsFractionalOverflowAndNonNumericRevocationVersions() {
        rejects(TokenFixtures.claims().claim("ver", -0.5));
        rejects(TokenFixtures.claims().claim("ver", 1.5));
        rejects(TokenFixtures.claims().claim("ver", new java.math.BigInteger("18446744073709551616")));
        rejects(TokenFixtures.claims().claim("ver", "0"));
    }

    @Test
    void rejectsAbbreviatedUuidClaimsRatherThanNormalizingThem() {
        rejects(TokenFixtures.claims().subject("1-1-1-1-1"));
        rejects(TokenFixtures.claims().jwtID("1-1-1-1-1"));
    }

    @Test
    void rejectsMissingRolesAndExcessiveTokenLifetime() {
        rejects(TokenFixtures.claims().claim("roles", List.of()));
        rejects(TokenFixtures.claims().claim("roles", List.of("creator")));
        rejects(TokenFixtures.claims().expirationTime(Date.from(Instant.now().plusSeconds(3600))));
    }

    private void rejects(JWTClaimsSet.Builder claims) {
        assertThatThrownBy(() -> decoder.decode(TokenFixtures.sign(claims))).isInstanceOf(JwtException.class);
    }
}

```

### .\src\test\java\com\adrovis\influencer\security\RouteSecurityTests.java
```java
package com.adrovis.influencer.security;

import com.adrovis.influencer.config.CorsConfig;
import com.adrovis.influencer.controller.HealthController;
import com.adrovis.influencer.controller.InquiryController;
import com.adrovis.influencer.controller.NewsletterController;
import com.adrovis.influencer.entity.InquiryEntity;
import com.adrovis.influencer.repository.InquiryRepository;
import com.adrovis.influencer.repository.NewsletterSubscriptionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {HealthController.class, InquiryController.class, NewsletterController.class,
        AccountConnectionController.class}, properties = {"adrovis.frontend-origin=https://app.example.com",
        "spring.profiles.active=test"})
@Import({SecurityConfig.class, CorsConfig.class, CurrentAccount.class})
class RouteSecurityTests {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private JwtDecoder decoder;

    @MockBean
    private InquiryRepository inquiries;

    @MockBean
    private NewsletterSubscriptionRepository subscribers;

    @BeforeEach
    void setup() {
        when(decoder.decode("creator-token")).thenReturn(jwt("CREATOR"));
        when(decoder.decode("admin-token")).thenReturn(jwt("ADMIN"));
        when(decoder.decode("invalid-token")).thenThrow(new BadJwtException("Invalid"));
        when(decoder.decode("unavailable-token")).thenThrow(new JwtException("Unavailable"));
    }

    @Test
    void leavesPublicHealthAccessible() throws Exception {
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk());
    }

    @Test
    void rejectsAnonymousAndInvalidTokens() throws Exception {
        mvc.perform(get("/api/v1/account/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/account/me").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void returnsIdentityFromTheTokenRatherThanRequestParameters() throws Exception {
        mvc.perform(get("/api/v1/account/me").param("accountId", "attacker")
                        .header("Authorization", "Bearer creator-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountId").value(TokenFixtures.ACCOUNT))
                .andExpect(jsonPath("$.roles[0]").value("CREATOR"));
    }

    @Test
    void normalUsersCannotReadInternalCollections() throws Exception {
        for (String path : List.of("/api/v1/inquiries", "/api/v1/newsletter/subscribers",
                "/api/v1/collector/creators", "/api/v1/collector/creators/export", "/api/v1/analytics/overview")) {
            mvc.perform(get(path).header("Authorization", "Bearer creator-token"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void anAdminCanReadInquiries() throws Exception {
        when(inquiries.findFilteredInquiries(isNull(), isNull(), isNull(), any())).thenReturn(Page.empty());
        mvc.perform(get("/api/v1/inquiries").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void theOldAdminKeyDoesNotBypassAuthentication() throws Exception {
        mvc.perform(get("/api/v1/analytics/overview").header("X-Admin-Key", "legacy-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void preservesPublicInquirySubmission() throws Exception {
        when(inquiries.save(any())).thenReturn(InquiryEntity.builder().id(UUID.randomUUID()).build());
        mvc.perform(post("/api/v1/inquiries").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Test User","email":"user@example.com","message":"A campaign inquiry"}
                """)).andExpect(status().isCreated());
    }

    @Test
    void requiresSignupForNewsletterSubscription() throws Exception {
        when(subscribers.existsByEmailIgnoreCase(anyString())).thenReturn(true);
        mvc.perform(post("/api/v1/newsletter/subscribe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/newsletter/subscribe").header("Authorization", "Bearer creator-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void allowsOnlyApprovedOriginsIncludingOnActualWildcardAnnotatedControllers() throws Exception {
        mvc.perform(options("/api/v1/inquiries").header("Origin", "https://app.example.com")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.com"));
        mvc.perform(options("/api/v1/inquiries").header("Origin", "https://attacker.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/inquiries").header("Origin", "https://attacker.example.com")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsServiceUnavailableWhenAccountValidationCannotComplete() throws Exception {
        mvc.perform(get("/api/v1/account/me").header("Authorization", "Bearer unavailable-token"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_UNAVAILABLE"));
    }

    @Test
    void browserRoleHeadersAndQueryTokensCannotGrantAdminAccess() throws Exception {
        mvc.perform(get("/api/v1/inquiries").header("X-Role", "ADMIN")
                        .param("access_token", "admin-token").param("role", "ADMIN"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/inquiries").header("Authorization", "Bearer creator-token")
                        .header("X-Role", "ADMIN").param("role", "ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicReadRoutesDoNotAllowMutationOrMethodOverride() throws Exception {
        mvc.perform(post("/api/v1/campaigns").header("Authorization", "Bearer creator-token"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/inquiries/export").header("Authorization", "Bearer creator-token")
                        .header("X-HTTP-Method-Override", "GET"))
                .andExpect(status().isForbidden());
    }

    private Jwt jwt(String role) {
        return Jwt.withTokenValue("fixture").header("alg", "RS256").subject(TokenFixtures.ACCOUNT)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
                .claim("roles", List.of(role)).claim("token_use", "access")
                .claim("account_email", "user@example.com").build();
    }
}

```

### .\src\test\java\com\adrovis\influencer\security\TokenFixtures.java
```java
package com.adrovis.influencer.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;

final class TokenFixtures {

    static final String ISSUER = "https://auth.example.com";
    static final String ACCOUNT = UUID.randomUUID().toString();
    static final KeyPair KEYS = keys();

    static KeyPair keys() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    static AdrovisSecurityProperties properties() {
        String pem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{10}).encodeToString(KEYS.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
        return new AdrovisSecurityProperties(ISSUER, "adrovis-platform",
                new ByteArrayResource(pem.getBytes(StandardCharsets.US_ASCII)),
                URI.create(ISSUER + "/api/v1/auth/me"), null);
    }

    static JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().issuer(ISSUER).subject(ACCOUNT).audience("adrovis-platform")
                .jwtID(UUID.randomUUID().toString()).issueTime(Date.from(Instant.now().minusSeconds(5)))
                .expirationTime(Date.from(Instant.now().plusSeconds(600)))
                .claim("token_use", "access").claim("ver", 0).claim("roles", List.of("CREATOR"));
    }

    static String sign(JWTClaimsSet.Builder claims) {
        return sign(claims, KEYS, JWSAlgorithm.RS256);
    }

    static String sign(JWTClaimsSet.Builder claims, KeyPair keys, JWSAlgorithm algorithm) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(algorithm).type(JOSEObjectType.JWT)
                    .keyID("production-key").build(), claims.build());
            jwt.sign(new RSASSASigner((RSAPrivateKey) keys.getPrivate()));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}

```

### .\src\test\java\com\adrovis\influencer\security\TrustConfigurationTests.java
```java
package com.adrovis.influencer.security;

import com.adrovis.influencer.config.CorsConfig;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.UrlResource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrustConfigurationTests {

    @Test
    void acceptsConfiguredPublicKeyFileAndHttpsStatusEndpoint() {
        assertThatCode(() -> properties("https://auth.example.com", "https://auth.example.com/api/v1/auth/me"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsInsecureEndpointsAndUnexpectedPaths() {
        assertThatThrownBy(() -> properties("http://auth.example.com", "https://auth.example.com/api/v1/auth/me"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> properties("https://auth.example.com", "https://auth.example.com/other"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> properties("https://auth.example.com", "http://auth.example.com/api/v1/auth/me"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsRemoteKeyLoadingAndMissingTrust() throws Exception {
        assertThatThrownBy(() -> new AdrovisSecurityProperties("https://auth.example.com", "adrovis-platform",
                new UrlResource("https://untrusted.example.com/key.pem"),
                URI.create("https://auth.example.com/api/v1/auth/me"), Duration.ofSeconds(10)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AdrovisSecurityProperties(null, "adrovis-platform", null, null, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requiresExactHttpsProductionOrigins() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        CorsConfig config = new CorsConfig();
        assertThatCode(() -> config.coreCorsSource("https://app.example.com", prod)).doesNotThrowAnyException();
        for (String value : new String[]{"*", "http://localhost:3000", "https://app.example.com/path", ""}) {
            assertThatThrownBy(() -> config.coreCorsSource(value, prod)).isInstanceOf(IllegalStateException.class);
        }
    }

    private AdrovisSecurityProperties properties(String issuer, String status) {
        return new AdrovisSecurityProperties(issuer, "adrovis-platform", new FileSystemResource("public.pem"),
                URI.create(status), Duration.ofSeconds(10));
    }
}

```

