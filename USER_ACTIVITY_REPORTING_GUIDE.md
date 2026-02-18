# DSpace User Activity Reporting Feature - Implementation Guide

## Quick Start

This implementation adds a complete reporting system to DSpace that extracts submission and review data from provenance metadata and provides both programmatic access and REST API endpoints.

## What Was Implemented

### 1. Data Models (Domain Objects)

#### `UserAction.java`

Represents a single submission or review action:

```java
- actionType: "SUBMITTED" or "REVIEWED"
- userName: Full name of the user
- email: User's email address
- actionDate: ZonedDateTime of the action
- itemUUID: UUID of the item
- details: Additional information (workflow info, approval status, etc.)
```

#### `UserActivityStats.java`

Aggregates all actions by a single user:

```java
- userName: User's full name
- email: User's email
- totalSubmissions: Count of submissions
- totalReviews: Count of reviews
- actions: List of all UserAction objects
```

### 2. Parser Utility

#### `ProvenanceParser.java`

Extracts structured data from unstructured provenance text using regex patterns:

**Supported patterns**:

1. **Submission**: `Submitted by <Name> (<Email>) on <DateTime> ...`
2. **Approval**: `Approved for entry into archive by <Name> (<Email>) on <DateTime>`
3. **Review Action**: `action:reviewaction ... by <Name> (<Email>) on <DateTime>`

The parser handles:

- Date parsing with ISO 8601 format (with Z or GMT suffix)
- Email extraction
- Name parsing
- Extraction of additional details

### 3. Service Layer

#### `UserActivityReportService` (Interface)

```java
List<UserAction> getAllActions(Context context)
// Returns all submissions and reviews

Map<String, UserActivityStats> getUserStatistics(Context context)
// Returns aggregated stats per user

UserActivityStats getUserStatistics(Context context, String email)
// Returns stats for specific user

Map<String, Integer> getTotalStatistics(Context context)
// Returns summary counts
```

#### `UserActivityReportServiceImpl` (Implementation)

Queries DSpace database:

1. Finds `dc.description.provenance` metadata field
2. Retrieves all provenance metadata values
3. Parses each value using ProvenanceParser
4. Associates actions with items
5. Aggregates by user email

### 4. REST API Layer

#### `UserActivityReportController`

Exposes 4 endpoints (all require ADMIN authority):

| Method | Endpoint                                    | Description                     |
| ------ | ------------------------------------------- | ------------------------------- |
| GET    | `/api/reporting/user-activity/report`       | Full report with all users      |
| GET    | `/api/reporting/user-activity/user/{email}` | Statistics for specific user    |
| GET    | `/api/reporting/user-activity/summary`      | Summary counts only             |
| GET    | `/api/reporting/user-activity/actions`      | All actions without aggregation |

#### REST DTOs

- `UserActivityReportRest`: Full report model
- `UserActivityStatsRest`: User statistics model
- `UserActionRest`: Individual action model

## File Locations

### Backend (dspace-api)

```
dspace-api/src/main/java/org/dspace/app/reporting/
├── model/
│   ├── UserAction.java
│   └── UserActivityStats.java
├── utils/
│   └── ProvenanceParser.java
└── service/
    ├── UserActivityReportService.java
    └── UserActivityReportServiceImpl.java
```

### REST API (dspace-server-webapp)

```
dspace-server-webapp/src/main/java/org/dspace/app/rest/
├── UserActivityReportController.java
└── model/
    ├── UserActionRest.java
  ├── UserActivityStatsRest.java
  └── UserActivityReportRest.java
```

### Documentation

```
DSpace/
└── USER_ACTIVITY_REPORTING.md
```

## Integration Steps

### 1. Verify Dependencies

The implementation uses standard Spring, JPA, and DSpace dependencies already present in the project.

### 2. Build the Project

```bash
cd DSpace
mvn clean install -DskipTests
```

### 3. Deploy to DSpace

The compiled JAR will include all new classes automatically when deployed.

### 4. Access the API

```bash
# Replace YOUR_TOKEN and URL accordingly
curl -H "Authorization: Bearer YOUR_TOKEN" \
  http://localhost:8080/server/api/reporting/user-activity/summary
```

## Usage Examples

### Example 1: Get Summary Statistics

```bash
curl -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  http://localhost:8080/server/api/reporting/user-activity/summary
```

**Response**:

```json
{
  "submissions": 42,
  "reviews": 38,
  "totalUsers": 5
}
```

### Example 2: Get Full Report

```bash
curl -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  http://localhost:8080/server/api/reporting/user-activity/report
```

**Response**:

```json
{
  "totalUsers": 2,
  "totalSubmissions": 5,
  "totalReviews": 5,
  "userStats": [
    {
      "userName": "Jesiel Analista",
      "email": "jesiel.analista@gmail.com",
      "totalSubmissions": 3,
      "totalReviews": 0,
      "actions": [
        {
          "actionType": "SUBMITTED",
          "userName": "Jesiel Analista",
          "email": "jesiel.analista@gmail.com",
          "actionDate": "2026-01-06T21:14:20Z",
          "itemUUID": "550e8400-e29b-41d4-a716-446655440000",
          "details": "workflow start=Step: reviewstep - action:claimaction"
        }
      ]
    }
  ]
}
```

### Example 3: Get Specific User Report

```bash
curl -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  http://localhost:8080/server/api/reporting/user-activity/user/jesielsilva@ibict.br
```

### Example 4: Get All Actions

```bash
curl -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  http://localhost:8080/server/api/reporting/user-activity/actions
```

## Database Queries

### Check if Provenance Metadata Exists

```sql
SELECT COUNT(*) FROM metadatavalue
WHERE metadata_field_id = (
  SELECT metadata_field_id FROM metadatafieldregistry
  WHERE element='description' AND qualifier='provenance'
);
```

### View Sample Provenance Data

```sql
SELECT m.text_value, i.uuid
FROM metadatavalue m
JOIN item i ON m.dspace_object_id = i.uuid
WHERE m.metadata_field_id = (
  SELECT metadata_field_id FROM metadatafieldregistry
  WHERE element='description' AND qualifier='provenance'
)
LIMIT 5;
```

### Count Submissions and Reviews

```sql
-- This query would need the provenance parsing logic
-- The application does the parsing automatically
```

## Code Example: Programmatic Usage

```java
import org.dspace.app.reporting.service.SubmissionReviewReportService;
import org.dspace.app.reporting.model.UserSubmissionReviewStats;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ReportGenerator {

    @Autowired
    private SubmissionReviewReportService reportService;

    public void generateReport() throws SQLException {
        // Create a context
        Context context = new Context();

        try {
            // Get summary statistics
            Map<String, Integer> summary = reportService.getTotalStatistics(context);
            System.out.println("Total Submissions: " + summary.get("submissions"));
            System.out.println("Total Reviews: " + summary.get("reviews"));
            System.out.println("Total Users: " + summary.get("totalUsers"));

            // Get all user statistics
            Map<String, UserSubmissionReviewStats> stats = reportService.getUserStatistics(context);
            for (UserSubmissionReviewStats userStats : stats.values()) {
                System.out.println("\nUser: " + userStats.getUserName());
                System.out.println("Email: " + userStats.getEmail());
                System.out.println("Submissions: " + userStats.getTotalSubmissions());
                System.out.println("Reviews: " + userStats.getTotalReviews());
            }
        } finally {
            context.complete();
        }
    }
}
```

## Performance Notes

- **Initial Query**: First call queries all provenance metadata (could be large in big repositories)
- **Parsing**: Uses compiled regex patterns for efficiency
- **Aggregation**: All processing happens in-memory after database retrieval
- **Recommendations**:
  - Consider caching results if called frequently
  - Add pagination for large datasets in future versions
  - Monitor database performance on very large instances

## Security

- All endpoints require `@PreAuthorize("hasAuthority('ADMIN')")`
- Only users with ADMIN role can access reporting endpoints
- No authentication bypass is possible
- Email parameter is not used for SQL injection (uses parameter binding)

## Extending the Feature

### Adding New Action Types

1. Extend regex patterns in `ProvenanceParser.java`
2. Add new patterns for parsing additional provenance text
3. Update `UserAction.java` if new fields are needed

### Adding Filtering

1. Add filter parameters to service methods
2. Apply filtering in `SubmissionReviewReportServiceImpl`
3. Update REST controller endpoints

### Adding Pagination

1. Extend service methods to accept page/size parameters
2. Implement in service implementation
3. Update REST controller to use Spring's Pageable

### Adding Export

1. Create export service to convert reports to CSV/Excel
2. Add REST endpoint to serve files
3. Use Apache POI for Excel generation

## Troubleshooting

### No Data Returned

- **Cause**: No provenance metadata exists
- **Solution**: Check if items have been submitted/reviewed through DSpace workflow
- **Query**:
  ```sql
  SELECT COUNT(*) FROM metadatavalue
  WHERE metadata_field_id = (SELECT metadata_field_id FROM metadatafieldregistry
  WHERE element='description' AND qualifier='provenance');
  ```

### 401 Unauthorized

- **Cause**: Missing or invalid authentication token
- **Solution**: Provide valid JWT token with ADMIN role

### 403 Forbidden

- **Cause**: User lacks ADMIN authority
- **Solution**: Assign ADMIN role to user or use admin account

### Parsing Errors

- **Cause**: Provenance text format not recognized
- **Solution**: Add new regex pattern to ProvenanceParser for your format

## Testing

To test the implementation:

1. **Setup DSpace** with test data including submitted items
2. **Verify provenance metadata** exists:
   ```bash
   curl http://localhost:8080/server/api/items/YOUR_ITEM_UUID/metadata
   ```
3. **Call reporting endpoints**:
   ```bash
   curl -H "Authorization: Bearer YOUR_TOKEN" \
     http://localhost:8080/server/api/reporting/submission-review/summary
   ```
4. **Verify output** matches expected format

## Support

For issues or questions:

1. Check the logs at `$DSPACE/log/`
2. Review ProvenanceParser regex patterns for parsing issues
3. Verify database connectivity
4. Check user permissions (must have ADMIN role)
