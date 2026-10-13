import { CommonModule } from "@angular/common";
import { AfterViewInit, Component, ElementRef, Input, OnChanges, OnDestroy, ViewChild } from "@angular/core";
import { IonicModule } from "@ionic/angular";
import { TranslateModule, TranslateService } from "@ngx-translate/core";
import { Chart, registerables } from "chart.js";
import { Subscription } from "rxjs";
import { displayPrice, normalizeForecast } from "./forecast-utils";
import { JsonrpcRequest } from "src/app/shared/jsonrpc/base";
import { ComponentJsonApiRequest } from "src/app/shared/jsonrpc/request/componentJsonApiRequest";
import { Edge, Websocket } from "src/app/shared/shared";

Chart.register(...registerables);

interface ForecastResponse {
    result?: { prices?: unknown };
}

/**
 * Displays the provider's quarter-hourly forecast independently of an ESS controller.
 * The API returns currency/MWh; EUR/MWh is converted to ct/kWh for display.
 */
@Component({
    selector: "oe-tariff-price-chart",
    standalone: true,
    imports: [CommonModule, IonicModule, TranslateModule],
    template: `
        <ion-card>
            <ion-card-header>
                <ion-card-title>{{ title }}</ion-card-title>
                <ion-card-subtitle>{{ alias }}</ion-card-subtitle>
            </ion-card-header>
            <ion-card-content>
                <div style="height: 240px; position: relative"><canvas #chartCanvas></canvas></div>
                <ion-note *ngIf="message">{{ message }}</ion-note>
            </ion-card-content>
        </ion-card>
    `,
})
export class TariffPriceChartComponent implements AfterViewInit, OnChanges, OnDestroy {
    @Input({ required: true }) public componentId!: string;
    @Input({ required: true }) public edge!: Edge;
    @Input() public alias: string = "";
    @Input() public currency: string = "";

    @ViewChild("chartCanvas") private chartCanvas!: ElementRef<HTMLCanvasElement>;

    public message: string = "";
    public get title(): string { return this.localized("TITLE"); }
    private chart: Chart<"line"> | null = null;
    private refreshInterval: ReturnType<typeof setInterval> | null = null;
    private ready = false;
    private destroyed = false;
    private requestVersion = 0;
    private languageSubscription: Subscription;
    private lastPrices: { timestamp: string; price: number }[] = [];

    constructor(private websocket: Websocket, private translate: TranslateService) {
        this.languageSubscription = this.translate.onLangChange.subscribe(() => {
            this.updateLabels();
        });
    }

    public ngAfterViewInit(): void {
        this.ready = true;
        void this.refresh();
        this.refreshInterval = setInterval(() => void this.refresh(), 5 * 60 * 1000);
    }

    public ngOnChanges(): void {
        if (this.ready) {
            void this.refresh();
        }
    }

    public ngOnDestroy(): void {
        if (this.refreshInterval != null) {
            clearInterval(this.refreshInterval);
        }
        this.destroyed = true;
        this.requestVersion++;
        this.languageSubscription.unsubscribe();
        this.chart?.destroy();
    }

    private async refresh(): Promise<void> {
        if (!this.edge || !this.componentId || !this.ready) {
            return;
        }
        const requestedComponent = this.componentId;
        const requestedEdge = this.edge;
        const version = ++this.requestVersion;
        try {
            const response = (await this.edge.sendRequest(this.websocket, new ComponentJsonApiRequest({
                componentId: requestedComponent,
                payload: new JsonrpcRequest("getPriceForecast", {}),
            }))) as ForecastResponse;

            if (this.destroyed || version !== this.requestVersion || requestedComponent !== this.componentId || requestedEdge !== this.edge) {
                return;
            }
            const prices = normalizeForecast(response?.result?.prices, Date.now());
            if (prices.length === 0) {
                this.message = this.localized("EMPTY");
                this.lastPrices = [];
                this.chart?.destroy();
                this.chart = null;
                return;
            }

            this.message = "";
            this.lastPrices = prices;
            this.renderChart();
        } catch {
            if (!this.destroyed && version === this.requestVersion && requestedComponent === this.componentId && requestedEdge === this.edge) {
                this.message = this.localized("UNAVAILABLE");
            }
        }
    }

    private localized(key: "TITLE" | "PRICE" | "EMPTY" | "UNAVAILABLE"): string {
        const translationKey = `EDGE.INDEX.TARIFF_FORECAST.${key}`;
        const translated = this.translate.instant(translationKey);
        if (translated !== translationKey) {
            return translated;
        }
        const english: Record<typeof key, string> = {
            TITLE: "Electricity price forecast", PRICE: "Electricity price",
            EMPTY: "No future tariff prices available", UNAVAILABLE: "Price forecast temporarily unavailable",
        };
        return english[key];
    }

    private updateLabels(): void {
        if (this.chart) {
            this.renderChart();
        }
    }

    private renderChart(): void {
        if (!this.ready || this.destroyed || this.lastPrices.length === 0) {
            return;
        }
        const labels = this.lastPrices.map(item => new Date(item.timestamp).toLocaleString(this.translate.currentLang || undefined, {
            weekday: "short", hour: "2-digit", minute: "2-digit",
        }));
        const values = this.lastPrices.map(item => displayPrice(item.price, this.currency));
        const unit = this.currency === "EUR" ? "ct/kWh" : `${this.currency || "Currency"}/MWh`;
        const label = `${this.localized("PRICE")} (${unit})`;
        if (this.chart) {
            this.chart.data.labels = labels;
            this.chart.data.datasets[0].data = values;
            this.chart.data.datasets[0].label = label;
            this.chart.update("none");
            return;
        }
        const dataset = { label, data: values, stepped: "before" as const, pointRadius: 0, pointHitRadius: 8, borderWidth: 2, datalabels: { display: false } };
        this.chart = new Chart(this.chartCanvas.nativeElement, {
            type: "line",
            data: { labels, datasets: [dataset] },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: { intersect: false, mode: "nearest", axis: "x" },
                scales: { x: { ticks: { maxTicksLimit: 9, autoSkip: true } } },
            },

        });
    }

}
