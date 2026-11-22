# Switchly evaluation API

All paths below are relative to `/api/v1`. The management API uses a signed-in user's `Authorization: Bearer <access-token>` and the project's organization membership. The evaluation API uses `Authorization: SDK <secret>` and accepts POST requests with JSON bodies. Use HTTPS for both.

## RC-1 workspace access

Registering creates an organization admin. Login accepts email, password and an optional organization UUID. Valid credentials with several memberships return HTTP 409, `WORKSPACE_REQUIRED`, and the user's organization choices. Submit the selected UUID as `organizationId`. A signed JWT and its rotating refresh token bind to one membership. Spring checks the live membership for every management request.

| Operation | Endpoint |
| --- | --- |
| List workspace members (employee sees self) | `GET /orgs/{orgId}/members` |
| List pending invitations or create one | `GET, POST /orgs/{orgId}/members/invitations` |
| Cancel an invitation by email | `POST /orgs/{orgId}/members/invitations/cancel` |
| Accept a 48-hour, single-use invite | `POST /auth/accept-invite` with token, invited email, fullName and password |
| Update role or assignment | `PUT /orgs/{orgId}/members/{userId}/role` |
| Remove a membership | `DELETE /orgs/{orgId}/members/{userId}` |
| Test a saved flag against an evaluation context | `POST /environments/{envId}/flags/{flagId}/preview` with `{"context":{"userId":"sample"}}` |

Creating an invitation accepts email, role and projectId. Set projectId to one project in this organization for EMPLOYEE, or null for ADMIN. Issuance returns the one-time link token. Reissuing cancels the previous link. Existing accounts authenticate with their current password to accept. Admins can manage other admins while the organization retains at least one. The local model drafting endpoint belongs to the console gateway and requires project access.

## Provision a server-side SDK

1. Create a project with `POST /orgs/{orgId}/projects`. The request accepts `{"name":"Payments","key":"payment-service"}`. Omit `key` to generate an immutable public key. Read it from the project's `key` field on creation, listing or `GET /projects/{projectId}`. Keys are unique within an organization. An SDK credential identifies its project even when another organization uses the same project key.
2. Create a logical flag with `POST /projects/{projectId}/flags`. For example, use `{"key":"new-checkout","name":"New checkout","valueType":"BOOLEAN","defaultValue":false}`. Use `GET /projects/{projectId}/environments` to find the management environment IDs.
3. Set `{"value":true}` with `PUT /environments/{envId}/flags/{flagId}/value` and `{"enabled":true}` with `PUT /environments/{envId}/flags/{flagId}/state`. Each environment has its own value, state, targeting rules and rollout.
4. An organization ADMIN creates the key with `POST /projects/{projectId}/sdk-keys`. Pass a name and 1–100 distinct existing environment keys.

```json
{"name":"Payments backend","environmentKeys":["staging","production"]}
```

A successful creation returns HTTP 201. This example uses placeholders.

```json
{
  "key": {
    "id": "<key-id>",
    "name": "Payments backend",
    "environmentKeys": ["production", "staging"],
    "createdAt": "2026-01-15T12:00:00Z",
    "revokedAt": null
  },
  "secret": "<one-time SDK secret>"
}
```

Store the secret in the calling backend's secret manager. The credential is for **server-side SDKs only**. Keep it out of browser bundles, mobile apps, URLs and logs. A browser application can ask its own backend for the decisions it needs. The calling server is responsible for the accuracy of context attributes and for its own user authorization.

| SDK key operation | Method and path | Response |
| --- | --- | --- |
| Issue | `POST /projects/{projectId}/sdk-keys` | 201, key metadata and one-time secret |
| List | `GET /projects/{projectId}/sdk-keys` | 200, array of metadata including revocation status |
| Revoke | `DELETE /projects/{projectId}/sdk-keys/{keyId}` | 204, empty body. Repeating the request for the same key also returns 204 |

All three operations require ADMIN. Each secret contains 32 bytes from Java's `SecureRandom`, encoded as unpadded base64url and prefixed with `swk_`. PostgreSQL indexes a SHA-256 digest of the entire secret. The foreign keys bind grants to environment IDs in the issuing project. Recreating an environment with the same public key requires a new grant. To rotate credentials, issue a replacement, deploy it to the server, then revoke the previous key.

## Evaluate one flag

`POST /evaluate`

```json
{
  "projectKey": "payment-service",
  "environmentKey": "production",
  "flagKey": "new-checkout",
  "context": {
    "userId": "user-123",
    "country": "IN",
    "platform": "web",
    "custom": {"paid": true, "seats": 12}
  }
}
```

Example HTTP 200 response for an eligible rollout participant:

```json
{
  "flagKey": "new-checkout",
  "environmentKey": "production",
  "valueType": "BOOLEAN",
  "value": true,
  "enabled": true,
  "reason": "ROLLOUT_INCLUDED",
  "evaluatedAt": "2026-01-15T12:00:00Z"
}
```

`enabled` reports the environment's configured switch. Read `value` for the actual decision, including fallbacks. BOOLEAN returns a JSON boolean, STRING a string, NUMBER a number, and JSON an object or array. The service preserves numeric precision when reading JSON. The calling runtime must also support the precision an application uses.

`context` is optional. Omission and `{}` provide an empty context. Standard attributes are `userId`, `email`, `country`, `device`, `platform` and `appVersion`. Their values are strings of up to 1,024 UTF-16 code units. A supplied user ID must contain a non-whitespace character. Custom attributes are a flat object of up to 64 string, boolean or number values. A custom name starts with an ASCII letter, followed by up to 63 letters, digits or underscores. Refer to it in a condition as `custom.seats`. Omit absent attributes. Explicit nulls, nested custom values and unrecognized context fields return 400.

## Evaluate a batch

`POST /evaluate/batch` uses the same SDK authentication and context. Send 1–100 distinct flag keys.

```json
{
  "projectKey": "payment-service",
  "environmentKey": "production",
  "flagKeys": ["new-checkout", "checkout-title"],
  "context": {"userId":"user-123"}
}
```

The HTTP 200 body is a JSON array of single-flag results in request order. All results share one evaluation timestamp and one database snapshot. Any missing flag or configuration returns one HTTP 404 for the entire batch. A duplicate key returns 400. Single and batch responses use public keys.

## Decision order

| Order | Decision | Returned value and reason |
| --- | --- | --- |
| 1 | Environment switch disabled | Logical default, `FLAG_DISABLED_DEFAULT` |
| 2 | Rules exist, none match | Logical default, `TARGETING_NO_MATCH` |
| 3 | First matching rule has action `DEFAULT` | Logical default, `TARGETING_DEFAULT` |
| 4 | First matching rule has action `SERVE`, rollout unset | Environment value, `TARGETING_MATCH` |
| 5 | Rules empty and rollout unset | Environment value, `FLAG_ENABLED` |
| 6 | Rules permit serving and rollout is set | Environment value for `ROLLOUT_INCLUDED`. Logical default for `ROLLOUT_EXCLUDED` or `ROLLOUT_MISSING_IDENTITY` |

A rule list is an allowlist with an explicit fallback. An empty list passes all contexts to the rollout stage. Evaluation validates the logical default and, for enabled flags, the configured value. An incompatible value in either check produces a generic error.

## Configure targeting

Use the JWT management API. An assigned EMPLOYEE or organization ADMIN can read and replace the rules.

| Operation | Method and path | Body or result |
| --- | --- | --- |
| Read ordered rules | `GET /environments/{envId}/flags/{flagId}/targeting` | 200, JSON array |
| Replace all rules | `PUT /environments/{envId}/flags/{flagId}/targeting` | Send the complete array. Returns 200 and the saved array |

```json
[
  {"action":"DEFAULT","conditions":{"match":"AND","conditions":[
    {"attribute":"country","operator":"EQUALS","value":"CA"}
  ]}},
  {"action":"SERVE","segmentKey":"paying-web"},
  {"action":"SERVE","conditions":{"match":"OR","conditions":[
    {"attribute":"email","operator":"ENDS_WITH","value":"@example.org"},
    {"attribute":"custom.seats","operator":"GREATER_THAN_OR_EQUAL","value":10}
  ]}}
]
```

Array position defines priority, with index zero first. A rule contains exactly one inline condition group or project segment reference. A group contains 1–32 conditions, combined using `AND` or `OR`. A ruleset contains up to 32 rules. Sending `[]` clears it. Replacement is atomic, including reference validation.

| Operator | Operand and comparison |
| --- | --- |
| `EQUALS`, `NOT_EQUALS` | One string, boolean or number of the attribute's type. Numeric equality compares decimal values, so 4 and 4.0 compare equal |
| `CONTAINS`, `STARTS_WITH`, `ENDS_WITH` | One string. Literal, case-sensitive substring, prefix or suffix |
| `GREATER_THAN`, `GREATER_THAN_OR_EQUAL`, `LESS_THAN`, `LESS_THAN_OR_EQUAL` | One JSON number, for numeric custom attributes. Comparisons use decimal ordering |
| `IN`, `NOT_IN` | Array of 1–100 scalars, all the same type. Uses equality with any member |
| `EXISTS`, `NOT_EXISTS` | Test presence or absence. Omit the `value` property |

Standard attributes accept only string operands for value comparisons. `appVersion` supports string operations. For ordered versions, supply an explicitly numeric custom build number. Case and whitespace are significant, and values do not undergo coercion. Missing attributes or incompatible runtime custom types make every value comparison false, including `NOT_EQUALS` and `NOT_IN`. Invalid operand types and unknown configuration fields are rejected when saving a rule or segment.

## Manage user segments

A segment belongs to one project. Its immutable key can be referenced from that project's environments. Both roles can read and write within their project scope.

| Operation | Method and path | Result |
| --- | --- | --- |
| Create | `POST /projects/{projectId}/segments` | 201, segment |
| List | `GET /projects/{projectId}/segments` | 200, array ordered by key |
| Get | `GET /projects/{projectId}/segments/{key}` | 200, segment |
| Replace definition | `PUT /projects/{projectId}/segments/{key}` | 200, updated segment |
| Preview membership | `POST /projects/{projectId}/segments/{key}/evaluate` | 200, e.g. `{"member":true}`. Send `{"context":{...}}` or `{}` |
| Delete | `DELETE /projects/{projectId}/segments/{key}` | 204, empty body. A referenced segment returns 409 |

Example create body:

```json
{
  "key": "paying-web",
  "name": "Paying web customers",
  "description": "Customers on paid plans using the web app",
  "conditions": {"match":"AND","conditions":[
    {"attribute":"custom.paid","operator":"EQUALS","value":true},
    {"attribute":"platform","operator":"EQUALS","value":"web"}
  ]}
}
```

The response includes those fields plus `id`, `projectId`, `createdAt` and `updatedAt`. An update supplies `name`, optional `description` and the complete `conditions` group. A committed segment edit applies to subsequent evaluations of every referencing rule. Segment definitions contain attribute conditions only.

## Percentage rollouts

| Operation | Method and path | Result |
| --- | --- | --- |
| Read | `GET /environments/{envId}/flags/{flagId}/rollout` | 200, e.g. `{"percentage":null}` |
| Replace or clear | `PUT /environments/{envId}/flags/{flagId}/rollout` | Send `{"percentage":10}` or `{"percentage":null}`. Returns 200 with the saved setting |

Both roles can manage rollouts within their project scope. The percentage accepts JSON integers from 0 through 100 inclusive. Every configured percentage requires a stable user ID, including 0 and 100. Missing identity returns the logical default with `ROLLOUT_MISSING_IDENTITY`. Clearing the percentage removes the identity requirement.

The bucketing algorithm uses these exact steps:

1. Join the lowercase, hyphenated flag UUID, environment UUID and original user ID with a single ASCII colon between fields. Preserve the user ID's case, whitespace and Unicode representation.
2. Hash the UTF-8 bytes with SHA-256. Interpret the first four digest bytes as an unsigned, big-endian 32-bit integer.
3. Compute `bucket = integer % 10000`. Include the identity when `bucket < percentage * 100`.

For flag `00000000-0000-0000-0000-000000000001`, environment `00000000-0000-0000-0000-000000000002` and user `user-123`, the bucket is **3031**. That identity is excluded at 30% and included at 31%. Increasing a percentage retains earlier participants for unchanged inputs. Buckets remain independent across flag and environment identities.

## Errors, limits and consistency

Errors use `{"error":{"code":"FORBIDDEN","message":"SDK key does not grant this scope"}}`.

| HTTP | Code | Meaning |
| --- | --- | --- |
| 400 | `MALFORMED_REQUEST`, `VALIDATION_FAILED`, `INVALID_ARGUMENT` | Unreadable JSON, DTO validation failure, invalid keys, operands, context, batch or rollout |
| 401 | `UNAUTHORIZED` | Missing, invalid or revoked credential, or wrong credential scheme for the endpoint |
| 403 | `FORBIDDEN` | Insufficient membership role or SDK project/environment scope. Unknown and ungranted environments share this response |
| 404 | `NOT_FOUND` | Missing flag or environment configuration within the authorized scope, or unknown management resource |
| 409 | `CONFLICT` | Duplicate management key, incompatible management scope, unknown project segment reference or deletion of a referenced segment |
| 429 | `RATE_LIMIT_EXCEEDED` | Evaluation quota exhausted. `Retry-After` gives the remaining wait in seconds |
| 500 | `INTERNAL_SERVER_ERROR` | Unexpected server error, including invalid stored values. Callers receive a generic message |

Both evaluation endpoints share a sliding window, by resolved client IP on each API instance. The default is **600 requests per 60 seconds**, including invalid credentials. A batch counts as one request. Set `SWITCHLY_RATE_LIMIT_EVALUATION_LIMIT` to change the quota. The existing `SWITCHLY_RATE_LIMIT_ENABLED` switch controls enforcement. Only configured trusted proxies influence forwarded IP resolution. SDKs should honor `Retry-After` and choose an application-owned fallback when evaluation is unavailable.

The SDK filter performs an indexed credential lookup. A read-only repeatable-read transaction verifies the live environment grant and loads the requested flags with their typed configurations in one query. Enabled configurations share one ordered rules query that joins project segments. This is at most four SQL queries per successful request, including authentication, for either one flag or 100. The evaluator operates entirely on loaded objects. There is no cross-request flag, segment or credential cache. Requests starting after a committed update observe it. An in-flight evaluation may finish against its existing snapshot.

## Database upgrade

Flyway V7 creates project-scoped segments and ordered targeting rules with composite ownership constraints. V8 adds nullable rollout percentages with a database range check. V9 provisions public project keys, SDK credential digests and explicit environment grants. Existing flags start with empty rules and an unset rollout. Existing projects receive distinct generated public keys. V9's per-row project-key default rewrites the projects table in PostgreSQL. Allow for the table lock and index build when scheduling the migration on a populated deployment.
