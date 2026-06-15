package pl.zzpj.ai_service.support;

import ai.onnxruntime.OrtEnvironment;

/**
 * Detects whether the ONNX Runtime native library can actually initialize in the
 * current JVM / host.
 *
 * <p>On some host + JDK combinations the bundled {@code onnxruntime.dll} fails to load
 * with an {@link UnsatisfiedLinkError} ("DLL initialization routine failed") even though
 * the pure-Java classes are present (observed with onnxruntime 1.21.1 under JDK 21 on
 * Windows, while the same lib loads fine under JDK 25). This is an environment-level
 * defect, not a defect in the service code.
 *
 * <p>Tests that exercise the real ONNX model use {@link #isAvailable()} together with a
 * JUnit assumption so they run (and assert real classification) wherever the native
 * runtime works, and are skipped — not failed — where it cannot initialize.
 */
public final class OnnxRuntimeAvailability {

    private OnnxRuntimeAvailability() {
    }

    /**
     * @return {@code true} if the ONNX Runtime native library initializes successfully.
     */
    public static boolean isAvailable() {
        try {
            OrtEnvironment env = OrtEnvironment.getEnvironment();
            return env != null;
        } catch (Throwable t) {
            return false;
        }
    }
}
