package in.retailflow.api.security.tenant;

final class TenantBypassHolder {

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private TenantBypassHolder() {}

    static void enter() {
        DEPTH.set(DEPTH.get() + 1);
    }

    static void exit() {
        int depth = DEPTH.get() - 1;
        if (depth <= 0) {
            DEPTH.remove();
        } else {
            DEPTH.set(depth);
        }
    }

    static boolean isBypass() {
        return DEPTH.get() > 0;
    }
}
