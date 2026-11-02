package pt.lisboapedala.municipal_reporting.query;

import java.time.YearMonth;

/**
 * Consulta só de leitura publicada por municipal_reporting para o relatório mensal (endpoint 19 de
 * ARCHITECTURE.md §7.3, US-4). Usada pela api; a implementação ({@code MonthlyReportService}) é privada ao módulo.
 */
public interface MonthlyReportQuery {

    /**
     * Agrega as viagens fechadas no mês (UTC) por freguesia de origem, com a estimativa de CO₂ evitado
     * (§7.4.4). Um mês sem viagens dá um relatório com totais a zero.
     */
    MonthlyReport generate(YearMonth month);
}
