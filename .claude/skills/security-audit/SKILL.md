---
name: security-audit
description: Use when performing security audit on source code — scans for hardcoded credentials, SQL injection, configuration secrets, and other vulnerabilities. Use when the user says "安全检查", "security audit", "安全审计", "漏洞扫描", "检查安全", or when performing code review that must include security assessment.
---

# Security Audit

## Overview

Systematic security vulnerability scanning for source code, configuration files, and database scripts. Covers credential leaks, injection risks, configuration exposure, and other common vulnerability categories.

This skill is **platform-agnostic** — it defines the methodology. The calling agent or project context provides the specific file paths, tech stack details, and configuration file locations to scan.

## Core Principle

**Every finding must be verified, not assumed.** A pattern match (e.g., `password =`) is a *candidate*, not a *confirmed leak*. Check context: is it a test fixture? An example in a README? A comment? Only report confirmed risks.

## Scan Categories

### Category 1: Hardcoded Credentials & Secrets

**What to scan:**
- Source code (`.java`, `.js`, `.ts`, `.vue`, `.py`, `.go`, etc.)
- Configuration files (`.yml`, `.yaml`, `.properties`, `.json`, `.env`, `.xml`)
- Documentation and scripts (`.md`, `.sh`, `.bat`, `.sql`)

**Detection patterns (grep):**

```
# Passwords and credentials
password\s*[:=]\s*['"][^'"]+['"]
passwd\s*[:=]\s*['"][^'"]+['"]
secret\s*[:=]\s*['"][^'"]+['"]
api[_-]?key\s*[:=]\s*['"][^'"]+['"]
token\s*[:=]\s*['"][^'"]+['"]
access[_-]?key\s*[:=]\s*['"][^'"]+['"]
private[_-]?key\s*[:=]
-----BEGIN (RSA|EC|DSA|PGP) PRIVATE KEY-----

# Connection strings with embedded credentials
jdbc:[a-z]+://[^/]+\?[^ ]*password=
mongodb://[^:]+:[^@]+@
redis://[^:]+:[^@]+@

# Auth tokens in code
Bearer\s+[A-Za-z0-9\-._~+/]+=*
x-api-key:\s*['"][^'"]+['"]
Authorization:\s*['"][^'"]+['"]

# Default/weak credentials
username\s*[:=]\s*['"]admin['"]
password\s*[:=]\s*['"](admin|123456|password|root|test)
```

**Verification steps:**
1. Is the value a literal string or an environment variable reference (`${ENV_VAR}`)?
2. Is this in a test file or example documentation?
3. Is the file tracked by git (not `.gitignore`d)?
4. Is the credential for a local/dev service only?

**Severity:**
- Production credential in source code → 🔴 Critical
- Dev credential committed to git → 🟠 High
- Default credential that user must change → 🟡 Medium
- Placeholder value in example file → 🔵 Info (note but don't block)

### Category 2: SQL Injection

**What to scan:**
- Repository/DAO classes (Java `@Query`, JPA native queries)
- Raw SQL strings in service/controller code
- MyBatis XML mappers
- Dynamic query builders (JPA Criteria API, QueryDSL — these are safe, but worth verifying)
- JDBC `Statement` (vs safe `PreparedStatement`)

**Detection patterns (grep):**

```
# String concatenation in SQL queries
"SELECT.*"  \+
"INSERT.*"  \+
"UPDATE.*"  \+
"DELETE.*"  \+
'SELECT.*'  \+
"WHERE.*" \+ .* \+   (Java/Kotlin string concat)
`SELECT.*\$\{           (JavaScript template literal with interpolation)

# Unsafe JDBC usage
Statement\s+(?!.*prepare)
createStatement\(\)

# Dynamic ORDER BY / GROUP BY without whitelist
ORDER BY \$\{|ORDER BY \#
GROUP BY \$\{|GROUP BY \#

# LIKE with concatenated wildcards (may be safe, but flag it)
LIKE\s+['"]%"\s*\+
LIKE\s+['"]%\s*\+
```

**Verification steps:**
1. Is user input directly concatenated into the SQL string?
2. Is JPA parameter binding (`?1`, `:name`, `@Param`) used or bypassed?
3. For dynamic `ORDER BY` / `GROUP BY`: is there a whitelist of allowed column names?
4. Is `PreparedStatement` used for all JDBC queries?

**Safe patterns (no false alarm):**
```java
// SAFE: JPA parameter binding
@Query("SELECT u FROM User u WHERE u.name = :name")
User findByName(@Param("name") String name);

// SAFE: PreparedStatement
PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
ps.setLong(1, userId);

// SAFE: JPA Criteria API / QueryDSL
criteriaBuilder.equal(root.get("name"), name);
```

**Unsafe patterns (flag these):**
```java
// DANGEROUS: String concatenation
String sql = "SELECT * FROM users WHERE name = '" + name + "'";

// DANGEROUS: Template interpolation in native query
@Query(value = "SELECT * FROM users WHERE name = '" + ?1 + "'", nativeQuery = true)

// DANGEROUS: Dynamic ORDER BY without whitelist
String sql = "SELECT * FROM items ORDER BY " + sortField + " " + sortOrder;
```

**Severity:**
- Direct user input concatenation → 🔴 Critical
- Dynamic ORDER BY / GROUP BY without whitelist → 🟠 High
- LIKE with concatenated user input → 🟡 Medium (less exploitable but not safe)
- Parameter binding with safe API → ✅ Safe (no report)

### Category 3: Configuration File Secrets

**What to scan:**
- `application.yml`, `application.properties`, `application-*.yml`
- `.env`, `.env.local`, `.env.production`
- `secrets.json`, `credentials.json`
- `web.config`, `app.config`, `settings.py`
- Docker files (`Dockerfile`, `docker-compose.yml`)
- CI/CD config (`.github/workflows/*.yml`, `.gitlab-ci.yml`, `Jenkinsfile`)

**Detection patterns:**

```
# Plaintext values that should be environment variables
spring\.datasource\.password:\s*[^$]
jwt\.secret:\s*[^$]
DB_PASSWORD=\s*['"][^'"]+['"]
REDIS_PASSWORD=\s*['"][^'"]+['"]
SMTP_PASSWORD=\s*['"][^'"]+['"]

# API keys in config
aws\.accessKeyId:\s*[^$]
aliyun\.accessKey:\s*[^$]
OSS_ACCESS_KEY=\s*[^$]

# Missing environment variable defaults (check if the default is dangerous)
\$\{[A-Z_]+:([^}]+)\}   # Extract default value after colon, check if it's a real credential

# Debug/verbose logging in production-like config
show.sql:\s*true
debug:\s*true
level:\s*DEBUG
```

**Verification steps:**
1. Is the value after a `:` or `=` a real secret or a placeholder?
2. Does the config use environment variable substitution (`${ENV_VAR}`)?
3. For `${VAR:default}` — is the default value a real secret (dangerous) or a safe placeholder?
4. Is this a production config file (`application-prod.yml`) vs dev config?
5. Are debug/verbose logging settings enabled in non-dev profiles?

**Severity:**
- Production DB password in plaintext → 🔴 Critical
- JWT secret / encryption key in plaintext → 🔴 Critical
- API key for external service in config → 🔴 Critical
- Debug SQL logging enabled outside dev → 🟠 High
- Dev-only credentials committed to repo → 🟡 Medium

### Category 4: Additional Vulnerabilities

#### 4.1 Missing Authentication / Authorization

```
# Endpoints without auth check
@GetMapping|@PostMapping|@PutMapping|@DeleteMapping  → check if method is behind SecurityFilterChain
.permitAll\(\)                                        → check if this endpoint SHOULD be public
anonymous\(\)                                          → check if anonymous access is intentional

# Missing role validation
@PreAuthorize → missing on sensitive operations (delete, admin actions)
hasRole|hasAuthority → verify the role name is correct
```

**Severity:** 🔴 Critical — data exposure or privilege escalation

#### 4.2 Insecure Direct Object Reference (IDOR)

```java
// Check: does the method verify that the requesting user owns the target resource?
public Result<?> getItem(@PathVariable Long id) {
    // Is there an ownership check?
    // userId from auth == item.getOwnerId()  ?
}
```

**Detection pattern:** Any controller method that takes a resource ID without verifying ownership against the authenticated user.

**Severity:** 🔴 Critical — unauthorized data access

#### 4.3 Cross-Site Scripting (XSS)

```
# Check output encoding in templates
v-html=         → Vue: is the content user-generated? Must be sanitized
<rich-text>     → Miniprogram: is nodes content from user input?
innerHTML       → JS: direct DOM insertion of user content
document.write  → Never safe with user input

# Check input that reaches the frontend without escaping
# Backend: does the API return user-generated content in JSON? (OK — frontend must escape)
# Frontend: does it render user content with v-html / dangerouslySetInnerHTML?
```

**Severity:** 🟠 High — depends on context (stored vs reflected, CSP presence)

#### 4.4 Sensitive Data Exposure in Logs

```
# Java
log\.(debug|info|warn|error)\(.*password
log\.(debug|info|warn|error)\(.*token
log\.(debug|info|warn|error)\(.*secret
System\.out\.print.*password
toString\(\)  → on entities: does it include sensitive fields?

# JavaScript
console\.(log|debug|info|warn|error)\(.*password
console\.(log|debug|info|warn|error)\(.*token
```

**Severity:** 🟠 High — logs are often less protected than databases

#### 4.5 File Upload Security

```
# Check upload endpoints
MultipartFile  → is file type validated?
max-file-size  → is there a size limit?
upload-dir     → is the path inside the web root or outside?
```

**Checklist:**
- File extension whitelist (not just client-side)
- File size limit enforced server-side
- Upload directory outside web root (not directly accessible via URL)
- Filename sanitization (prevent path traversal: `../../../etc/passwd`)
- Malware scanning (if applicable)

**Severity:** 🟠 High — path traversal can overwrite system files

#### 4.6 CORS Misconfiguration

```
# Too permissive
allowedOrigins: "*"           → allows any origin with credentials
allowedOrigins: "*" + allowCredentials: true  → browser will reject, but indicates confusion
allowedOriginPatterns: "*"    → same as wildcard

# Should be specific
allowedOrigins: "https://example.com"  → correct
```

**Severity:** 🟡 Medium — limited by same-origin policy and browser enforcement

#### 4.7 Dependency Vulnerabilities

**Check:**
- `pom.xml` / `build.gradle` / `package.json` — any dependencies with known CVEs?
- Version ranges using `SNAPSHOT` or `latest` (unpinned = supply chain risk)
- Unmaintained dependencies (check last release date)

**Severity:** 🟡 Medium — depends on CVE severity

#### 4.8 Insecure Deserialization

```java
// Java native deserialization (dangerous)
ObjectInputStream.readObject()
ObjectInputStream.readUnshared()

// Jackson with unsafe type resolution (check config)
objectMapper.enableDefaultTyping()  → DANGEROUS if processing untrusted input
```

**Severity:** 🟠 High — can lead to RCE

## Scan Procedure

### Phase 1: Automated Pattern Scan

Run grep-based detection across all source directories:

```bash
# Credential scan (adjust file extensions per project)
grep -rn -iE "(password|passwd|secret|api[_-]?key|private[_-]?key)\s*[:=]\s*['\"][^'\"]{4,}" --include="*.java" --include="*.yml" --include="*.properties" --include="*.js" --include="*.ts" --include="*.vue" --include="*.xml"

# SQL injection scan
grep -rn -iE "\"(SELECT|INSERT|UPDATE|DELETE).*\"\s*\+" --include="*.java"

# Config secrets
grep -rn -iE "^(jwt\.secret|spring\.datasource\.password|DB_PASSWORD|REDIS_PASSWORD)" --include="*.yml" --include="*.properties"

# Sensitive data in logs
grep -rn -iE "(log|console)\.(debug|info|warn|error|log)\(.*(password|token|secret)" --include="*.java" --include="*.js" --include="*.ts"

# Debug config
grep -rn -iE "(show.sql|ddl-auto|debug)\s*:\s*true" --include="*.yml" --include="*.properties"
```

### Phase 2: Context Verification

For each pattern match:
1. Read surrounding 5-10 lines for context
2. Classify: confirmed vulnerability, false positive, needs more investigation
3. Assess exploitability: is this reachable from untrusted input?
4. Assess impact: what data/access does this expose?

### Phase 3: Manual Review (Targeted)

After automated scan, manually review:

1. **Auth chain**: Read `SecurityConfig.java` / `SecurityFilterChain` — which endpoints are public? Which need specific roles?
2. **Sensitive endpoints**: For each DELETE / admin endpoint, verify auth check exists
3. **File upload paths**: Read upload controller, check file type validation, storage location
4. **Entity toString()**: Spot-check entities for logged sensitive fields
5. **CORS config**: Verify origin whitelist
6. **JWT implementation**: Check key derivation, algorithm, expiration, token storage

### Phase 4: Report

Generate findings ranked by severity.

## Report Format

```
====================================
  Security Audit Report
====================================

## 🔴 Critical
| # | File:Line | Category | Issue | Recommendation |
|---|-----------|----------|-------|----------------|
| 1 | application.yml:11 | Config Secret | DB password in plaintext | Use ${DB_PASSWORD} env variable |
| 2 | application.yml:42 | Hardcoded Secret | JWT secret in config file | Use ${JWT_SECRET} env variable |

## 🟠 High
| # | File:Line | Category | Issue | Recommendation |
|---|-----------|----------|-------|----------------|
| 1 | UserRepository.java:23 | SQL Injection | Dynamic ORDER BY without whitelist | Validate sortField against enum/whitelist |

## 🟡 Medium
| # | File:Line | Category | Issue | Recommendation |
|---|-----------|----------|-------|----------------|
| 1 | application.yml:17 | Debug Config | show-sql: true may leak data in logs | Disable in non-dev profiles |

## 🔵 Info
| # | File:Line | Category | Issue |
|---|-----------|----------|-------|

## Scan Summary

- Files scanned: [N]
- Patterns checked: [N]
- Confirmed findings: [N]
- False positives filtered: [N]

## Assessment

[PASS / PASS WITH WARNINGS / FAIL — with reasoning]
```

## Triage Rules

| Finding Type | Dev (local) | Staging | Production |
|-------------|-------------|---------|------------|
| Plaintext DB password | 🟡 Medium | 🟠 High | 🔴 Critical |
| JWT secret in config | 🟡 Medium | 🔴 Critical | 🔴 Critical |
| show-sql: true | 🔵 Info | 🟡 Medium | 🟠 High |
| Debug logging | 🔵 Info | 🟡 Medium | 🟠 High |
| SQL injection | 🔴 Critical | 🔴 Critical | 🔴 Critical |
| Missing auth guard | 🔴 Critical | 🔴 Critical | 🔴 Critical |
| IDOR | 🔴 Critical | 🔴 Critical | 🔴 Critical |
| CORS wildcard | 🔵 Info | 🟡 Medium | 🟠 High |

## Common False Positives

| Pattern | Why flagged | Why it might be safe |
|---------|------------|---------------------|
| `password = "xxx"` in test file | Looks like a credential | Test fixture with fake data |
| `jwt.secret: ${JWT_SECRET:dev-secret}` | Has a default value | Default is only used when env var is missing; check if default is specific enough |
| `"SELECT * FROM " + TABLE_NAME` | String concat with SQL | `TABLE_NAME` is a constant, not user input |
| `LIKE '%' + searchTerm + '%'` | Looks like concat | Verify searchTerm is parameterized and only `%` is concatenated |
| `.permitAll()` on `/api/auth/login` | No auth required | Login endpoint should be public |
| `console.log(response)` | Logging response data | Check if response contains PII / tokens |

## The Bottom Line

**A scan is only as good as its verification.** Grep finds candidates; human review confirms or dismisses. Never report a finding you haven't verified with surrounding context.

**Prioritize by exploitability, not pattern count.** One reachable SQL injection is worse than 50 hardcoded dev passwords.
