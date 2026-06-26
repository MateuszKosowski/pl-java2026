import { operationCosts, operationLabels, planOrder } from "./config";

const textEncoder = new TextEncoder();

export function canUpgrade(currentPlanCode, targetPlan) {
    return planOrder.indexOf(targetPlan) > planOrder.indexOf(currentPlanCode);
}

export function formatPlanExpiry(activeUntil) {
    if (!activeUntil) return "No expiration";
    return new Intl.DateTimeFormat("pl-PL", {
        dateStyle: "medium",
        timeStyle: "short",
    }).format(new Date(activeUntil));
}

export function operationCost(operation) {
    return operationCosts[operation] ?? 0;
}

export function operationName(operation) {
    return operationLabels[operation] ?? operation;
}

export function utf8ByteLength(text) {
    return textEncoder.encode(text).length;
}

export function fileSignature(file) {
    return `${file.name}|${file.size}|${file.lastModified}`;
}
