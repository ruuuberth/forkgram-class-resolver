package dev.ruuuberth.forkgramresolver;

import dalvik.system.DexFile;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Finds the class owning Forkgram's update-processing method without an
 * obfuscation-name map.
 *
 * The resolver deliberately fails closed: if more than one candidate reaches
 * the confidence threshold, no class is selected.
 */
public final class ForkgramClassResolver {
    private static final String[] PACKAGE_PREFIXES = {
            "org.telegram.",
            "org.forkgram.",
            "org.forkclient."
    };

    private static final int REQUIRED_SCORE = 160;

    private ForkgramClassResolver() {}

    public static DiscoveryResult discover(ClassLoader classLoader, String apkPath) {
        List<Candidate> candidates = new ArrayList<>();
        for (String className : enumerateClasses(apkPath)) {
            if (!isCandidateNamespace(className)) continue;

            Class<?> type;
            try {
                type = Class.forName(className, false, classLoader);
            } catch (Throwable ignored) {
                continue;
            }

            for (Method method : safeMethods(type)) {
                if (!hasUpdateProcessorSignature(method)) continue;

                List<String> evidence = new ArrayList<>();
                int score = 100;
                evidence.add("5-parameter update-processor shape (+100)");

                if (hasUpdateLikeCollection(type, method)) {
                    score += 45;
                    evidence.add("same owner consumes TL/message-domain types (+45)");
                }
                if (hasTelegramDomainParameters(type)) {
                    score += 25;
                    evidence.add("same owner references Telegram TL domain (+25)");
                }
                if (hasControllerShape(type)) {
                    score += 20;
                    evidence.add("same owner has controller/storage infrastructure (+20)");
                }
                if (Modifier.isStatic(method.getModifiers())) {
                    score += 5;
                    evidence.add("processor is static (+5)");
                }

                candidates.add(new Candidate(type, method, score, evidence));
            }
        }

        candidates.sort((a, b) -> Integer.compare(b.score, a.score));
        if (candidates.isEmpty()) {
            return DiscoveryResult.failure("No update-processor candidates found", candidates);
        }

        Candidate best = candidates.get(0);
        if (best.score < REQUIRED_SCORE) {
            return DiscoveryResult.failure("Best candidate did not reach confidence threshold", candidates);
        }

        if (candidates.size() > 1 && candidates.get(1).score == best.score) {
            return DiscoveryResult.failure("Ambiguous: multiple candidates share the best score", candidates);
        }

        return DiscoveryResult.success(best, candidates);
    }

    private static boolean hasUpdateProcessorSignature(Method method) {
        Class<?>[] p = method.getParameterTypes();
        if (p.length != 5) return false;

        // This is intentionally structural. The first three parameters are
        // expected to be ArrayList in known Telegram/Forkgram builds, while
        // accepting List subtypes keeps the resolver tolerant of compiler or
        // fork changes until runtime evidence lets us tighten this fingerprint.
        return isListLike(p[0]) && isListLike(p[1]) && isListLike(p[2])
                && p[3] == boolean.class && p[4] == int.class;
    }

    private static boolean isListLike(Class<?> type) {
        return type == ArrayList.class || List.class.isAssignableFrom(type);
    }

    private static boolean hasUpdateLikeCollection(Class<?> owner, Method method) {
        Class<?> first = method.getParameterTypes()[0];
        if (!isListLike(first)) return false;

        // The runtime signature alone is not enough. Look for methods on the
        // same class that consume Telegram TL objects or message-domain types.
        for (Method m : safeMethods(owner)) {
            for (Class<?> p : m.getParameterTypes()) {
                String n = p.getName();
                if (n.contains("TLObject") || n.contains("TLRPC") || n.contains("Message")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasTelegramDomainParameters(Class<?> owner) {
        for (Method m : safeMethods(owner)) {
            for (Class<?> p : m.getParameterTypes()) {
                String n = p.getName();
                if (n.startsWith("org.telegram.tgnet.")
                        || n.contains("TLRPC$Chat")
                        || n.contains("TLRPC$User")
                        || n.contains("TLRPC$InputChannel")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasControllerShape(Class<?> owner) {
        int interestingFields = 0;
        for (Field field : safeFields(owner)) {
            String n = field.getType().getName();
            if (n.contains("MessagesStorage")
                    || n.contains("ConnectionsManager")
                    || n.contains("NotificationCenter")
                    || n.contains("MessagesController")) {
                interestingFields++;
            }
        }
        return interestingFields >= 2;
    }

    private static Method[] safeMethods(Class<?> type) {
        try {
            return type.getDeclaredMethods();
        } catch (Throwable ignored) {
            return new Method[0];
        }
    }

    private static Field[] safeFields(Class<?> type) {
        try {
            return type.getDeclaredFields();
        } catch (Throwable ignored) {
            return new Field[0];
        }
    }

    private static boolean isCandidateNamespace(String className) {
        for (String prefix : PACKAGE_PREFIXES) {
            if (className.startsWith(prefix)) return true;
        }
        return false;
    }

    private static List<String> enumerateClasses(String apkPath) {
        List<String> result = new ArrayList<>();
        if (apkPath == null || apkPath.isEmpty()) return result;

        try {
            DexFile dex = new DexFile(apkPath);
            try {
                Enumeration<String> entries = dex.entries();
                while (entries.hasMoreElements()) result.add(entries.nextElement());
            } finally {
                dex.close();
            }
        } catch (IOException | RuntimeException ignored) {
            // Discovery is best-effort. The caller receives an empty result
            // rather than crashing the target application.
        }
        return result;
    }

    public static final class Candidate {
        public final Class<?> owner;
        public final Method method;
        public final int score;
        public final List<String> evidence;

        Candidate(Class<?> owner, Method method, int score, List<String> evidence) {
            this.owner = owner;
            this.method = method;
            this.score = score;
            this.evidence = evidence;
        }
    }

    public static final class DiscoveryResult {
        public final boolean success;
        public final String reason;
        public final Candidate selected;
        public final List<Candidate> candidates;

        private DiscoveryResult(boolean success, String reason, Candidate selected,
                                List<Candidate> candidates) {
            this.success = success;
            this.reason = reason;
            this.selected = selected;
            this.candidates = candidates;
        }

        static DiscoveryResult success(Candidate selected, List<Candidate> candidates) {
            return new DiscoveryResult(true, null, selected, candidates);
        }

        static DiscoveryResult failure(String reason, List<Candidate> candidates) {
            return new DiscoveryResult(false, reason, null, candidates);
        }
    }
}
