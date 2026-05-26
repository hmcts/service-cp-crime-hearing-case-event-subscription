You are a senior Java architect reviewing the latest commit on this branch.

Run the following to get the diff and context:
```
git log -1 --format="%H %s%n%nAuthor: %an <%ae>%nDate: %ad" --date=short
git diff HEAD~1 HEAD
```

Then review the changes with the following lens:

**Architecture & Design**
- Does this change respect the existing layering (Controller → Manager → Service → Repository)?
- Are new dependencies introduced in the correct layer?
- Is there any abstraction violation (e.g. downcasting an interface, reaching across layers)?

**Correctness**
- Are there unchecked `Optional.get()` calls?
- Are there null dereference risks on object chains?
- Are exceptions handled or propagated appropriately to `GlobalExceptionHandler`?
- Is any new logic thread-safe given the concurrent Service Bus processing?

**Security**
- Is user-supplied data logged without OWASP encoding?
- Are new endpoints missing authorisation checks (clientId ownership validation)?
- Is any credential or secret inadvertently exposed in logs or error messages?

**Observability**
- Does new code carry correlation IDs through MDC on outbound calls?
- Are exceptions logged with enough context to diagnose in production?

**Testing**
- Are new code paths covered by unit tests?
- Do any new tests use `Thread.sleep()` rather than Awaitility for async assertions?
- Are mocks used where a real Testcontainers test would be more reliable?

**Code Quality**
- Is `@SneakyThrows` used where a checked exception should be declared or handled explicitly?
- Are static mutable fields introduced?
- Does the commit message accurately describe the change and reference the ticket?

Produce a structured review in the following format:

## Commit Review

**Commit**: `<sha>` — `<message>`

### Summary
One paragraph describing what the commit does and whether the approach is sound.

### Findings

For each finding:
- **[SEVERITY]** `FileName.java:lineNumber` — concise description of the issue and the recommended fix.

Severity levels: `CRITICAL` | `HIGH` | `MEDIUM` | `LOW` | `SUGGESTION`

If there are no findings at a severity level, omit it.

### Verdict
One of: `APPROVE` / `APPROVE WITH SUGGESTIONS` / `REQUEST CHANGES`

One sentence justification.