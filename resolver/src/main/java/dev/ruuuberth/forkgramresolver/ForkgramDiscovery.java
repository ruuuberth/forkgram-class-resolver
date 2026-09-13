package dev.ruuuberth.forkgramresolver;

import android.content.Context;
import android.util.Log;

/** Diagnostic entry point intended for an LSPosed module during development. */
public final class ForkgramDiscovery {
    private static final String TAG = "ForkgramResolver";

    private ForkgramDiscovery() {}

    public static ForkgramClassResolver.DiscoveryResult run(Context context) {
        String apkPath = context.getApplicationInfo().sourceDir;
        ForkgramClassResolver.DiscoveryResult result = ForkgramClassResolver.discover(
                context.getClassLoader(), apkPath);

        if (result.success) {
            ForkgramClassResolver.Candidate c = result.selected;
            Log.i(TAG, "Selected owner=" + c.owner.getName()
                    + " method=" + c.method.getName() + " score=" + c.score);
            logEvidence(c);
        } else {
            Log.w(TAG, "Discovery failed: " + result.reason
                    + " candidates=" + result.candidates.size());
            for (ForkgramClassResolver.Candidate c : result.candidates) {
                Log.d(TAG, "candidate owner=" + c.owner.getName()
                        + " method=" + c.method.getName() + " score=" + c.score);
                logEvidence(c);
            }
        }
        return result;
    }

    private static void logEvidence(ForkgramClassResolver.Candidate candidate) {
        for (String evidence : candidate.evidence) {
            Log.d(TAG, "  evidence: " + evidence);
        }
    }
}
