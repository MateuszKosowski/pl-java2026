<script>
    import { onMount } from "svelte";
    import "./watermark-console.css";
    import { readRole } from "$lib/watermark-console/auth";
    import AccountPanel from "$lib/watermark-console/components/AccountPanel.svelte";
    import DetectPanel from "$lib/watermark-console/components/DetectPanel.svelte";
    import ExtractPanel from "$lib/watermark-console/components/ExtractPanel.svelte";
    import PricingPanel from "$lib/watermark-console/components/PricingPanel.svelte";
    import Tabs from "$lib/watermark-console/components/Tabs.svelte";
    import VisualizePanel from "$lib/watermark-console/components/VisualizePanel.svelte";
    import {
        canUpgrade,
        fileSignature,
        operationCost,
        operationName,
        utf8ByteLength,
    } from "$lib/watermark-console/domain";
    import { createApiClient, readError } from "$lib/watermark-console/http";
    import {
        maxImageSizeBytes,
        maxImageSizeLabel,
        tabs,
    } from "$lib/watermark-console/config";

    let token = $state("");
    let activeTab = $state("embed");
    let accountLoading = $state(true);
    let accountError = $state("");
    let subscription = $state(null);
    let tokenBalance = $state(null);
    let plans = $state([]);
    let currentRole = $state("USER");

    let paymentSession = $state(null);
    let paymentProcessing = $state(false);
    let paymentError = $state("");

    let embedFiles = $state();
    let watermarkText = $state("");
    let embedProcessing = $state(false);
    let resultImageUrl = $state(null);
    let classification = $state(null);
    let embedError = $state("");
    let capacity = $state(null);
    let capacityChecking = $state(false);
    let capacityError = $state(null);
    let capacityAbort = null;
    let lastProbedFileSignature = "";

    let detectFiles = $state();
    let detectProcessing = $state(false);
    let detectResult = $state(null);
    let detectError = $state("");

    let extractFiles = $state();
    let extractProcessing = $state(false);
    let extractResult = $state(null);
    let extractNotice = $state("");
    let extractError = $state("");

    let visualizeFiles = $state();
    let visualizeProcessing = $state(false);
    let visualizeImageUrl = $state(null);
    let visualizeError = $state("");

    const api = createApiClient(
        () => token,
        () => logout(),
    );

    onMount(async () => {
        token = localStorage.getItem("jwt_token") ?? "";
        if (!token) {
            window.location.href = "/login";
            return;
        }
        currentRole = readRole(token);
        await loadAccount();
    });

    function logout() {
        localStorage.removeItem("jwt_token");
        window.location.href = "/login";
    }

    async function loadAccount() {
        accountLoading = true;
        accountError = "";
        try {
            plans = await api.get("/api/subscriptions/plans");
            subscription = await api.get("/api/subscriptions/me");
            tokenBalance = await api.get("/api/subscriptions/me/tokens");
        } catch (error) {
            accountError =
                error instanceof Error
                    ? error.message
                    : "Nie udało się pobrać danych subskrypcji.";
        } finally {
            accountLoading = false;
        }
    }

    async function refreshTokens() {
        try {
            tokenBalance = await api.get("/api/subscriptions/me/tokens");
        } catch {
            accountError = "Nie udało się odświeżyć salda tokenów.";
        }
    }

    async function initiatePayment(targetPlan) {
        paymentError = "";
        paymentProcessing = true;
        try {
            paymentSession = await api.post("/api/payments/mock/sessions", {
                targetPlan,
            });
            activeTab = "pricing"; // Ensure we stay on pricing to see the session
        } catch (error) {
            paymentError =
                error instanceof Error
                    ? error.message
                    : "Błąd podczas tworzenia sesji płatności.";
        } finally {
            paymentProcessing = false;
        }
    }

    function canUpgradeTo(targetPlan) {
        return canUpgrade(subscription?.planCode, targetPlan);
    }

    async function completePayment(outcome) {
        if (!paymentSession) return;
        paymentError = "";
        paymentProcessing = true;
        try {
            paymentSession = await api.post(
                `/api/payments/mock/sessions/${paymentSession.id}/${outcome}`,
                {},
            );
            if (outcome === "succeed") {
                await loadAccount();
            }
        } catch (error) {
            paymentError =
                error instanceof Error
                    ? error.message
                    : "Błąd podczas finalizacji płatności.";
        } finally {
            paymentProcessing = false;
        }
    }

    function currentPlan() {
        if (!subscription) return null;
        return (
            plans.find((plan) => plan.code === subscription.planCode) ?? null
        );
    }

    function planAllows(operation) {
        const plan = currentPlan();
        return Boolean(plan?.allowedOperations?.includes(operation));
    }

    function availableTokens() {
        return tokenBalance?.availableTokens ?? 0;
    }

    function hasTokensFor(operation) {
        return availableTokens() >= operationCost(operation);
    }

    function embedOperation() {
        if (!capacity?.imageOk || !capacity.lengthBits) return null;
        return `EMBED_${capacity.lengthBits}`;
    }

    function embedActionStatus() {
        const operation = embedOperation();
        return operation ? actionStatus(operation) : "";
    }

    function embedInputDisabled() {
        return Boolean(embedActionStatus());
    }

    function embedAiStatus() {
        const operation = embedOperation();
        if (!operation || !tokenBalance) return "";
        const embedCost = operationCost(operation);
        const aiCost = operationCost("AI_CLASSIFICATION");
        if (!planAllows("AI_CLASSIFICATION"))
            return "AI classification will be skipped by this plan.";
        if (availableTokens() - embedCost >= aiCost)
            return `AI classification available: +${aiCost} tokens.`;
        return "AI classification will be skipped because the remaining token balance is too low.";
    }

    function actionStatus(operation) {
        if (!subscription || !tokenBalance) return "Ładowanie danych planu.";
        if (!planAllows(operation))
            return `Plan ${subscription.planCode} does not allow ${operationName(operation)}.`;
        if (!hasTokensFor(operation))
            return `Not enough tokens: requires ${operationCost(operation)}, available ${availableTokens()}.`;
        return "";
    }

    function tabOperation(tabId) {
        if (tabId === "detect") return "DETECT";
        if (tabId === "extract") return "EXTRACT";
        if (tabId === "visualize") return "VISUALIZE";
        return null;
    }

    function tabStatus(tabId) {
        const operation = tabOperation(tabId);
        return operation ? actionStatus(operation) : "";
    }

    function tabDisabled(tabId) {
        return Boolean(tabStatus(tabId));
    }

    function selectTab(tabId) {
        if (!tabDisabled(tabId)) activeTab = tabId;
    }

    function validateImage(files) {
        if (!files || files.length === 0) return "Wybierz obraz z dysku.";
        if (files[0].size < 100)
            return "Wybrany plik jest zbyt mały lub uszkodzony.";
        if (files[0].size > maxImageSizeBytes)
            return `Wybrany obraz jest za duży. Maksymalny rozmiar pliku to ${maxImageSizeLabel}.`;
        return "";
    }

    async function probeCapacity() {
        if (!embedFiles || embedFiles.length === 0) return;
        capacityAbort?.abort();
        const validation = validateImage(embedFiles);
        capacity = null;
        capacityError = validation || null;
        if (validation) {
            capacityChecking = false;
            return;
        }
        const controller = new AbortController();
        capacityAbort = controller;
        capacityChecking = true;
        try {
            const response = await api.postImage(
                "/api/watermark/capacity",
                embedFiles[0],
                undefined,
                { signal: controller.signal },
            );
            if (controller.signal.aborted) return;
            if (response.ok) {
                capacity = await response.json();
            } else {
                capacityError = await readError(response);
            }
        } catch (error) {
            if (error instanceof DOMException && error.name === "AbortError")
                return;
            capacityError = "Nie udało się sprawdzić pojemności obrazu.";
        } finally {
            if (capacityAbort === controller) {
                capacityAbort = null;
                capacityChecking = false;
            }
        }
    }

    $effect(() => {
        if (embedFiles && embedFiles.length > 0) {
            const sig = fileSignature(embedFiles[0]);
            if (sig !== lastProbedFileSignature) {
                lastProbedFileSignature = sig;
                probeCapacity();
            }
        } else {
            lastProbedFileSignature = "";
            capacity = null;
            capacityError = null;
        }
    });

    $effect(() => {
        if (tabDisabled(activeTab)) activeTab = "embed";
    });

    async function embedWatermark() {
        const validation = validateImage(embedFiles);
        if (validation) {
            embedError = validation;
            return;
        }
        if (capacityChecking) {
            embedError = "Poczekaj na sprawdzenie pojemności obrazu.";
            return;
        }
        if (!capacity?.imageOk) {
            embedError = "Obraz nie spełnia minimalnych wymagań watermarkingu.";
            return;
        }
        const operation = embedOperation();
        if (!operation) {
            embedError = "Nie udało się ustalić typu operacji.";
            return;
        }
        const status = actionStatus(operation);
        if (status) {
            embedError = status;
            return;
        }
        if (!watermarkText.trim()) {
            embedError = "Wpisz tekst do ukrycia.";
            return;
        }
        if (utf8ByteLength(watermarkText) > capacity.maxTextBytes) {
            embedError = `Tekst jest za długi. Limit dla tego obrazu to ${capacity.maxTextBytes} bajtów.`;
            return;
        }

        embedError = "";
        embedProcessing = true;
        resultImageUrl = null;
        classification = null;

        try {
            const response = await api.postImage(
                "/api/watermark/embed",
                embedFiles[0],
                watermarkText,
            );
            if (response.ok) {
                classification = readClassification(response.headers);
                resultImageUrl = URL.createObjectURL(await response.blob());
                await refreshTokens();
            } else {
                embedError = await readError(response);
                await refreshTokens();
            }
        } catch {
            embedError = "Błąd połączenia z serwerem.";
        } finally {
            embedProcessing = false;
        }
    }

    async function detectWatermark() {
        const validation = validateImage(detectFiles);
        if (validation) {
            detectError = validation;
            return;
        }
        const status = actionStatus("DETECT");
        if (status) {
            detectError = status;
            return;
        }

        detectError = "";
        detectProcessing = true;
        detectResult = null;
        try {
            const response = await api.postImage(
                "/api/watermark/detect",
                detectFiles[0],
            );
            if (response.ok) {
                detectResult = await response.json();
            } else {
                detectError = await readError(response);
            }
            await refreshTokens();
        } catch {
            detectError = "Błąd połączenia z serwerem.";
        } finally {
            detectProcessing = false;
        }
    }

    async function extractWatermark() {
        const validation = validateImage(extractFiles);
        if (validation) {
            extractError = validation;
            return;
        }
        const status = actionStatus("EXTRACT");
        if (status) {
            extractError = status;
            return;
        }

        extractError = "";
        extractNotice = "";
        extractProcessing = true;
        extractResult = null;
        try {
            const response = await api.postImage(
                "/api/watermark/extract",
                extractFiles[0],
            );
            if (response.ok) {
                extractResult = await response.json();
            } else if (response.status === 400) {
                extractNotice = await readError(response);
            } else if (response.status === 403) {
                extractError =
                    currentRole === "ADMIN"
                        ? "Admin should be allowed to read this watermark. Log in again to refresh the role claim."
                        : "Nie jesteś właścicielem tego znaku wodnego.";
            } else {
                extractError = await readError(response);
            }
            await refreshTokens();
        } catch {
            extractError = "Błąd połączenia z serwerem.";
        } finally {
            extractProcessing = false;
        }
    }

    async function visualizeWatermark() {
        const validation = validateImage(visualizeFiles);
        if (validation) {
            visualizeError = validation;
            return;
        }
        const status = actionStatus("VISUALIZE");
        if (status) {
            visualizeError = status;
            return;
        }

        visualizeError = "";
        visualizeProcessing = true;
        visualizeImageUrl = null;
        try {
            const response = await api.postImage(
                "/api/watermark/visualize",
                visualizeFiles[0],
            );
            if (response.ok) {
                visualizeImageUrl = URL.createObjectURL(await response.blob());
            } else {
                visualizeError = await readError(response);
            }
            await refreshTokens();
        } catch {
            visualizeError = "Błąd połączenia z serwerem.";
        } finally {
            visualizeProcessing = false;
        }
    }

    function readClassification(headers) {
        const category = headers.get("X-Image-Category");
        const label = headers.get("X-Image-Label");
        const confidence = headers.get("X-Image-Confidence");
        const categoryConfidence = headers.get("X-Image-Category-Confidence");

        if (!category || category === "unknown") return null;
        const toPercent = (value) =>
            value ? Math.round(parseFloat(value) * 100) : null;
        return {
            category,
            label: label && label !== "unknown" ? label : null,
            confidence: toPercent(confidence),
            categoryConfidence: toPercent(categoryConfidence),
        };
    }
</script>

<main class="container">
    <header class="topbar">
        <div>
            <h1>Watermark Console</h1>
            <p>PNG watermarking with subscription-aware token limits.</p>
        </div>
        <button class="btn btn-outline compact" onclick={logout}>Logout</button>
    </header>

    <AccountPanel
        {accountLoading}
        {accountError}
        {subscription}
        {tokenBalance}
        {currentRole}
        onRefresh={loadAccount}
    />

    <Tabs {tabs} {activeTab} {tabStatus} onSelect={selectTab} />

    {#if activeTab === "embed"}
        <section class="panel">
            <div class="panel-heading">
                <h2>Embed watermark</h2>
                <span class="cost"
                    >{embedOperation()
                        ? `${operationName(embedOperation())}: ${operationCost(embedOperation())} tokens`
                        : "Capacity check is free"}</span
                >
            </div>

            <label class="field">
                <span>Base image</span>
                <input type="file" accept="image/png" bind:files={embedFiles} />
            </label>

            {#if capacityChecking}
                <div class="notice">Checking image capacity...</div>
            {:else if capacityError}
                <div class="alert alert-error">{capacityError}</div>
            {:else if capacity && !capacity.imageOk}
                <div class="alert alert-error">
                    Image is too small: {capacity.imageWidth}x{capacity.imageHeight}.
                    Minimum is {capacity.minImageWidth}x{capacity.minImageHeight}.
                </div>
            {:else if capacity}
                {@const embedStatus = embedActionStatus()}
                <div class="capacity-grid">
                    <div>
                        <span>Size</span><strong
                            >{capacity.imageWidth}x{capacity.imageHeight}</strong
                        >
                    </div>
                    <div>
                        <span>Tier</span><strong>{embedOperation()}</strong>
                    </div>
                    <div>
                        <span>Text limit</span><strong
                            >{capacity.maxTextBytes} B</strong
                        >
                    </div>
                    <div class:blocked={embedStatus}>
                        <span>Plan check</span><strong
                            >{embedStatus || "Allowed"}</strong
                        >
                    </div>
                </div>
                {#if embedStatus}
                    <div class="alert alert-warning">
                        This image requires {operationName(embedOperation())},
                        which is not available for the current plan or token
                        balance. The embed action is disabled for this image.
                    </div>
                {/if}
                {#if embedAiStatus()}
                    <div class="notice">{embedAiStatus()}</div>
                {/if}
            {/if}

            <label class="field">
                <span>
                    Hidden text
                    {#if capacity?.imageOk}
                        <small
                            class:over={utf8ByteLength(watermarkText) >
                                capacity.maxTextBytes}
                        >
                            {utf8ByteLength(watermarkText)} / {capacity.maxTextBytes}
                            B
                        </small>
                    {/if}
                </span>
                <input
                    type="text"
                    bind:value={watermarkText}
                    placeholder="Text to hide in the image"
                    disabled={embedInputDisabled()}
                />
            </label>

            <button
                class="btn btn-primary"
                onclick={embedWatermark}
                disabled={embedProcessing ||
                    capacityChecking ||
                    (capacity !== null && !capacity.imageOk) ||
                    embedInputDisabled()}
            >
                {embedProcessing ? "Processing..." : "Generate watermarked PNG"}
            </button>

            {#if embedError}
                <div class="alert alert-error">{embedError}</div>
            {/if}
        </section>

        {#if resultImageUrl}
            <section class="panel result-panel">
                <h2>Watermarked image</h2>
                {#if classification}
                    <div class="notice">
                        AI classification: <strong
                            >{classification.category}</strong
                        >
                        {#if classification.categoryConfidence !== null}
                            ({classification.categoryConfidence}%)
                        {/if}
                        {#if classification.label}
                            - {classification.label}
                        {/if}
                    </div>
                {:else}
                    <div class="notice">
                        AI classification was skipped or unavailable.
                    </div>
                {/if}
                <div class="image-frame">
                    <img src={resultImageUrl} alt="Watermarked output" />
                </div>
                <a
                    href={resultImageUrl}
                    download="watermarked_image.png"
                    class="download-link"
                >
                    <button class="btn btn-success">Download PNG</button>
                </a>
            </section>
        {/if}
    {:else if activeTab === "detect"}
        <DetectPanel
            processing={detectProcessing}
            result={detectResult}
            error={detectError}
            status={actionStatus("DETECT")}
            onFilesChange={(files) => (detectFiles = files)}
            onSubmit={detectWatermark}
        />
    {:else if activeTab === "extract"}
        <ExtractPanel
            processing={extractProcessing}
            result={extractResult}
            notice={extractNotice}
            error={extractError}
            status={actionStatus("EXTRACT")}
            onFilesChange={(files) => (extractFiles = files)}
            onSubmit={extractWatermark}
        />
    {:else if activeTab === "visualize"}
        <VisualizePanel
            processing={visualizeProcessing}
            imageUrl={visualizeImageUrl}
            error={visualizeError}
            status={actionStatus("VISUALIZE")}
            onFilesChange={(files) => (visualizeFiles = files)}
            onSubmit={visualizeWatermark}
        />
    {:else if activeTab === "pricing"}
        <PricingPanel
            {plans}
            {subscription}
            {paymentSession}
            {paymentProcessing}
            {paymentError}
            {canUpgradeTo}
            onInitiatePayment={initiatePayment}
            onCompletePayment={completePayment}
            onDismissPayment={() => (paymentSession = null)}
        />
    {/if}
</main>
