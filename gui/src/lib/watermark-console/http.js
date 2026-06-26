import { maxImageSizeLabel } from "./config";

export function createApiClient(getToken, onUnauthorized) {
    async function request(url, options = {}) {
        const response = await fetch(url, {
            ...options,
            headers: {
                ...(options.headers ?? {}),
                Authorization: `Bearer ${getToken()}`,
            },
        });
        if (response.status === 401) {
            onUnauthorized();
            throw new Error("Sesja wygasła.");
        }
        return response;
    }

    async function get(url) {
        const response = await request(url);
        if (!response.ok) throw new Error(await readError(response));
        return response.json();
    }

    async function post(url, body) {
        const response = await request(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body),
        });
        if (!response.ok) throw new Error(await readError(response));
        return response.json();
    }

    function postImage(url, image, text, options = {}) {
        const formData = new FormData();
        formData.append("image", image);
        if (text !== undefined) formData.append("text", text);
        return request(url, {
            method: "POST",
            body: formData,
            signal: options.signal,
        });
    }

    return { get, post, postImage };
}

export async function readError(response) {
    if (response.status === 413) {
        return `Wybrany obraz jest za duży. Maksymalny rozmiar pliku to ${maxImageSizeLabel}.`;
    }
    try {
        const data = await response.json();
        if (typeof data.detail === "string") return data.detail;
        if (data.detail?.message) return data.detail.message;
        if (data.message) return data.message;
        if (data.error) return data.error;
        if (data.code) return data.code;
    } catch {
        // Fall through to the generic message.
    }
    return "Wystąpił błąd podczas przetwarzania.";
}
