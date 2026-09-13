package dev.ruuuberth.forkgramresolver.test;

import android.app.Application;
import android.content.Context;

import dev.ruuuberth.forkgramresolver.ForkgramDiscovery;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Development-only LSPosed entry point. It discovers the update processor
 * when Forkgram attaches its application context and does not hook the
 * discovered processor itself.
 */
public final class ForkgramXposedEntry implements IXposedHookLoadPackage {
    private static final String TARGET_PACKAGE = "org.forkgram.messenger";
    private static final String TAG = "ForkgramResolver";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log(TAG + ": Forkgram process loaded");

        XposedHelpers.findAndHookMethod(
                Application.class,
                "attach",
                Context.class,
                new XC_MethodHook() {
                    private boolean ran;

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (ran) {
                            return;
                        }
                        ran = true;

                        Context context = (Context) param.args[0];
                        try {
                            ForkgramDiscovery.run(context);
                            XposedBridge.log(TAG + ": discovery completed");
                        } catch (Throwable t) {
                            XposedBridge.log(TAG + ": discovery crashed: " + t);
                        }
                    }
                });
    }
}
