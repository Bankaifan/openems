import { CommonModule } from "@angular/common";
import { AfterViewInit, Component, ElementRef, Input, OnChanges, OnDestroy, ViewChild } from "@angular/core";
import { IonicModule } from "@ionic/angular";
import { Chart, registerables } from "chart.js";
import { ComponentJsonApiRequest } from "src/app/shared/jsonrpc/request/componentJsonApiRequest";
import { JsonrpcRequest } from "src/app/shared/jsonrpc/base";
import { Edge, Websocket } from "src/app/shared/shared";

Chart.register(...registerables);

interface ForecastEntry {
    timestamp: string;
    price?: number;
}

interface ForecastResponse {
    result?: { prices?: ForecastEntry[] };
}

/**
 * Displays the provider's quarter-hourly forecast independently of an ESS controller.
 * The API returns currency/MWh; EUR/MWh is converted to ct/kWh for display.
 */
@Component({
    selector: "oe-tariff-price-chart",
    standalone: true,
    imports: [CommonModule, IonicModule],
    template: `
        <ion-card>
            <ion-card-header>
                <ion-card-title>Electricity price forecast</ion-card-title>
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

    @ViewChild("chartCanvas") private chartCanvas!: ElementRef<HTMLCanvasElement>;

    public message: string = "";
    private chart: Chart<"line"> | null = null;
    private refreshInterval: ReturnType<typeof setInterval> | null = null;
    private ready = false;

    constructor(private websocket: Websocket) { }

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
        this.chart?.destroy();
    }

    private async refresh(): Promise<void> {
        if (!this.edge || !this.componentId || !this.ready) {
            return;
        }
        const requestedComponent = this.componentId;
        try {
            const response = (await this.edge.sendRequest(this.websocket, new ComponentJsonApiRequest({
                componentId: requestedComponent,
                payload: new JsonrpcRequest("getPriceForecast", {}),
            }))) as ForecastResponse;

            if (requestedComponent !== this.componentId) {
                return;
            }
            const now = Date.now();
            const prices = (response?.result?.prices ?? [])
                .filter((entry) => Number.isFinite(Date.parse(entry.timestamp))
                    && Date.parse(entry.timestamp) + 15 * 60 * 1000 > now
                    && typeof entry.price === "number" && Number.isFinite(entry.price))
                .sort((a, b) => Date.parse(a.timestamp) - Date.parse(b.timestamp));

            if (prices.length === 0) {
                this.message = "No future tariff prices available";
                this.chart?.destroy();
                this.chart = null;
                return;
            }

            this.message = "";
            const labels = prices.map((item) => new Date(item.timestamp).toLocaleString(undefined, {
                weekday: "short", hour: "2-digit", minute: "2-digit",
            }));
            const values = prices.map((item) => (item.price as number) / 10);
            this.chart?.destroy();
            this.chart = new Chart(this.chartCanvas.nativeElement, {
                type: "line",
                data: {
                    labels,
                    datasets: [{
                        label: "Electricity price (ct/kWh)",
                        data: values,
                        stepped: "before",
                        pointRadius: 0,
                        pointHitRadius: 8,
                        borderWidth: 2,
                    }],
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    interaction: { intersect: false, mode: "index" },
                    scales: { x: { ticks: { maxTicksLimit: 9, autoSkip: true } } },
                },
            });
        } catch {
            if (requestedComponent === this.componentId) {
                this.message = "Price forecast temporarily unavailable";
            }
        }
    }
}
