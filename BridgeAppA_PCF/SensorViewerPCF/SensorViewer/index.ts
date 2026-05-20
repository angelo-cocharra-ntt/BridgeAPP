import { IInputs, IOutputs } from "./generated/ManifestTypes";

/**
 * SensorViewer PCF Component
 *
 * Faz fetch a um endpoint JSON servido pela App B (Ktor @ localhost:8080/api/sensors)
 * e expõe cada campo do contador como output bound — permitindo que a Canvas App
 * leia os valores com `SensorViewer1.voltageL1`, `SensorViewer1.currentTotal`, etc.
 * e os grave no Dynamics via `Patch()`.
 *
 * Inputs:
 *   serverUrl              — URL base do servidor (default http://localhost:8080)
 *   counterId              — ID do contador (enviado como ?counterId=...)
 *   refreshIntervalSeconds — intervalo de refresh automático (0 desativa)
 *   refreshTrigger         — alterar este valor força nova leitura imediata
 *
 * Outputs (bound): todos os campos do MeterReading.
 *
 * Comunicação: 100% local no dispositivo Android (WebView → localhost:8080).
 */

const DEFAULT_SERVER_URL = "https://localhost:8443";
const DEFAULT_REFRESH_SECONDS = 10;

interface MeterPayload {
    serialNumber?: string;
    model?: string;
    firmware?: string;
    manufacturer?: string;
    voltageL1?: number;
    voltageL2?: number;
    voltageL3?: number;
    currentL1?: number;
    currentL2?: number;
    currentL3?: number;
    currentTotal?: number;
    activePowerP?: number;
    activePowerN?: number;
    reactivePowerQp?: number;
    reactivePowerQn?: number;
    powerFactor?: number;
    status?: string;
    lastError?: string | null;
    timestamp?: number;
    deviceId?: string;
}

interface ApiResponse {
    current?: MeterPayload;
    history?: MeterPayload[];
    serverVersion?: string;
}

export class SensorViewer implements ComponentFramework.StandardControl<IInputs, IOutputs> {

    private container: HTMLDivElement;
    private notifyOutputChanged: () => void;

    private serverUrl = DEFAULT_SERVER_URL;
    private counterId = "";
    private refreshIntervalMs: number = DEFAULT_REFRESH_SECONDS * 1000;
    private lastRefreshTrigger = "";

    private refreshTimer: ReturnType<typeof setInterval> | null = null;
    private inFlight: AbortController | null = null;

    private latest: MeterPayload = { status: "LOADING" };

    // Elementos da UI mínima
    private statusEl: HTMLDivElement;
    private metaEl: HTMLDivElement;
    private bodyEl: HTMLDivElement;
    private errorEl: HTMLDivElement;

    public init(
        context: ComponentFramework.Context<IInputs>,
        notifyOutputChanged: () => void,
        _state: ComponentFramework.Dictionary,
        container: HTMLDivElement
    ): void {
        this.container = container;
        this.notifyOutputChanged = notifyOutputChanged;

        this.readInputs(context);
        this.buildUi();
        this.fetchNow();
        this.startAutoRefresh();
    }

    public updateView(context: ComponentFramework.Context<IInputs>): void {
        const oldServerUrl = this.serverUrl;
        const oldCounterId = this.counterId;
        const oldInterval = this.refreshIntervalMs;
        const oldTrigger = this.lastRefreshTrigger;

        this.readInputs(context);

        const triggerChanged = this.lastRefreshTrigger !== oldTrigger;
        const targetChanged = this.serverUrl !== oldServerUrl || this.counterId !== oldCounterId;
        const intervalChanged = this.refreshIntervalMs !== oldInterval;

        if (intervalChanged) {
            this.startAutoRefresh();
        }
        if (targetChanged || triggerChanged) {
            this.fetchNow();
        }
    }

    public getOutputs(): IOutputs {
        const r = this.latest;
        return {
            serialNumber: r.serialNumber ?? "",
            model: r.model ?? "",
            firmware: r.firmware ?? "",
            manufacturer: r.manufacturer ?? "",
            voltageL1: r.voltageL1 ?? 0,
            voltageL2: r.voltageL2 ?? 0,
            voltageL3: r.voltageL3 ?? 0,
            currentL1: r.currentL1 ?? 0,
            currentL2: r.currentL2 ?? 0,
            currentL3: r.currentL3 ?? 0,
            currentTotal: r.currentTotal ?? 0,
            activePowerP: r.activePowerP ?? 0,
            activePowerN: r.activePowerN ?? 0,
            reactivePowerQp: r.reactivePowerQp ?? 0,
            reactivePowerQn: r.reactivePowerQn ?? 0,
            powerFactor: r.powerFactor ?? 0,
            status: r.status ?? "",
            lastError: r.lastError ?? "",
            lastUpdated: r.timestamp ? new Date(r.timestamp) : new Date()
        };
    }

    public destroy(): void {
        this.stopAutoRefresh();
        if (this.inFlight) {
            this.inFlight.abort();
            this.inFlight = null;
        }
    }

    // -----------------------------------------------------------------
    // Inputs
    // -----------------------------------------------------------------
    private readInputs(context: ComponentFramework.Context<IInputs>): void {
        const rawUrl = context.parameters.serverUrl?.raw;
        this.serverUrl = (rawUrl && rawUrl.trim().length > 0)
            ? rawUrl.replace(/\/+$/, "")
            : DEFAULT_SERVER_URL;

        this.counterId = context.parameters.counterId?.raw ?? "";

        const intervalSec = context.parameters.refreshIntervalSeconds?.raw;
        this.refreshIntervalMs = (intervalSec && intervalSec > 0)
            ? intervalSec * 1000
            : (intervalSec === 0 ? 0 : DEFAULT_REFRESH_SECONDS * 1000);

        this.lastRefreshTrigger = context.parameters.refreshTrigger?.raw ?? "";
    }

    // -----------------------------------------------------------------
    // Fetch
    // -----------------------------------------------------------------
    private buildEndpointUrl(): string {
        const base = `${this.serverUrl}/api/sensors`;
        const params = new URLSearchParams();
        if (this.counterId) {
            params.set("counterId", this.counterId);
        }
        params.set("_t", Date.now().toString()); // cache-buster
        return `${base}?${params.toString()}`;
    }

    private fetchNow(): Promise<void> {
        // Cancela qualquer pedido em curso
        if (this.inFlight) {
            this.inFlight.abort();
        }
        const controller = new AbortController();
        this.inFlight = controller;

        const url = this.buildEndpointUrl();

        return fetch(url, {
            method: "GET",
            signal: controller.signal,
            headers: { "Accept": "application/json" }
        })
            .then(resp => {
                if (!resp.ok) {
                    throw new Error(`HTTP ${resp.status}`);
                }
                return resp.json() as Promise<ApiResponse | MeterPayload>;
            })
            .then(json => {
                // Aceita tanto { current: {...}, history: [...] } como o objeto direto
                const reading: MeterPayload =
                    (json as ApiResponse).current
                        ? (json as ApiResponse).current!
                        : (json as MeterPayload);

                this.latest = reading;
                this.renderReading();
                this.notifyOutputChanged();
                return;
            })
            .catch(err => {
                if ((err as Error).name === "AbortError") return;
                this.latest = {
                    ...this.latest,
                    status: "ERROR",
                    lastError: `Falha a contactar o servidor: ${(err as Error).message}`,
                    timestamp: Date.now()
                };
                this.renderReading();
                this.notifyOutputChanged();
            })
            .finally(() => {
                if (this.inFlight === controller) {
                    this.inFlight = null;
                }
            });
    }

    // -----------------------------------------------------------------
    // Refresh automático
    // -----------------------------------------------------------------
    private startAutoRefresh(): void {
        this.stopAutoRefresh();
        if (this.refreshIntervalMs <= 0) return;
        this.refreshTimer = setInterval(() => this.fetchNow(), this.refreshIntervalMs);
    }

    private stopAutoRefresh(): void {
        if (this.refreshTimer !== null) {
            clearInterval(this.refreshTimer);
            this.refreshTimer = null;
        }
    }

    // -----------------------------------------------------------------
    // UI mínima
    // -----------------------------------------------------------------
    private buildUi(): void {
        this.container.style.cssText =
            "width:100%;height:100%;padding:8px;margin:0;overflow:auto;" +
            "font-family:'Segoe UI',Roboto,Arial,sans-serif;color:#2c3e50;background:#f4f6f9;box-sizing:border-box;";

        this.statusEl = document.createElement("div");
        this.statusEl.style.cssText =
            "display:inline-block;padding:3px 10px;border-radius:99px;font-size:0.72rem;" +
            "color:white;font-weight:600;background:#f39c12;margin-bottom:6px;";

        this.metaEl = document.createElement("div");
        this.metaEl.style.cssText = "font-size:0.78rem;color:#7f8c8d;margin-bottom:10px;";

        this.errorEl = document.createElement("div");
        this.errorEl.style.cssText =
            "background:#fdf2f2;border:1px solid #f5c6cb;border-radius:8px;padding:8px;" +
            "font-size:0.8rem;color:#c0392b;margin-bottom:10px;display:none;";

        this.bodyEl = document.createElement("div");

        this.container.appendChild(this.statusEl);
        this.container.appendChild(this.metaEl);
        this.container.appendChild(this.errorEl);
        this.container.appendChild(this.bodyEl);
    }

    private renderReading(): void {
        const r = this.latest;

        // Status badge
        const statusColor =
            r.status === "OK"    ? "#27ae60" :
            r.status === "ERROR" ? "#e74c3c" : "#f39c12";
        const statusLabel =
            r.status === "OK"       ? "Ligado" :
            r.status === "ERROR"    ? "Erro" :
            r.status === "LOADING"  ? "A carregar..." : "A ligar...";
        this.statusEl.textContent = statusLabel;
        this.statusEl.style.background = statusColor;

        // Meta
        const ts = r.timestamp ? new Date(r.timestamp).toLocaleTimeString() : "—";
        const counterLabel = this.counterId ? ` · ${this.counterId}` : "";
        this.metaEl.textContent = `${ts}${counterLabel}`;

        // Erro
        if (r.status === "ERROR" && r.lastError) {
            this.errorEl.textContent = r.lastError;
            this.errorEl.style.display = "block";
        } else {
            this.errorEl.style.display = "none";
        }

        // Body
        const fmt = (n: number | undefined, d = 2) =>
            (n === undefined || n === null) ? "—" : n.toFixed(d);

        this.bodyEl.innerHTML = `
            <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(110px,1fr));gap:8px;">
              ${this.card("L1 (V)", `${r.voltageL1 ?? "—"}`, "#2980b9")}
              ${this.card("L2 (V)", `${r.voltageL2 ?? "—"}`, "#2980b9")}
              ${this.card("L3 (V)", `${r.voltageL3 ?? "—"}`, "#2980b9")}
              ${this.card("I Total (A)", fmt(r.currentTotal), "#27ae60")}
              ${this.card("P+ (W)", fmt(r.activePowerP, 1), "#e67e22")}
              ${this.card("P- (W)", fmt(r.activePowerN, 1), "#8e44ad")}
              ${this.card("cos φ", fmt(r.powerFactor, 3), "#16a085")}
            </div>
            ${r.serialNumber ? `
              <div style="margin-top:10px;font-size:0.78rem;color:#7f8c8d;">
                ${r.manufacturer ?? ""} ${r.model ?? ""} · SN ${r.serialNumber} · FW ${r.firmware ?? ""}
              </div>` : ""}
        `;
    }

    private card(label: string, value: string, color: string): string {
        return `
            <div style="background:white;border-radius:8px;padding:10px 8px;box-shadow:0 1px 4px rgba(0,0,0,0.07);text-align:center;">
              <div style="font-size:0.65rem;color:#95a5a6;text-transform:uppercase;letter-spacing:.4px;margin-bottom:4px;">${label}</div>
              <div style="font-size:1.3rem;font-weight:700;line-height:1;color:${color};">${value}</div>
            </div>`;
    }
}
