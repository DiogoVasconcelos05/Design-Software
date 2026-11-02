package pt.lisboapedala.redistribution_planning.query;

import java.util.List;

/**
 * Consulta só de leitura publicada por redistribution_planning para as rotas das carrinhas (endpoint 18 de
 * ARCHITECTURE.md §7.3, Req. 6). Usada pela api; a implementação ({@code RedistributionService}) é privada
 * ao módulo. Sugerir rotas não altera estado: só lê a ocupação através de {@code StationOccupancyQuery}.
 */
public interface RedistributionQuery {

    /**
     * Rotas sugeridas, no máximo uma por carrinha. Lança {@code IllegalArgumentException} se {@code vans} ou
     * {@code vanCapacity} não forem positivos.
     */
    List<VanRoute> suggestRoutes(int vans, int vanCapacity);
}
