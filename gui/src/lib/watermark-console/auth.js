export function readRole(jwt) {
    try {
        const parts = jwt.split(".");
        if (parts.length < 2) return "USER";
        const segment = parts[1].replace(/-/g, "+").replace(/_/g, "/");
        const padded = segment + "=".repeat((4 - (segment.length % 4)) % 4);
        const payload = JSON.parse(atob(padded));
        if (payload.role) return String(payload.role).toUpperCase();
        if (payload.sub === "admin" && payload.userId === 1) return "ADMIN";
    } catch {
        return "USER";
    }
    return "USER";
}
