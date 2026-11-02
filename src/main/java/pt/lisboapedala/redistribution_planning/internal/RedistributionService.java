package pt.lisboapedala.redistribution_planning.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pt.lisboapedala.redistribution_planning.query.RedistributionQuery;
import pt.lisboapedala.redistribution_planning.query.RouteStop;
import pt.lisboapedala.redistribution_planning.query.VanRoute;
import pt.lisboapedala.station_inventory.query.StationOccupancy;
import pt.lisboapedala.station_inventory.query.StationOccupancyQuery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Serviço responsável pelo cálculo e otimização das rotas de reequilíbrio de frota.
 * Não utiliza persistência (BD) nem anotações transacionais, garantindo uma execução pura em memória (FF-07, FF-08).
 */
@Service
public class RedistributionService implements RedistributionQuery {

    private static final Logger log = LoggerFactory.getLogger(RedistributionService.class);

    private final StationOccupancyQuery stationOccupancyQuery;

    public RedistributionService(StationOccupancyQuery stationOccupancyQuery) {
        this.stationOccupancyQuery = Objects.requireNonNull(stationOccupancyQuery, "stationOccupancyQuery não pode ser nula");
    }

    /**
     * Calcula as sugestões de rotas de redistribuição para as carrinhas operacionais.
     *
     * @param vans        Número de carrinhas disponíveis para redistribuição (> 0).
     * @param vanCapacity Capacidade máxima de transporte de cada carrinha (> 0).
     * @return Lista de rotas sugeridas por carrinha.
     */
    @Override
    public List<VanRoute> suggestRoutes(int vans, int vanCapacity) {
        if (vans <= 0) {
            throw new IllegalArgumentException("O número de carrinhas deve ser superior a 0");
        }
        if (vanCapacity <= 0) {
            throw new IllegalArgumentException("A capacidade da carrinha deve ser superior a 0");
        }

        List<StationOccupancy> stations = stationOccupancyQuery.findAll();
        if (stations.isEmpty()) {
            log.info("Nenhuma estação encontrada para planeamento de redistribuição.");
            return List.of();
        }

        // Deteção de desequilíbrios: idealmente cada estação deve estar a ~50% de ocupação
        List<StationBalance> surpluses = new ArrayList<>();
        List<StationBalance> deficits = new ArrayList<>();

        for (StationOccupancy station : stations) {
            int capacity = station.totalDocks(); // Corrigo: utiliza totalDocks() em vez de capacity()
            if (capacity <= 0) {
                continue;
            }

            int currentBikes = station.availableBikes();
            int idealBikes = capacity / 2;
            int diff = currentBikes - idealBikes;

            if (diff > 0) {
                surpluses.add(new StationBalance(station, diff));
            } else if (diff < 0) {
                deficits.add(new StationBalance(station, Math.abs(diff)));
            }
        }

        // Ordena por maior excesso e maior falta de bicicletas
        surpluses.sort(Comparator.comparingInt(StationBalance::amount).reversed());
        deficits.sort(Comparator.comparingInt(StationBalance::amount).reversed());

        List<VanRoute> routes = new ArrayList<>();

        int surplusIdx = 0;
        int deficitIdx = 0;

        for (int vanIndex = 1; vanIndex <= vans; vanIndex++) {
            if (surplusIdx >= surpluses.size() || deficitIdx >= deficits.size()) {
                break; // Sem mais reequilíbrios pendentes
            }

            String vanId = "VAN-" + vanIndex;
            List<RouteStop> stops = new ArrayList<>();
            int currentVanLoad = 0;
            int stopSequence = 1;

            // 1. Fase de Recolha (PICKUP) em estações com excesso
            while (surplusIdx < surpluses.size() && currentVanLoad < vanCapacity) {
                StationBalance surplus = surpluses.get(surplusIdx);
                int spaceLeftInVan = vanCapacity - currentVanLoad;
                int toPickup = Math.min(surplus.amount(), spaceLeftInVan);

                stops.add(new RouteStop(
                        stopSequence++,
                        surplus.station().stationId(),
                        surplus.station().name(),
                        RouteStop.ACTION_PICKUP,
                        toPickup
                ));

                currentVanLoad += toPickup;
                int remainingSurplus = surplus.amount() - toPickup;

                if (remainingSurplus > 0) {
                    surpluses.set(surplusIdx, new StationBalance(surplus.station(), remainingSurplus));
                } else {
                    surplusIdx++;
                }
            }

            // 2. Fase de Entrega (DROP_OFF) em estações com défice
            int totalBikesMovedByVan = currentVanLoad;
            while (deficitIdx < deficits.size() && currentVanLoad > 0) {
                StationBalance deficit = deficits.get(deficitIdx);
                int toDrop = Math.min(deficit.amount(), currentVanLoad);

                stops.add(new RouteStop(
                        stopSequence++,
                        deficit.station().stationId(),
                        deficit.station().name(),
                        RouteStop.ACTION_DROP_OFF,
                        toDrop
                ));

                currentVanLoad -= toDrop;
                int remainingDeficit = deficit.amount() - toDrop;

                if (remainingDeficit > 0) {
                    deficits.set(deficitIdx, new StationBalance(deficit.station(), remainingDeficit));
                } else {
                    deficitIdx++;
                }
            }

            if (!stops.isEmpty()) {
                routes.add(new VanRoute(vanId, stops, totalBikesMovedByVan));
            }
        }

        log.info("Planeamento de redistribuição concluído: {} rotas geradas para {} carrinhas.", routes.size(), vans);
        return List.copyOf(routes);
    }

    private record StationBalance(StationOccupancy station, int amount) {}
}