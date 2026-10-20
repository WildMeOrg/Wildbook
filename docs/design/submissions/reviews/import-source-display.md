# Import source display follow-up

The imports page labels its filename column Source. Tasks with a nonempty submissions API submissionId show API, including existing imports. Other tasks retain their filename or the existing dash fallback. English, German, Spanish, French and Italian header translations are included.

Actual Claude CLI reviewed the approach and final diff. Final verdict: "Approved. I found no blockers."

Validation: offline Maven packaging succeeded with tests skipped for this display-only change. The packaged JSP and all five resource bundles match source bytes. Live page rendering remains to be checked after deployment. imports.jsp was previously committed with CRLF; it is now LF per the development skill. Review with whitespace ignored to see the seven added and two removed logical lines.
