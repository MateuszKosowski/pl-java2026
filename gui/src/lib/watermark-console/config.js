export const tabs = [
    { id: "embed", label: "Embed" },
    { id: "detect", label: "Detect" },
    { id: "extract", label: "Extract" },
    { id: "visualize", label: "Visualize" },
    { id: "pricing", label: "Pricing" },
];

export const planOrder = ["FREE", "STANDARD", "PRO"];

export const maxImageSizeBytes = 20_000_000;
export const maxImageSizeLabel = "20 MB";

export const operationCosts = {
    CAPACITY_CHECK: 0,
    DETECT: 1,
    EXTRACT: 2,
    VISUALIZE: 3,
    EMBED_768: 5,
    EMBED_1024: 8,
    AI_CLASSIFICATION: 2,
};

export const operationLabels = {
    CAPACITY_CHECK: "Capacity check",
    DETECT: "Detect",
    EXTRACT: "Extract",
    VISUALIZE: "Visualize",
    EMBED_768: "Embed basic",
    EMBED_1024: "Embed large",
    AI_CLASSIFICATION: "AI classification",
};
